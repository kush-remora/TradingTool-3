package com.tradingtool.model.telegram

class TelegramResponseModel {
    data class StatusResponse(
        val status: String,
        val configured: Boolean,
    )

    data class ActionResponse(
        val ok: Boolean,
        val message: String,
        val telegramDescription: String? = null,
    )
}
