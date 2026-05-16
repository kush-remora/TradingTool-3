package com.tradingtool.core.screener

import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.technical.roundTo2
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

class BaseSwingService(
    private val stockHandler: StockJdbiHandler,
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val candleCache: CandleCacheService,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ist = ZoneId.of("Asia/Kolkata")
    private val analysisSemaphore = Semaphore(15)

    suspend fun analyze(indexKeys: List<String>): List<BaseSwingResult> = coroutineScope {
        val startTime = System.currentTimeMillis()
        log.info("Starting Base-Swing analysis for indices: {}", indexKeys)

        // 1. Resolve symbols from stocks table and index_constituents
        val symbolsToAnalyze = mutableSetOf<SymbolMetadata>()
        
        indexKeys.forEach { key ->
            if (key == "WATCHLIST") {
                val stocks = stockHandler.read { it.listAll() }
                symbolsToAnalyze.addAll(stocks.map { SymbolMetadata(it.symbol, it.companyName, it.instrumentToken) })
            } else {
                val constituents = indexConstituentHandler.read { it.listActiveByIndex(key) }
                symbolsToAnalyze.addAll(constituents.map { SymbolMetadata(it.symbol, it.companyName, it.instrumentToken) })
            }
        }

        log.info("Analyzing {} unique symbols", symbolsToAnalyze.size)

        val results = symbolsToAnalyze.map { metadata ->
            async {
                analysisSemaphore.withPermit {
                    analyzeSymbol(metadata)
                }
            }
        }.awaitAll().filterNotNull()

        log.info("Completed Base-Swing analysis in {}ms", System.currentTimeMillis() - startTime)
        results.sortedByDescending { it.setupScore }
    }

    private suspend fun analyzeSymbol(metadata: SymbolMetadata): BaseSwingResult? {
        val today = LocalDate.now(ist)
        val from = today.minusYears(1) // Need 1 year for 52w high

        val candles = candleCache.getDailyCandles(metadata.instrumentToken, metadata.symbol, from, today)
        if (candles.size < 20) return null

        val currentPrice = candles.last().close
        val candles30d = candles.takeLast(22) // approx 30 calendar days
        val price30dAgo = candles30d.first().close
        
        val high30d = candles30d.maxOf { it.high }
        val low30d = candles30d.minOf { it.low }
        
        val high52w = candles.maxOf { it.high }

        // 4. Weekly Pulses (Breakdown of H-L range for each of last 4 weeks)
        val weeklyPulses = calculateWeeklyPulses(candles)

        // 1. Base Stability: Price is where it was ~30 days ago (+/- 4%)
        val baseDriftPct = if (price30dAgo > 0) ((currentPrice - price30dAgo) / price30dAgo * 100.0).roundTo2() else null
        
        // 2. Internal Volatility: High/Low range is healthy (> 7%)
        val internalVolPct = if (low30d > 0) ((high30d - low30d) / low30d * 100.0).roundTo2() else 0.0
        
        // 3. Safety: Not at 52w high (> 10% distance)
        val distFrom52wHighPct = if (high52w > 0) ((high52w - currentPrice) / high52w * 100.0).roundTo2() else null

        // Scoring Logic
        var score = 50
        val reasons = mutableListOf<String>()

        if (baseDriftPct != null && abs(baseDriftPct) <= 3.0) {
            score += 20
            reasons.add("Stable Base (Drift ${baseDriftPct}%)")
        } else if (baseDriftPct != null && abs(baseDriftPct) <= 5.0) {
            score += 10
            reasons.add("Fairly Stable (Drift ${baseDriftPct}%)")
        } else {
            score -= 20
            reasons.add("Trending stock (Drift ${baseDriftPct}%)")
        }

        if (internalVolPct >= 8.0) {
            score += 20
            reasons.add("High Internal Swing (${internalVolPct}%)")
        } else if (internalVolPct >= 5.0) {
            score += 10
            reasons.add("Good Internal Swing (${internalVolPct}%)")
        } else {
            score -= 10
            reasons.add("Low volatility base")
        }

        if (distFrom52wHighPct != null && distFrom52wHighPct >= 12.0) {
            score += 10
            reasons.add("Safe from 52w peak (${distFrom52wHighPct}% away)")
        } else if (distFrom52wHighPct != null && distFrom52wHighPct < 5.0) {
            score -= 30
            reasons.add("Near 52w High (High FOMO risk)")
        }

        // Pulse Reward (Consistent breathing)
        if (weeklyPulses.size >= 3) {
            val avgPulse = weeklyPulses.map { it.swingPct }.average()
            if (avgPulse >= 5.0) {
                score += 10
                reasons.add("Active Pulse (Avg ${avgPulse.roundTo2()}%)")
            }
        }

        return BaseSwingResult(
            symbol = metadata.symbol,
            companyName = metadata.companyName,
            instrumentToken = metadata.instrumentToken,
            currentPrice = currentPrice.roundTo2(),
            price30dAgo = price30dAgo.roundTo2(),
            baseDriftPct = baseDriftPct,
            high30d = high30d.roundTo2(),
            low30d = low30d.roundTo2(),
            internalVolPct = internalVolPct,
            distFrom52wHighPct = distFrom52wHighPct,
            high52w = high52w.roundTo2(),
            weeklyPulses = weeklyPulses,
            setupScore = score.coerceIn(0, 100),
            reasoning = reasons.joinToString("; ")
        )
    }

    private fun calculateWeeklyPulses(candles: List<DailyCandle>): List<WeeklyPulse> {
        val pulses = mutableListOf<WeeklyPulse>()
        val recentCandles = candles.takeLast(30) // Look at last 6 calendar weeks to find 4 full trading weeks
        
        // Partition into Monday-Friday chunks
        val weeksMap = recentCandles.groupBy { candle ->
            val date = candle.candleDate
            // Group by year and week-of-year for simplicity
            val weekFields = java.time.temporal.WeekFields.of(java.util.Locale.getDefault())
            "${date.year}-W${date.get(weekFields.weekOfWeekBasedYear())}"
        }

        val sortedWeekKeys = weeksMap.keys.sorted().takeLast(4)
        
        sortedWeekKeys.forEachIndexed { index, key ->
            val weekCandles = weeksMap[key] ?: return@forEachIndexed
            val high = weekCandles.maxOf { it.high }
            val low = weekCandles.minOf { it.low }
            val swing = if (low > 0) ((high - low) / low * 100.0).roundTo2() else 0.0
            
            pulses.add(WeeklyPulse(
                label = "W${sortedWeekKeys.size - index}",
                startDate = weekCandles.first().candleDate.toString(),
                endDate = weekCandles.last().candleDate.toString(),
                swingPct = swing
            ))
        }

        return pulses.reversed() // W1 is most recent
    }

    private data class SymbolMetadata(
        val symbol: String,
        val companyName: String,
        val instrumentToken: Long,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SymbolMetadata) return false
            return symbol == other.symbol
        }
        override fun hashCode(): Int = symbol.hashCode()
    }
}
