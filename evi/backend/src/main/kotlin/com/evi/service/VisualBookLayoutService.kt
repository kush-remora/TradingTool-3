package com.evi.service

import com.evi.client.EviHttpClient
import com.evi.model.VisualBookDebugInfo
import com.evi.model.VisualBookLayoutOption
import com.evi.model.VisualBookLayoutRequest
import com.evi.model.VisualBookLayoutResponse
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory

private val GEMINI_MODELS = listOf(
    "gemini-3-flash-preview",
    "gemini-3.1-flash-lite",
    "gemini-3.8-flash",
)

class VisualBookLayoutService(
    private val apiKey: String,
    private val httpClient: EviHttpClient,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(VisualBookLayoutService::class.java)

    suspend fun generate(request: VisualBookLayoutRequest): VisualBookLayoutResponse {
        val startTime = System.currentTimeMillis()
        val hasKey = apiKey.isNotBlank()

        if (!hasKey) {
            val latency = System.currentTimeMillis() - startTime
            log.warn("Gemini API key is blank or missing. Returning deterministic fallback layouts.")
            return fallbackResponse(
                request = request,
                debug = VisualBookDebugInfo(
                    latencyMs = latency,
                    apiKeyConfigured = false,
                    providerStatus = "missing_api_key",
                    errorMessage = "Gemini API key is not configured in environment or local config.",
                    model = GEMINI_MODELS.first(),
                    requestPromptSnippet = "API key missing; prompt execution skipped.",
                ),
            )
        }

        val prompt = buildPrompt(request)
        val promptSnippet = prompt.trim().take(300)

        var lastError: Exception? = null
        var lastStatus: Int? = null
        var lastResponseSnippet: String? = null
        var lastEndpoint: String? = null
        var lastModelUsed: String = GEMINI_MODELS.first()

        for (model in GEMINI_MODELS) {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
            lastEndpoint = endpoint
            lastModelUsed = model

            log.info("Attempting Gemini layout generation via model '{}' (prompt length: {} chars)", model, prompt.length)

            try {
                val (rawBody, statusCode) = executeGeminiCall(endpoint, prompt)
                lastStatus = statusCode
                lastResponseSnippet = rawBody.take(400)

                val latency = System.currentTimeMillis() - startTime
                val generatedJson = extractGeneratedJson(rawBody)
                val generated = objectMapper.readValue(generatedJson, GeminiLayoutPayload::class.java)
                val validated = validateOptions(generated.options)

                log.info("Gemini layout generation succeeded with model '{}' in {}ms", model, latency)

                return VisualBookLayoutResponse(
                    options = validated,
                    generationSource = "gemini",
                    debug = VisualBookDebugInfo(
                        latencyMs = latency,
                        apiKeyConfigured = true,
                        providerStatus = "success",
                        model = model,
                        endpoint = endpoint,
                        requestPromptSnippet = promptSnippet,
                        responseStatusCode = statusCode,
                        responseSnippet = lastResponseSnippet,
                    ),
                )
            } catch (ex: Exception) {
                lastError = ex
                log.warn("Gemini call failed for model '{}': {}. Trying next candidate model if available.", model, ex.message)
            }
        }

        val totalLatency = System.currentTimeMillis() - startTime
        log.error("All Gemini candidate models failed after {}ms. Falling back to deterministic layouts.", totalLatency)

        return fallbackResponse(
            request = request,
            debug = VisualBookDebugInfo(
                latencyMs = totalLatency,
                apiKeyConfigured = true,
                providerStatus = "error",
                errorMessage = lastError?.message ?: "Gemini provider unavailable",
                model = lastModelUsed,
                endpoint = lastEndpoint,
                requestPromptSnippet = promptSnippet,
                responseStatusCode = lastStatus,
                responseSnippet = lastResponseSnippet ?: lastError?.message,
            ),
        )
    }

    private suspend fun executeGeminiCall(
        endpoint: String,
        prompt: String,
    ): Pair<String, Int> {
        val payload = mapOf(
            "contents" to listOf(
                mapOf("parts" to listOf(mapOf("text" to prompt))),
            ),
            "generationConfig" to mapOf(
                "responseMimeType" to "application/json",
                "responseJsonSchema" to responseSchema,
            ),
        )

        val response = httpClient.post(
            url = endpoint,
            body = objectMapper.writeValueAsString(payload),
            headers = mapOf("x-goog-api-key" to apiKey, "Accept" to "application/json"),
        )

        if (response.statusCode in 200..299) {
            return Pair(response.body, response.statusCode)
        } else {
            throw IllegalStateException("HTTP ${response.statusCode}: ${response.body.take(300)}")
        }
    }

    private fun extractGeneratedJson(responseBody: String): String {
        val root = objectMapper.readTree(responseBody)
        val textNode: JsonNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text")
        require(textNode.isTextual) { "Gemini response did not include a layout payload." }
        return textNode.asText()
    }

    private fun validateOptions(options: List<VisualBookLayoutOption>): List<VisualBookLayoutOption> {
        require(options.size == 2) { "Gemini must return exactly two layout options." }

        return options.mapIndexed { index, option ->
            require(option.template in allowedTemplates) { "Gemini returned an unsupported template." }
            require(option.imagePlacement in allowedImagePlacements) { "Gemini returned an unsupported image placement." }
            require(option.headline.isNotBlank() && option.headline.length <= 120) { "Gemini returned an invalid headline." }
            require(option.summary.isNotBlank() && option.summary.length <= 360) { "Gemini returned an invalid summary." }
            require(option.rationale.isNotBlank() && option.rationale.length <= 240) { "Gemini returned an invalid rationale." }

            option.copy(
                id = "option-${index + 1}",
                headline = option.headline.trim(),
                summary = option.summary.trim(),
                rationale = option.rationale.trim(),
                figureCaption = option.figureCaption?.trim()?.take(160),
            )
        }
    }

    private fun fallbackResponse(
        request: VisualBookLayoutRequest,
        debug: VisualBookDebugInfo? = null,
    ): VisualBookLayoutResponse {
        val title = request.bookTitle?.trim()?.takeIf { it.isNotEmpty() } ?: "Technical Specification"
        val firstSentence = extractPageHeadline(request.pageText)
        val headlineOption1 = firstSentence.ifBlank { title }
        val headlineOption2 = if (firstSentence.isNotBlank()) "Architecture Focus: $firstSentence" else "System Specs: $title"
        val summary1 = compactText(request.pageText)
        val summary2 = "Technical overview detailing system components, interfaces, and operational flow."
        val hasImages = request.images.isNotEmpty()

        return VisualBookLayoutResponse(
            options = listOf(
                VisualBookLayoutOption(
                    id = "option-1",
                    template = "hero-split",
                    imagePlacement = if (hasImages) "right" else "none",
                    headline = headlineOption1.take(120),
                    summary = summary1,
                    rationale = "Side-by-side architecture layout: structured technical documentation on the left, uncropped technical diagram on the right.",
                    figureCaption = if (hasImages) "Figure 1: System Workflow & Technical Architecture Reference" else null,
                ),
                VisualBookLayoutOption(
                    id = "option-2",
                    template = "editorial",
                    imagePlacement = if (hasImages) "top" else "none",
                    headline = headlineOption2.take(120),
                    summary = summary2,
                    rationale = "Inline technical documentation layout: overview at top, centered uncropped diagram, specifications below.",
                    figureCaption = if (hasImages) "Figure 1: Architectural Data Flow Diagram" else null,
                ),
            ),
            generationSource = "fallback",
            debug = debug,
        )
    }

    private fun extractPageHeadline(pageText: String): String {
        val raw = pageText
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
        val clean = raw.split(Regex("[.!?\\n]")).firstOrNull()?.trim().orEmpty()
        return if (clean.length > 80) clean.take(80) + "…" else clean
    }

    private fun buildPrompt(request: VisualBookLayoutRequest): String = """
        You are an Enterprise Technical Documentation Art Director at software company EY.
        The document text contains structured technical product documentation with section headers, subheadings, bullet items, and technical specifications.
        The visual asset is a technical diagram, architecture flowchart, process workflow, or UI mockup.

        Your task: Propose exactly two meaningfully different, production-grade enterprise layouts that compose the technical documentation with the visual asset without cropping the visual.

        Book title: ${request.bookTitle.orEmpty()}
        Book objective: ${request.objective.orEmpty()}
        Page text: ${request.pageText}
        Image metadata: ${objectMapper.writeValueAsString(request.images)}

        Available layout templates:
        1. "hero-split": Architecture Split layout. Main column presents the structured technical documentation (sections, subheadings, indented bullets); side column features the uncropped technical diagram in an elevated figure card.
        2. "editorial": Workflow Inline layout. Executive summary and title at top, center features the uncropped technical diagram spanning full width, followed by structured technical bullet points and specifications below it.
        3. "image-led": Visual-first overview.

        Available image placements: "left", "right", "top", "none", "full-bleed".
        The "headline" MUST be the technical document or page title extracted from the text (do NOT invent narrative fantasy or simply repeat the Book Title).
        The "summary" should be a crisp executive brief (under 360 characters) summarizing the technical capabilities on this page.
        The "figureCaption" should be a professional technical caption for the visual asset (e.g., "Figure 1: High-Availability PO Pipeline & Replenishment Architecture").
        The "rationale" explains why this layout effectively balances technical readability with diagram clarity (under 240 characters).
        Keep headline under 120 chars, summary under 360 chars, rationale under 240 chars.
    """.trimIndent()

    private fun compactText(pageText: String): String = pageText
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(360)

    private data class GeminiLayoutPayload(
        val options: List<VisualBookLayoutOption>,
    )

    private companion object {
        val allowedTemplates = setOf("hero-split", "editorial", "image-led")
        val allowedImagePlacements = setOf("none", "left", "right", "top", "full-bleed")

        val responseSchema: Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "options" to mapOf(
                    "type" to "array",
                    "minItems" to 2,
                    "maxItems" to 2,
                    "items" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "id" to mapOf("type" to "string"),
                            "template" to mapOf("type" to "string", "enum" to allowedTemplates.toList()),
                            "imagePlacement" to mapOf("type" to "string", "enum" to allowedImagePlacements.toList()),
                            "headline" to mapOf("type" to "string"),
                            "summary" to mapOf("type" to "string"),
                            "rationale" to mapOf("type" to "string"),
                            "figureCaption" to mapOf("type" to "string"),
                        ),
                        "required" to listOf("id", "template", "imagePlacement", "headline", "summary", "rationale"),
                    ),
                ),
            ),
            "required" to listOf("options"),
        )
    }
}
