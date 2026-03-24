package com.tradingtool.resources.watchlist

import com.google.inject.Inject
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.core.watchlist.IndicatorService
import com.tradingtool.core.watchlist.WatchlistService
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import java.util.concurrent.CompletableFuture

@Path("/api/watchlist")
@Produces(MediaType.APPLICATION_JSON)
class WatchlistResource @Inject constructor(
    private val indicatorService: IndicatorService,
    private val watchlistService: WatchlistService,
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

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun ok(entity: Any): Response = Response.ok(entity).build()
    private fun badRequest(detail: String): Response = Response.status(400).entity(error(detail)).build()
    private fun notFound(detail: String): Response = Response.status(404).entity(error(detail)).build()
    private fun error(detail: String) = mapOf("detail" to detail)
}
