package com.evi.resource

import com.evi.model.AuthorityGenerationRequest
import com.evi.service.AuthorityGenerationService
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

private const val MAX_SOURCE_DOCUMENT_LENGTH = 60_000
private const val MAX_SOURCE_STRUCTURE_LENGTH = 120_000
private const val MAX_USER_INSTRUCTIONS_LENGTH = 4_000
private const val MAX_AUTHORITY_IMAGES = 6
private const val MAX_AUTHORITY_REQUESTS_PER_MINUTE = 10
private const val AUTHORITY_RATE_LIMIT_WINDOW_MS = 60_000L
private const val MAX_TRACKED_AUTHORITY_CLIENTS = 1_000

@Path("/api/visual-book")
@Produces(MediaType.APPLICATION_JSON)
class AuthorityGenerationResource @Inject constructor(
    resourceScope: EviResourceScope,
    private val authorityGenerationService: AuthorityGenerationService,
) {
    private val ioScope = resourceScope.ioScope
    private val rateLimiter = AuthorityGenerationRateLimiter()

    @POST
    @Path("/authority")
    @Consumes(MediaType.APPLICATION_JSON)
    fun generateAuthorityResult(
        request: AuthorityGenerationRequest,
        @Context headers: HttpHeaders,
    ): CompletableFuture<Response> = ioScope.endpoint {
        if (!rateLimiter.allow(clientId(headers))) {
            return@endpoint Response.status(429)
                .entity(mapOf("detail" to "Please wait a minute before generating another authority result."))
                .build()
        }

        val validatedRequest = try {
            validateAuthorityGenerationRequest(request)
        } catch (error: IllegalArgumentException) {
            return@endpoint badRequest(error.message ?: "Invalid authority generation request.")
        }

        ok(authorityGenerationService.generate(validatedRequest))
    }

    private fun clientId(headers: HttpHeaders): String = headers.getHeaderString("X-Forwarded-For")
        ?.substringBefore(',')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: "unknown"
}

internal fun validateAuthorityGenerationRequest(request: AuthorityGenerationRequest): AuthorityGenerationRequest {
    require(request.authorityMode != null) { "authorityMode is required." }

    val sourceContent = request.sourceDocumentContent.trim()
    require(sourceContent.isNotEmpty()) { "sourceDocumentContent is required." }
    require(sourceContent.length <= MAX_SOURCE_DOCUMENT_LENGTH) {
        "sourceDocumentContent must not exceed $MAX_SOURCE_DOCUMENT_LENGTH characters."
    }

    val structureJson = request.sourceStructureJson?.trim()?.takeIf { it.isNotEmpty() }
    require(structureJson == null || structureJson.length <= MAX_SOURCE_STRUCTURE_LENGTH) {
        "sourceStructureJson must not exceed $MAX_SOURCE_STRUCTURE_LENGTH characters."
    }

    val instructions = request.userInstructions?.trim()?.takeIf { it.isNotEmpty() }
    require(instructions == null || instructions.length <= MAX_USER_INSTRUCTIONS_LENGTH) {
        "userInstructions must not exceed $MAX_USER_INSTRUCTIONS_LENGTH characters."
    }

    require(request.visualAssets.size <= MAX_AUTHORITY_IMAGES) {
        "A request can contain at most $MAX_AUTHORITY_IMAGES visual assets."
    }

    return request.copy(
        sourceDocumentContent = sourceContent,
        sourceStructureJson = structureJson,
        userInstructions = instructions,
    )
}

private class AuthorityGenerationRateLimiter {
    private val requestsByClient = ConcurrentHashMap<String, RateLimitWindow>()

    fun allow(clientId: String): Boolean {
        val now = System.currentTimeMillis()
        if (!requestsByClient.containsKey(clientId) && requestsByClient.size >= MAX_TRACKED_AUTHORITY_CLIENTS) {
            requestsByClient.entries.removeIf { (_, window) ->
                now - window.startedAtMs >= AUTHORITY_RATE_LIMIT_WINDOW_MS
            }
            if (requestsByClient.size >= MAX_TRACKED_AUTHORITY_CLIENTS) return false
        }

        val currentWindow = requestsByClient.compute(clientId) { _, existing ->
            when {
                existing == null || now - existing.startedAtMs >= AUTHORITY_RATE_LIMIT_WINDOW_MS -> RateLimitWindow(now, 1)
                else -> existing.copy(requestCount = existing.requestCount + 1)
            }
        } ?: return false

        return currentWindow.requestCount <= MAX_AUTHORITY_REQUESTS_PER_MINUTE
    }

    private data class RateLimitWindow(
        val startedAtMs: Long,
        val requestCount: Int,
    )
}
