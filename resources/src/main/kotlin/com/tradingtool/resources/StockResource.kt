package com.tradingtool.resources

import com.google.inject.Inject
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.model.stock.DayDetail
import com.tradingtool.core.model.stock.DeliveryDayDetail
import com.tradingtool.core.model.stock.InstrumentSearchResult
import com.tradingtool.core.model.stock.PivotLevels
import com.tradingtool.core.model.stock.StockDetailResponse
import com.tradingtool.core.model.stock.StockQuoteSnapshot
import com.tradingtool.core.strategy.momentum.PARTICIPATION_DELIVERY_HISTORY_SESSIONS
import com.tradingtool.core.strategy.momentum.MOMENTUM_RSI_WARMUP_CALENDAR_DAYS
import com.tradingtool.core.strategy.momentum.calculateMomentumEvidence
import com.tradingtool.resources.common.badRequest
import com.tradingtool.resources.common.endpoint
import com.tradingtool.resources.common.notFound
import com.tradingtool.resources.common.ok
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.Locale
import java.util.concurrent.CompletableFuture
import kotlin.math.round

import com.tradingtool.core.database.StockDeliveryJdbiHandler
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.TickStore
import com.tradingtool.core.kite.TickerSubscriptions
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.ZoneId

@Path("/api/stocks")
@Produces(MediaType.APPLICATION_JSON)
class StockResource @Inject constructor(
    private val kiteClient: KiteConnectClient,
    private val resourceScope: ResourceScope,
    private val candleCacheService: CandleCacheService,
    private val deliveryDb: StockDeliveryJdbiHandler,
    private val instrumentCache: InstrumentCache,
    private val tickStore: TickStore,
    private val tickerSubscriptions: TickerSubscriptions,
    private val noteService: com.tradingtool.core.note.NoteService,
) {
    private companion object {
        const val DELIVERY_HISTORY_DAYS = 75
        const val MOMENTUM_DELIVERY_HISTORY_SESSIONS = PARTICIPATION_DELIVERY_HISTORY_SESSIONS
        const val DETAIL_HISTORY_CALENDAR_DAYS = MOMENTUM_RSI_WARMUP_CALENDAR_DAYS
    }

    private val ioScope = resourceScope.ioScope
    private val log = LoggerFactory.getLogger(javaClass)
    private val indiaTimeZone = ZoneId.of("Asia/Kolkata")

    @GET
    @Path("/notes/{instrumentToken}")
    fun getNotes(@PathParam("instrumentToken") instrumentToken: Long): CompletableFuture<Response> = ioScope.endpoint { ok(noteService.list(instrumentToken)) }

    @POST
    @Path("/notes")
    @Consumes(MediaType.APPLICATION_JSON)
    fun createNote(request: com.tradingtool.core.note.CreateNoteRequest?): CompletableFuture<Response> = ioScope.endpoint {
        val body = request ?: return@endpoint badRequest("Request body is required.")
        if (body.instrumentToken <= 0 || body.notes.isBlank()) return@endpoint badRequest("Instrument token and note are required.")
        ok(noteService.create(body.copy(notes = body.notes.trim())))
    }

    @DELETE
    @Path("/notes/{id}")
    fun deleteNote(@PathParam("id") id: Long): CompletableFuture<Response> = ioScope.endpoint {
        if (!noteService.delete(id)) return@endpoint notFound("Note not found.")
        ok(mapOf("success" to true))
    }

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
            log.warn("Kite connection not authenticated, using cached candles for fallback quotes.")
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
            val previousDayVolume = loadPreviousDayVolume(symbol, token)

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
                            previousDayVolume = loadPreviousDayVolume(symbol, token),
                            updatedAt = quote.timestamp?.toString() ?: Instant.now().toString(),
                        )
                    )
                }
            } catch (e: Exception) {
                log.error("Failed to fetch quotes from Kite for ${kiteSymbols.joinToString()}: ${e.message}. Using cached candles.", e)
                snapshots.addAll(fetchFallbackQuotes(kiteSymbolsToFetch))
            }
        }

        ok(snapshots)
    }

    @GET
    @Path("/instruments")
    fun getInstruments(): CompletableFuture<Response> = ioScope.endpoint {
        if (instrumentCache.isEmpty()) {
            if (!kiteClient.isAuthenticated) {
                return@endpoint badRequest("Kite login is required before stocks can be loaded.")
            }
            instrumentCache.refresh(kiteClient.client().getInstruments("NSE"))
        }

        val results = instrumentCache.all().map { inst ->
            InstrumentSearchResult(
                instrumentToken = inst.instrument_token,
                tradingSymbol = inst.tradingsymbol,
                companyName = inst.name ?: inst.tradingsymbol,
                exchange = inst.exchange,
                instrumentType = inst.instrument_type
            )
        }
        ok(results)
    }

    @GET
    @Path("/by-symbol/{symbol}/detail")
    fun getStockDetail(
        @PathParam("symbol") symbol: String,
        @QueryParam("days") days: Int?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val normalizedSymbol = symbol.trim().uppercase(Locale.ROOT)
        if (normalizedSymbol.isBlank()) {
            return@endpoint badRequest("symbol is required.")
        }

        val requestedDays = (days ?: 7).coerceIn(1, 200)
        val token = instrumentCache.token("NSE", normalizedSymbol)
            ?: return@endpoint notFound("Unknown NSE symbol: $normalizedSymbol")

        val today = LocalDate.now(indiaTimeZone)
        val recentCandles = candleCacheService.getDailyCandles(
            token = token,
            symbol = normalizedSymbol,
            from = today.minusDays(DETAIL_HISTORY_CALENDAR_DAYS),
            to = today,
        )
            .sortedBy { candle -> candle.candleDate }
            .toMutableList()
        loadLatestLiveCandle(normalizedSymbol, token)
            ?.takeIf { liveCandle -> recentCandles.lastOrNull()?.candleDate?.isBefore(liveCandle.candleDate) ?: true }
            ?.let(recentCandles::add)

        if (recentCandles.isEmpty()) {
            return@endpoint notFound("No daily candle data found for: $normalizedSymbol")
        }

        val daysToDisplay = recentCandles.takeLast(requestedDays)
        val displayStartIndex = recentCandles.size - daysToDisplay.size
        val detailDays = daysToDisplay.mapIndexed { index, candle ->
            val sourceIndex = displayStartIndex + index
            val previousCandle = recentCandles.getOrNull(sourceIndex - 1)
            val prior20 = recentCandles.subList(maxOf(0, sourceIndex - 20), sourceIndex)
            val avgPrior20Volume = prior20
                .takeIf { it.isNotEmpty() }
                ?.map { prior -> prior.volume.toDouble() }
                ?.average()
                ?.takeIf { average -> average.isFinite() && average > 0.0 }

            DayDetail(
                date = candle.candleDate.toString(),
                open = candle.open,
                high = candle.high,
                low = candle.low,
                close = candle.close,
                volume = candle.volume,
                dailyChangePct = previousCandle
                    ?.takeIf { previous -> previous.close > 0.0 }
                    ?.let { previous -> roundTo2(((candle.close - previous.close) / previous.close) * 100) },
                rsi14 = null,
                volRatio = avgPrior20Volume?.let { average -> roundTo2(candle.volume / average) },
            )
        }

        val latestDayIndex = recentCandles.lastIndex
        val latestPrior20 = recentCandles
            .subList(maxOf(0, latestDayIndex - 20), latestDayIndex)
            .map { candle -> candle.volume.toDouble() }
            .takeIf { volumes -> volumes.isNotEmpty() }
            ?.average()
            ?.takeIf { average -> average.isFinite() && average > 0.0 }
            ?.let(::roundTo2)
        val pivotLevels = buildPivotLevels(recentCandles.last())
        val last252 = recentCandles.takeLast(252)
        val fundamentals = com.tradingtool.core.model.stock.StockFundamentals(
            currentPrice = recentCandles.last().close,
            fiftyTwoWeekLow = last252.minOfOrNull { it.low },
            fiftyTwoWeekHigh = last252.maxOfOrNull { it.high },
            sma200 = recentCandles.takeLast(200).takeIf { it.size == 200 }?.map { it.close }?.average()?.let(::roundTo2),
        )
        val deliveryRecords = deliveryDb.read { dao ->
            dao.findRecentByInstrumentToken(
                instrumentToken = token,
                beforeDate = today.plusDays(1),
                limit = MOMENTUM_DELIVERY_HISTORY_SESSIONS,
            )
        }
        val deliveryByDate = deliveryRecords.associate { delivery -> delivery.tradingDate to delivery.delivPer }
        val deliveryDays = deliveryRecords.take(DELIVERY_HISTORY_DAYS).map { delivery ->
            DeliveryDayDetail(
                date = delivery.tradingDate.toString(),
                deliveryPercentage = delivery.delivPer,
                deliveredQuantity = delivery.delivQty,
                tradedQuantity = delivery.ttlTrdQnty,
            )
        }

        ok(
            StockDetailResponse(
                symbol = normalizedSymbol,
                exchange = "NSE",
                avgVolume20d = latestPrior20,
                pivotLevels = pivotLevels,
                fundamentals = fundamentals,
                days = detailDays,
                deliveryDays = deliveryDays,
                momentumEvidence = calculateMomentumEvidence(
                    candles = recentCandles,
                    asOfDate = today,
                    deliveryPercentageByDate = deliveryByDate,
                ),
            )
        )
    }

    private fun loadLatestLiveCandle(symbol: String, token: Long): DailyCandle? {
        val tick = tickStore.get(token)
        if (tick != null) {
            return DailyCandle(
                instrumentToken = token,
                symbol = symbol,
                candleDate = Instant.ofEpochMilli(tick.updatedAt).atZone(indiaTimeZone).toLocalDate(),
                open = tick.open,
                high = tick.high,
                low = tick.low,
                close = tick.ltp,
                volume = tick.volume,
            )
        }
        if (!kiteClient.isAuthenticated) {
            return null
        }

        return runCatching {
            val quote = kiteClient.client().getQuote(arrayOf("NSE:$symbol"))["NSE:$symbol"] ?: return null
            val ohlc = quote.ohlc ?: return null
            DailyCandle(
                instrumentToken = token,
                symbol = symbol,
                candleDate = quote.timestamp?.toInstant()?.atZone(indiaTimeZone)?.toLocalDate()
                    ?: LocalDate.now(indiaTimeZone),
                open = ohlc.open,
                high = ohlc.high,
                low = ohlc.low,
                close = quote.lastPrice,
                volume = quote.volumeTradedToday?.toLong() ?: 0L,
            )
        }.onFailure { error ->
            log.warn("Could not load the live candle for {}: {}", symbol, error.message)
        }.getOrNull()
    }

    private suspend fun fetchFallbackQuotes(symbols: List<String>): List<StockQuoteSnapshot> {
        return symbols.mapNotNull { symbol ->
            val token = instrumentCache.token("NSE", symbol) ?: return@mapNotNull null
            val today = LocalDate.now(indiaTimeZone)
            val recentCandles = try {
                candleCacheService.getDailyCandles(token, symbol, today.minusDays(10), today)
                    .sortedByDescending(DailyCandle::candleDate)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                log.warn("Could not load fallback candles for {}: {}", symbol, error.message)
                return@mapNotNull null
            }
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

    private suspend fun loadPreviousDayVolume(symbol: String, token: Long): Long? {
        val today = LocalDate.now(indiaTimeZone)
        return try {
            candleCacheService.getDailyCandles(token, symbol, today.minusDays(10), today)
                .sortedByDescending(DailyCandle::candleDate)
                .getOrNull(1)
                ?.volume
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.warn("Could not load previous-day volume for {}: {}", symbol, error.message)
            null
        }
    }

    private fun roundTo2(value: Double): Double {
        return round(value * 100.0) / 100.0
    }

    private fun buildPivotLevels(candle: com.tradingtool.core.candle.DailyCandle): PivotLevels {
        val pivot = (candle.high + candle.low + candle.close) / 3.0
        val r1 = (2 * pivot) - candle.low
        val s1 = (2 * pivot) - candle.high
        val range = candle.high - candle.low
        val r2 = pivot + range
        val s2 = pivot - range
        val r3 = candle.high + 2 * (pivot - candle.low)
        val s3 = candle.low - 2 * (candle.high - pivot)

        return PivotLevels(
            pivot = roundTo2(pivot),
            r1 = roundTo2(r1),
            r2 = roundTo2(r2),
            r3 = roundTo2(r3),
            s1 = roundTo2(s1),
            s2 = roundTo2(s2),
            s3 = roundTo2(s3),
        )
    }
}
