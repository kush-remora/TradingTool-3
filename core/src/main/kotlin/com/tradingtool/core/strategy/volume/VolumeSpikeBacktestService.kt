package com.tradingtool.core.strategy.volume

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.database.EarningsResultJdbiHandler
import com.tradingtool.core.database.RedisHandler
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.KiteConnectClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlin.math.floor

@Singleton
class VolumeSpikeBacktestService @Inject constructor(
    private val redis: RedisHandler,
    private val stockHandler: StockJdbiHandler,
    private val earningsHandler: EarningsResultJdbiHandler,
    private val instrumentCache: InstrumentCache,
    private val kiteClient: KiteConnectClient,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(VolumeSpikeBacktestService::class.java)
    private val ist: ZoneId = ZoneId.of("Asia/Kolkata")
    private val instrumentCacheMutex = Mutex()

    suspend fun runBacktest(request: VolumeSpikeBacktestRunConfig): VolumeSpikeBacktestResponse {
        val stocksBySymbol = stockHandler.read { dao ->
            dao.listAll()
        }.filter { stock ->
            stock.exchange.equals("NSE", ignoreCase = true)
        }.associateBy { stock ->
            stock.symbol.uppercase()
        }

        val earningsRows = loadEarningsRows(request)
        val earningsSymbolUniverse = earningsRows.map { row -> row.stockSymbol.uppercase() }.toSet()
        val manualSymbols = request.manualSymbols.map { symbol -> symbol.trim().uppercase() }.filter { symbol -> symbol.isNotEmpty() }.toSet()

        val candidateSymbols: List<String> = when (request.earningsFilterMode) {
            EarningsFilterMode.OFF -> (stocksBySymbol.keys + manualSymbols).sorted()
            EarningsFilterMode.CUSTOM_WINDOW -> (earningsSymbolUniverse + manualSymbols).sorted()
            EarningsFilterMode.MANUAL_SYMBOL -> manualSymbols.sorted()
        }

        val earningsBySymbol: Map<String, List<LocalDate>> = earningsRows
            .groupBy { row -> row.stockSymbol.uppercase() }
            .mapValues { (_, rows) -> rows.map { row -> row.resultDate }.distinct().sorted() }

        val allTrades = mutableListOf<VolumeSpikeBacktestTrade>()
        val symbolsWithNoToken = mutableListOf<String>()
        val symbolsWithNoIntradayData = mutableListOf<String>()
        val symbolsSkippedByEarningsFilter = mutableListOf<String>()
        val symbolsWithNoTrades = mutableListOf<String>()
        val kiteFetchFailures = mutableListOf<String>()

        var cacheHits = 0
        var cacheMisses = 0
        var symbolsWithResolvedToken = 0

        ensureInstrumentCacheLoaded()

        for (symbol in candidateSymbols) {
            val instrumentToken = resolveInstrumentToken(symbol)
            if (instrumentToken == null || instrumentToken <= 0L) {
                symbolsWithNoToken += symbol
                continue
            }

            val baselineFetchFrom = request.fromDate.minusDays(BASELINE_PREFETCH_DAYS)
            symbolsWithResolvedToken += 1
            val cacheResult = loadIntradayCandles(
                symbol = symbol,
                instrumentToken = instrumentToken,
                fromDate = baselineFetchFrom,
                toDate = request.toDate,
            )

            if (cacheResult.source == CandleSource.CACHE) {
                cacheHits += 1
            } else {
                cacheMisses += 1
            }

            if (cacheResult.fetchFailure) {
                kiteFetchFailures += symbol
            }

            if (cacheResult.bars.isEmpty()) {
                symbolsWithNoIntradayData += symbol
                continue
            }

            val symbolTrades = backtestSymbol(
                symbol = symbol,
                instrumentToken = instrumentToken,
                bars = cacheResult.bars,
                request = request,
                earningsBySymbol = earningsBySymbol,
            )

            if (request.earningsFilterMode == EarningsFilterMode.CUSTOM_WINDOW && symbolTrades.skippedByEarningsFilter) {
                symbolsSkippedByEarningsFilter += symbol
            }

            if (symbolTrades.trades.isEmpty()) {
                symbolsWithNoTrades += symbol
            } else {
                allTrades += symbolTrades.trades
            }
        }

        val orderedTrades = allTrades.sortedBy { trade -> trade.entryTime }
        val summary = buildSummary(
            trades = orderedTrades,
            symbolsConsidered = candidateSymbols.size,
        )

        return VolumeSpikeBacktestResponse(
            config = VolumeSpikeBacktestConfigSnapshot(
                fromDate = request.fromDate.toString(),
                toDate = request.toDate.toString(),
                delayMinutes = request.delayMinutes,
                earningsFilterMode = request.earningsFilterMode,
                earningsWindowStartOffsetDays = request.earningsWindowStartOffsetDays,
                earningsWindowEndOffsetDays = request.earningsWindowEndOffsetDays,
                rvolThreshold = request.rvolThreshold,
                minDayMoveFromOpenPct = request.minDayMoveFromOpenPct,
                targetPct = request.targetPct,
                stopPct = request.stopPct,
                minThirtyMinReturnPct = request.minThirtyMinReturnPct,
                latestEntryTime = request.latestEntryTime.toString(),
                buyerDominancePct = request.buyerDominancePct,
                positionSizeInr = request.positionSizeInr,
                feePerTradeInr = request.feePerTradeInr,
            ),
            summary = summary,
            diagnostics = VolumeSpikeBacktestDiagnostics(
                symbolsFromEarningsUniverse = earningsSymbolUniverse.size,
                symbolsFromManualInput = manualSymbols.size,
                symbolsWithResolvedToken = symbolsWithResolvedToken,
                symbolsWithNoToken = symbolsWithNoToken.sorted(),
                symbolsWithNoIntradayData = symbolsWithNoIntradayData.sorted(),
                symbolsSkippedByEarningsFilter = symbolsSkippedByEarningsFilter.sorted(),
                symbolsWithNoTrades = symbolsWithNoTrades.sorted(),
                cacheHits = cacheHits,
                cacheMisses = cacheMisses,
                kiteFetchFailures = kiteFetchFailures.sorted(),
            ),
            trades = orderedTrades,
        )
    }

    private fun buildSummary(
        trades: List<VolumeSpikeBacktestTrade>,
        symbolsConsidered: Int,
    ): VolumeSpikeBacktestSummary {
        val winningTrades = trades.count { trade -> trade.netPnlInr > 0.0 }
        val losingTrades = trades.size - winningTrades
        val grossPnl = trades.sumOf { trade -> trade.grossPnlInr }
        val totalFees = trades.sumOf { trade -> trade.feeInr }
        val netPnl = trades.sumOf { trade -> trade.netPnlInr }
        val avgNetReturnPct = if (trades.isNotEmpty()) {
            trades.map { trade -> trade.netReturnPct }.average()
        } else {
            0.0
        }

        var runningPnl = 0.0
        var peakPnl = 0.0
        var maxDrawdown = 0.0
        for (trade in trades) {
            runningPnl += trade.netPnlInr
            peakPnl = maxOf(peakPnl, runningPnl)
            val drawdown = peakPnl - runningPnl
            maxDrawdown = maxOf(maxDrawdown, drawdown)
        }

        return VolumeSpikeBacktestSummary(
            symbolsConsidered = symbolsConsidered,
            totalTrades = trades.size,
            winningTrades = winningTrades,
            losingTrades = losingTrades,
            winRatePct = if (trades.isNotEmpty()) (winningTrades.toDouble() / trades.size) * 100.0 else 0.0,
            grossPnlInr = grossPnl,
            totalFeesInr = totalFees,
            netPnlInr = netPnl,
            avgNetReturnPct = avgNetReturnPct,
            maxDrawdownInr = maxDrawdown,
        )
    }

    private fun backtestSymbol(
        symbol: String,
        instrumentToken: Long,
        bars: List<IntradayBar>,
        request: VolumeSpikeBacktestRunConfig,
        earningsBySymbol: Map<String, List<LocalDate>>,
    ): SymbolBacktestResult {
        val barsByDate = bars.groupBy { bar -> bar.timestamp.toLocalDate() }
            .toSortedMap()
            .mapValues { (_, dayBars) -> dayBars.sortedBy { bar -> bar.timestamp } }

        val dates = barsByDate.keys.toList()
        if (dates.size < 21) {
            return SymbolBacktestResult(emptyList(), skippedByEarningsFilter = false)
        }

        val delayBars = ((request.delayMinutes + 4) / 5).coerceAtLeast(1)
        val trades = mutableListOf<VolumeSpikeBacktestTrade>()
        var skippedByEarningsFilter = false

        for (dateIndex in 20 until dates.size) {
            val currentDate = dates[dateIndex]
            if (currentDate.isBefore(request.fromDate) || currentDate.isAfter(request.toDate)) {
                continue
            }
            val dayBars = barsByDate[currentDate].orEmpty()
            if (dayBars.size < 8) {
                continue
            }

            val dayOpen = dayBars.first().open
            if (dayOpen <= 0.0) {
                continue
            }

            val vwapByIndex = computeVwapByIndex(dayBars)
            var dayTrade: VolumeSpikeBacktestTrade? = null

            for (barIndex in dayBars.indices) {
                if (barIndex < 6) {
                    continue
                }

                val currentBar = dayBars[barIndex]
                val prior30MinHigh = dayBars.subList(barIndex - 6, barIndex).maxOf { bar -> bar.high }
                val baselineVolumes = collectBaselineSlotVolumes(
                    dates = dates,
                    barsByDate = barsByDate,
                    dateIndex = dateIndex,
                    slotIndex = barIndex,
                )
                if (baselineVolumes.size < 20) {
                    continue
                }

                val avgBaseline = baselineVolumes.average()
                if (avgBaseline <= 0.0) {
                    continue
                }

                val rvol = currentBar.volume / avgBaseline
                if (rvol < request.rvolThreshold) {
                    continue
                }

                val dayMoveFromOpenPct = ((currentBar.close / dayOpen) - 1.0) * 100.0
                if (dayMoveFromOpenPct < request.minDayMoveFromOpenPct) {
                    continue
                }

                val vwap = vwapByIndex[barIndex]

                if (!passesEarningsFilter(symbol, currentDate, request, earningsBySymbol)) {
                    skippedByEarningsFilter = true
                    continue
                }

                val entryIndex = barIndex + delayBars
                if (entryIndex >= dayBars.size) {
                    continue
                }

                val entryBar = dayBars[entryIndex]
                val entryPrice = entryBar.open
                if (entryPrice <= 0.0) {
                    continue
                }

                val quantity = floor(request.positionSizeInr / entryPrice).toInt()
                if (quantity <= 0) {
                    continue
                }

                val targetPrice = entryPrice * (1.0 + request.targetPct / 100.0)
                val stopPrice = entryPrice * (1.0 - request.stopPct / 100.0)

                val exitDecision = findExit(dayBars, entryIndex, targetPrice, stopPrice)
                val investedAmount = entryPrice * quantity
                val grossPnl = (exitDecision.price - entryPrice) * quantity
                val netPnl = grossPnl - request.feePerTradeInr
                val netReturnPct = if (investedAmount > 0.0) (netPnl / investedAmount) * 100.0 else 0.0

                dayTrade = VolumeSpikeBacktestTrade(
                    symbol = symbol,
                    instrumentToken = instrumentToken,
                    signalTime = currentBar.timestamp.toString(),
                    entryTime = entryBar.timestamp.toString(),
                    exitTime = exitDecision.timestamp.toString(),
                    entryPrice = entryPrice,
                    exitPrice = exitDecision.price,
                    quantity = quantity,
                    investedAmount = investedAmount,
                    targetPrice = targetPrice,
                    stopPrice = stopPrice,
                    rvolAtSignal = rvol,
                    signalCandleVolume = currentBar.volume,
                    avgSlotVolume20d = avgBaseline,
                    vwapAtSignal = vwap,
                    prior30MinHigh = prior30MinHigh,
                    exitReason = exitDecision.reason,
                    grossPnlInr = grossPnl,
                    feeInr = request.feePerTradeInr,
                    netPnlInr = netPnl,
                    netReturnPct = netReturnPct,
                )
                break
            }

            if (dayTrade != null) {
                trades += dayTrade
            }
        }

        return SymbolBacktestResult(trades = trades, skippedByEarningsFilter = skippedByEarningsFilter)
    }

    private fun computeVwapByIndex(dayBars: List<IntradayBar>): List<Double> {
        val output = MutableList(dayBars.size) { 0.0 }
        var cumulativeVolume = 0.0
        var cumulativePv = 0.0
        dayBars.forEachIndexed { index, bar ->
            cumulativeVolume += bar.volume
            cumulativePv += bar.close * bar.volume
            output[index] = if (cumulativeVolume > 0.0) cumulativePv / cumulativeVolume else bar.close
        }
        return output
    }

    private fun collectBaselineSlotVolumes(
        dates: List<LocalDate>,
        barsByDate: Map<LocalDate, List<IntradayBar>>,
        dateIndex: Int,
        slotIndex: Int,
    ): List<Double> {
        val volumes = mutableListOf<Double>()
        val startIndex = (dateIndex - 20).coerceAtLeast(0)
        for (index in startIndex until dateIndex) {
            val historicalDate = dates[index]
            val bar = barsByDate[historicalDate]?.getOrNull(slotIndex) ?: continue
            volumes += bar.volume
        }
        return volumes
    }

    private fun findExit(
        dayBars: List<IntradayBar>,
        entryIndex: Int,
        targetPrice: Double,
        stopPrice: Double,
    ): ExitDecision {
        for (index in entryIndex until dayBars.size) {
            val bar = dayBars[index]
            val stopHit = bar.low <= stopPrice
            val targetHit = bar.high >= targetPrice

            if (stopHit && targetHit) {
                return ExitDecision(price = stopPrice, timestamp = bar.timestamp, reason = "STOP_HIT")
            }
            if (stopHit) {
                return ExitDecision(price = stopPrice, timestamp = bar.timestamp, reason = "STOP_HIT")
            }
            if (targetHit) {
                return ExitDecision(price = targetPrice, timestamp = bar.timestamp, reason = "TARGET_HIT")
            }
        }

        val lastBar = dayBars.last()
        return ExitDecision(price = lastBar.close, timestamp = lastBar.timestamp, reason = "EOD")
    }

    private fun passesEarningsFilter(
        symbol: String,
        signalDate: LocalDate,
        request: VolumeSpikeBacktestRunConfig,
        earningsBySymbol: Map<String, List<LocalDate>>,
    ): Boolean {
        if (request.earningsFilterMode == EarningsFilterMode.OFF || request.earningsFilterMode == EarningsFilterMode.MANUAL_SYMBOL) {
            return true
        }

        val startOffset = request.earningsWindowStartOffsetDays ?: return false
        val endOffset = request.earningsWindowEndOffsetDays ?: return false
        val earningsDates = earningsBySymbol[symbol] ?: return false

        return earningsDates.any { earningsDate ->
            val offset = ChronoUnit.DAYS.between(earningsDate, signalDate).toInt()
            offset in startOffset..endOffset
        }
    }

    private suspend fun loadEarningsRows(request: VolumeSpikeBacktestRunConfig) = when (request.earningsFilterMode) {
        EarningsFilterMode.OFF -> emptyList()
        EarningsFilterMode.CUSTOM_WINDOW -> {
            val startOffset = request.earningsWindowStartOffsetDays ?: 0
            val endOffset = request.earningsWindowEndOffsetDays ?: 0
            val queryFrom = request.fromDate.minusDays(endOffset.toLong())
            val queryTo = request.toDate.minusDays(startOffset.toLong())
            earningsHandler.read { dao ->
                dao.findByResultDateRange(queryFrom, queryTo)
            }
        }
        EarningsFilterMode.MANUAL_SYMBOL -> emptyList()
    }

    private suspend fun ensureInstrumentCacheLoaded() {
        if (!instrumentCache.isEmpty()) return
        instrumentCacheMutex.withLock {
            if (!instrumentCache.isEmpty()) return
            log.info("Volume spike backtest: instrument cache empty, loading NSE instruments from Kite.")
            val instruments = withContext(Dispatchers.IO) {
                kiteClient.client().getInstruments("NSE")
            }
            instrumentCache.refresh(instruments)
            log.info("Volume spike backtest: instrument cache loaded with {} NSE instruments.", instruments.size)
        }
    }

    private fun resolveInstrumentToken(symbol: String): Long? =
        instrumentCache.token("NSE", symbol.uppercase())

    private suspend fun loadIntradayCandles(
        symbol: String,
        instrumentToken: Long,
        fromDate: LocalDate,
        toDate: LocalDate,
    ): CandleLoadResult {
        val cacheKey = buildCacheKey(symbol, fromDate, toDate)

        val cachedBars = readBarsFromRedis(cacheKey)
        if (cachedBars != null) {
            return CandleLoadResult(
                source = CandleSource.CACHE,
                bars = cachedBars,
                fetchFailure = false,
            )
        }

        val fetchedBars = runCatching {
            fetchBarsFromKite(instrumentToken, symbol, fromDate, toDate)
        }.getOrElse { error ->
            log.warn("Volume spike backtest: failed to fetch 5m candles for {}: {}", symbol, error.message)
            return CandleLoadResult(
                source = CandleSource.KITE,
                bars = emptyList(),
                fetchFailure = true,
            )
        }

        if (fetchedBars.isNotEmpty()) {
            writeBarsToRedis(cacheKey, fetchedBars)
        }

        return CandleLoadResult(
            source = CandleSource.KITE,
            bars = fetchedBars,
            fetchFailure = false,
        )
    }

    private suspend fun readBarsFromRedis(cacheKey: String): List<IntradayBar>? {
        val cachedJson = runCatching {
            redis.get(cacheKey)
        }.getOrNull() ?: return null

        if (cachedJson.isBlank()) {
            return null
        }

        return runCatching {
            objectMapper.readValue(cachedJson, object : TypeReference<List<IntradayBar>>() {})
        }.getOrNull()
    }

    private suspend fun writeBarsToRedis(cacheKey: String, bars: List<IntradayBar>) {
        runCatching {
            redis.set(cacheKey, objectMapper.writeValueAsString(bars), CANDLE_CACHE_TTL_SECONDS)
        }.onFailure { error ->
            log.warn("Volume spike backtest: failed to write cache key {}: {}", cacheKey, error.message)
        }
    }

    private suspend fun fetchBarsFromKite(
        instrumentToken: Long,
        symbol: String,
        fromDate: LocalDate,
        toDate: LocalDate,
    ): List<IntradayBar> {
        val bars = mutableListOf<IntradayBar>()
        var cursor = fromDate

        while (!cursor.isAfter(toDate)) {
            val chunkEnd = minOf(cursor.plusDays(KITE_FETCH_CHUNK_DAYS - 1), toDate)
            delay(KITE_RATE_LIMIT_DELAY_MS)
            val history = withContext(Dispatchers.IO) {
                kiteClient.client().getHistoricalData(
                    Date.from(cursor.atStartOfDay(ist).toInstant()),
                    Date.from(chunkEnd.plusDays(1).atStartOfDay(ist).minusSeconds(1).toInstant()),
                    instrumentToken.toString(),
                    KITE_INTERVAL_5M,
                    false,
                    false,
                )
            }

            history.dataArrayList.forEach { bar ->
                if (bar == null) return@forEach
                runCatching {
                    val timestamp = LocalDateTime.parse(bar.timeStamp.substring(0, 19))
                    if (timestamp.toLocalDate() !in fromDate..toDate) {
                        return@runCatching null
                    }
                    IntradayBar(
                        timestamp = timestamp,
                        open = bar.open,
                        high = bar.high,
                        low = bar.low,
                        close = bar.close,
                        volume = bar.volume.toDouble(),
                    )
                }.getOrNull()?.let { parsed -> bars += parsed }
            }

            cursor = chunkEnd.plusDays(1)
        }

        return bars
            .distinctBy { bar -> bar.timestamp }
            .sortedBy { bar -> bar.timestamp }
            .also { parsed ->
                log.info("Volume spike backtest: loaded {} 5m candles for {}", parsed.size, symbol)
            }
    }

    private fun buildCacheKey(symbol: String, fromDate: LocalDate, toDate: LocalDate): String =
        "backtest:volume_spike:candles:${symbol.uppercase()}:5minute:$fromDate:$toDate"

    private data class IntradayBar(
        val timestamp: LocalDateTime,
        val open: Double,
        val high: Double,
        val low: Double,
        val close: Double,
        val volume: Double,
    )

    private data class SymbolBacktestResult(
        val trades: List<VolumeSpikeBacktestTrade>,
        val skippedByEarningsFilter: Boolean,
    )

    private data class ExitDecision(
        val price: Double,
        val timestamp: LocalDateTime,
        val reason: String,
    )

    private data class CandleLoadResult(
        val source: CandleSource,
        val bars: List<IntradayBar>,
        val fetchFailure: Boolean,
    )

    private enum class CandleSource {
        CACHE,
        KITE,
    }

    private companion object {
        const val BASELINE_PREFETCH_DAYS: Long = 60L
        const val CANDLE_CACHE_TTL_SECONDS: Long = 48L * 60L * 60L
        const val KITE_FETCH_CHUNK_DAYS: Long = 30L
        const val KITE_RATE_LIMIT_DELAY_MS: Long = 350L
        const val KITE_INTERVAL_5M: String = "5minute"
    }
}
