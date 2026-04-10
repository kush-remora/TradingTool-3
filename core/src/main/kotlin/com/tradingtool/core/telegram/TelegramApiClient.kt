package com.tradingtool.core.telegram

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.tradingtool.core.http.Result
import com.tradingtool.core.http.SuspendHttpClient
import com.tradingtool.core.model.telegram.TelegramApiResponse
import com.tradingtool.core.model.telegram.TelegramIncomingMessage
import com.tradingtool.core.model.telegram.TelegramSendFileRequest
import com.google.inject.Inject
import com.google.inject.Singleton
import com.google.inject.name.Named
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal data class TelegramApiCallResult(
    val statusCode: Int,
    val response: Result<TelegramApiResponse>,
)

@Singleton
class TelegramApiClient @Inject constructor(
    @Named("telegramBotToken") private val botToken: String,
    @Named("telegramChatId") private val chatId: String,
    private val httpClient: SuspendHttpClient,
    private val objectMapper: ObjectMapper,
) {
    /**
     * Check if Telegram API is properly configured.
     * Returns false if botToken or chatId are blank.
     */
    fun isConfigured(): Boolean {
        return botToken.isNotBlank() && chatId.isNotBlank()
    }

    init {
        // Don't throw - let isConfigured() handle validation
        // Services can gracefully handle unconfigured state
    }

    internal suspend fun sendText(text: String): TelegramApiCallResult {
        check(isConfigured()) {
            "Telegram API is not configured. Set TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID."
        }

        val body = buildFormUrlEncoded(
            "chat_id" to chatId,
            "text" to text,
        )

        val response = httpClient.post(
            url = "$baseUrl/sendMessage",
            body = body,
            headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
        )

        return TelegramApiCallResult(
            statusCode = 200, // Form responses don't expose status in same way
            response = parseResponseJson(response),
        )
    }

    internal suspend fun sendFile(
        method: String,
        fileFieldName: String,
        request: TelegramSendFileRequest,
    ): TelegramApiCallResult {
        check(isConfigured()) {
            "Telegram API is not configured. Set TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID."
        }

        val boundary = "----TelegramBoundary${System.currentTimeMillis()}"
        val bodyBytes = buildMultipartBody(
            boundary = boundary,
            fileFieldName = fileFieldName,
            fileBytes = request.bytes,
            fileName = sanitizeFileName(request.fileName),
            contentType = request.contentType,
            chatId = chatId,
            caption = request.caption?.trim()?.takeIf { it.isNotEmpty() },
        )

        val response = httpClient.post(
            url = "$baseUrl/$method",
            body = bodyBytes.decodeToString(),
            headers = mapOf("Content-Type" to "multipart/form-data; boundary=$boundary"),
        )

        return TelegramApiCallResult(
            statusCode = 200,
            response = parseResponseJson(response),
        )
    }

    internal suspend fun getRecentMessages(limit: Int = 30): Result<List<TelegramIncomingMessage>> {
        if (!isConfigured()) return Result.Success(emptyList())

        val response = httpClient.get(
            url = "$baseUrl/getUpdates?limit=$limit&allowed_updates=%5B%22message%22,%22channel_post%22%5D",
            headers = mapOf("Accept" to "application/json"),
        )

        return when (response) {
            is Result.Success -> {
                val root = objectMapper.readTree(response.data)
                val ok = root.path("ok").asBoolean(false)
                if (!ok) return Result.Success(emptyList())

                val resultNode = root.path("result")
                if (resultNode !is ArrayNode) return Result.Success(emptyList())

                val messages = resultNode.mapNotNull { updateNode ->
                    val messageNode = if (!updateNode.path("message").isMissingNode) {
                        updateNode.path("message")
                    } else {
                        updateNode.path("channel_post")
                    }
                    if (messageNode.isMissingNode) return@mapNotNull null

                    val messageChatId = messageNode.path("chat").path("id").asText("")
                    if (messageChatId != chatId) return@mapNotNull null

                    val text = messageNode.path("text").asText("").trim()
                    if (text.isEmpty()) return@mapNotNull null

                    val messageId = messageNode.path("message_id").asLong(0L)
                    val epochSeconds = messageNode.path("date").asLong(0L)
                    if (messageId == 0L || epochSeconds <= 0L) return@mapNotNull null

                    TelegramIncomingMessage(
                        messageId = messageId,
                        text = text,
                        receivedAt = java.time.Instant.ofEpochSecond(epochSeconds).toString(),
                    )
                }.sortedByDescending { it.receivedAt }

                Result.Success(messages)
            }
            is Result.Failure -> {
                // Keep UI usable when Telegram API is temporarily unavailable.
                Result.Success(emptyList())
            }
        }
    }

    private fun parseResponseJson(responseResult: Result<String>): Result<TelegramApiResponse> {
        return responseResult.map { responseText ->
            try {
                objectMapper.readValue(responseText, TelegramApiResponse::class.java)
            } catch (e: Exception) {
                throw e
            }
        }.let { result ->
            when (result) {
                is Result.Success -> Result.Success(result.data)
                is Result.Failure -> {
                    // If HTTP call failed, return a failure response
                    Result.Success(TelegramApiResponse(ok = false, description = result.error.describe()))
                }
            }
        }
    }

    private fun buildFormUrlEncoded(vararg pairs: Pair<String, String>): String {
        return pairs.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, StandardCharsets.UTF_8)}=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
        }
    }

    private fun buildMultipartBody(
        boundary: String,
        fileFieldName: String,
        fileBytes: ByteArray,
        fileName: String,
        contentType: String,
        chatId: String,
        caption: String?,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val lineEnd = "\r\n".toByteArray(StandardCharsets.UTF_8)
        val boundaryBytes = "--$boundary".toByteArray(StandardCharsets.UTF_8)
        val finalBoundaryBytes = "--$boundary--".toByteArray(StandardCharsets.UTF_8)

        output.write(boundaryBytes)
        output.write(lineEnd)
        output.write("Content-Disposition: form-data; name=\"chat_id\"".toByteArray(StandardCharsets.UTF_8))
        output.write(lineEnd)
        output.write(lineEnd)
        output.write(chatId.toByteArray(StandardCharsets.UTF_8))
        output.write(lineEnd)

        if (caption != null) {
            output.write(boundaryBytes)
            output.write(lineEnd)
            output.write("Content-Disposition: form-data; name=\"caption\"".toByteArray(StandardCharsets.UTF_8))
            output.write(lineEnd)
            output.write(lineEnd)
            output.write(caption.toByteArray(StandardCharsets.UTF_8))
            output.write(lineEnd)
        }

        output.write(boundaryBytes)
        output.write(lineEnd)
        output.write(
            "Content-Disposition: form-data; name=\"$fileFieldName\"; filename=\"$fileName\""
                .toByteArray(StandardCharsets.UTF_8)
        )
        output.write(lineEnd)
        output.write("Content-Type: $contentType".toByteArray(StandardCharsets.UTF_8))
        output.write(lineEnd)
        output.write(lineEnd)
        output.write(fileBytes)
        output.write(lineEnd)

        output.write(finalBoundaryBytes)
        output.write(lineEnd)

        return output.toByteArray()
    }

    private fun sanitizeFileName(fileName: String): String {
        val cleanName = fileName.trim().replace("\"", "")
        if (cleanName.isBlank()) {
            return "upload.bin"
        }
        return cleanName
    }

    private val baseUrl: String
        get() = "https://api.telegram.org/bot$botToken"
}
