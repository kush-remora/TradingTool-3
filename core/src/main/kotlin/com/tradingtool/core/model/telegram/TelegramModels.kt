package com.tradingtool.core.model.telegram

import com.fasterxml.jackson.annotation.JsonProperty

data class TelegramSendTextRequest(
    val text: String,
)

data class TelegramSendFileRequest(
    val bytes: ByteArray,
    val fileName: String,
    val contentType: String,
    val caption: String?,
)

data class TelegramSendResponse(
    val ok: Boolean,
    val message: String,
    val telegramDescription: String? = null,
)

enum class TelegramSendStatus {
    SUCCESS,
    BAD_REQUEST,
    NOT_CONFIGURED,
    FAILED,
}

data class TelegramSendResult(
    val status: TelegramSendStatus,
    val response: TelegramSendResponse,
)

internal data class TelegramApiResponse(
    @JsonProperty("ok")
    val ok: Boolean,
    @JsonProperty("description")
    val description: String? = null,
)

data class TelegramIncomingMessage(
    @JsonProperty("message_id")
    val messageId: Long,
    val text: String,
    @JsonProperty("received_at")
    val receivedAt: String,
)
