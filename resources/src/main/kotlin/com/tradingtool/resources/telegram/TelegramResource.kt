package com.tradingtool.resources.telegram

import com.tradingtool.core.model.telegram.TelegramSendFileRequest
import com.tradingtool.core.model.telegram.TelegramSendResult
import com.tradingtool.core.model.telegram.TelegramSendStatus
import com.tradingtool.core.model.telegram.TelegramSendTextRequest
import com.tradingtool.core.telegram.TelegramSender
import com.tradingtool.model.telegram.TelegramRequestModel
import com.tradingtool.model.telegram.TelegramResponseModel
import com.google.inject.Inject
import com.tradingtool.core.di.ResourceScope
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import org.glassfish.jersey.media.multipart.FormDataBodyPart
import org.glassfish.jersey.media.multipart.FormDataParam
import java.io.InputStream
import java.util.concurrent.CompletableFuture

@Path("/api/telegram")
class TelegramResource @Inject constructor(
    private val telegramSender: TelegramSender,
    private val resourceScope: ResourceScope,
) {
    private val ioScope = resourceScope.ioScope

    @GET
    @Path("status")
    @Produces(MediaType.APPLICATION_JSON)
    fun getStatus(): Response = Response.ok(
        TelegramResponseModel.StatusResponse(status = "ok", configured = telegramSender.isConfigured())
    ).build()

    @POST
    @Path("send/text")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun sendText(body: TelegramRequestModel.SendTextRequest?): CompletableFuture<Response> = ioScope.async {
        val text = body?.text?.trim().orEmpty()
        if (text.isEmpty()) {
            return@async Response.status(400)
                .entity(TelegramResponseModel.ActionResponse(ok = false, message = "Request body must have a non-empty 'text' field."))
                .build()
        }
        toTelegramResponse(telegramSender.sendText(TelegramSendTextRequest(text = text)))
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
        handleFileUpload(inputStream, fileMetadata, caption, "Image file is required.", telegramSender::sendImage)
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
        handleFileUpload(inputStream, fileMetadata, caption, "Excel file is required.", telegramSender::sendExcel)
    }.asCompletableFuture()

    @DELETE
    @Path("messages/{messageId}")
    @Produces(MediaType.APPLICATION_JSON)
    fun deleteMessage(@PathParam("messageId") messageId: String): Response = Response.status(501)
        .entity(TelegramResponseModel.ActionResponse(ok = false, message = "Delete is not enabled in send-only mode. Message ID: $messageId"))
        .build()

    // Shared handler for multipart file uploads — eliminates duplication between sendImage and sendExcel.
    private suspend fun handleFileUpload(
        inputStream: InputStream?,
        fileMetadata: FormDataBodyPart?,
        caption: String?,
        missingFileMessage: String,
        senderFn: suspend (TelegramSendFileRequest) -> TelegramSendResult,
    ): Response {
        if (inputStream == null || fileMetadata == null) {
            return Response.status(400)
                .entity(TelegramResponseModel.ActionResponse(ok = false, message = missingFileMessage))
                .build()
        }
        val request = TelegramSendFileRequest(
            bytes = inputStream.readBytes(),
            fileName = fileMetadata.contentDisposition?.fileName ?: "upload.bin",
            contentType = fileMetadata.mediaType?.toString() ?: "application/octet-stream",
            caption = caption?.trim()?.takeIf { it.isNotEmpty() },
        )
        return toTelegramResponse(senderFn(request))
    }

    private fun toTelegramResponse(result: TelegramSendResult): Response {
        val httpStatus = when (result.status) {
            TelegramSendStatus.SUCCESS -> 200
            TelegramSendStatus.BAD_REQUEST -> 400
            TelegramSendStatus.NOT_CONFIGURED -> 503
            TelegramSendStatus.FAILED -> 502
        }
        return Response.status(httpStatus)
            .entity(TelegramResponseModel.ActionResponse(
                ok = result.response.ok,
                message = result.response.message,
                telegramDescription = result.response.telegramDescription,
            ))
            .build()
    }
}
