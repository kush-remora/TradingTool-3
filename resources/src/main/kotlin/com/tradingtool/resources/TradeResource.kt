package com.tradingtool.resources

import com.google.inject.Inject
import com.tradingtool.core.model.trade.CloseTradeInput
import com.tradingtool.core.model.trade.CreateTradeInput
import com.tradingtool.core.trade.service.TradeReadinessService
import com.tradingtool.core.trade.service.TradeService
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.resources.common.badRequest
import com.tradingtool.resources.common.created
import com.tradingtool.resources.common.endpoint
import com.tradingtool.resources.common.notFound
import com.tradingtool.resources.common.ok
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
import java.util.concurrent.CompletableFuture
import java.util.Locale

@Path("/api/trades")
@Produces(MediaType.APPLICATION_JSON)
class TradeResource @Inject constructor(
    private val tradeService: TradeService,
    private val tradeReadinessService: TradeReadinessService,
    private val resourceScope: ResourceScope,
) {
    private val ioScope = resourceScope.ioScope

    /** GET /api/trades — all trades with GTT targets, newest first. */
    @GET
    fun getAllTrades(): CompletableFuture<Response> = ioScope.endpoint {
        ok(tradeService.getTradesWithTargets())
    }

    /** GET /api/trades/readiness?symbols=INFY,TCS — compact trade-readiness snapshot (max 3 symbols). */
    @GET
    @Path("/readiness")
    fun getReadiness(@QueryParam("symbols") symbols: String?): CompletableFuture<Response> = ioScope.endpoint {
        val requestedSymbols = symbols
            ?.split(",")
            ?.map { it.trim().uppercase(Locale.ROOT) }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.take(3)
            ?: emptyList()
        if (requestedSymbols.isEmpty()) return@endpoint badRequest("Query parameter 'symbols' is required")

        ok(tradeReadinessService.getReadiness(requestedSymbols))
    }

    /** GET /api/trades/{tradeId} — single trade with GTT targets. */
    @GET
    @Path("/{tradeId}")
    fun getTradeById(@PathParam("tradeId") tradeId: Long): CompletableFuture<Response> = ioScope.endpoint {
        val trade = tradeService.getTradeWithTargets(tradeId)
            ?: return@endpoint notFound("Trade not found")
        ok(trade)
    }

    /**
     * POST /api/trades — create or consolidate trade for a stock.
     *
     * If stock_id already has a trade, merges via weighted average:
     *   new_avg = (qty1 * price1 + qty2 * price2) / (qty1 + qty2)
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    fun createTrade(body: CreateTradeInput?): CompletableFuture<Response> = ioScope.endpoint {
        val input = body ?: return@endpoint badRequest("Request body is required")
        if (input.quantity <= 0) return@endpoint badRequest("Quantity must be positive")
        if (input.avgBuyPrice.toDoubleOrNull() == null || input.avgBuyPrice.toDouble() <= 0)
            return@endpoint badRequest("Avg buy price must be a positive number")
        if (input.stopLossPercent.toDoubleOrNull() == null || input.stopLossPercent.toDouble() < 0)
            return@endpoint badRequest("Stop loss percent must be a non-negative number")
        created(tradeService.createOrConsolidateTrade(input))
    }

    /** POST /api/trades/{tradeId}/close — record exit price and close the position. */
    @POST
    @Path("/{tradeId}/close")
    @Consumes(MediaType.APPLICATION_JSON)
    fun closeTrade(
        @PathParam("tradeId") tradeId: Long,
        body: CloseTradeInput?
    ): CompletableFuture<Response> = ioScope.endpoint {
        val input = body ?: return@endpoint badRequest("Request body is required")
        val result = tradeService.closeTrade(tradeId, input)
            ?: return@endpoint notFound("Trade not found")
        ok(result)
    }

    /** DELETE /api/trades/{tradeId} — delete trade by ID. */
    @DELETE
    @Path("/{tradeId}")
    fun deleteTrade(@PathParam("tradeId") tradeId: Long): CompletableFuture<Response> = ioScope.endpoint {
        if (!tradeService.deleteTrade(tradeId)) return@endpoint notFound("Trade not found")
        Response.noContent().build()
    }
}
