package com.evi.service

import com.evi.client.EviHttpClient
import com.evi.client.EviHttpBinaryResponseData
import com.evi.client.EviHttpRequestData
import com.evi.client.EviHttpResponseData
import com.evi.model.VisualBookLayoutRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import kotlin.test.Test
import kotlin.test.assertEquals

class VisualBookLayoutServiceTest {
    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    @Test
    fun `returns Gemini layout options when the provider response is valid`() = kotlinx.coroutines.runBlocking {
        val layoutPayload = objectMapper.writeValueAsString(
            mapOf(
                "options" to listOf(
                    option("hero-split", "right", "A hopeful opening"),
                    option("editorial", "left", "A quieter opening"),
                ),
            ),
        )
        val providerResponse = objectMapper.writeValueAsString(
            mapOf("candidates" to listOf(mapOf("content" to mapOf("parts" to listOf(mapOf("text" to layoutPayload)))))),
        )
        val httpClient = FakeHttpClient(providerResponse)
        val service = VisualBookLayoutService("test-key", httpClient, objectMapper)

        val response = service.generate(request())

        assertEquals("gemini", response.generationSource)
        assertEquals(listOf("option-1", "option-2"), response.options.map { it.id })
        assertEquals("test-key", httpClient.headers["x-goog-api-key"])
    }

    @Test
    fun `uses a deterministic fallback when no Gemini key is configured`() = kotlinx.coroutines.runBlocking {
        val service = VisualBookLayoutService("", FakeHttpClient(""), objectMapper)

        val response = service.generate(request())

        assertEquals("fallback", response.generationSource)
        assertEquals(2, response.options.size)
    }

    private fun request() = VisualBookLayoutRequest(
        bookTitle = "The Quiet Future",
        objective = "A visual introduction",
        pageText = "Cities work best when they make room for people to pause and reconnect.",
    )

    private fun option(template: String, imagePlacement: String, headline: String) = mapOf(
        "id" to "provider-id",
        "template" to template,
        "imagePlacement" to imagePlacement,
        "headline" to headline,
        "summary" to "A concise editorial summary for the page.",
        "rationale" to "A brief reason for the composition.",
    )

    private class FakeHttpClient(private val responseBody: String) : EviHttpClient {
        var headers: Map<String, String> = emptyMap()

        override suspend fun post(url: String, body: String?, headers: Map<String, String>): EviHttpResponseData {
            this.headers = headers
            return EviHttpResponseData(200, responseBody)
        }

        override suspend fun execute(request: EviHttpRequestData): EviHttpResponseData {
            this.headers = request.headers
            return EviHttpResponseData(200, responseBody)
        }

        override suspend fun executeBytes(request: EviHttpRequestData): EviHttpBinaryResponseData {
            return EviHttpBinaryResponseData(200, responseBody.toByteArray())
        }
    }
}
