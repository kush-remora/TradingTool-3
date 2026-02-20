package com.tradingtool.telegram

import com.google.inject.Inject
import com.tradingtool.core.model.telegram.TelegramSendFileRequest
import com.tradingtool.core.model.telegram.TelegramSendResult
import com.tradingtool.core.model.telegram.TelegramSendStatus
import com.tradingtool.core.model.telegram.TelegramSendTextRequest
import com.tradingtool.core.telegram.TelegramSender
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private data class UploadedFile(
    val bytes: ByteArray,
    val fileName: String,
    val contentType: String,
    val caption: String?,
)

private val requestJson: Json = Json { ignoreUnknownKeys = true }

class TelegramResource(
    @Inject
    private val telegramSender: TelegramSender,
) {
    fun register(route: Route) {
        route.route("/api/telegram") {
            get("status") {
                handleGetStatus(call)
            }
            post("send/text") {
                handlePostSendText(call)
            }
            post("send/image") {
                handlePostSendImage(call)
            }
            post("send/excel") {
                handlePostSendExcel(call)
            }
            delete("messages/{messageId}") {
                handleDeleteMessage(call)
            }
        }
    }

    private suspend fun handleGetStatus(call: ApplicationCall) {
        val payload = buildJsonObject {
            put("status", "ok")
            put("configured", telegramSender.isConfigured())
        }
        call.respondText(
            status = HttpStatusCode.OK,
            text = payload.toString(),
            contentType = ContentType.Application.Json,
        )
    }

    private suspend fun handlePostSendText(call: ApplicationCall) {
        val request: TelegramSendTextRequest = parseSendTextRequest(call) ?: return
        val result: TelegramSendResult = telegramSender.sendText(request)
        respondTelegramResult(call, result)
    }

    private suspend fun handlePostSendImage(call: ApplicationCall) {
        val uploadedFile: UploadedFile = receiveUploadedFile(call) ?: run {
            respondTelegramJson(
                call = call,
                status = HttpStatusCode.BadRequest,
                ok = false,
                message = "Image file is required.",
            )
            return
        }

        val result: TelegramSendResult = telegramSender.sendImage(
            TelegramSendFileRequest(
                bytes = uploadedFile.bytes,
                fileName = uploadedFile.fileName,
                contentType = uploadedFile.contentType,
                caption = uploadedFile.caption,
            ),
        )
        respondTelegramResult(call, result)
    }

    private suspend fun handlePostSendExcel(call: ApplicationCall) {
        val uploadedFile: UploadedFile = receiveUploadedFile(call) ?: run {
            respondTelegramJson(
                call = call,
                status = HttpStatusCode.BadRequest,
                ok = false,
                message = "Excel file is required.",
            )
            return
        }

        val result: TelegramSendResult = telegramSender.sendExcel(
            TelegramSendFileRequest(
                bytes = uploadedFile.bytes,
                fileName = uploadedFile.fileName,
                contentType = uploadedFile.contentType,
                caption = uploadedFile.caption,
            ),
        )
        respondTelegramResult(call, result)
    }

    private suspend fun handleDeleteMessage(call: ApplicationCall) {
        val messageId: String = call.parameters["messageId"].orEmpty()
        respondTelegramJson(
            call = call,
            status = HttpStatusCode.NotImplemented,
            ok = false,
            message = "Delete is not enabled in send-only mode. Message ID: $messageId",
        )
    }

    private suspend fun receiveUploadedFile(call: ApplicationCall): UploadedFile? {
        val multipartData = call.receiveMultipart()

        var fileBytes: ByteArray? = null
        var fileName: String = "upload.bin"
        var contentType: String = ContentType.Application.OctetStream.toString()
        var caption: String? = null

        while (true) {
            val part: PartData = multipartData.readPart() ?: break
            when (part) {
                is PartData.FormItem -> {
                    if (part.name == "caption") {
                        val cleanedCaption: String = part.value.trim()
                        caption = cleanedCaption.ifEmpty { null }
                    }
                }

                is PartData.FileItem -> {
                    if (part.name == "file" && fileBytes == null) {
                        fileBytes = part.provider().readRemaining().readByteArray()
                        val originalFileName: String = part.originalFileName?.trim().orEmpty()
                        if (originalFileName.isNotEmpty()) {
                            fileName = originalFileName
                        }
                        contentType = part.contentType?.toString()
                            ?: ContentType.Application.OctetStream.toString()
                    }
                }

                else -> Unit
            }
            part.dispose()
        }

        val bytes: ByteArray = fileBytes ?: return null
        return UploadedFile(
            bytes = bytes,
            fileName = fileName,
            contentType = contentType,
            caption = caption,
        )
    }

    private suspend fun respondTelegramResult(
        call: ApplicationCall,
        result: TelegramSendResult,
    ) {
        val httpStatusCode: HttpStatusCode = when (result.status) {
            TelegramSendStatus.SUCCESS -> HttpStatusCode.OK
            TelegramSendStatus.BAD_REQUEST -> HttpStatusCode.BadRequest
            TelegramSendStatus.NOT_CONFIGURED -> HttpStatusCode.ServiceUnavailable
            TelegramSendStatus.FAILED -> HttpStatusCode.BadGateway
        }
        respondTelegramJson(
            call = call,
            status = httpStatusCode,
            ok = result.response.ok,
            message = result.response.message,
            telegramDescription = result.response.telegramDescription,
        )
    }

    private suspend fun parseSendTextRequest(call: ApplicationCall): TelegramSendTextRequest? {
        val bodyText: String = call.receiveText()
        val text: String = runCatching {
            requestJson
                .parseToJsonElement(bodyText)
                .jsonObject["text"]
                ?.jsonPrimitive
                ?.content
                ?.trim()
                .orEmpty()
        }.getOrDefault("")

        if (text.isEmpty()) {
            respondTelegramJson(
                call = call,
                status = HttpStatusCode.BadRequest,
                ok = false,
                message = "Request body must be valid JSON with a non-empty 'text' field.",
            )
            return null
        }

        return TelegramSendTextRequest(text = text)
    }

    private suspend fun respondTelegramJson(
        call: ApplicationCall,
        status: HttpStatusCode,
        ok: Boolean,
        message: String,
        telegramDescription: String? = null,
    ) {
        val payload = buildJsonObject {
            put("ok", ok)
            put("message", message)
            if (telegramDescription == null) {
                put("telegramDescription", JsonNull)
            } else {
                put("telegramDescription", telegramDescription)
            }
        }
        call.respondText(
            status = status,
            text = payload.toString(),
            contentType = ContentType.Application.Json,
        )
    }
}

fun Route.registerTelegramResource(
    resource: TelegramResource,
) {
    resource.register(this)
}
