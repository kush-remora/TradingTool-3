package com.evi.service

import com.evi.client.EviHttpBinaryResponseData
import com.evi.client.EviHttpClient
import com.evi.client.EviHttpRequestData
import com.evi.client.EviHttpResponseData
import com.evi.model.AuthorityGenerationRequest
import com.evi.model.AuthorityMode
import com.evi.model.VisualBookImageMetadata
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthorityGenerationServiceTest {
    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    @Test
    fun `returns the selected mode when the LLM is not configured`() = kotlinx.coroutines.runBlocking {
        val service = AuthorityGenerationService(
            apiKey = "",
            httpClient = FailingHttpClient,
            objectMapper = objectMapper,
        )

        val response = service.generate(request(AuthorityMode.PRESERVE))

        assertEquals(AuthorityMode.PRESERVE, response.authorityMode)
        assertEquals("fallback", response.generationSource)
        assertNull(response.result)
        assertEquals("missing_api_key", response.debug?.providerStatus)
    }

    @Test
    fun `loads the selected prompt and validates its mode-specific payload`() = kotlinx.coroutines.runBlocking {
        val generatedPayload = objectMapper.writeValueAsString(
            mapOf(
                "authority_mode" to "TRANSFORM",
                "transformation_variants" to listOf(
                    mapOf("variant_id" to "executive"),
                    mapOf("variant_id" to "walkthrough"),
                ),
            ),
        )
        val providerBody = objectMapper.writeValueAsString(
            mapOf(
                "candidates" to listOf(
                    mapOf("content" to mapOf("parts" to listOf(mapOf("text" to generatedPayload)))),
                ),
            ),
        )
        val httpClient = CapturingHttpClient(providerBody)
        val service = AuthorityGenerationService("test-key", httpClient, objectMapper)

        val response = service.generate(request(AuthorityMode.TRANSFORM))

        assertEquals("llm", response.generationSource)
        assertNotNull(response.result)
        assertTrue(httpClient.requestBody.contains("ACTIVE AUTHORITY MODE: TRANSFORM"))
        assertTrue(httpClient.requestBody.contains("The source content remains available"))
    }

    @Test
    fun `requires exactly two preserve placement variants`() = kotlinx.coroutines.runBlocking {
        val generatedPayload = objectMapper.writeValueAsString(
            mapOf(
                "authority_mode" to "PRESERVE",
                "preserved_blocks" to listOf(emptyMap<String, Any>()),
                "preserve_variants" to listOf(
                    mapOf("variant_id" to "before", "placement_strategy" to "ADDED_PAGE_BEFORE_SOURCE"),
                    mapOf("variant_id" to "after", "placement_strategy" to "ADDED_PAGE_AFTER_SOURCE"),
                ),
            ),
        )
        val providerBody = objectMapper.writeValueAsString(
            mapOf(
                "candidates" to listOf(
                    mapOf("content" to mapOf("parts" to listOf(mapOf("text" to generatedPayload)))),
                ),
            ),
        )
        val httpClient = CapturingHttpClient(providerBody)
        val service = AuthorityGenerationService("test-key", httpClient, objectMapper)

        val response = service.generate(request(AuthorityMode.PRESERVE))

        assertEquals("llm", response.generationSource)
        assertEquals(2, response.result?.path("preserve_variants")?.size())
        assertTrue(httpClient.requestBody.contains("additive page-placement variants"))
    }

    private fun request(mode: AuthorityMode) = AuthorityGenerationRequest(
        authorityMode = mode,
        sourceDocumentContent = "The source content remains available to the selected contract.",
        sourceStructureJson = "{\"blocks\":[{\"source_block_id\":\"block-1\"}]}",
        visualAssets = listOf(VisualBookImageMetadata(name = "diagram.png", mimeType = "image/png")),
    )

    private object FailingHttpClient : EviHttpClient {
        override suspend fun execute(request: EviHttpRequestData): EviHttpResponseData = error("HTTP should not be called")

        override suspend fun executeBytes(request: EviHttpRequestData): EviHttpBinaryResponseData = error("HTTP should not be called")

        override suspend fun post(url: String, body: String?, headers: Map<String, String>): EviHttpResponseData =
            error("HTTP should not be called")
    }

    private class CapturingHttpClient(private val responseBody: String) : EviHttpClient {
        var requestBody: String = ""

        override suspend fun execute(request: EviHttpRequestData): EviHttpResponseData =
            EviHttpResponseData(200, responseBody)

        override suspend fun executeBytes(request: EviHttpRequestData): EviHttpBinaryResponseData =
            EviHttpBinaryResponseData(200, responseBody.toByteArray())

        override suspend fun post(url: String, body: String?, headers: Map<String, String>): EviHttpResponseData {
            requestBody = body.orEmpty()
            return EviHttpResponseData(200, responseBody)
        }
    }
}
