package com.tradingtool.core.telegram

import com.tradingtool.core.model.telegram.TelegramApiResponse
import com.tradingtool.core.model.telegram.TelegramSendFileRequest
import com.tradingtool.core.model.telegram.TelegramSendResponse
import com.tradingtool.core.model.telegram.TelegramSendResult
import com.tradingtool.core.model.telegram.TelegramSendStatus
import com.tradingtool.core.model.telegram.TelegramSendTextRequest

import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

class TelegramSender(
    private val botToken: String,
    private val chatId: String,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : AutoCloseable {

    fun isConfigured(): Boolean {
        return botToken.isNotBlank() && chatId.isNotBlank()
    }

    suspend fun sendText(request: TelegramSendTextRequest): TelegramSendResult {
        if (!isConfigured()) {
            return notConfiguredResult()
        }

        val text = request.text.trim()
        if (text.isEmpty()) {
            return badRequestResult("Text message cannot be empty.")
        }

        return runCatching {
            val formBody = buildFormUrlEncoded(
                "chat_id" to chatId,
                "text" to text,
            )

            val httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/sendMessage"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build()

            val response = httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()).await()
            parseHttpResponse(response, successMessage = "Text sent to Telegram.")
        }.getOrElse { error ->
            failedResult(error.message ?: "Failed to send text to Telegram.")
        }
    }

    suspend fun sendImage(request: TelegramSendFileRequest): TelegramSendResult {
        if (!isConfigured()) {
            return notConfiguredResult()
        }

        if (!isImageFile(request)) {
            return badRequestResult("Only image files are allowed for this endpoint.")
        }

        return sendMultipartFile(
            method = "sendPhoto",
            fileFieldName = "photo",
            request = request,
            successMessage = "Image sent to Telegram.",
        )
    }

    suspend fun sendExcel(request: TelegramSendFileRequest): TelegramSendResult {
        if (!isConfigured()) {
            return notConfiguredResult()
        }

        if (!isExcelFile(request)) {
            return badRequestResult("Only .xls or .xlsx files are allowed for this endpoint.")
        }

        return sendMultipartFile(
            method = "sendDocument",
            fileFieldName = "document",
            request = request,
            successMessage = "Excel file sent to Telegram.",
        )
    }

    override fun close() {
        // JDK HttpClient doesn't require explicit close
    }

    private suspend fun sendMultipartFile(
        method: String,
        fileFieldName: String,
        request: TelegramSendFileRequest,
        successMessage: String,
    ): TelegramSendResult {
        return runCatching {
            val boundary = "----TelegramBoundary${System.currentTimeMillis()}"
            val safeFileName = sanitizeFileName(request.fileName)

            val multipartBody = buildMultipartBody(
                boundary = boundary,
                fileFieldName = fileFieldName,
                fileBytes = request.bytes,
                fileName = safeFileName,
                contentType = request.contentType,
                chatId = chatId,
                caption = request.caption?.trim()?.takeIf { it.isNotEmpty() },
            )

            val httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/$method"))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                .build()

            val response = httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()).await()
            parseHttpResponse(response, successMessage = successMessage)
        }.getOrElse { error ->
            failedResult(error.message ?: "Failed to upload file to Telegram.")
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

        // chat_id field
        output.write(boundaryBytes)
        output.write(lineEnd)
        output.write("Content-Disposition: form-data; name=\"chat_id\"".toByteArray(StandardCharsets.UTF_8))
        output.write(lineEnd)
        output.write(lineEnd)
        output.write(chatId.toByteArray(StandardCharsets.UTF_8))
        output.write(lineEnd)

        // caption field (optional)
        if (caption != null) {
            output.write(boundaryBytes)
            output.write(lineEnd)
            output.write("Content-Disposition: form-data; name=\"caption\"".toByteArray(StandardCharsets.UTF_8))
            output.write(lineEnd)
            output.write(lineEnd)
            output.write(caption.toByteArray(StandardCharsets.UTF_8))
            output.write(lineEnd)
        }

        // file field
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

        // final boundary
        output.write(finalBoundaryBytes)
        output.write(lineEnd)

        return output.toByteArray()
    }

    private fun buildFormUrlEncoded(vararg pairs: Pair<String, String>): String {
        return pairs.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, StandardCharsets.UTF_8)}=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
        }
    }

    private fun parseHttpResponse(
        response: HttpResponse<String>,
        successMessage: String,
    ): TelegramSendResult {
        val responseText = response.body() ?: ""
        val parsedResponse = parseTelegramResponse(responseText)

        if (response.statusCode() !in 200..299 || !parsedResponse.ok) {
            val description = parsedResponse.description
                ?: "Telegram API request failed with status ${response.statusCode()}."
            return failedResult(description)
        }

        return TelegramSendResult(
            status = TelegramSendStatus.SUCCESS,
            response = TelegramSendResponse(
                ok = true,
                message = successMessage,
                telegramDescription = parsedResponse.description,
            ),
        )
    }

    private fun parseTelegramResponse(responseText: String): TelegramApiResponse {
        return runCatching {
            json.decodeFromString<TelegramApiResponse>(responseText)
        }.getOrElse {
            TelegramApiResponse(
                ok = false,
                description = responseText.ifBlank { "Unexpected Telegram response." },
            )
        }
    }

    private fun isImageFile(request: TelegramSendFileRequest): Boolean {
        val lowerContentType = request.contentType.lowercase()
        if (lowerContentType.startsWith("image/")) {
            return true
        }

        val lowerName = request.fileName.lowercase()
        return lowerName.endsWith(".png")
            || lowerName.endsWith(".jpg")
            || lowerName.endsWith(".jpeg")
            || lowerName.endsWith(".webp")
    }

    private fun isExcelFile(request: TelegramSendFileRequest): Boolean {
        val lowerName = request.fileName.lowercase()
        if (lowerName.endsWith(".xls") || lowerName.endsWith(".xlsx")) {
            return true
        }

        val lowerContentType = request.contentType.lowercase()
        return lowerContentType == "application/vnd.ms-excel"
            || lowerContentType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }

    private fun sanitizeFileName(fileName: String): String {
        val cleanName = fileName.trim().replace("\"", "")
        if (cleanName.isBlank()) {
            return "upload.bin"
        }
        return cleanName
    }

    private fun notConfiguredResult(): TelegramSendResult {
        return TelegramSendResult(
            status = TelegramSendStatus.NOT_CONFIGURED,
            response = TelegramSendResponse(
                ok = false,
                message = "Telegram is not configured. Set TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID.",
            ),
        )
    }

    private fun badRequestResult(message: String): TelegramSendResult {
        return TelegramSendResult(
            status = TelegramSendStatus.BAD_REQUEST,
            response = TelegramSendResponse(
                ok = false,
                message = message,
            ),
        )
    }

    private fun failedResult(description: String): TelegramSendResult {
        return TelegramSendResult(
            status = TelegramSendStatus.FAILED,
            response = TelegramSendResponse(
                ok = false,
                message = "Telegram API request failed.",
                telegramDescription = description,
            ),
        )
    }

    private val baseUrl: String
        get() = "https://api.telegram.org/bot$botToken"
}
