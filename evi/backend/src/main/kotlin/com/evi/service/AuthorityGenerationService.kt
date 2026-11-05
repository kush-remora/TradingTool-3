package com.evi.service

import com.evi.client.EviHttpClient
import com.evi.model.AuthorityGenerationRequest
import com.evi.model.AuthorityGenerationResponse
import com.evi.model.AuthorityMode
import com.evi.model.VisualBookDebugInfo
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.time.Duration

private val AUTHORITY_PROVIDER_MODELS = listOf(
    "gemini-3-flash-preview",
    "gemini-3.1-flash-lite",
    "gemini-3.8-flash",
)
private val PRESERVE_PLACEMENT_STRATEGIES = setOf(
    "ADDED_PAGE_BEFORE_SOURCE",
    "ADDED_PAGE_AFTER_SOURCE",
)
private val LLM_REQUEST_TIMEOUT: Duration = Duration.ofSeconds(60)

class AuthorityGenerationService(
    private val apiKey: String,
    private val httpClient: EviHttpClient,
    private val objectMapper: ObjectMapper,
    private val promptRepository: AuthorityPromptRepository = AuthorityPromptRepository(objectMapper),
) {
    private val log = LoggerFactory.getLogger(AuthorityGenerationService::class.java)

    suspend fun generate(request: AuthorityGenerationRequest): AuthorityGenerationResponse {
        val mode = requireNotNull(request.authorityMode) { "authorityMode is required." }
        val startTime = System.currentTimeMillis()
        val contract = promptRepository.load(mode)
        val systemInstruction = renderTemplate(contract.systemInstruction, request)
        val prompt = renderUserTemplate(contract.userTemplate, request)
        val promptSnippet = prompt.take(300)

        if (apiKey.isBlank()) {
            return AuthorityGenerationResponse(
                authorityMode = mode,
                generationSource = "fallback",
                debug = debugInfo(
                    startTime = startTime,
                    mode = mode,
                    providerStatus = "missing_api_key",
                    errorMessage = "LLM provider credentials are not configured in the environment or local config.",
                    promptSnippet = promptSnippet,
                ),
            )
        }

        var lastError: Exception? = null
        var lastStatus: Int? = null
        var lastResponseSnippet: String? = null
        var lastEndpoint: String? = null
        var lastModel = AUTHORITY_PROVIDER_MODELS.first()

        for (model in AUTHORITY_PROVIDER_MODELS) {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
            lastEndpoint = endpoint
            lastModel = model
            try {
                val (rawBody, statusCode) = executeLlmCall(
                    endpoint = endpoint,
                    contract = contract,
                    systemInstruction = systemInstruction,
                    prompt = prompt,
                )
                lastStatus = statusCode
                lastResponseSnippet = rawBody.take(400)
                val generated = extractGeneratedJson(rawBody)
                validateGeneratedPayload(mode, generated)

                return AuthorityGenerationResponse(
                    authorityMode = mode,
                    generationSource = "llm",
                    result = generated,
                    debug = debugInfo(
                        startTime = startTime,
                        mode = mode,
                        providerStatus = "success",
                        model = model,
                        endpoint = endpoint,
                        promptSnippet = promptSnippet,
                        responseStatusCode = statusCode,
                        responseSnippet = lastResponseSnippet,
                    ),
                )
            } catch (error: Exception) {
                lastError = error
                log.warn("Authority generation failed for model '{}': {}", model, error.message)
            }
        }

        val totalLatency = System.currentTimeMillis() - startTime
        return AuthorityGenerationResponse(
            authorityMode = mode,
            generationSource = "fallback",
            debug = debugInfo(
                startTime = startTime,
                mode = mode,
                providerStatus = "error",
                errorMessage = lastError?.message ?: "LLM provider unavailable",
                model = lastModel,
                endpoint = lastEndpoint,
                promptSnippet = promptSnippet,
                responseStatusCode = lastStatus,
                responseSnippet = lastResponseSnippet ?: lastError?.message,
                latencyOverride = totalLatency,
            ),
        )
    }

    private suspend fun executeLlmCall(
        endpoint: String,
        contract: AuthorityPromptContract,
        systemInstruction: String,
        prompt: String,
    ): Pair<String, Int> {
        val payload = mapOf(
            "systemInstruction" to mapOf(
                "parts" to listOf(mapOf("text" to systemInstruction)),
            ),
            "contents" to listOf(
                mapOf("parts" to listOf(mapOf("text" to prompt))),
            ),
            "generationConfig" to mapOf(
                "responseMimeType" to "application/json",
                "responseJsonSchema" to contract.responseSchema,
            ),
        )

        val response = httpClient.postWithTimeout(
            url = endpoint,
            body = objectMapper.writeValueAsString(payload),
            headers = mapOf("x-goog-api-key" to apiKey, "Accept" to "application/json"),
            timeout = LLM_REQUEST_TIMEOUT,
        )
        if (response.statusCode !in 200..299) {
            throw IllegalStateException("HTTP ${response.statusCode}: ${response.body.take(300)}")
        }
        return response.body to response.statusCode
    }

    private fun renderUserTemplate(template: String, request: AuthorityGenerationRequest): String {
        return renderTemplate(template, request)
    }

    private fun renderTemplate(template: String, request: AuthorityGenerationRequest): String {
        val sourceContent = request.sourceDocumentContent.trim()
        val structure = request.sourceStructureJson?.trim()?.takeIf { it.isNotEmpty() }
            ?: defaultSourceStructure(sourceContent)
        val assets = objectMapper.writeValueAsString(request.visualAssets)
        return template
            .replace("{{SOURCE_DOCUMENT_CONTENT}}", sourceContent)
            .replace("{{SOURCE_STRUCTURE_JSON}}", structure)
            .replace("{{VISUAL_ASSETS_METADATA}}", assets)
            .replace("{{USER_INSTRUCTIONS}}", request.userInstructions?.trim().orEmpty())
    }

    private fun defaultSourceStructure(sourceContent: String): String = objectMapper.writeValueAsString(
        mapOf(
            "blocks" to listOf(
                mapOf(
                    "source_block_id" to "page-1-text",
                    "block_type" to "PAGE_TEXT",
                    "verbatim_text" to sourceContent,
                    "original_style_token" to "SOURCE_PAGE_TEXT",
                ),
            ),
        ),
    )

    private fun extractGeneratedJson(responseBody: String): JsonNode {
        val root = objectMapper.readTree(responseBody)
        val textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text")
        require(textNode.isTextual) { "LLM response did not include an authority payload." }
        return objectMapper.readTree(textNode.asText())
    }

    private fun validateGeneratedPayload(mode: AuthorityMode, payload: JsonNode) {
        require(payload.isObject) { "LLM authority payload must be a JSON object." }
        require(payload.path("authority_mode").asText() == mode.name) {
            "LLM returned the wrong authority_mode."
        }
        when (mode) {
            AuthorityMode.PRESERVE -> {
                require(payload.has("preserved_blocks")) {
                    "PRESERVE response is missing preserved_blocks."
                }
                val variants = payload.path("preserve_variants")
                require(variants.isArray && variants.size() == 2) {
                    "PRESERVE response must contain exactly two preserve_variants."
                }
                require(variants.all { it.isObject && it.path("variant_id").isTextual }) {
                    "Each PRESERVE variant must include a variant_id."
                }
                val placementStrategies = variants.map { it.path("placement_strategy").asText() }
                require(
                    placementStrategies.all { it in PRESERVE_PLACEMENT_STRATEGIES } &&
                        placementStrategies.toSet().size == 2,
                ) {
                    "PRESERVE variants must use two different additive page-placement strategies."
                }
            }
            AuthorityMode.RESTRUCTURE -> require(payload.has("restructured_sections")) {
                "RESTRUCTURE response is missing restructured_sections."
            }
            AuthorityMode.TRANSFORM -> {
                val variants = payload.path("transformation_variants")
                require(variants.isArray && variants.size() == 2) {
                    "TRANSFORM response must contain exactly two transformation_variants."
                }
                val variantIds = variants.map { it.path("variant_id") }
                require(
                    variantIds.all { it.isTextual } && variantIds.map { it.asText() }.toSet().size == 2,
                ) {
                    "TRANSFORM variants must contain two distinct variant_id values."
                }
            }
        }
    }

    private fun debugInfo(
        startTime: Long,
        mode: AuthorityMode,
        providerStatus: String,
        errorMessage: String? = null,
        model: String = AUTHORITY_PROVIDER_MODELS.first(),
        endpoint: String? = null,
        promptSnippet: String? = null,
        responseStatusCode: Int? = null,
        responseSnippet: String? = null,
        latencyOverride: Long? = null,
    ): VisualBookDebugInfo = VisualBookDebugInfo(
        latencyMs = latencyOverride ?: (System.currentTimeMillis() - startTime),
        apiKeyConfigured = apiKey.isNotBlank(),
        providerStatus = providerStatus,
        errorMessage = errorMessage,
        model = model,
        endpoint = endpoint,
        requestPromptSnippet = "[$mode] ${promptSnippet.orEmpty()}".take(300),
        responseStatusCode = responseStatusCode,
        responseSnippet = responseSnippet,
    )
}
