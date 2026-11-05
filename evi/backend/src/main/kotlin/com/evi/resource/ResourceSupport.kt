package com.evi.resource

import com.google.inject.Singleton
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

@Singleton
open class EviResourceScope(
    val ioScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
)

fun ok(entity: Any): Response = Response.ok(entity).build()
fun created(entity: Any): Response = Response.status(201).entity(entity).build()
fun badRequest(detail: String): Response = Response.status(400).entity(error(detail)).build()
fun notFound(detail: String): Response = Response.status(404).entity(error(detail)).build()
fun serviceUnavailable(detail: String): Response = Response.status(503).entity(error(detail)).build()
fun internalError(detail: String): Response = Response.status(500).entity(error(detail)).build()

private fun error(detail: String) = mapOf("detail" to detail)

private val log = LoggerFactory.getLogger("EviAsyncEndpoint")

fun CoroutineScope.endpoint(block: suspend () -> Response): CompletableFuture<Response> =
    async {
        runCatching { block() }
            .getOrElse { e ->
                log.error("Unhandled API exception in endpoint", e)
                internalError(e.message ?: "Unexpected error")
            }
    }.asCompletableFuture()
