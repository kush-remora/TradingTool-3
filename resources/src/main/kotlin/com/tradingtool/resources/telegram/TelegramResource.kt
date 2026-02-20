package com.tradingtool.resources.telegram

import com.tradingtool.core.model.telegram.TelegramSendFileRequest
import com.tradingtool.core.model.telegram.TelegramSendResult
import com.tradingtool.core.model.telegram.TelegramSendStatus
import com.tradingtool.core.model.telegram.TelegramSendTextRequest
import com.tradingtool.core.telegram.TelegramSender
import com.tradingtool.model.telegram.TelegramRequestModel
import com.tradingtool.model.telegram.TelegramResponseModel
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import org.glassfish.jersey.media.multipart.FormDataBodyPart
import org.glassfish.jersey.media.multipart.FormDataParam
import java.io.InputStream
import java.util.concurrent.CompletableFuture

@Path("/api/telegram")
class TelegramResource(
    private val telegramSender: TelegramSender,
) {
    /**
     * Coroutine scope for lightweight request handling.
     * Uses Dispatchers.Default for non-blocking work.
     * Actual I/O operations happen in HttpRequestExecutor's Dispatchers.IO pool.
     */
    private val resourceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @GET
    @Path("status")
    @Produces(MediaType.APPLICATION_JSON)
    fun getStatus(): Response {
        return Response.ok(
            TelegramResponseModel.StatusResponse(
                status = "ok",
                configured = telegramSender.isConfigured(),
            ),
        ).build()
    }

    @POST
    @Path("send/text")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun sendText(body: TelegramRequestModel.SendTextRequest?): CompletableFuture<Response> = resourceScope.async {
        val text: String = body?.text?.trim().orEmpty()
        if (text.isEmpty()) {
            return@async Response.status(400)
                .entity(
                    TelegramResponseModel.ActionResponse(
                        ok = false,
                        message = "Request body must be valid JSON with a non-empty 'text' field.",
                    ),
                )
                .build()
        }

        val result = telegramSender.sendText(TelegramSendTextRequest(text = text))
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
    ): CompletableFuture<Response> = resourceScope.async {
        if (inputStream == null || fileMetadata == null) {
            return@async Response.status(400)
                .entity(
                    TelegramResponseModel.ActionResponse(
                        ok = false,
                        message = "Image file is required.",
                    ),
                )
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
    ): CompletableFuture<Response> = resourceScope.async {
        if (inputStream == null || fileMetadata == null) {
            return@async Response.status(400)
                .entity(
                    TelegramResponseModel.ActionResponse(
                        ok = false,
                        message = "Excel file is required.",
                    ),
                )
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
        return Response.status(501)
            .entity(
                TelegramResponseModel.ActionResponse(
                    ok = false,
                    message = "Delete is not enabled in send-only mode. Message ID: $messageId",
                ),
            )
            .build()
    }

    private fun toTelegramResponse(result: TelegramSendResult): Response {
        val httpStatus = when (result.status) {
            TelegramSendStatus.SUCCESS -> 200
            TelegramSendStatus.BAD_REQUEST -> 400
            TelegramSendStatus.NOT_CONFIGURED -> 503
            TelegramSendStatus.FAILED -> 502
        }

        return Response.status(httpStatus)
            .entity(
                TelegramResponseModel.ActionResponse(
                    ok = result.response.ok,
                    message = result.response.message,
                    telegramDescription = result.response.telegramDescription,
                ),
            )
            .build()
    }
}
