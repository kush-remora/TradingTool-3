package com.tradingtool.resources

import com.google.inject.Inject
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.model.stock.InstrumentSearchResult
import com.tradingtool.core.model.stock.StockQuoteSnapshot
import com.tradingtool.resources.common.badRequest
import com.tradingtool.resources.common.endpoint
import com.tradingtool.resources.common.ok
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.Locale
import java.util.concurrent.CompletableFuture

import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.TickStore
import com.tradingtool.core.kite.TickerSubscriptions

@Path("/api/stocks")
@Produces(MediaType.APPLICATION_JSON)
class StockResource @Inject constructor(
    private val kiteClient: KiteConnectClient,
    private val resourceScope: ResourceScope,
    private val candleDb: CandleJdbiHandler,
    private val instrumentCache: InstrumentCache,
    private val tickStore: TickStore,
    private val tickerSubscriptions: TickerSubscriptions,
) {
    private val ioScope = resourceScope.ioScope
    private val log = LoggerFactory.getLogger(javaClass)

    @GET
    @Path("/quotes")
    fun getQuotes(@QueryParam("symbols") symbols: String?): CompletableFuture<Response> = ioScope.endpoint {
        val requestedSymbols = symbols
            ?.split(",")
            ?.map { it.trim().uppercase(Locale.ROOT) }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?: emptyList()
            
        if (requestedSymbols.isEmpty()) {
            return@endpoint ok(emptyList<StockQuoteSnapshot>())
        }

        if (!kiteClient.isAuthenticated) {
            log.warn("Kite connection not authenticated, falling back to local DB for LTP.")
            return@endpoint ok(fetchFallbackQuotes(requestedSymbols))
        }

        val kiteSymbolsToFetch = mutableListOf<String>()
        val snapshots = mutableListOf<StockQuoteSnapshot>()

        requestedSymbols.forEach { symbol ->
            val token = instrumentCache.token("NSE", symbol)
            if (token == null) {
                log.warn("Unknown symbol requested: $symbol")
                return@forEach
            }
            val previousDayVolume = loadPreviousDayVolume(token)

            val tick = tickStore.get(token)
            if (tick != null) {
                snapshots.add(
                    StockQuoteSnapshot(
                        symbol = symbol,
                        ltp = tick.ltp,
                        averagePrice = tick.averagePrice,
                        changePercent = if (tick.close > 0) ((tick.ltp - tick.close) / tick.close) * 100 else null,
                        dayOpen = tick.open,
                        dayHigh = tick.high,
                        dayLow = tick.low,
                        volume = tick.volume,
                        previousDayVolume = previousDayVolume,
                        updatedAt = Instant.ofEpochMilli(tick.updatedAt).toString()
                    )
                )
            } else {
                // Not in cache, so we need to subscribe to the ticker for future updates
                tickerSubscriptions.addInstrument(token)
                kiteSymbolsToFetch.add(symbol)
            }
        }

        if (kiteSymbolsToFetch.isNotEmpty()) {
            val kiteSymbols = kiteSymbolsToFetch.map { "NSE:$it" }.toTypedArray()
            try {
                val quotes = kiteClient.client().getQuote(kiteSymbols)
                kiteSymbolsToFetch.forEach { symbol ->
                    val quote = quotes["NSE:$symbol"] ?: return@forEach
                    val token = instrumentCache.token("NSE", symbol) ?: return@forEach
                    snapshots.add(
                        StockQuoteSnapshot(
                            symbol = symbol,
                            ltp = quote.lastPrice,
                            averagePrice = quote.averagePrice.takeIf { averagePrice -> averagePrice > 0.0 },
                            changePercent = quote.ohlc?.close?.takeIf { close -> close > 0.0 }?.let { close ->
                                ((quote.lastPrice - close) / close) * 100
                            },
                            dayOpen = quote.ohlc?.open,
                            dayHigh = quote.ohlc?.high,
                            dayLow = quote.ohlc?.low,
                            volume = quote.volumeTradedToday?.toLong(),
                            previousDayVolume = loadPreviousDayVolume(token),
                            updatedAt = quote.timestamp?.toString() ?: Instant.now().toString(),
                        )
                    )
                }
            } catch (e: Exception) {
                log.error("Failed to fetch quotes from Kite for ${kiteSymbols.joinToString()}: ${e.message}. Falling back to DB.", e)
                snapshots.addAll(fetchFallbackQuotes(kiteSymbolsToFetch))
            }
        }

        ok(snapshots)
    }

    @GET
    @Path("/instruments")
    fun getInstruments(): Response {
        val results = instrumentCache.all().map { inst ->
            InstrumentSearchResult(
                instrumentToken = inst.instrument_token,
                tradingSymbol = inst.tradingsymbol,
                companyName = inst.name ?: inst.tradingsymbol,
                exchange = inst.exchange,
                instrumentType = inst.instrument_type
            )
        }
        return ok(results)
    }

    private suspend fun fetchFallbackQuotes(symbols: List<String>): List<StockQuoteSnapshot> {
        return symbols.mapNotNull { symbol ->
            val token = instrumentCache.token("NSE", symbol) ?: return@mapNotNull null
            val recentCandles = candleDb.read { it.getRecentDailyCandles(token, 2) }
            val lastCandle = recentCandles.firstOrNull() ?: return@mapNotNull null
            val previousCandle = recentCandles.getOrNull(1)
            val changePercent = previousCandle
                ?.takeIf { candle -> candle.close > 0.0 }
                ?.let { candle -> ((lastCandle.close - candle.close) / candle.close) * 100 }

            StockQuoteSnapshot(
                symbol = symbol,
                ltp = lastCandle.close,
                averagePrice = null,
                changePercent = changePercent,
                dayOpen = lastCandle.open,
                dayHigh = lastCandle.high,
                dayLow = lastCandle.low,
                volume = lastCandle.volume,
                previousDayVolume = previousCandle?.volume,
                updatedAt = lastCandle.candleDate.toString()
            )
        }
    }

    private suspend fun loadPreviousDayVolume(token: Long): Long? {
        val recentCandles = candleDb.read { it.getRecentDailyCandles(token, 2) }
        return recentCandles.getOrNull(1)?.volume
    }
}
