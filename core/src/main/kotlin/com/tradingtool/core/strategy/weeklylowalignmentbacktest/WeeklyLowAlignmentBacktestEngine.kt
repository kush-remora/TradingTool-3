package com.tradingtool.core.strategy.weeklylowalignmentbacktest

import com.tradingtool.core.candle.DailyCandle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.round

class WeeklyLowAlignmentBacktestEngine {
    fun run(
        symbol: String,
        companyName: String?,
        candles: List<DailyCandle>,
        testFrom: LocalDate,
        toDate: LocalDate,
        targetPct: Double,
        maxHoldingTradingDays: Int,
        minimumRetestGapTradingDays: Int = MINIMUM_RETEST_GAP_TRADING_DAYS,
    ): WeeklyLowAlignmentBacktestSymbolReport {
        require(targetPct > 0.0) { "targetPct must be positive." }
        require(maxHoldingTradingDays > 0) { "maxHoldingTradingDays must be positive." }
        require(minimumRetestGapTradingDays > 0) { "minimumRetestGapTradingDays must be positive." }

        val sortedCandles = candles.sortedBy(DailyCandle::candleDate)
        require(sortedCandles.isNotEmpty()) { "No daily candle data is available for $symbol." }

        val currentWeekStart = toDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val completedCandles = sortedCandles.filter { candle ->
            candle.candleDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) < currentWeekStart
        }
        val weeks = completedCandles
            .groupBy { candle -> candle.candleDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .mapValues { (_, weekCandles) -> weekCandles.sortedBy(DailyCandle::candleDate) }
        val firstEntryWeek = testFrom.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
        val entryWeekStarts = weeks.keys
            .filter { weekStart -> weekStart >= firstEntryWeek && weeks.containsKey(weekStart.minusWeeks(1)) }
            .sorted()
        val candleIndexByDate = completedCandles.mapIndexed { index, candle -> candle.candleDate to index }.toMap()
        val instrumentToken = sortedCandles.first().instrumentToken
        var activeThroughIndex = -1
        val trades = mutableListOf<WeeklyLowAlignmentBacktestTrade>()

        for (entryWeekStart in entryWeekStarts) {
            val previousWeekStart = entryWeekStart.minusWeeks(1)
            val previousWeek = requireNotNull(weeks[previousWeekStart])
            val entryWeek = requireNotNull(weeks[entryWeekStart])
            val entryWeekIndex = requireNotNull(candleIndexByDate[entryWeek.first().candleDate])
            val trade = if (entryWeekIndex <= activeThroughIndex) {
                buildBaseTrade(
                    symbol = symbol,
                    instrumentToken = instrumentToken,
                    previousWeekStart = previousWeekStart,
                    entryWeekStart = entryWeekStart,
                    previousWeek = previousWeek,
                    outcome = WeeklyLowAlignmentBacktestOutcomes.POSITION_OPEN_SKIP,
                    targetPct = targetPct,
                )
            } else {
                buildTrade(
                    symbol = symbol,
                    instrumentToken = instrumentToken,
                    previousWeekStart = previousWeekStart,
                    previousWeek = previousWeek,
                    entryWeekStart = entryWeekStart,
                    entryWeek = entryWeek,
                    completedCandles = completedCandles,
                    candleIndexByDate = candleIndexByDate,
                    targetPct = targetPct,
                    maxHoldingTradingDays = maxHoldingTradingDays,
                    minimumRetestGapTradingDays = minimumRetestGapTradingDays,
                )
            }
            trade.exitDate?.let { exitDate -> activeThroughIndex = candleIndexByDate[LocalDate.parse(exitDate)] ?: activeThroughIndex }
            trades += trade
        }

        return WeeklyLowAlignmentBacktestSymbolReport(
            symbol = symbol,
            companyName = companyName,
            testedFromDate = testFrom.toString(),
            testedToDate = completedCandles.lastOrNull()?.candleDate?.toString() ?: toDate.toString(),
            summary = summarize(trades),
            trades = trades,
        )
    }

    private fun buildTrade(
        symbol: String,
        instrumentToken: Long,
        previousWeekStart: LocalDate,
        previousWeek: List<DailyCandle>,
        entryWeekStart: LocalDate,
        entryWeek: List<DailyCandle>,
        completedCandles: List<DailyCandle>,
        candleIndexByDate: Map<LocalDate, Int>,
        targetPct: Double,
        maxHoldingTradingDays: Int,
        minimumRetestGapTradingDays: Int,
    ): WeeklyLowAlignmentBacktestTrade {
        val previousLowCandle = previousWeek.minBy(DailyCandle::low)
        val previousLow = previousLowCandle.low
        val entryPrice = previousLow * ENTRY_PRICE_MULTIPLIER
        val targetPrice = entryPrice * (1.0 + targetPct / 100.0)
        val retests = entryWeek.filter { candle ->
            abs((candle.low - previousLow) / previousLow) * 100.0 <= RETEST_TOLERANCE_PCT
        }
        val eligibleRetest = retests.firstOrNull { candle ->
            tradingDayGap(candleIndexByDate, previousLowCandle.candleDate, candle.candleDate) >= minimumRetestGapTradingDays
        }
        if (eligibleRetest == null) {
            return buildBaseTrade(
                symbol = symbol,
                instrumentToken = instrumentToken,
                previousWeekStart = previousWeekStart,
                entryWeekStart = entryWeekStart,
                previousWeek = previousWeek,
                outcome = if (retests.isEmpty()) {
                    WeeklyLowAlignmentBacktestOutcomes.NO_RETEST
                } else {
                    WeeklyLowAlignmentBacktestOutcomes.TOO_SOON_RETEST
                },
                targetPct = targetPct,
                retestDate = retests.firstOrNull()?.candleDate,
                retestLow = retests.firstOrNull()?.low,
                retestGapTradingDays = retests.firstOrNull()?.let { candle ->
                    tradingDayGap(candleIndexByDate, previousLowCandle.candleDate, candle.candleDate)
                },
            )
        }

        val entryIndex = requireNotNull(candleIndexByDate[eligibleRetest.candleDate])
        val maxExitIndex = minOf(entryIndex + maxHoldingTradingDays, completedCandles.lastIndex)
        val exit = findExit(completedCandles, entryIndex, maxExitIndex, targetPrice)
            ?: Exit(
                candle = completedCandles[maxExitIndex],
                price = completedCandles[maxExitIndex].close,
                outcome = WeeklyLowAlignmentBacktestOutcomes.TIME_EXIT,
            )
        return buildBaseTrade(
            symbol = symbol,
            instrumentToken = instrumentToken,
            previousWeekStart = previousWeekStart,
            entryWeekStart = entryWeekStart,
            previousWeek = previousWeek,
            outcome = exit.outcome,
            targetPct = targetPct,
            retestDate = eligibleRetest.candleDate,
            retestLow = eligibleRetest.low,
            retestGapTradingDays = tradingDayGap(candleIndexByDate, previousLowCandle.candleDate, eligibleRetest.candleDate),
            entryDate = eligibleRetest.candleDate,
            exitDate = exit.candle.candleDate,
            exitPrice = exit.price,
            holdingTradingDays = candleIndexByDate.getValue(exit.candle.candleDate) - entryIndex,
            returnPct = ((exit.price / entryPrice) - 1.0) * 100.0,
        )
    }

    private fun buildBaseTrade(
        symbol: String,
        instrumentToken: Long,
        previousWeekStart: LocalDate,
        entryWeekStart: LocalDate,
        previousWeek: List<DailyCandle>,
        outcome: String,
        targetPct: Double,
        retestDate: LocalDate? = null,
        retestLow: Double? = null,
        retestGapTradingDays: Int? = null,
        entryDate: LocalDate? = null,
        exitDate: LocalDate? = null,
        exitPrice: Double? = null,
        holdingTradingDays: Int? = null,
        returnPct: Double? = null,
    ): WeeklyLowAlignmentBacktestTrade {
        val previousLowCandle = previousWeek.minBy(DailyCandle::low)
        val entryPrice = previousLowCandle.low * ENTRY_PRICE_MULTIPLIER
        return WeeklyLowAlignmentBacktestTrade(
            symbol = symbol,
            instrumentToken = instrumentToken,
            previousWeekStartDate = previousWeekStart.toString(),
            entryWeekStartDate = entryWeekStart.toString(),
            previousWeekLow = previousLowCandle.low,
            previousWeekLowDate = previousLowCandle.candleDate.toString(),
            retestDate = retestDate?.toString(),
            retestLow = retestLow,
            retestGapTradingDays = retestGapTradingDays,
            entryPrice = roundTo2(entryPrice),
            targetPrice = roundTo2(entryPrice * (1.0 + targetPct / 100.0)),
            outcome = outcome,
            entryDate = entryDate?.toString(),
            exitDate = exitDate?.toString(),
            exitPrice = exitPrice?.let(::roundTo2),
            holdingTradingDays = holdingTradingDays,
            returnPct = returnPct?.let(::roundTo2),
        )
    }

    private fun findExit(
        candles: List<DailyCandle>,
        entryIndex: Int,
        lastExitIndex: Int,
        targetPrice: Double,
    ): Exit? {
        if (entryIndex + 1 > lastExitIndex) return null
        for (index in (entryIndex + 1)..lastExitIndex) {
            val candle = candles[index]
            if (candle.open >= targetPrice || candle.high >= targetPrice) {
                return Exit(candle, targetPrice, WeeklyLowAlignmentBacktestOutcomes.TARGET_HIT)
            }
        }
        return null
    }

    private fun tradingDayGap(indexByDate: Map<LocalDate, Int>, firstDate: LocalDate, secondDate: LocalDate): Int =
        requireNotNull(indexByDate[secondDate]) - requireNotNull(indexByDate[firstDate])

    private fun summarize(trades: List<WeeklyLowAlignmentBacktestTrade>): WeeklyLowAlignmentBacktestSummary {
        val returns = trades.mapNotNull(WeeklyLowAlignmentBacktestTrade::returnPct)
        return WeeklyLowAlignmentBacktestSummary(
            setupCount = trades.size,
            noRetestCount = trades.count { trade -> trade.outcome == WeeklyLowAlignmentBacktestOutcomes.NO_RETEST },
            tooSoonRetestCount = trades.count { trade -> trade.outcome == WeeklyLowAlignmentBacktestOutcomes.TOO_SOON_RETEST },
            filledTradeCount = returns.size,
            targetHitCount = trades.count { trade -> trade.outcome == WeeklyLowAlignmentBacktestOutcomes.TARGET_HIT },
            timeExitCount = trades.count { trade -> trade.outcome == WeeklyLowAlignmentBacktestOutcomes.TIME_EXIT },
            positionOpenSkipCount = trades.count { trade -> trade.outcome == WeeklyLowAlignmentBacktestOutcomes.POSITION_OPEN_SKIP },
            averageReturnPct = returns.takeIf(List<Double>::isNotEmpty)?.average()?.let(::roundTo2),
        )
    }

    private fun roundTo2(value: Double): Double = round(value * 100.0) / 100.0

    private data class Exit(
        val candle: DailyCandle,
        val price: Double,
        val outcome: String,
    )

    private companion object {
        const val ENTRY_PRICE_MULTIPLIER = 1.01
        const val RETEST_TOLERANCE_PCT = 1.0
        const val MINIMUM_RETEST_GAP_TRADING_DAYS = 5
    }
}
