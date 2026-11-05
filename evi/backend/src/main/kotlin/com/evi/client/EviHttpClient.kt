package com.evi.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class EviHttpRequestData(
    val method: String,
    val uri: URI,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val timeout: Duration? = null,
)

data class EviHttpResponseData(
    val statusCode: Int,
    val body: String,
)

data class EviHttpBinaryResponseData(
    val statusCode: Int,
    val body: ByteArray,
)

interface EviHttpClient {
    suspend fun execute(request: EviHttpRequestData): EviHttpResponseData
    suspend fun executeBytes(request: EviHttpRequestData): EviHttpBinaryResponseData
    suspend fun post(url: String, body: String?, headers: Map<String, String> = emptyMap()): EviHttpResponseData

    suspend fun postWithTimeout(
        url: String,
        body: String?,
        headers: Map<String, String> = emptyMap(),
        timeout: Duration,
    ): EviHttpResponseData = post(url, body, headers)
}

class JdkEviHttpClient(
    private val httpClient: HttpClient = HttpClient.newBuilder().build(),
) : EviHttpClient {

    override suspend fun post(
        url: String,
        body: String?,
        headers: Map<String, String>,
    ): EviHttpResponseData {
        val requestData = EviHttpRequestData(
            method = "POST",
            uri = URI.create(url),
            headers = headers,
            body = body?.toByteArray(Charsets.UTF_8),
        )
        return execute(requestData)
    }

    override suspend fun postWithTimeout(
        url: String,
        body: String?,
        headers: Map<String, String>,
        timeout: Duration,
    ): EviHttpResponseData {
        val requestData = EviHttpRequestData(
            method = "POST",
            uri = URI.create(url),
            headers = headers,
            body = body?.toByteArray(Charsets.UTF_8),
            timeout = timeout,
        )
        return execute(requestData)
    }

    override suspend fun execute(request: EviHttpRequestData): EviHttpResponseData = withContext(Dispatchers.IO) {
        val httpRequest = buildRequest(request, HttpResponse.BodyHandlers.ofString())
        val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        EviHttpResponseData(
            statusCode = response.statusCode(),
            body = response.body(),
        )
    }

    override suspend fun executeBytes(request: EviHttpRequestData): EviHttpBinaryResponseData = withContext(Dispatchers.IO) {
        val httpRequest = buildRequest(request, HttpResponse.BodyHandlers.ofByteArray())
        val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray())
        EviHttpBinaryResponseData(
            statusCode = response.statusCode(),
            body = response.body(),
        )
    }

    private fun <T> buildRequest(request: EviHttpRequestData, handler: HttpResponse.BodyHandler<T>): HttpRequest {
        val builder = HttpRequest.newBuilder()
            .uri(request.uri)

        request.timeout?.let { builder.timeout(it) }

        request.headers.forEach { (key, value) ->
            builder.header(key, value)
        }

        val bodyPublisher = request.body?.let {
            HttpRequest.BodyPublishers.ofByteArray(it)
        } ?: HttpRequest.BodyPublishers.noBody()

        builder.method(request.method, bodyPublisher)
        return builder.build()
    }
}
