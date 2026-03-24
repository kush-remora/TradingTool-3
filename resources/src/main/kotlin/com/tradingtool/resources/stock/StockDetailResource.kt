package com.tradingtool.resources.stock

import com.google.inject.Inject
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.stock.service.StockDetailService
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import java.util.concurrent.CompletableFuture

@Path("/api/stocks")
@Produces(MediaType.APPLICATION_JSON)
class StockDetailResource @Inject constructor(
    private val stockDetailService: StockDetailService,
    private val kiteClient: KiteConnectClient,
    private val resourceScope: ResourceScope,
) {
    private val ioScope = resourceScope.ioScope

    /**
     * Returns 7-day OHLCV detail for a stock: daily % change, RSI14, and volume vs 20-day average.
     * Fetches live from Kite — not cached, do not call in tight loops.
     *
     * Example: GET /api/stocks/by-symbol/RELIANCE/detail
     */
    @GET
    @Path("/by-symbol/{symbol}/detail")
    fun getDetail(@PathParam("symbol") symbol: String): CompletableFuture<Response> = ioScope.async {
        val sym = symbol.trim().uppercase()
        if (sym.isEmpty()) return@async badRequest("Symbol is required")
        if (!kiteClient.isAuthenticated) return@async serviceUnavailable("Kite is not authenticated")

        val detail = stockDetailService.getDetail(sym, kiteClient)
            ?: return@async notFound("Stock '$sym' not found in watchlist")

        Response.ok(detail).build()
    }.asCompletableFuture()

    private fun badRequest(msg: String): Response = Response.status(400).entity(mapOf("detail" to msg)).build()
    private fun notFound(msg: String): Response = Response.status(404).entity(mapOf("detail" to msg)).build()
    private fun serviceUnavailable(msg: String): Response = Response.status(503).entity(mapOf("detail" to msg)).build()
}
