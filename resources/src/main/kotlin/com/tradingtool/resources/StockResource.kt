package com.tradingtool.resources

import com.google.inject.Inject
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.kite.LiveMarketService
import com.tradingtool.core.kite.TickerSubscriptions
import com.tradingtool.core.model.stock.CreateStockInput
import com.tradingtool.core.model.stock.InstrumentSearchResult
import com.tradingtool.core.model.stock.StockQuoteSnapshot
import com.tradingtool.core.model.stock.UpdateStockPayload
import com.tradingtool.core.stock.service.StockDetailService
import com.tradingtool.core.stock.service.StockService
import com.tradingtool.core.trade.service.TradeService
import com.tradingtool.resources.common.badRequest
import com.tradingtool.resources.common.conflict
import com.tradingtool.resources.common.created
import com.tradingtool.resources.common.endpoint
import com.tradingtool.resources.common.internalError
import com.tradingtool.resources.common.notFound
import com.tradingtool.resources.common.ok
import com.tradingtool.resources.common.serviceUnavailable
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds

@Path("/api/stocks")
@Produces(MediaType.APPLICATION_JSON)
class StockResource @Inject constructor(
    private val stockService: StockService,
    private val tradeService: TradeService,
    private val resourceScope: ResourceScope,
    private val tickerSubscriptions: TickerSubscriptions,
    private val stockDetailService: StockDetailService,
    private val kiteClient: KiteConnectClient,
    private val liveMarketService: LiveMarketService,
    private val instrumentCache: InstrumentCache,
    private val watchlistConfigService: com.tradingtool.core.watchlist.WatchlistConfigService,
) {
    private val ioScope = resourceScope.ioScope
    private val instrumentMutex = Mutex()
    private val log = LoggerFactory.getLogger(javaClass)

    // ── Stock CRUD ────────────────────────────────────────────────────────────

    /** Health check — confirms stocks and trades tables are accessible. */
    @GET
    @Path("/health")
    fun health(): CompletableFuture<Response> = ioScope.endpoint {
        ok(stockService.checkTablesAccess())
    }

    /** List all unique tags (name + color) currently assigned to stocks. */
    @GET
    @Path("/tags")
    fun listTags(): CompletableFuture<Response> = ioScope.endpoint {
        ok(stockService.listAllTags())
    }

    /** List all pre-defined tags from watchlist_config.json. */
    @GET
    @Path("/config/tags")
    fun listConfigTags(): CompletableFuture<Response> = ioScope.endpoint {
        ok(watchlistConfigService.loadConfig().tagDefinitions)
    }

    /** List all stocks. Optional ?tag=Momentum filter. */
    @GET
    fun listStocks(@QueryParam("tag") tag: String?): CompletableFuture<Response> = ioScope.endpoint {
        val stocks = if (tag.isNullOrBlank()) stockService.listAll() else stockService.listByTag(tag.trim())
        ok(stocks)
    }

    /** Get a single stock by ID. */
    @GET
    @Path("/{id}")
    fun getById(@PathParam("id") id: String): CompletableFuture<Response> = ioScope.endpoint {
        val stockId = id.toLongOrNull() ?: return@endpoint badRequest("Path parameter 'id' must be a valid integer")
        val stock = stockService.getById(stockId) ?: return@endpoint notFound("Stock $stockId not found")
        ok(stock)
    }

    /** Get a single stock by NSE symbol. */
    @GET
    @Path("/by-symbol/{symbol}")
    fun getBySymbol(@PathParam("symbol") symbol: String): CompletableFuture<Response> = ioScope.endpoint {
        val sym = symbol.trim().uppercase()
        if (sym.isEmpty()) return@endpoint badRequest("Path parameter 'symbol' is required")
        val stock = stockService.getBySymbol(sym, "NSE") ?: return@endpoint notFound("Stock '$sym' not found")
        ok(stock)
    }

    /** Get consolidated trade for a stock (at most one — unique constraint on stock_id). */
    @GET
    @Path("/{id}/trades")
    fun getTradesForStock(@PathParam("id") id: String): CompletableFuture<Response> = ioScope.endpoint {
        val stockId = id.toLongOrNull() ?: return@endpoint badRequest("Path parameter 'id' must be a valid integer")
        val trade = tradeService.getTradeByStockId(stockId)
        ok(if (trade != null) listOf(trade) else emptyList<Any>())
    }

    /** Create a new stock. instrument_token, symbol, company_name, exchange are required. */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    fun createStock(body: CreateStockInput?): CompletableFuture<Response> = ioScope.endpoint {
        val input = body ?: return@endpoint badRequest("Request body is required")
        if (input.symbol.isBlank()) return@endpoint badRequest("Field 'symbol' is required")
        if (input.companyName.isBlank()) return@endpoint badRequest("Field 'company_name' is required")
        if (input.exchange.isBlank()) return@endpoint badRequest("Field 'exchange' is required")
        val priority = input.priority
        if (priority != null && priority !in 1..5) return@endpoint badRequest("Field 'priority' must be between 1 and 5")

        // 409 on duplicate — needs its own runCatching since error type drives the status code.
        runCatching {
            val stock = stockService.create(input)
            if (stock.instrumentToken > 0) tickerSubscriptions.addInstrument(stock.instrumentToken)
            created(stock)
        }.getOrElse { e ->
            if (e.message?.contains("duplicate") == true || e.message?.contains("unique") == true) {
                conflict("Stock '${input.symbol}' already exists")
            } else {
                internalError(e.message ?: "Unexpected error")
            }
        }
    }

    /** Update notes, priority, and/or tags on a stock. Only provided fields are changed. */
    @PATCH
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    fun updateStock(
        @PathParam("id") id: String,
        body: UpdateStockPayload?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val stockId = id.toLongOrNull() ?: return@endpoint badRequest("Path parameter 'id' must be a valid integer")
        val payload = body ?: return@endpoint badRequest("Request body is required")
        if (payload.notes == null && payload.priority == null && payload.tags == null)
            return@endpoint badRequest("At least one field (notes, priority, tags) must be provided")
        val priority = payload.priority
        if (priority != null && priority !in 1..5) return@endpoint badRequest("Field 'priority' must be between 1 and 5")

        val updated = stockService.update(stockId, payload) ?: return@endpoint notFound("Stock $stockId not found")
        ok(updated)
    }

    /** Delete a stock. Associated trades will have stock_id set to null. */
    @DELETE
    @Path("/{id}")
    fun deleteStock(@PathParam("id") id: String): CompletableFuture<Response> = ioScope.endpoint {
        val stockId = id.toLongOrNull() ?: return@endpoint badRequest("Path parameter 'id' must be a valid integer")
        val stock = stockService.getById(stockId) ?: return@endpoint notFound("Stock $stockId not found")
        if (!stockService.delete(stockId)) return@endpoint notFound("Stock $stockId not found")
        if (stock.instrumentToken > 0) tickerSubscriptions.removeInstrument(stock.instrumentToken)
        ok(mapOf("deleted" to true))
    }

    // ── Stock Detail ──────────────────────────────────────────────────────────

    /**
     * Returns N-day OHLCV detail: daily % change, RSI14, volume vs 20-day average.
     * Fetches live from Kite — not cached, do not call in tight loops.
     */
    @GET
    @Path("/by-symbol/{symbol}/detail")
    fun getDetail(
        @PathParam("symbol") symbol: String,
        @QueryParam("days") days: Int?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val sym = symbol.trim().uppercase()
        if (sym.isEmpty()) return@endpoint badRequest("Symbol is required")
        if (!kiteClient.isAuthenticated) return@endpoint serviceUnavailable("Kite is not authenticated")
        val dayCount = days ?: 7
        if (dayCount !in 1..200) return@endpoint badRequest("Query parameter 'days' must be between 1 and 200")
        val detail = stockDetailService.getDetail(sym, kiteClient, dayCount)
            ?: return@endpoint notFound("Stock '$sym' not found in watchlist")
        ok(detail)
    }

    /**
     * GET /api/stocks/quotes?symbols=INFY,TCS,RELIANCE
     * Returns a live quote snapshot for each requested NSE symbol.
     */
    @GET
    @Path("/quotes")
    fun getQuotes(@QueryParam("symbols") symbols: String?): CompletableFuture<Response> = ioScope.endpoint {
        val requestedSymbols = symbols
            ?.split(",")
            ?.map { it.trim().uppercase() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?: emptyList()
        if (requestedSymbols.isEmpty()) return@endpoint badRequest("Query parameter 'symbols' is required")

        val stocks = requestedSymbols.associateWith { sym -> stockService.getBySymbol(sym, "NSE") }
        val instruments = stocks.values
            .filterNotNull()
            .map { "${it.exchange}:${it.symbol}" }

        val quotes = liveMarketService.getQuotes(instruments)
        val updatedAt = java.time.Instant.now().toString()

        val snapshots = requestedSymbols.map { symbol ->
            val stock = stocks[symbol]
            val quote = stock?.let { quotes["${it.exchange}:${it.symbol}"] }
            val ohlc = quote?.ohlc

            StockQuoteSnapshot(
                symbol = symbol,
                ltp = quote?.lastPrice,
                dayOpen = ohlc?.open,
                dayHigh = ohlc?.high,
                dayLow = ohlc?.low,
                volume = quote?.volumeTradedToday?.toLong(),
                updatedAt = updatedAt,
            )
        }
        ok(snapshots)
    }

    // ── Instruments ───────────────────────────────────────────────────────────

    /**
     * GET /api/stocks/instruments — all cached NSE EQ instruments (~8k stocks).
     * Returns the full list for client-side search/filtering.
     * Cache is auto-populated if empty (login required first).
     */
    @GET
    @Path("/instruments")
    fun getAllInstruments(): CompletableFuture<Response> = ioScope.endpoint {
        if (instrumentCache.isEmpty()) {
            instrumentMutex.withLock {
                // Double-check after acquiring lock
                if (instrumentCache.isEmpty()) {
                    try {
                        val instruments = withTimeout(30.seconds) {
                            kiteClient.client().getInstruments("NSE")
                        }
                        instrumentCache.refresh(instruments)
                        log.info("Auto-refreshed {} NSE instruments", instrumentCache.size())
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        log.error("Timeout fetching instruments from Kite", e)
                        return@endpoint serviceUnavailable("Kite fetch timed out (30s)")
                    } catch (e: IOException) {
                        log.error("Network error fetching instruments from Kite", e)
                        return@endpoint serviceUnavailable("Network error: ${e.message}")
                    } catch (e: RuntimeException) {
                        log.error("Kite API error fetching instruments", e)
                        return@endpoint serviceUnavailable("Kite API error: ${e.message}")
                    }
                }
            }
        }

        if (instrumentCache.isEmpty()) return@endpoint serviceUnavailable("Instrument cache is empty and could not be populated")

        val results = instrumentCache.all()
            .map { inst ->
                InstrumentSearchResult(
                    instrumentToken = inst.instrument_token,
                    tradingSymbol = inst.tradingsymbol,
                    companyName = inst.name ?: "",
                    exchange = inst.exchange,
                    instrumentType = inst.instrument_type,
                )
            }
            .toList()

        ok(results)
    }
}
