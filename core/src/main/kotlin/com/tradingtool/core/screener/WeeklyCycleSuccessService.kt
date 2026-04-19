package com.tradingtool.core.screener

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.technical.roundTo2
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields

@Singleton
class WeeklyCycleSuccessService @Inject constructor(
    private val stockHandler: StockJdbiHandler,
    private val candleCache: CandleCacheService,
) {
    private val log = LoggerFactory.getLogger(WeeklyCycleSuccessService::class.java)
    private val ist = ZoneId.of("Asia/Kolkata")
    private val analysisSemaphore = Semaphore(15)

    data class ScanResult(
        val results: List<WeeklyCycleSuccessRow>,
        val weeksEvaluated: Int,
    )

    suspend fun invalidateDailyCache(symbol: String) {
        candleCache.invalidateDailyCandles(symbol)
    }

    suspend fun scan(
        symbols: List<String>,
        symbolBuckets: Map<String, List<String>>,
        weeksRequested: Int,
        highLowThresholdPct: Double,
        rocThresholdPct: Double,
    ): ScanResult = coroutineScope {
        val uniqueSymbols = symbols
            .map { symbol -> symbol.trim().uppercase() }
            .filter { symbol -> symbol.isNotEmpty() }
            .distinct()

        if (uniqueSymbols.isEmpty()) {
            return@coroutineScope ScanResult(emptyList(), 0)
        }

        val stocksBySymbol = stockHandler.read { dao ->
            dao.listBySymbols(uniqueSymbols, "NSE")
        }.associateBy { stock -> stock.symbol.uppercase() }

        val today = LocalDate.now(ist)
        val fromDate = today.minusWeeks((weeksRequested + 12).toLong())

        val rows = uniqueSymbols.map { symbol ->
            async {
                analysisSemaphore.withPermit {
                    val stock = stocksBySymbol[symbol]
                    val token = stock?.instrumentToken ?: 0L
                    if (token <= 0L) {
                        return@withPermit WeeklyCycleSuccessRow(
                            symbol = symbol,
                            companyName = stock?.companyName ?: symbol,
                            instrumentToken = 0L,
                            universeBuckets = symbolBuckets[symbol] ?: emptyList(),
                            successCount = 0,
                            cycleCount = 0,
                            successRatePct = 0.0,
                            failedStartWeeks = emptyList(),
                            lastCycleMetrics = null,
                        )
                    }

                    val candles = candleCache.getDailyCandles(
                        token = token,
                        symbol = symbol,
                        from = fromDate,
                        to = today,
                    )
                    val completedWeeks = selectCompletedWeeks(candles, today).takeLast(weeksRequested)
                    val (evaluatedCycles, failedStartWeeks) = evaluateWeeks(
                        weeks = completedWeeks,
                        highLowThresholdPct = highLowThresholdPct,
                        rocThresholdPct = rocThresholdPct,
                    )

                    val successCount = evaluatedCycles.count { cycle -> cycle.success }
                    val cycleCount = evaluatedCycles.size
                    val successRatePct = if (cycleCount == 0) {
                        0.0
                    } else {
                        (successCount.toDouble() / cycleCount.toDouble() * 100.0).roundTo2()
                    }

                    WeeklyCycleSuccessRow(
                        symbol = symbol,
                        companyName = stock?.companyName ?: symbol,
                        instrumentToken = token,
                        universeBuckets = symbolBuckets[symbol] ?: emptyList(),
                        successCount = successCount,
                        cycleCount = cycleCount,
                        successRatePct = successRatePct,
                        failedStartWeeks = failedStartWeeks,
                        lastCycleMetrics = evaluatedCycles.lastOrNull(),
                    )
                }
            }
        }.awaitAll()

        val weeksEvaluated = rows.maxOfOrNull { row -> row.cycleCount + row.failedStartWeeks.size } ?: 0
        log.info(
            "Weekly cycle success scan complete: symbols={} weeksRequested={} weeksEvaluated={} highLow={} roc={}",
            uniqueSymbols.size,
            weeksRequested,
            weeksEvaluated,
            highLowThresholdPct,
            rocThresholdPct,
        )

        ScanResult(
            results = rows.sortedByDescending { row -> row.successRatePct },
            weeksEvaluated = weeksEvaluated,
        )
    }
}

internal data class WeeklyCycleWeek(
    val isoYear: Int,
    val isoWeek: Int,
    val dailyCandles: Map<Int, DailyCandle>,
)

internal fun selectCompletedWeeks(candles: List<DailyCandle>, today: LocalDate): List<WeeklyCycleWeek> {
    val currentWeekStart = today.minusDays((today.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())

    return candles
        .groupBy { candle ->
            Pair(
                candle.candleDate.get(WeekFields.ISO.weekBasedYear()),
                candle.candleDate.get(WeekFields.ISO.weekOfWeekBasedYear()),
            )
        }
        .map { (weekKey, weekCandles) ->
            WeeklyCycleWeek(
                isoYear = weekKey.first,
                isoWeek = weekKey.second,
                dailyCandles = weekCandles.associateBy { candle -> candle.candleDate.get(WeekFields.ISO.dayOfWeek()) },
            )
        }
        .sortedWith(compareBy({ week -> week.isoYear }, { week -> week.isoWeek }))
        .filter { week ->
            val weekEndDate = week.dailyCandles.values.maxOfOrNull { candle -> candle.candleDate } ?: return@filter false
            weekEndDate.isBefore(currentWeekStart)
        }
}

internal fun evaluateWeeks(
    weeks: List<WeeklyCycleWeek>,
    highLowThresholdPct: Double,
    rocThresholdPct: Double,
): Pair<List<WeeklyCycleMetrics>, List<String>> {
    val evaluations = mutableListOf<WeeklyCycleMetrics>()
    val failedStartWeeks = mutableListOf<String>()

    weeks.forEach { week ->
        val weekLabel = formatWeekLabel(week)
        val startDay = when {
            week.dailyCandles.containsKey(1) -> 1
            week.dailyCandles.containsKey(2) -> 2
            else -> {
                failedStartWeeks += weekLabel
                null
            }
        } ?: return@forEach

        val startCandle = week.dailyCandles[startDay] ?: return@forEach
        if (startCandle.low <= 0.0) {
            failedStartWeeks += weekLabel
            return@forEach
        }

        val endDay = if (week.dailyCandles.containsKey(5)) {
            5
        } else {
            week.dailyCandles.keys.maxOrNull() ?: return@forEach
        }
        val endCandle = week.dailyCandles[endDay] ?: return@forEach

        val weekHigh = week.dailyCandles.values.maxOfOrNull { candle -> candle.high } ?: return@forEach
        val highLowPct = ((weekHigh - startCandle.low) / startCandle.low * 100.0).roundTo2()
        val rocPct = ((endCandle.close - startCandle.low) / startCandle.low * 100.0).roundTo2()
        val success = highLowPct >= highLowThresholdPct && rocPct >= rocThresholdPct

        evaluations += WeeklyCycleMetrics(
            weekLabel = weekLabel,
            startDay = dayName(startDay),
            endDay = dayName(endDay),
            startLow = startCandle.low.roundTo2(),
            endClose = endCandle.close.roundTo2(),
            weekHigh = weekHigh.roundTo2(),
            highLowPct = highLowPct,
            rocPct = rocPct,
            success = success,
        )
    }

    return Pair(evaluations, failedStartWeeks)
}

private fun formatWeekLabel(week: WeeklyCycleWeek): String {
    return String.format("%04d-W%02d", week.isoYear, week.isoWeek)
}

private fun dayName(day: Int): String {
    return when (day) {
        1 -> "Mon"
        2 -> "Tue"
        3 -> "Wed"
        4 -> "Thu"
        5 -> "Fri"
        else -> "D$day"
    }
}
