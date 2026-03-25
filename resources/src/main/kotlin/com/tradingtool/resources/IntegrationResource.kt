package com.tradingtool.resources

import com.google.inject.Inject
import com.tradingtool.core.database.KiteTokenJdbiHandler
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.model.telegram.TelegramSendFileRequest
import com.tradingtool.core.model.telegram.TelegramSendResult
import com.tradingtool.core.model.telegram.TelegramSendStatus
import com.tradingtool.core.model.telegram.TelegramSendTextRequest
import com.tradingtool.core.telegram.TelegramSender
import com.tradingtool.model.telegram.TelegramRequestModel
import com.tradingtool.model.telegram.TelegramResponseModel
import com.tradingtool.resources.common.badRequest
import com.tradingtool.resources.common.endpoint
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.glassfish.jersey.media.multipart.FormDataBodyPart
import org.glassfish.jersey.media.multipart.FormDataParam
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.util.concurrent.CompletableFuture

/**
 * Handles health checks, Kite OAuth, and Telegram messaging.
 * All external integration endpoints live here.
 */
@Path("/")
class IntegrationResource @Inject constructor(
    private val kiteClient: KiteConnectClient,
    private val tokenDb: KiteTokenJdbiHandler,
    private val telegramSender: TelegramSender,
    private val instrumentCache: InstrumentCache,
    private val resourceScope: ResourceScope,
) {
    private val ioScope = resourceScope.ioScope
    private val log = LoggerFactory.getLogger(javaClass)

    // ── Health ────────────────────────────────────────────────────────────────

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun root(): Response = Response.ok(mapOf("service" to "TradingTool-3", "status" to "ok")).build()

    @GET
    @Path("health")
    @Produces(MediaType.APPLICATION_JSON)
    fun health(): Response = Response.ok(mapOf("status" to "ok")).build()

    // ── Kite OAuth ────────────────────────────────────────────────────────────

    /**
     * Returns the Kite login URL.
     * Open in browser to start the OAuth flow.
     * Telegram cron sends this link daily after 6 AM IST token expiry.
     */
    @GET
    @Path("kite/login")
    @Produces(MediaType.APPLICATION_JSON)
    fun getLoginUrl(): CompletableFuture<Response> = ioScope.endpoint {
        Response.ok(mapOf("loginUrl" to kiteClient.loginUrl())).build()
    }

    /**
     * Zerodha redirects here after the user logs in.
     * Exchanges request_token for access_token, persists to Supabase, notifies via Telegram.
     *
     * Registered redirect URL: https://tradingtool-3-service.onrender.com/kite/callback
     */
    @GET
    @Path("kite/callback")
    @Produces(MediaType.APPLICATION_JSON)
    fun handleCallback(
        @QueryParam("request_token") requestToken: String?,
        @QueryParam("status") status: String?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        if (status != null && status != "success") {
            log.warn("[KiteCallback] Zerodha returned status={} — login was not completed", status)
            return@endpoint badRequest("Kite login was not completed. status=$status")
        }
        if (requestToken.isNullOrBlank()) return@endpoint badRequest("Missing request_token parameter")

        log.info(
            "[KiteCallback] Received callback with request token prefix={} | apiKey prefix={} | apiSecret length={}",
            requestToken.prefix(),
            kiteClient.apiKey.take(8),
            kiteClient.apiSecretLength(),
        )

        val sessionResult = kiteClient.generateSession(requestToken)
        when (sessionResult) {
            is com.tradingtool.core.http.Result.Success -> {
                val user = sessionResult.data
                val accessToken = user.accessToken
                if (accessToken.isBlank() || accessToken == requestToken) {
                    log.error(
                        "[KiteCallback] Refusing to persist invalid access token. requestPrefix={}, accessPrefix={}",
                        requestToken.prefix(),
                        accessToken.prefix(),
                    )
                    return@endpoint Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(mapOf("error" to "Kite returned an invalid access token."))
                        .build()
                }

                log.info(
                    "[KiteCallback] Session exchange succeeded for userId={}, access token prefix={}",
                    user.userId,
                    accessToken.prefix(),
                )
                tokenDb.write { dao -> dao.saveToken(accessToken) }
                telegramSender.sendText(
                    TelegramSendTextRequest("Kite login successful. Token refreshed for user: ${user.userId}")
                )
                // Refresh instrument cache on a background thread — non-blocking.
                Thread {
                    try {
                        val instruments = kiteClient.client().getInstruments("NSE")
                        instrumentCache.refresh(instruments)
                        log.info("[InstrumentCache] Refreshed {} NSE instruments after login", instrumentCache.size())
                    } catch (e: Exception) {
                        log.error("[InstrumentCache] Cache refresh after login failed: {}", e.message)
                    }
                }.also { it.isDaemon = true }.start()
                Response.ok(mapOf("status" to "authenticated", "userId" to user.userId)).build()
            }
            is com.tradingtool.core.http.Result.Failure -> {
                log.error(
                    "[KiteCallback] Session exchange failed for request token prefix={}: {}",
                    requestToken.prefix(),
                    sessionResult.error.describe(),
                )
                Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(mapOf("error" to sessionResult.error.describe()))
                    .build()
            }
        }
    }

    // ── Telegram ──────────────────────────────────────────────────────────────

    @GET
    @Path("api/telegram/status")
    @Produces(MediaType.APPLICATION_JSON)
    fun getTelegramStatus(): Response = Response.ok(
        TelegramResponseModel.StatusResponse(status = "ok", configured = telegramSender.isConfigured())
    ).build()

    @POST
    @Path("api/telegram/send/text")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun sendText(body: TelegramRequestModel.SendTextRequest?): CompletableFuture<Response> = ioScope.endpoint {
        val text = body?.text?.trim().orEmpty()
        if (text.isEmpty()) {
            return@endpoint Response.status(400)
                .entity(TelegramResponseModel.ActionResponse(ok = false, message = "Request body must have a non-empty 'text' field."))
                .build()
        }
        toTelegramResponse(telegramSender.sendText(TelegramSendTextRequest(text = text)))
    }

    @POST
    @Path("api/telegram/send/image")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    fun sendImage(
        @FormDataParam("file") inputStream: InputStream?,
        @FormDataParam("file") fileMetadata: FormDataBodyPart?,
        @FormDataParam("caption") caption: String?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        handleFileUpload(inputStream, fileMetadata, caption, "Image file is required.", telegramSender::sendImage)
    }

    @POST
    @Path("api/telegram/send/excel")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    fun sendExcel(
        @FormDataParam("file") inputStream: InputStream?,
        @FormDataParam("file") fileMetadata: FormDataBodyPart?,
        @FormDataParam("caption") caption: String?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        handleFileUpload(inputStream, fileMetadata, caption, "Excel file is required.", telegramSender::sendExcel)
    }

    @DELETE
    @Path("api/telegram/messages/{messageId}")
    @Produces(MediaType.APPLICATION_JSON)
    fun deleteMessage(@PathParam("messageId") messageId: String): Response = Response.status(501)
        .entity(TelegramResponseModel.ActionResponse(ok = false, message = "Delete is not enabled in send-only mode. Message ID: $messageId"))
        .build()

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

    private fun String.prefix(): String = take(12)
}
