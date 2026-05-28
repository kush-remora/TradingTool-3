package com.tradingtool.core.strategy.volume

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.database.CandleJdbiHandler
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
import java.util.Date
import kotlin.math.floor

import com.tradingtool.core.database.IndexConstituentJdbiHandler

@Singleton
class IntradayShockBacktestService @Inject constructor(
    private val redis: RedisHandler,
    private val stockHandler: StockJdbiHandler,
    private val candleHandler: CandleJdbiHandler,
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val instrumentCache: InstrumentCache,
    private val kiteClient: KiteConnectClient,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(IntradayShockBacktestService::class.java)
    private val ist: ZoneId = ZoneId.of("Asia/Kolkata")
    private val instrumentCacheMutex = Mutex()

    suspend fun runBacktest(request: IntradayShockRunConfig): IntradayShockBacktestResponse {
        var stocks = stockHandler.read { dao ->
            dao.listAll()
        }.filter { stock ->
            stock.exchange.equals("NSE", ignoreCase = true)
        }.sortedBy { stock -> stock.symbol }

        if (request.manualSymbols.isNotEmpty()) {
            val symbolSet = request.manualSymbols.map { it.uppercase() }.toSet()
            stocks = stocks.filter { it.symbol.uppercase() in symbolSet }
        } else if (!request.universe.isNullOrBlank()) {
            val constituents = indexConstituentHandler.read { dao ->
                val directMatches = dao.listActiveByIndex(request.universe)
                if (directMatches.isNotEmpty()) {
                    directMatches
                } else {
                    val normalizedRequested = normalizeIndexKey(request.universe)
                    val resolvedIndexKeys = dao.listUniqueIndices()
                        .map { summary -> summary.indexKey }
                        .filter { indexKey -> normalizeIndexKey(indexKey) == normalizedRequested }

                    resolvedIndexKeys
                        .flatMap { indexKey -> dao.listActiveByIndex(indexKey) }
                }
            }
            val symbolSet = constituents.map { it.symbol.uppercase() }.toSet()
            stocks = stocks.filter { it.symbol.uppercase() in symbolSet }
        }

        val allTrades = mutableListOf<IntradayShockBacktestTrade>()
        val symbolsWithNoToken = mutableListOf<String>()
        val symbolsWithNoIntradayData = mutableListOf<String>()
        val symbolsWithNoTrades = mutableListOf<String>()
        val kiteFetchFailures = mutableListOf<String>()

        var cacheHits = 0
        var cacheMisses = 0
        var symbolsWithResolvedToken = 0

        ensureInstrumentCacheLoaded()

        for (stock in stocks) {
            val symbol = stock.symbol.uppercase()
            val instrumentToken = resolveInstrumentToken(symbol)
            if (instrumentToken == null || instrumentToken <= 0L) {
                symbolsWithNoToken += symbol
                continue
            }

            symbolsWithResolvedToken += 1
            val cacheResult = loadIntradayCandles(
                symbol = symbol,
                instrumentToken = instrumentToken,
                fromDate = request.fromDate,
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

            // Fetch daily candles (we need up to 252 trading days before fromDate, so ~365 calendar days + full backtest window)
            val dailyFetchFrom = request.fromDate.minusDays(400)
            val dailyCandles = candleHandler.read { dao ->
                dao.getDailyCandlesBySymbol(symbol, dailyFetchFrom, request.toDate)
            }

            val symbolTrades = backtestSymbol(
                symbol = symbol,
                instrumentToken = instrumentToken,
                intradayBars = cacheResult.bars,
                dailyCandles = dailyCandles,
                request = request,
            )

            if (symbolTrades.isEmpty()) {
                symbolsWithNoTrades += symbol
            } else {
                allTrades += symbolTrades
            }
        }

        val orderedTrades = allTrades.sortedBy { trade -> trade.entryTime }
        val summary = buildSummary(orderedTrades, stocks.size)

        return IntradayShockBacktestResponse(
            config = IntradayShockBacktestConfigSnapshot(
                fromDate = request.fromDate.toString(),
                toDate = request.toDate.toString(),
                universe = request.universe,
                manualSymbols = request.manualSymbols,
                scanEndMinutes = request.scanEndMinutes,
                entryDelayMinutes = request.entryDelayMinutes,
                gapUpTolerancePct = request.gapUpTolerancePct,
                targetPct = request.targetPct,
                hardStopPct = request.hardStopPct,
            ),
            summary = summary,
            diagnostics = IntradayShockBacktestDiagnostics(
                symbolsConsidered = stocks.size,
                symbolsWithResolvedToken = symbolsWithResolvedToken,
                symbolsWithNoToken = symbolsWithNoToken.sorted(),
                symbolsWithNoIntradayData = symbolsWithNoIntradayData.sorted(),
                symbolsWithNoTrades = symbolsWithNoTrades.sorted(),
                cacheHits = cacheHits,
                cacheMisses = cacheMisses,
                kiteFetchFailures = kiteFetchFailures.sorted(),
            ),
            trades = orderedTrades,
        )
    }

    private fun buildSummary(trades: List<IntradayShockBacktestTrade>, symbolsConsidered: Int): IntradayShockBacktestSummary {
        val winningTrades = trades.count { trade -> trade.netPnlInr > 0.0 }
        val losingTrades = trades.size - winningTrades
        val grossPnl = trades.sumOf { trade -> trade.grossPnlInr }
        val totalFees = trades.sumOf { trade -> trade.feeInr }
        val netPnl = trades.sumOf { trade -> trade.netPnlInr }
        val avgNetReturnPct = if (trades.isNotEmpty()) trades.map { it.netReturnPct }.average() else 0.0

        var runningPnl = 0.0
        var peakPnl = 0.0
        var maxDrawdown = 0.0
        for (trade in trades) {
            runningPnl += trade.netPnlInr
            peakPnl = maxOf(peakPnl, runningPnl)
            val drawdown = peakPnl - runningPnl
            maxDrawdown = maxOf(maxDrawdown, drawdown)
        }

        return IntradayShockBacktestSummary(
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
        intradayBars: List<IntradayBar>,
        dailyCandles: List<DailyCandle>,
        request: IntradayShockRunConfig,
    ): List<IntradayShockBacktestTrade> {
        val trades = mutableListOf<IntradayShockBacktestTrade>()
        
        val barsByDate = intradayBars.groupBy { it.timestamp.toLocalDate() }
            .toSortedMap()
            .mapValues { (_, dayBars) -> dayBars.sortedBy { it.timestamp } }

        for ((date, dayBars) in barsByDate) {
            if (date.isBefore(request.fromDate) || date.isAfter(request.toDate)) continue

            // 1. Calculate Daily Historical Max Volumes
            val historicalDaily = dailyCandles.filter { it.candleDate.isBefore(date) }.sortedBy { it.candleDate }
            if (historicalDaily.size < 60) continue // Need at least 60 days history

            val maxVol60 = historicalDaily.takeLast(60).maxOf { it.volume }.toDouble()
            val maxVol63 = if (historicalDaily.size >= 63) historicalDaily.takeLast(63).maxOf { it.volume }.toDouble() else maxVol60
            val maxVol126 = if (historicalDaily.size >= 126) historicalDaily.takeLast(126).maxOf { it.volume }.toDouble() else maxVol63
            val maxVol252 = if (historicalDaily.size >= 252) historicalDaily.takeLast(252).maxOf { it.volume }.toDouble() else maxVol126

            // Base filter logic (Turnover & 10-week SMA volume)
            val last50 = historicalDaily.takeLast(50)
            val avgTurnover = last50.map { it.close * it.volume }.average()
            if (avgTurnover < request.minTurnover) continue

            val last50Volume = last50.map { it.volume }.average()
            if (last50Volume < request.minVolumeSma) continue

            // Gap check
            val prevDay = historicalDaily.last()
            val dayOpen = dayBars.first().open
            val gapPct = ((dayOpen / prevDay.close) - 1.0) * 100.0
            
            // Allow only if gap is less than the gapUpTolerancePct (or negative)
            if (gapPct > request.gapUpTolerancePct) continue

            // 2. Intraday Volume Accumulation
            val scanEndTarget = LocalDateTime.of(date, java.time.LocalTime.of(9, 15).plusMinutes(request.scanEndMinutes.toLong()))
            var morningVolume = 0.0
            var morningLow = Double.MAX_VALUE
            var scanBarIndex = -1

            for (i in dayBars.indices) {
                val bar = dayBars[i]
                if (bar.timestamp.isBefore(scanEndTarget)) {
                    morningVolume += bar.volume
                    morningLow = minOf(morningLow, bar.low)
                    scanBarIndex = i
                } else {
                    break
                }
            }

            if (scanBarIndex == -1) continue

            // Volume hurdle check
            if (morningVolume <= maxVol60 && morningVolume <= maxVol63 && morningVolume <= maxVol126 && morningVolume <= maxVol252) {
                continue // Did not clear any volume hurdle
            }

            // 3. Entry Logic
            val entryTarget = LocalDateTime.of(date, java.time.LocalTime.of(9, 15).plusMinutes(request.entryDelayMinutes.toLong()))
            val entryIndex = dayBars.indexOfFirst { it.timestamp == entryTarget || it.timestamp.isAfter(entryTarget) }
            if (entryIndex == -1) continue

            val entryBar = dayBars[entryIndex]
            val entryPrice = entryBar.open
            if (entryPrice <= 0.0) continue

            val quantity = floor(request.positionSizeInr / entryPrice).toInt()
            if (quantity <= 0) continue

            val targetPrice = entryPrice * (1.0 + request.targetPct / 100.0)
            val hardStopPrice = entryPrice * (1.0 - request.hardStopPct / 100.0)
            // The wyckoff stop is the low of the morning accumulation range
            val wyckoffStopPrice = morningLow
            val stopPrice = maxOf(hardStopPrice, wyckoffStopPrice)

            // 4. Exit Logic
            var exitDecision: ExitDecision? = null
            for (i in entryIndex until dayBars.size) {
                val bar = dayBars[i]
                
                // End of day time limit check
                if (bar.timestamp.toLocalTime() >= request.exitTime) {
                    exitDecision = ExitDecision(bar.close, bar.timestamp, "EOD")
                    break
                }

                // We evaluate target first for this bar
                if (bar.high >= targetPrice && bar.low <= stopPrice) {
                    // It hit both. Assume stopped out for safety (pessimistic backtest)
                    exitDecision = ExitDecision(stopPrice, bar.timestamp, "STOP_HIT")
                    break
                } else if (bar.high >= targetPrice) {
                    exitDecision = ExitDecision(targetPrice, bar.timestamp, "TARGET_HIT")
                    break
                } else if (bar.low <= stopPrice) {
                    exitDecision = ExitDecision(stopPrice, bar.timestamp, "STOP_HIT")
                    break
                }
            }

            if (exitDecision == null) {
                val lastBar = dayBars.last()
                exitDecision = ExitDecision(lastBar.close, lastBar.timestamp, "EOD_FORCED")
            }

            val investedAmount = entryPrice * quantity
            val grossPnl = (exitDecision.price - entryPrice) * quantity
            val netPnl = grossPnl - request.feePerTradeInr
            val netReturnPct = if (investedAmount > 0.0) (netPnl / investedAmount) * 100.0 else 0.0

            trades += IntradayShockBacktestTrade(
                symbol = symbol,
                instrumentToken = instrumentToken,
                signalTime = dayBars[scanBarIndex].timestamp.toString(),
                entryTime = entryBar.timestamp.toString(),
                exitTime = exitDecision.timestamp.toString(),
                entryPrice = entryPrice,
                exitPrice = exitDecision.price,
                quantity = quantity,
                investedAmount = investedAmount,
                targetPrice = targetPrice,
                stopPrice = stopPrice,
                morningVolume = morningVolume,
                maxDailyVolume60d = maxVol60,
                maxDailyVolume63d = maxVol63,
                maxDailyVolume126d = maxVol126,
                maxDailyVolume252d = maxVol252,
                gapPct = gapPct,
                exitReason = exitDecision.reason,
                grossPnlInr = grossPnl,
                feeInr = request.feePerTradeInr,
                netPnlInr = netPnl,
                netReturnPct = netReturnPct,
            )
        }

        return trades
    }

    private suspend fun ensureInstrumentCacheLoaded() {
        if (!instrumentCache.isEmpty()) return
        instrumentCacheMutex.withLock {
            if (!instrumentCache.isEmpty()) return
            log.info("Intraday shock backtest: instrument cache empty, loading NSE instruments from Kite.")
            val instruments = withContext(Dispatchers.IO) {
                kiteClient.client().getInstruments("NSE")
            }
            instrumentCache.refresh(instruments)
            log.info("Intraday shock backtest: instrument cache loaded with {} NSE instruments.", instruments.size)
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
            log.warn("Intraday shock backtest: failed to fetch 5m candles for {}: {}", symbol, error.message)
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
            log.warn("Intraday shock backtest: failed to write cache key {}: {}", cacheKey, error.message)
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
                log.info("Intraday shock backtest: loaded {} 5m candles for {}", parsed.size, symbol)
            }
    }

    private fun buildCacheKey(symbol: String, fromDate: LocalDate, toDate: LocalDate): String =
        "backtest:intradayshock:candles:${symbol.uppercase()}:5minute:$fromDate:$toDate"

    private fun normalizeIndexKey(raw: String): String {
        return raw.trim()
            .uppercase()
            .replace(Regex("[^A-Z0-9]+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
    }

    private data class IntradayBar(
        val timestamp: LocalDateTime,
        val open: Double,
        val high: Double,
        val low: Double,
        val close: Double,
        val volume: Double,
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
        const val CANDLE_CACHE_TTL_SECONDS: Long = 48L * 60L * 60L
        const val KITE_FETCH_CHUNK_DAYS: Long = 30L
        const val KITE_RATE_LIMIT_DELAY_MS: Long = 350L
        const val KITE_INTERVAL_5M: String = "5minute"
    }
}
