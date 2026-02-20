package com.tradingtool.resources.telegram

import com.tradingtool.core.model.telegram.TelegramSendFileRequest
import com.tradingtool.core.model.telegram.TelegramSendResult
import com.tradingtool.core.model.telegram.TelegramSendStatus
import com.tradingtool.core.model.telegram.TelegramSendTextRequest
import com.tradingtool.core.telegram.TelegramSender
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.glassfish.jersey.media.multipart.FormDataBodyPart
import org.glassfish.jersey.media.multipart.FormDataParam
import java.io.InputStream
import java.util.concurrent.CompletableFuture

@Path("/api/telegram")
class TelegramResource(
    private val telegramSender: TelegramSender,
) {
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    @GET
    @Path("status")
    @Produces(MediaType.APPLICATION_JSON)
    fun getStatus(): Response {
        val payload = buildJsonObject {
            put("status", "ok")
            put("configured", telegramSender.isConfigured())
        }
        return Response.ok(payload.toString()).type(MediaType.APPLICATION_JSON).build()
    }

    @POST
    @Path("send/text")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun sendText(body: String): CompletableFuture<Response> = ioScope.async {
        val request = parseTextRequest(body)
            ?: return@async Response.status(400)
                .entity(errorJson("Request body must be valid JSON with a non-empty 'text' field."))
                .type(MediaType.APPLICATION_JSON)
                .build()

        val result = telegramSender.sendText(request)
        toTelegramResponse(result)
    }.asCompletableFuture()

    @POST
    @Path("send/image")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    fun sendImage(
        @FormDataParam("file") inputStream: InputStream?,
        @FormDataParam("file") fileMetadata: FormDataBodyPart?,
        @FormDataParam("caption") caption: String?,
    ): CompletableFuture<Response> = ioScope.async {
        if (inputStream == null || fileMetadata == null) {
            return@async Response.status(400)
                .entity(errorJson("Image file is required."))
                .type(MediaType.APPLICATION_JSON)
                .build()
        }

        val fileBytes = inputStream.readBytes()
        val fileName = fileMetadata.contentDisposition?.fileName ?: "image.bin"
        val contentType = fileMetadata.mediaType?.toString() ?: "application/octet-stream"

        val request = TelegramSendFileRequest(
            bytes = fileBytes,
            fileName = fileName,
            contentType = contentType,
            caption = caption?.trim()?.takeIf { it.isNotEmpty() },
        )

        val result = telegramSender.sendImage(request)
        toTelegramResponse(result)
    }.asCompletableFuture()

    @POST
    @Path("send/excel")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    fun sendExcel(
        @FormDataParam("file") inputStream: InputStream?,
        @FormDataParam("file") fileMetadata: FormDataBodyPart?,
        @FormDataParam("caption") caption: String?,
    ): CompletableFuture<Response> = ioScope.async {
        if (inputStream == null || fileMetadata == null) {
            return@async Response.status(400)
                .entity(errorJson("Excel file is required."))
                .type(MediaType.APPLICATION_JSON)
                .build()
        }

        val fileBytes = inputStream.readBytes()
        val fileName = fileMetadata.contentDisposition?.fileName ?: "file.xlsx"
        val contentType = fileMetadata.mediaType?.toString() ?: "application/octet-stream"

        val request = TelegramSendFileRequest(
            bytes = fileBytes,
            fileName = fileName,
            contentType = contentType,
            caption = caption?.trim()?.takeIf { it.isNotEmpty() },
        )

        val result = telegramSender.sendExcel(request)
        toTelegramResponse(result)
    }.asCompletableFuture()

    @DELETE
    @Path("messages/{messageId}")
    @Produces(MediaType.APPLICATION_JSON)
    fun deleteMessage(@PathParam("messageId") messageId: String): Response {
        val payload = buildJsonObject {
            put("ok", false)
            put("message", "Delete is not enabled in send-only mode. Message ID: $messageId")
        }
        return Response.status(501).entity(payload.toString()).type(MediaType.APPLICATION_JSON).build()
    }

    private fun parseTextRequest(body: String): TelegramSendTextRequest? {
        val text = runCatching {
            val jsonElement = json.parseToJsonElement(body) as? JsonObject ?: return null
            jsonElement["text"]?.jsonPrimitive?.content?.trim() ?: ""
        }.getOrElse { "" }

        if (text.isEmpty()) {
            return null
        }

        return TelegramSendTextRequest(text = text)
    }

    private fun toTelegramResponse(result: TelegramSendResult): Response {
        val httpStatus = when (result.status) {
            TelegramSendStatus.SUCCESS -> 200
            TelegramSendStatus.BAD_REQUEST -> 400
            TelegramSendStatus.NOT_CONFIGURED -> 503
            TelegramSendStatus.FAILED -> 502
        }

        val payload = buildJsonObject {
            put("ok", result.response.ok)
            put("message", result.response.message)
            put("telegramDescription", result.response.telegramDescription)
        }

        return Response.status(httpStatus)
            .entity(payload.toString())
            .type(MediaType.APPLICATION_JSON)
            .build()
    }

    private fun errorJson(message: String): String {
        return buildJsonObject {
            put("ok", false)
            put("message", message)
        }.toString()
    }
}
