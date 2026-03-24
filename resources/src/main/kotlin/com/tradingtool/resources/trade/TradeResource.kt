package com.tradingtool.resources.trade

import com.google.inject.Inject
import com.tradingtool.core.model.trade.CreateTradeInput
import com.tradingtool.core.trade.service.TradeService
import com.tradingtool.core.di.ResourceScope
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import java.util.concurrent.CompletableFuture

@Path("/api/trades")
class TradeResource @Inject constructor(
    private val tradeService: TradeService,
    private val resourceScope: ResourceScope,
) {
    private val ioScope = resourceScope.ioScope

    /**
     * GET /api/trades — Fetch all trades with GTT targets, ordered by creation date (newest first).
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun getAllTrades(): CompletableFuture<Response> = ioScope.async {
        try {
            val trades = tradeService.getTradesWithTargets()
            Response.ok(trades).build()
        } catch (e: Exception) {
            Response.status(500)
                .entity(mapOf("detail" to "Error fetching trades: ${e.message}"))
                .build()
        }
    }.asCompletableFuture()

    /**
     * GET /api/trades/{tradeId} — Fetch single trade with GTT targets.
     */
    @GET
    @Path("/{tradeId}")
    @Produces(MediaType.APPLICATION_JSON)
    fun getTradeById(@PathParam("tradeId") tradeId: Long): CompletableFuture<Response> = ioScope.async {
        try {
            val trade = tradeService.getTradeWithTargets(tradeId)
            if (trade == null) {
                Response.status(404)
                    .entity(mapOf("detail" to "Trade not found"))
                    .build()
            } else {
                Response.ok(trade).build()
            }
        } catch (e: Exception) {
            Response.status(500)
                .entity(mapOf("detail" to "Error fetching trade: ${e.message}"))
                .build()
        }
    }.asCompletableFuture()

    /**
     * POST /api/trades — Create new trade or consolidate with existing trade for the same stock.
     *
     * Request body:
     * {
     *   "stock_id": 123,
     *   "nse_symbol": "INFY",
     *   "quantity": 10,
     *   "avg_buy_price": "3100.50",
     *   "today_low": "3050.00",        // optional
     *   "stop_loss_percent": "5.5",
     *   "notes": "...",                // optional
     *   "trade_date": "2026-03-24"     // default: today
     * }
     *
     * If stock_id already exists, consolidates via weighted average:
     * new_avg = (qty1 * price1 + qty2 * price2) / (qty1 + qty2)
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun createTrade(body: CreateTradeInput?): CompletableFuture<Response> = ioScope.async {
        val input = body
            ?: return@async Response.status(400)
                .entity(mapOf("detail" to "Request body is required"))
                .build()

        // Validate required fields
        if (input.quantity <= 0) {
            return@async Response.status(400)
                .entity(mapOf("detail" to "Quantity must be positive"))
                .build()
        }

        if (input.avgBuyPrice.toDoubleOrNull() == null || input.avgBuyPrice.toDouble() <= 0) {
            return@async Response.status(400)
                .entity(mapOf("detail" to "Avg buy price must be a positive number"))
                .build()
        }

        if (input.stopLossPercent.toDoubleOrNull() == null || input.stopLossPercent.toDouble() < 0) {
            return@async Response.status(400)
                .entity(mapOf("detail" to "Stop loss percent must be a non-negative number"))
                .build()
        }

        try {
            val created = tradeService.createOrConsolidateTrade(input)
            Response.status(201)
                .entity(created)
                .build()
        } catch (e: Exception) {
            Response.status(500)
                .entity(mapOf("detail" to "Error creating trade: ${e.message}"))
                .build()
        }
    }.asCompletableFuture()

    /**
     * DELETE /api/trades/{tradeId} — Delete trade by ID.
     */
    @DELETE
    @Path("/{tradeId}")
    @Produces(MediaType.APPLICATION_JSON)
    fun deleteTrade(@PathParam("tradeId") tradeId: Long): CompletableFuture<Response> = ioScope.async {
        try {
            val deleted = tradeService.deleteTrade(tradeId)
            if (deleted) {
                Response.noContent().build()
            } else {
                Response.status(404)
                    .entity(mapOf("detail" to "Trade not found"))
                    .build()
            }
        } catch (e: Exception) {
            Response.status(500)
                .entity(mapOf("detail" to "Error deleting trade: ${e.message}"))
                .build()
        }
    }.asCompletableFuture()
}
