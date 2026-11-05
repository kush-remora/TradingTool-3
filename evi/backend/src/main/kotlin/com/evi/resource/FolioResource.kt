package com.evi.resource

import com.evi.model.CreateFolioRequest
import com.evi.service.FolioExtractionException
import com.evi.service.FolioNotFoundException
import com.evi.service.FolioPageOutOfBoundsException
import com.evi.service.FolioService
import com.evi.service.FolioStorageNotConfiguredException
import com.google.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.glassfish.jersey.media.multipart.FormDataBodyPart
import org.glassfish.jersey.media.multipart.FormDataParam
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

private const val MAX_FOLIO_TITLE_LENGTH = 120
private const val MAX_FOLIO_OBJECTIVE_LENGTH = 600
private const val MAX_FOLIO_THEME_LENGTH = 120
private const val MAX_FOLIO_FILE_NAME_LENGTH = 255
private const val MAX_FOLIO_PDF_BYTES = 50 * 1024 * 1024
private const val MAX_FOLIO_UPLOADS_PER_MINUTE = 5
private const val FOLIO_RATE_LIMIT_WINDOW_MS = 60_000L
private const val MAX_TRACKED_FOLIO_CLIENTS = 1_000
private val PDF_SIGNATURE = "%PDF-".encodeToByteArray()

@Path("/api/folios")
@Produces(MediaType.APPLICATION_JSON)
class FolioResource @Inject constructor(
    resourceScope: EviResourceScope,
    private val folioService: FolioService,
) {
    private val ioScope = resourceScope.ioScope
    private val uploadRateLimiter = FolioUploadRateLimiter()

    @GET
    fun listFolios(): CompletableFuture<Response> = ioScope.endpoint {
        ok(folioService.listRecent())
    }

    @GET
    @Path("/{id}/pages/{pageNumber}")
    fun getPage(
        @PathParam("id") idString: String,
        @PathParam("pageNumber") pageNumber: Int,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val folioId = try {
            UUID.fromString(idString.trim())
        } catch (_: IllegalArgumentException) {
            return@endpoint badRequest("Invalid folio ID format.")
        }
        if (pageNumber < 1) {
            return@endpoint badRequest("Page number must be at least 1.")
        }

        try {
            ok(folioService.getPage(folioId, pageNumber))
        } catch (error: FolioNotFoundException) {
            notFound(error.message ?: "Folio not found.")
        } catch (error: FolioPageOutOfBoundsException) {
            badRequest(error.message ?: "Page out of bounds.")
        } catch (_: FolioStorageNotConfiguredException) {
            serviceUnavailable("PDF storage is not configured yet.")
        } catch (error: FolioExtractionException) {
            Response.status(422)
                .entity(mapOf("detail" to (error.message ?: "Unable to extract text from PDF.")))
                .build()
        } catch (error: Exception) {
            Response.status(500)
                .entity(mapOf("detail" to (error.message ?: "Unable to load folio page.")))
                .build()
        }
    }

    @GET
    @Path("/{id}/pages/{pageNumber}/image")
    @Produces("image/png", MediaType.APPLICATION_JSON)
    fun getPageImage(
        @PathParam("id") idString: String,
        @PathParam("pageNumber") pageNumber: Int,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val folioId = try {
            UUID.fromString(idString.trim())
        } catch (_: IllegalArgumentException) {
            return@endpoint Response.status(400)
                .type(MediaType.APPLICATION_JSON)
                .entity(mapOf("detail" to "Invalid folio ID format."))
                .build()
        }
        if (pageNumber < 1) {
            return@endpoint Response.status(400)
                .type(MediaType.APPLICATION_JSON)
                .entity(mapOf("detail" to "Page number must be at least 1."))
                .build()
        }

        try {
            Response.ok(folioService.renderPage(folioId, pageNumber), "image/png")
                .header("Cache-Control", "private, max-age=300")
                .build()
        } catch (error: FolioNotFoundException) {
            notFound(error.message ?: "Folio not found.")
        } catch (error: FolioPageOutOfBoundsException) {
            badRequest(error.message ?: "Page out of bounds.")
        } catch (_: FolioStorageNotConfiguredException) {
            serviceUnavailable("PDF storage is not configured yet.")
        } catch (error: FolioExtractionException) {
            Response.status(422)
                .entity(mapOf("detail" to (error.message ?: "Unable to render PDF page.")))
                .type(MediaType.APPLICATION_JSON)
                .build()
        } catch (error: Exception) {
            Response.status(500)
                .entity(mapOf("detail" to (error.message ?: "Unable to render PDF page.")))
                .type(MediaType.APPLICATION_JSON)
                .build()
        }
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    fun createFolio(
        @FormDataParam("title") title: String?,
        @FormDataParam("objective") objective: String?,
        @FormDataParam("theme") theme: String?,
        @FormDataParam("file") inputStream: InputStream?,
        @FormDataParam("file") fileMetadata: FormDataBodyPart?,
        @Context headers: HttpHeaders,
    ): CompletableFuture<Response> = ioScope.endpoint {
        if (!uploadRateLimiter.allow(clientId(headers))) {
            return@endpoint Response.status(429)
                .entity(mapOf("detail" to "Please wait a minute before uploading another folio."))
                .build()
        }

        val pdfBytes = try {
            readPdf(inputStream)
        } catch (error: IllegalArgumentException) {
            return@endpoint badRequest(error.message ?: "Invalid PDF upload.")
        }
        val request = try {
            validateCreateFolioRequest(title, objective, theme, fileMetadata, pdfBytes)
        } catch (error: IllegalArgumentException) {
            return@endpoint badRequest(error.message ?: "Invalid folio details.")
        }

        try {
            created(folioService.create(request, pdfBytes))
        } catch (_: FolioStorageNotConfiguredException) {
            serviceUnavailable("PDF storage is not configured yet.")
        }
    }

    private fun clientId(headers: HttpHeaders): String = headers.getHeaderString("X-Forwarded-For")
        ?.substringBefore(',')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: "unknown"
}

internal fun validateCreateFolioRequest(
    title: String?,
    objective: String?,
    theme: String?,
    fileMetadata: FormDataBodyPart?,
    pdfBytes: ByteArray,
): CreateFolioRequest {
    val normalizedTitle = requiredText(title, "Title", MAX_FOLIO_TITLE_LENGTH)
    val normalizedObjective = requiredText(objective, "Objective", MAX_FOLIO_OBJECTIVE_LENGTH)
    val normalizedTheme = optionalText(theme, MAX_FOLIO_THEME_LENGTH)
    val fileName = (fileMetadata?.formDataContentDisposition?.fileName
        ?: fileMetadata?.contentDisposition?.fileName)
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("A PDF file is required (field name: file).")

    require(fileName.length <= MAX_FOLIO_FILE_NAME_LENGTH) {
        "PDF file name must not exceed $MAX_FOLIO_FILE_NAME_LENGTH characters."
    }
    require(fileName.endsWith(".pdf", ignoreCase = true)) { "Uploaded file must use a .pdf extension." }
    require(pdfBytes.hasPdfSignature()) { "Uploaded file is not a valid PDF." }

    return CreateFolioRequest(
        title = normalizedTitle,
        objective = normalizedObjective,
        theme = normalizedTheme,
        sourceFileName = fileName,
        sourceFileSizeBytes = pdfBytes.size.toLong(),
    )
}

private fun readPdf(inputStream: InputStream?): ByteArray {
    requireNotNull(inputStream) { "A PDF file is required (field name: file)." }
    val bytes = inputStream.readNBytes(MAX_FOLIO_PDF_BYTES + 1)
    require(bytes.isNotEmpty()) { "Uploaded PDF is empty." }
    require(bytes.size <= MAX_FOLIO_PDF_BYTES) { "Uploaded PDF exceeds the 50 MB limit." }
    return bytes
}

private fun requiredText(value: String?, label: String, maxLength: Int): String {
    val normalized = value?.trim().orEmpty()
    require(normalized.isNotEmpty()) { "$label is required." }
    require(normalized.length <= maxLength) { "$label must not exceed $maxLength characters." }
    return normalized
}

private fun optionalText(value: String?, maxLength: Int): String? {
    val normalized = value?.trim()?.takeIf { it.isNotEmpty() }
    require(normalized == null || normalized.length <= maxLength) {
        "Theme must not exceed $maxLength characters."
    }
    return normalized
}

private fun ByteArray.hasPdfSignature(): Boolean = size >= PDF_SIGNATURE.size &&
    PDF_SIGNATURE.indices.all { index -> this[index] == PDF_SIGNATURE[index] }

private class FolioUploadRateLimiter {
    private val requestsByClient = ConcurrentHashMap<String, FolioRateLimitWindow>()

    fun allow(clientId: String): Boolean {
        val now = System.currentTimeMillis()
        if (!requestsByClient.containsKey(clientId) && requestsByClient.size >= MAX_TRACKED_FOLIO_CLIENTS) {
            requestsByClient.entries.removeIf { (_, window) ->
                now - window.startedAtMs >= FOLIO_RATE_LIMIT_WINDOW_MS
            }
            if (requestsByClient.size >= MAX_TRACKED_FOLIO_CLIENTS) {
                return false
            }
        }
        val currentWindow = requestsByClient.compute(clientId) { _, existing ->
            when {
                existing == null || now - existing.startedAtMs >= FOLIO_RATE_LIMIT_WINDOW_MS -> FolioRateLimitWindow(now, 1)
                else -> existing.copy(requestCount = existing.requestCount + 1)
            }
        } ?: return false

        return currentWindow.requestCount <= MAX_FOLIO_UPLOADS_PER_MINUTE
    }

    private data class FolioRateLimitWindow(
        val startedAtMs: Long,
        val requestCount: Int,
    )
}
