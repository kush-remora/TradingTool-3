package com.tradingtool.core.telegram

import com.tradingtool.core.model.telegram.TelegramSendFileRequest
import com.tradingtool.core.model.telegram.TelegramSendResponse
import com.tradingtool.core.model.telegram.TelegramSendResult
import com.tradingtool.core.model.telegram.TelegramSendStatus
import com.tradingtool.core.model.telegram.TelegramSendTextRequest
import com.google.inject.Singleton

@Singleton
class TelegramSender(
    private val telegramApiClient: TelegramApiClient,
) {
    fun isConfigured(): Boolean = telegramApiClient.isConfigured()

    suspend fun sendText(request: TelegramSendTextRequest): TelegramSendResult {
        val text = request.text.trim()
        if (text.isEmpty()) return badRequest("Text message cannot be empty.")
        return runCatching {
            toSendResult(telegramApiClient.sendText(text), "Text sent to Telegram.")
        }.getOrElse { failedResult(it.message ?: "Failed to send text to Telegram.") }
    }

    suspend fun sendImage(request: TelegramSendFileRequest): TelegramSendResult {
        if (detectFileType(request) != FileType.IMAGE) return badRequest("Only image files (PNG, JPG, WEBP) are allowed.")
        return sendFile("sendPhoto", "photo", request, "Image sent to Telegram.")
    }

    suspend fun sendExcel(request: TelegramSendFileRequest): TelegramSendResult {
        if (detectFileType(request) != FileType.EXCEL) return badRequest("Only .xls or .xlsx files are allowed.")
        return sendFile("sendDocument", "document", request, "Excel file sent to Telegram.")
    }

    private suspend fun sendFile(
        method: String,
        fileFieldName: String,
        request: TelegramSendFileRequest,
        successMessage: String,
    ): TelegramSendResult = runCatching {
        toSendResult(telegramApiClient.sendFile(method, fileFieldName, request), successMessage)
    }.getOrElse { failedResult(it.message ?: "Failed to send file to Telegram.") }

    private fun toSendResult(apiResult: TelegramApiCallResult, successMessage: String): TelegramSendResult {
        if (apiResult.statusCode !in 200..299 || !apiResult.response.ok) {
            val description = apiResult.response.description
                ?: "Telegram API request failed with status ${apiResult.statusCode}."
            return failedResult(description)
        }
        return TelegramSendResult(
            status = TelegramSendStatus.SUCCESS,
            response = TelegramSendResponse(ok = true, message = successMessage, telegramDescription = apiResult.response.description),
        )
    }

    private enum class FileType { IMAGE, EXCEL }

    private fun detectFileType(request: TelegramSendFileRequest): FileType? {
        val name = request.fileName.lowercase()
        val mime = request.contentType.lowercase()
        return when {
            mime.startsWith("image/") || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp") -> FileType.IMAGE
            name.endsWith(".xls") || name.endsWith(".xlsx") || mime == "application/vnd.ms-excel" || mime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> FileType.EXCEL
            else -> null
        }
    }

    private fun badRequest(message: String) = TelegramSendResult(
        status = TelegramSendStatus.BAD_REQUEST,
        response = TelegramSendResponse(ok = false, message = message),
    )

    private fun failedResult(description: String) = TelegramSendResult(
        status = TelegramSendStatus.FAILED,
        response = TelegramSendResponse(ok = false, message = "Telegram API request failed.", telegramDescription = description),
    )
}