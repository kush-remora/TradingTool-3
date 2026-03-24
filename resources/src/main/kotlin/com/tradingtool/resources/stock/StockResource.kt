package com.tradingtool.resources.stock

import com.google.inject.Inject
import com.tradingtool.core.kite.TickerSubscriptions
import com.tradingtool.core.model.stock.CreateStockInput
import com.tradingtool.core.model.stock.UpdateStockPayload
import com.tradingtool.core.stock.service.StockService
import com.tradingtool.core.trade.service.TradeService
import com.tradingtool.core.di.ResourceScope
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import java.util.concurrent.CompletableFuture

@Path("/api/stocks")
@Produces(MediaType.APPLICATION_JSON)
class StockResource @Inject constructor(
    private val stockService: StockService,
    private val tradeService: TradeService,
    private val resourceScope: ResourceScope,
    private val tickerSubscriptions: TickerSubscriptions,
) {
    private val ioScope = resourceScope.ioScope

    /** Health check — confirms the stocks and trades tables are accessible. */
    @GET
    @Path("/health")
    fun health(): CompletableFuture<Response> = ioScope.async {
        ok(stockService.checkTablesAccess())
    }.asCompletableFuture()

    /** List all stocks. Optional ?tag=Momentum filter. */
    @GET
    fun listStocks(@QueryParam("tag") tag: String?): CompletableFuture<Response> = ioScope.async {
        runCatching {
            val stocks = if (tag.isNullOrBlank()) {
                stockService.listAll()
            } else {
                stockService.listByTag(tag.trim())
            }
            ok(stocks)
        }.getOrElse { internalError(it.message) }
    }.asCompletableFuture()

    /** Get a single stock by ID. */
    @GET
    @Path("/{id}")
    fun getById(@PathParam("id") id: String): CompletableFuture<Response> = ioScope.async {
        val stockId = id.toLongOrNull() ?: return@async badRequest("Path parameter 'id' must be a valid integer")
        val stock = stockService.getById(stockId) ?: return@async notFound("Stock $stockId not found")
        ok(stock)
    }.asCompletableFuture()

    /** Get a single stock by NSE symbol (exchange defaults to NSE). */
    @GET
    @Path("/by-symbol/{symbol}")
    fun getBySymbol(@PathParam("symbol") symbol: String): CompletableFuture<Response> = ioScope.async {
        val sym = symbol.trim().uppercase()
        if (sym.isEmpty()) return@async badRequest("Path parameter 'symbol' is required")
        val stock = stockService.getBySymbol(sym, "NSE") ?: return@async notFound("Stock '$sym' not found")
        ok(stock)
    }.asCompletableFuture()

    /** List all unique tags (name + color) across all stocks. Used to populate tag dropdown. */
    @GET
    @Path("/tags")
    fun listTags(): CompletableFuture<Response> = ioScope.async {
        ok(stockService.listAllTags())
    }.asCompletableFuture()

    /** Get all trades for a stock. */
    @GET
    @Path("/{id}/trades")
    fun getTradesForStock(@PathParam("id") id: String): CompletableFuture<Response> = ioScope.async {
        val stockId = id.toLongOrNull() ?: return@async badRequest("Path parameter 'id' must be a valid integer")
        val trade = tradeService.getTradeByStockId(stockId)
        // trades has UNIQUE(stock_id) — at most one consolidated position per stock
        ok(if (trade != null) listOf(trade) else emptyList<Any>())
    }.asCompletableFuture()

    /** Create a new stock. instrument_token, symbol, company_name, exchange are required. */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    fun createStock(body: CreateStockInput?): CompletableFuture<Response> = ioScope.async {
        val input = body ?: return@async badRequest("Request body is required")
        if (input.symbol.isBlank()) return@async badRequest("Field 'symbol' is required")
        if (input.companyName.isBlank()) return@async badRequest("Field 'company_name' is required")
        if (input.exchange.isBlank()) return@async badRequest("Field 'exchange' is required")
        val priority = input.priority
        if (priority != null && priority !in 1..5) return@async badRequest("Field 'priority' must be between 1 and 5")

        runCatching {
            val stock = stockService.create(input)
            if (stock.instrumentToken > 0) tickerSubscriptions.addInstrument(stock.instrumentToken)
            created(stock)
        }.getOrElse { e ->
            if (e.message?.contains("duplicate") == true || e.message?.contains("unique") == true) {
                Response.status(409).entity(error("Stock '${input.symbol}' already exists")).build()
            } else {
                internalError(e.message)
            }
        }
    }.asCompletableFuture()

    /** Update notes, priority, and/or tags on a stock. Only provided fields are changed. */
    @PATCH
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    fun updateStock(
        @PathParam("id") id: String,
        body: UpdateStockPayload?,
    ): CompletableFuture<Response> = ioScope.async {
        val stockId = id.toLongOrNull() ?: return@async badRequest("Path parameter 'id' must be a valid integer")
        val payload = body ?: return@async badRequest("Request body is required")
        if (payload.notes == null && payload.priority == null && payload.tags == null) {
            return@async badRequest("At least one field (notes, priority, tags) must be provided")
        }
        val priority = payload.priority
        if (priority != null && priority !in 1..5) return@async badRequest("Field 'priority' must be between 1 and 5")

        val updated = stockService.update(stockId, payload) ?: return@async notFound("Stock $stockId not found")
        ok(updated)
    }.asCompletableFuture()

    /** Delete a stock. Associated trades will have stock_id set to null. */
    @DELETE
    @Path("/{id}")
    fun deleteStock(@PathParam("id") id: String): CompletableFuture<Response> = ioScope.async {
        val stockId = id.toLongOrNull() ?: return@async badRequest("Path parameter 'id' must be a valid integer")
        val stock = stockService.getById(stockId) ?: return@async notFound("Stock $stockId not found")
        if (!stockService.delete(stockId)) return@async notFound("Stock $stockId not found")
        if (stock.instrumentToken > 0) tickerSubscriptions.removeInstrument(stock.instrumentToken)
        ok(mapOf("deleted" to true))
    }.asCompletableFuture()

    // ==================== Helpers ====================

    private fun ok(entity: Any): Response = Response.ok(entity).build()
    private fun created(entity: Any): Response = Response.status(201).entity(entity).build()
    private fun badRequest(detail: String): Response = Response.status(400).entity(error(detail)).build()
    private fun notFound(detail: String): Response = Response.status(404).entity(error(detail)).build()
    private fun internalError(detail: String?): Response = Response.status(500).entity(error(detail ?: "Unexpected error")).build()
    private fun error(detail: String) = mapOf("detail" to detail)
}
