package com.evi.resource

import com.evi.model.VisualBookImageMetadata
import com.evi.model.VisualBookLayoutRequest
import com.evi.service.VisualBookLayoutService
import com.google.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

private const val MAX_PAGE_TEXT_LENGTH = 12_000
private const val MAX_BOOK_TITLE_LENGTH = 120
private const val MAX_OBJECTIVE_LENGTH = 600
private const val MAX_IMAGES_PER_PAGE = 6
private const val MAX_REQUESTS_PER_MINUTE = 10
private const val RATE_LIMIT_WINDOW_MS = 60_000L
private const val MAX_TRACKED_CLIENTS = 1_000

@Path("/api/visual-book")
@Produces(MediaType.APPLICATION_JSON)
class VisualBookResource @Inject constructor(
    resourceScope: EviResourceScope,
    private val visualBookLayoutService: VisualBookLayoutService,
) {
    private val ioScope = resourceScope.ioScope
    private val rateLimiter = VisualBookRateLimiter()

    @POST
    @Path("/layout-options")
    @Consumes(MediaType.APPLICATION_JSON)
    fun generateLayoutOptions(
        request: VisualBookLayoutRequest,
        @Context headers: HttpHeaders,
    ): CompletableFuture<Response> {
        val clientId = headers.getHeaderString("X-Forwarded-For")
            ?.substringBefore(',')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "unknown"

        return ioScope.endpoint {
            if (!rateLimiter.allow(clientId)) {
                return@endpoint Response.status(429)
                    .entity(mapOf("detail" to "Please wait a minute before generating more layout options."))
                    .build()
            }

            val validatedRequest = try {
                validateVisualBookLayoutRequest(request)
            } catch (error: IllegalArgumentException) {
                return@endpoint badRequest(error.message ?: "Invalid layout request.")
            }

            ok(visualBookLayoutService.generate(validatedRequest))
        }
    }
}

internal fun validateVisualBookLayoutRequest(request: VisualBookLayoutRequest): VisualBookLayoutRequest {
    val pageText = request.pageText.trim()
    require(pageText.isNotEmpty()) { "pageText is required." }
    require(pageText.length <= MAX_PAGE_TEXT_LENGTH) { "pageText must not exceed $MAX_PAGE_TEXT_LENGTH characters." }

    val bookTitle = request.bookTitle?.trim()?.takeIf { it.isNotEmpty() }
    require(bookTitle == null || bookTitle.length <= MAX_BOOK_TITLE_LENGTH) { "bookTitle must not exceed $MAX_BOOK_TITLE_LENGTH characters." }

    val objective = request.objective?.trim()?.takeIf { it.isNotEmpty() }
    require(objective == null || objective.length <= MAX_OBJECTIVE_LENGTH) { "objective must not exceed $MAX_OBJECTIVE_LENGTH characters." }

    require(request.images.size <= MAX_IMAGES_PER_PAGE) { "A page can contain at most $MAX_IMAGES_PER_PAGE images." }
    val images = request.images.map(::validateImage)

    return request.copy(
        bookTitle = bookTitle,
        objective = objective,
        pageText = pageText,
        images = images,
    )
}

private fun validateImage(image: VisualBookImageMetadata): VisualBookImageMetadata {
    val name = image.name.trim()
    require(name.isNotEmpty() && name.length <= 160) { "Each image must have a name of at most 160 characters." }
    val mimeType = image.mimeType?.trim()?.takeIf { it.isNotEmpty() }
    require(mimeType == null || mimeType.startsWith("image/")) { "Each image must use an image MIME type." }
    require(image.byteSize == null || image.byteSize in 1L..20_000_000L) { "Each image must be between 1 byte and 20 MB." }
    return image.copy(name = name, mimeType = mimeType)
}

private class VisualBookRateLimiter {
    private val requestsByClient = ConcurrentHashMap<String, RateLimitWindow>()

    fun allow(clientId: String): Boolean {
        val now = System.currentTimeMillis()
        if (!requestsByClient.containsKey(clientId) && requestsByClient.size >= MAX_TRACKED_CLIENTS) {
            requestsByClient.entries.removeIf { (_, window) ->
                now - window.startedAtMs >= RATE_LIMIT_WINDOW_MS
            }
            if (requestsByClient.size >= MAX_TRACKED_CLIENTS) {
                return false
            }
        }
        val currentWindow = requestsByClient.compute(clientId) { _, existing ->
            when {
                existing == null || now - existing.startedAtMs >= RATE_LIMIT_WINDOW_MS -> RateLimitWindow(now, 1)
                else -> existing.copy(requestCount = existing.requestCount + 1)
            }
        } ?: return false

        return currentWindow.requestCount <= MAX_REQUESTS_PER_MINUTE
    }

    private data class RateLimitWindow(
        val startedAtMs: Long,
        val requestCount: Int,
    )
}
