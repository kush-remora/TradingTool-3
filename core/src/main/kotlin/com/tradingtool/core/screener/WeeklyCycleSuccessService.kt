package com.tradingtool.core.screener

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.model.stock.Stock
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
import kotlin.math.abs

@Singleton
class WeeklyCycleSuccessService @Inject constructor(
    private val stockHandler: StockJdbiHandler,
    private val candleCache: CandleCacheService,
    private val instrumentCache: InstrumentCache,
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
        stableBaseMaxDriftPct: Double,
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
                    val context = resolveSymbolContext(symbol, stock)
                    val token = context.instrumentToken
                    if (token <= 0L) {
                        return@withPermit WeeklyCycleSuccessRow(
                            symbol = symbol,
                            companyName = context.companyName,
                            instrumentToken = 0L,
                            universeBuckets = symbolBuckets[symbol] ?: emptyList(),
                            successCount = 0,
                            cycleCount = 0,
                            successRatePct = 0.0,
                            failedStartWeeks = emptyList(),
                            lastCycleMetrics = null,
                            stableBasePass = false,
                            stableBaseReason = "No instrument token found",
                            stableBaseDriftPct = null,
                            stableBaseLowMin = null,
                            stableBaseLowMax = null,
                            stableBaseWeeksCount = 0,
                            lastWeekMondayDipPct = null,
                            avg8wMondayDipPct = null,
                            mondayDipSamples8w = 0,
                        )
                    }

                    val candles = candleCache.getDailyCandles(
                        token = token,
                        symbol = symbol,
                        from = fromDate,
                        to = today,
                    )
                    val completedWeeksAll = selectCompletedWeeks(candles, today)
                    val completedWeeks = completedWeeksAll.takeLast(weeksRequested)
                    val (evaluatedCycles, failedStartWeeks) = evaluateWeeks(
                        weeks = completedWeeks,
                        highLowThresholdPct = highLowThresholdPct,
                        rocThresholdPct = rocThresholdPct,
                    )
                    val mondayDipMetrics = computeMondayDipMetrics(
                        completedWeeks = completedWeeksAll,
                        lookbackWeeks = 8,
                    )
                    val stableBase = evaluateStableBase(
                        cycles = evaluatedCycles,
                        maxDriftPct = stableBaseMaxDriftPct,
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
                        companyName = context.companyName,
                        instrumentToken = token,
                        universeBuckets = symbolBuckets[symbol] ?: emptyList(),
                        successCount = successCount,
                        cycleCount = cycleCount,
                        successRatePct = successRatePct,
                        failedStartWeeks = failedStartWeeks,
                        lastCycleMetrics = evaluatedCycles.lastOrNull(),
                        stableBasePass = stableBase.pass,
                        stableBaseReason = stableBase.reason,
                        stableBaseDriftPct = stableBase.driftPct,
                        stableBaseLowMin = stableBase.lowMin,
                        stableBaseLowMax = stableBase.lowMax,
                        stableBaseWeeksCount = stableBase.weeksCount,
                        lastWeekMondayDipPct = mondayDipMetrics.lastWeekMondayDipPct,
                        avg8wMondayDipPct = mondayDipMetrics.avg8wMondayDipPct,
                        mondayDipSamples8w = mondayDipMetrics.mondayDipSamples8w,
                    )
                }
            }
        }.awaitAll()

        val weeksEvaluated = rows.maxOfOrNull { row -> row.cycleCount + row.failedStartWeeks.size } ?: 0
        log.info(
            "Weekly cycle success scan complete: symbols={} weeksRequested={} weeksEvaluated={} highLow={} roc={} stableBaseMaxDrift={}",
            uniqueSymbols.size,
            weeksRequested,
            weeksEvaluated,
            highLowThresholdPct,
            rocThresholdPct,
            stableBaseMaxDriftPct,
        )

        ScanResult(
            results = rows.sortedByDescending { row -> row.successRatePct },
            weeksEvaluated = weeksEvaluated,
        )
    }

    private fun resolveSymbolContext(symbol: String, stock: Stock?): SymbolContext {
        if (stock != null && stock.instrumentToken > 0) {
            return SymbolContext(
                instrumentToken = stock.instrumentToken,
                companyName = stock.companyName.takeIf { name -> name.isNotBlank() } ?: symbol,
            )
        }

        val instrument = instrumentCache.find("NSE", symbol)
        if (instrument != null && instrument.instrument_token > 0) {
            return SymbolContext(
                instrumentToken = instrument.instrument_token,
                companyName = instrument.name?.takeIf { name -> name.isNotBlank() } ?: symbol,
            )
        }

        return SymbolContext(
            instrumentToken = 0L,
            companyName = stock?.companyName?.takeIf { name -> name.isNotBlank() } ?: symbol,
        )
    }

    private data class SymbolContext(
        val instrumentToken: Long,
        val companyName: String,
    )
}

internal data class StableBaseEvaluation(
    val pass: Boolean,
    val reason: String?,
    val driftPct: Double?,
    val lowMin: Double?,
    val lowMax: Double?,
    val weeksCount: Int,
)

internal data class MondayDipMetrics(
    val lastWeekMondayDipPct: Double?,
    val avg8wMondayDipPct: Double?,
    val mondayDipSamples8w: Int,
)

internal data class WeeklyCycleWeek(
    val isoYear: Int,
    val isoWeek: Int,
    val dailyCandles: Map<Int, DailyCandle>,
)

internal fun computeMondayDipPct(candle: DailyCandle?): Double? {
    if (candle == null || candle.open <= 0.0) {
        return null
    }
    return (((candle.open - candle.low) / candle.open) * 100.0).roundTo2()
}

internal fun computeMondayDipMetrics(
    completedWeeks: List<WeeklyCycleWeek>,
    lookbackWeeks: Int,
): MondayDipMetrics {
    return computeMondayDipMetricsFromCandles(
        weeklyCandles = completedWeeks.map { week -> week.dailyCandles },
        lookbackWeeks = lookbackWeeks,
    )
}

internal fun computeMondayDipMetricsFromCandles(
    weeklyCandles: List<Map<Int, DailyCandle>>,
    lookbackWeeks: Int,
): MondayDipMetrics {
    val lastWeek = weeklyCandles.lastOrNull()
    val lastWeekStartCandle = when {
        lastWeek?.containsKey(1) == true -> lastWeek[1]
        lastWeek?.containsKey(2) == true -> lastWeek[2]
        else -> null
    }

    val windowDips = weeklyCandles
        .takeLast(lookbackWeeks)
        .mapNotNull { candles ->
            val startCandle = when {
                candles.containsKey(1) -> candles[1]
                candles.containsKey(2) -> candles[2]
                else -> null
            }
            computeMondayDipPct(startCandle)
        }

    val avg8wMondayDipPct = if (windowDips.isEmpty()) {
        null
    } else {
        windowDips.average().roundTo2()
    }

    return MondayDipMetrics(
        lastWeekMondayDipPct = computeMondayDipPct(lastWeekStartCandle),
        avg8wMondayDipPct = avg8wMondayDipPct,
        mondayDipSamples8w = windowDips.size,
    )
}

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

internal fun evaluateStableBase(
    cycles: List<WeeklyCycleMetrics>,
    maxDriftPct: Double,
): StableBaseEvaluation {
    val validCycles = cycles.filter { cycle -> cycle.startLow > 0.0 }
    if (validCycles.size < 2) {
        return StableBaseEvaluation(
            pass = false,
            reason = "Need at least 2 valid start weeks",
            driftPct = null,
            lowMin = null,
            lowMax = null,
            weeksCount = validCycles.size,
        )
    }

    val anchorLow = validCycles.first().startLow
    val latestLow = validCycles.last().startLow
    if (anchorLow <= 0.0) {
        return StableBaseEvaluation(
            pass = false,
            reason = "Need at least 2 valid start weeks",
            driftPct = null,
            lowMin = null,
            lowMax = null,
            weeksCount = validCycles.size,
        )
    }

    val baseShiftPct = (abs(latestLow - anchorLow) / anchorLow * 100.0).roundTo2()
    val pass = baseShiftPct <= maxDriftPct
    val reason = if (pass) {
        null
    } else {
        "Base shift ${baseShiftPct.roundTo2()}% > ${maxDriftPct.roundTo2()}%"
    }

    return StableBaseEvaluation(
        pass = pass,
        reason = reason,
        driftPct = baseShiftPct,
        lowMin = minOf(anchorLow, latestLow).roundTo2(),
        lowMax = maxOf(anchorLow, latestLow).roundTo2(),
        weeksCount = validCycles.size,
    )
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
