package com.tradingtool.resources.watchlist

import com.google.inject.Inject
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.watchlist.IndicatorService
import com.tradingtool.core.watchlist.WatchlistService
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture

data class RefreshRequest(val tags: List<String> = emptyList())

@Path("/api/watchlist")
@Produces(MediaType.APPLICATION_JSON)
class WatchlistResource @Inject constructor(
    private val indicatorService: IndicatorService,
    private val watchlistService: WatchlistService,
    private val kiteClient: KiteConnectClient,
    private val resourceScope: ResourceScope,
) {
    private val ioScope = resourceScope.ioScope

    /**
     * Returns computed indicators (SMA, RSI, MACD, etc.) for all stocks under [tag].
     * Served from Redis L1; falls back to Postgres L2 on cache miss.
     */
    @GET
    @Path("/indicators")
    fun getIndicatorsByTag(@QueryParam("tag") tag: String?): CompletableFuture<Response> = ioScope.async {
        if (tag.isNullOrBlank()) return@async badRequest("Query parameter 'tag' is required")
        val indicators = indicatorService.getIndicatorsForTag(tag.trim())
        ok(indicators)
    }.asCompletableFuture()

    /**
     * Returns a merged watchlist row per stock under [tag]:
     * static info + computed indicators (Redis L1 / Postgres L2) + live LTP (Kite L0).
     * Live fields are null if Kite is unauthenticated or quotes are unavailable.
     */
    @GET
    @Path("/rows")
    fun getRows(@QueryParam("tag") tag: String?): CompletableFuture<Response> = ioScope.async {
        if (tag.isNullOrBlank()) return@async badRequest("Query parameter 'tag' is required")
        val rows = watchlistService.getRows(tag.trim())
        ok(rows)
    }.asCompletableFuture()

    /**
     * Returns raw OHLCV JSON for a stock (1 year, daily bars) from Redis L1.
     * Returns 404 if the cache is cold — the next cron run will repopulate it.
     */
    @GET
    @Path("/ohlcv/{instrumentToken}")
    fun getOhlcv(@PathParam("instrumentToken") instrumentToken: Long): CompletableFuture<Response> = ioScope.async {
        val raw = indicatorService.getHistoricalOhlcv(instrumentToken)
            ?: return@async notFound("OHLCV data not yet available for token $instrumentToken. It will be populated on the next cron run.")
        // Return the pre-serialized JSON string directly — do not wrap it.
        Response.ok(raw, MediaType.APPLICATION_JSON).build()
    }.asCompletableFuture()

    /**
     * Triggers a background indicator refresh and returns 202 immediately.
     *
     * - Empty or missing [tags] → refreshes all stocks (equivalent to running the cron job).
     * - Non-empty [tags] → refreshes only the stocks under those tags.
     *
     * Example: POST /api/watchlist/refresh  {"tags": ["momentum", "swing"]}
     */
    @POST
    @Path("/refresh")
    @Consumes(MediaType.APPLICATION_JSON)
    fun refresh(request: RefreshRequest?): CompletableFuture<Response> = ioScope.async {
        val tags = request?.tags?.filter { it.isNotBlank() } ?: emptyList()

        // Fire-and-forget: launch a sibling coroutine so this handler returns 202 immediately.
        ioScope.launch {
            if (tags.isEmpty()) {
                indicatorService.refreshAll(kiteClient)
            } else {
                tags.distinct().forEach { tag -> indicatorService.refreshTag(kiteClient, tag) }
            }
        }

        val message = if (tags.isEmpty()) "Refreshing all stocks" else "Refreshing tags: ${tags.joinToString()}"
        Response.accepted(mapOf("message" to message)).build()
    }.asCompletableFuture()

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun ok(entity: Any): Response = Response.ok(entity).build()
    private fun badRequest(detail: String): Response = Response.status(400).entity(error(detail)).build()
    private fun notFound(detail: String): Response = Response.status(404).entity(error(detail)).build()
    private fun error(detail: String) = mapOf("detail" to detail)
}
