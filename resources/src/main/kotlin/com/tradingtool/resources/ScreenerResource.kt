package com.tradingtool.resources

import com.google.inject.Inject
import com.tradingtool.core.stock.service.StockService
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.screener.CandleDataService
import com.tradingtool.core.screener.WeeklyPatternService
import com.tradingtool.resources.common.badRequest
import com.tradingtool.resources.common.endpoint
import com.tradingtool.resources.common.ok
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.concurrent.CompletableFuture
import java.time.Instant
import com.tradingtool.core.screener.WeeklyPatternListResponse

@Path("/api/screener")
@Produces(MediaType.APPLICATION_JSON)
class ScreenerResource @Inject constructor(
    private val candleDataService: CandleDataService,
    private val weeklyPatternService: WeeklyPatternService,
    private val stockService: StockService,
    private val kiteClient: KiteConnectClient,
    private val resourceScope: ResourceScope,
) {
    private val ioScope = resourceScope.ioScope

    /**
     * Fetches raw daily + 15-min candles from Kite and upserts them into the database.
     * Pass ?symbols=NETWEB,INFY or omit to sync all watchlist stocks.
     *
     * This is a synchronous call — expect 30–90 seconds for a full watchlist sync.
     */
    @POST
    @Path("/sync")
    fun sync(@QueryParam("symbols") symbolsParam: String?): CompletableFuture<Response> = ioScope.endpoint {
        val symbols = resolveSymbols(symbolsParam)
        if (symbols.isEmpty()) return@endpoint badRequest("No symbols found. Add stocks to your watchlist first.")

        val synced = candleDataService.sync(symbols, kiteClient)
        ok(mapOf("synced" to synced, "total" to symbols.size, "symbols" to symbols))
    }

    /**
     * Returns a weekly pattern scorecard for each symbol, computed from stored candle data.
     * Run POST /api/screener/sync first if no data is available.
     */
    @GET
    @Path("/weekly-pattern")
    fun weeklyPattern(@QueryParam("symbols") symbolsParam: String?): CompletableFuture<Response> = ioScope.endpoint {
        val symbols = resolveSymbols(symbolsParam)
        if (symbols.isEmpty()) return@endpoint badRequest("No symbols found. Add stocks to your watchlist first.")

        val results = weeklyPatternService.analyze(symbols)
        ok(WeeklyPatternListResponse(
            runAt = Instant.now().toString(),
            lookbackWeeks = weeklyPatternService.lookbackWeeks(),
            results = results
        ))
    }

    /**
     * Returns the detailed scorecard and heatmap for a single symbol.
     */
    @GET
    @Path("/weekly-pattern/{symbol}")
    fun weeklyPatternDetail(@PathParam("symbol") symbol: String): CompletableFuture<Response> = ioScope.endpoint {
        val detail = weeklyPatternService.analyzeDetail(symbol.uppercase())
        if (detail == null) {
            badRequest("No data found for symbol $symbol")
        } else {
            ok(detail)
        }
    }

    private suspend fun resolveSymbols(param: String?): List<String> {
        if (!param.isNullOrBlank()) {
            return param.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        return stockService.listByTag("weekly").map { it.symbol }
    }
}
