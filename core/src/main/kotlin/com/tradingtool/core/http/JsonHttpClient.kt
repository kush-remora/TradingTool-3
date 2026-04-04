package com.tradingtool.core.http

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.inject.Inject
import com.google.inject.Singleton

/**
 * High-level HTTP client with automatic JSON serialization/deserialization.
 *
 * Usage:
 *   val response = jsonHttpClient.get<UserResponse>("https://api.example.com/users/123")
 *   val postResponse = jsonHttpClient.post<CreateResponse>("https://api.example.com/users", newUser)
 */
@Singleton
class JsonHttpClient @Inject constructor(
    @PublishedApi internal val httpClient: SuspendHttpClient,
    @PublishedApi internal val objectMapper: ObjectMapper,
) {

    /**
     * GET request with automatic JSON deserialization.
     */
    suspend inline fun <reified T> get(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): Result<T> {
        val response = httpClient.get(url, headers)
        return parseResult<T>(response)
    }

    /**
     * POST request with automatic JSON serialization and deserialization.
     */
    suspend inline fun <reified T> post(
        url: String,
        body: Any? = null,
        headers: Map<String, String> = emptyMap(),
    ): Result<T> {
        val jsonBody = body?.let { serializeBody(it) }
        val response = httpClient.post(
            url,
            body = jsonBody,
            headers = headers + ("Content-Type" to "application/json"),
        )
        return parseResult<T>(response)
    }

    /**
     * PUT request with automatic JSON serialization and deserialization.
     */
    suspend inline fun <reified T> put(
        url: String,
        body: Any? = null,
        headers: Map<String, String> = emptyMap(),
    ): Result<T> {
        val jsonBody = body?.let { serializeBody(it) }
        val response = httpClient.put(
            url,
            body = jsonBody,
            headers = headers + ("Content-Type" to "application/json"),
        )
        return parseResult<T>(response)
    }

    /**
     * DELETE request with automatic JSON deserialization.
     */
    suspend inline fun <reified T> delete(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): Result<T> {
        val response = httpClient.delete(url, headers)
        return parseResult<T>(response)
    }

    /**
     * PATCH request with automatic JSON serialization and deserialization.
     */
    suspend inline fun <reified T> patch(
        url: String,
        body: Any? = null,
        headers: Map<String, String> = emptyMap(),
    ): Result<T> {
        val jsonBody = body?.let { serializeBody(it) }
        val response = httpClient.patch(
            url,
            body = jsonBody,
            headers = headers + ("Content-Type" to "application/json"),
        )
        return parseResult<T>(response)
    }

    /**
     * Raw request: for cases where you want to skip JSON parsing.
     */
    suspend fun getRaw(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): Result<String> = httpClient.get(url, headers)

    @PublishedApi
    internal fun serializeBody(body: Any): String = try {
        objectMapper.writeValueAsString(body)
    } catch (e: Exception) {
        throw IllegalArgumentException("Failed to serialize request body", e)
    }

    @PublishedApi
    internal inline fun <reified T> parseResult(httpResult: Result<String>): Result<T> {
        return when (httpResult) {
            is Result.Success -> {
                try {
                    val parsed = objectMapper.readValue(httpResult.data, T::class.java)
                    Result.Success(parsed)
                } catch (e: Exception) {
                    Result.Failure(HttpError.SerializationError(e))
                }
            }
            is Result.Failure -> Result.Failure(httpResult.error)
        }
    }
}
