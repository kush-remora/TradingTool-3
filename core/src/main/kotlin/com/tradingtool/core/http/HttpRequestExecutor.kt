package com.tradingtool.core.http

import com.google.inject.Inject
import com.google.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.io.IOException

interface HttpRequestExecutor {
    /**
     * Execute HTTP request on Dispatchers.IO thread pool.
     * Includes automatic retry logic for transient failures.
     */
    suspend fun execute(request: HttpRequestData): HttpResponseData

    /** Execute an HTTP request that returns binary content, such as a private PDF. */
    suspend fun executeBytes(request: HttpRequestData): HttpBinaryResponseData
}

@Singleton
class JdkHttpRequestExecutor @Inject constructor(
    private val httpClient: HttpClient,
) : HttpRequestExecutor {

    override suspend fun execute(request: HttpRequestData): HttpResponseData = withContext(Dispatchers.IO) {
        executeWithRetry(request, maxAttempts = 3)
    }

    override suspend fun executeBytes(request: HttpRequestData): HttpBinaryResponseData = withContext(Dispatchers.IO) {
        executeBytesWithRetry(request, maxAttempts = 3)
    }

    private suspend fun executeWithRetry(
        request: HttpRequestData,
        maxAttempts: Int
    ): HttpResponseData {
        var lastException: Exception? = null

        repeat(maxAttempts) { attempt ->
            try {
                return@executeWithRetry executeOnce(request)
            } catch (e: Exception) {
                lastException = e

                // Only retry on transient network errors
                val shouldRetry = isRetryableException(e) && attempt < maxAttempts - 1

                if (shouldRetry) {
                    val delayMs = calculateBackoffDelay(attempt)
                    delay(delayMs)
                } else {
                    // Don't retry, throw immediately
                    throw e
                }
            }
        }

        // All retries exhausted
        throw lastException ?: IOException("HTTP request failed after $maxAttempts attempts")
    }

    private suspend fun executeOnce(request: HttpRequestData): HttpResponseData {
        val httpRequest = buildHttpRequest(request)

        val response = httpClient
            .sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
            .await()

        return HttpResponseData(
            statusCode = response.statusCode(),
            body = response.body().orEmpty(),
        )
    }

    private suspend fun executeBytesWithRetry(
        request: HttpRequestData,
        maxAttempts: Int,
    ): HttpBinaryResponseData {
        var lastException: Exception? = null

        repeat(maxAttempts) { attempt ->
            try {
                return executeBytesOnce(request)
            } catch (error: Exception) {
                lastException = error
                if (isRetryableException(error) && attempt < maxAttempts - 1) {
                    delay(calculateBackoffDelay(attempt))
                } else {
                    throw error
                }
            }
        }

        throw lastException ?: IOException("HTTP request failed after $maxAttempts attempts")
    }

    private suspend fun executeBytesOnce(request: HttpRequestData): HttpBinaryResponseData {
        val response = httpClient
            .sendAsync(buildHttpRequest(request), HttpResponse.BodyHandlers.ofByteArray())
            .await()

        return HttpBinaryResponseData(
            statusCode = response.statusCode(),
            body = response.body(),
        )
    }

    private fun buildHttpRequest(request: HttpRequestData): HttpRequest {
        val builder = HttpRequest.newBuilder()
            .uri(request.uri)

        request.timeout?.let { timeout ->
            builder.timeout(timeout)
        }

        request.headers.forEach { (key, value) ->
            builder.header(key, value)
        }

        val bodyPublisher = request.body?.let { body ->
            HttpRequest.BodyPublishers.ofByteArray(body)
        } ?: HttpRequest.BodyPublishers.noBody()

        return builder
            .method(request.method, bodyPublisher)
            .build()
    }

    private fun isRetryableException(e: Exception): Boolean {
        return when (e) {
            is HttpTimeoutException -> true
            is IOException -> {
                // Retry on connection issues, not on protocol errors
                val message = e.message?.lowercase() ?: ""
                message.contains("timeout") ||
                message.contains("connection reset") ||
                message.contains("broken pipe") ||
                message.contains("connection refused")
            }
            else -> false
        }
    }

    private fun calculateBackoffDelay(attempt: Int): Long {
        // Exponential backoff: 100ms, 200ms, 400ms, ...
        return 100L * (1L shl attempt)
    }
}
