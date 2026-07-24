package com.tradingtool.core.strategy.weeklyfloor

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate
import java.time.temporal.WeekFields

class WeeklyFloorReboundEngine {

    fun run(symbol: String, candles: List<DailyCandle>, backtestTradingDays: Int): WeeklyFloorReboundReport {
        val sortedCandles = candles.sortedBy(DailyCandle::candleDate)
        require(sortedCandles.isNotEmpty()) { "No daily candle data available for $symbol." }

        val testStartIndex = (sortedCandles.size - backtestTradingDays).coerceAtLeast(0)
        val setupIndexes = firstTradingDayIndexesByWeek(sortedCandles, testStartIndex)
        val rows = setupIndexes.map { index -> evaluateWeek(sortedCandles, index) }
        val testCandles = sortedCandles.drop(testStartIndex)

        return WeeklyFloorReboundReport(
            symbol = symbol,
            testedFromDate = testCandles.first().candleDate.toString(),
            testedToDate = testCandles.last().candleDate.toString(),
            summary = summarize(rows),
            trades = rows,
        )
    }

    private fun firstTradingDayIndexesByWeek(candles: List<DailyCandle>, startIndex: Int): List<Int> =
        candles.indices.filter { index ->
            index >= startIndex &&
                (index == 0 || weekKey(candles[index - 1].candleDate) != weekKey(candles[index].candleDate)) &&
                isCompletedWeek(candles, index)
        }

    private fun isCompletedWeek(candles: List<DailyCandle>, entryIndex: Int): Boolean {
        val entryWeek = weekKey(candles[entryIndex].candleDate)
        val hasNextWeek = candles.drop(entryIndex + 1).any { candle -> weekKey(candle.candleDate) != entryWeek }
        val includesFriday = candles.drop(entryIndex).any { candle -> candle.candleDate.dayOfWeek == java.time.DayOfWeek.FRIDAY }
        return hasNextWeek || includesFriday
    }

    private fun evaluateWeek(candles: List<DailyCandle>, entryIndex: Int): WeeklyFloorReboundRow {
        val entryCandle = candles[entryIndex]
        if (entryIndex < REQUIRED_CONTEXT_TRADING_DAYS) {
            return ineligibleRow(entryCandle.candleDate, "INSUFFICIENT_HISTORY")
        }

        val completedWeeks = candles.subList(0, entryIndex)
            .groupBy { candle -> weekKey(candle.candleDate) }
            .values
            .toList()
            .takeLast(3)
        if (completedWeeks.size < 3) {
            return ineligibleRow(entryCandle.candleDate, "INSUFFICIENT_COMPLETED_WEEKS")
        }

        val weeklyLows = completedWeeks.map { week -> week.minOf(DailyCandle::low) }
        val baseFloor = weeklyLows.min()
        val floorTightnessPct = ((weeklyLows.max() / baseFloor) - 1.0) * 100.0
        if (floorTightnessPct > MAX_FLOOR_TIGHTNESS_PCT) {
            return ineligibleRow(entryCandle.candleDate, "FLOOR_TOO_WIDE", baseFloor)
        }

        val priorPeak = candles.subList(entryIndex - PRIOR_PEAK_LOOKBACK_DAYS, entryIndex - PRIOR_PEAK_EXCLUSION_DAYS)
            .maxOf(DailyCandle::high)
        if (priorPeak < baseFloor * PRIOR_PEAK_FACTOR) {
            return ineligibleRow(entryCandle.candleDate, "NO_PRIOR_DECLINE", baseFloor)
        }

        val priorClose = candles[entryIndex - 1].close
        val fiftyTwoWeekHigh = candles.subList(entryIndex - FIFTY_TWO_WEEK_LOOKBACK_DAYS, entryIndex)
            .maxOf(DailyCandle::high)
        if (priorClose > fiftyTwoWeekHigh * MAX_CLOSE_TO_FIFTY_TWO_WEEK_HIGH_FACTOR) {
            return ineligibleRow(entryCandle.candleDate, "TOO_CLOSE_TO_52_WEEK_HIGH", baseFloor)
        }

        val triggerPrice = baseFloor * ENTRY_TRIGGER_FACTOR
        if (entryCandle.high < triggerPrice) {
            return WeeklyFloorReboundRow(
                setupDate = entryCandle.candleDate.toString(),
                outcome = OUTCOME_NO_ENTRY,
                eligibilityReason = null,
                baseFloor = baseFloor,
                entryDate = null,
                entryPrice = null,
                stopPrice = null,
                targetPrice = null,
                exitDate = null,
                exitPrice = null,
                returnPct = null,
                gapEntry = false,
                gapStop = false,
                exitWasAmbiguous = false,
            )
        }

        val entryPrice = maxOf(entryCandle.open, triggerPrice)
        val stopPrice = baseFloor * STOP_FACTOR
        val targetPrice = entryPrice * TARGET_FACTOR
        val exit = findExit(candles, entryIndex, stopPrice, targetPrice)
        val returnPct = ((exit.price / entryPrice) - 1.0) * 100.0

        return WeeklyFloorReboundRow(
            setupDate = entryCandle.candleDate.toString(),
            outcome = exit.outcome,
            eligibilityReason = null,
            baseFloor = baseFloor,
            entryDate = entryCandle.candleDate.toString(),
            entryPrice = entryPrice,
            stopPrice = stopPrice,
            targetPrice = targetPrice,
            exitDate = exit.date.toString(),
            exitPrice = exit.price,
            returnPct = returnPct,
            gapEntry = entryCandle.open > triggerPrice,
            gapStop = exit.gapStop,
            exitWasAmbiguous = exit.ambiguous,
        )
    }

    private fun findExit(candles: List<DailyCandle>, entryIndex: Int, stopPrice: Double, targetPrice: Double): TradeExit {
        val entryWeek = weekKey(candles[entryIndex].candleDate)
        val exitCandles = candles.drop(entryIndex + 1).takeWhile { candle -> weekKey(candle.candleDate) == entryWeek }
        for (candle in exitCandles) {
            if (candle.open <= stopPrice) {
                return TradeExit(candle.candleDate, candle.open, OUTCOME_STOP_LOSS, gapStop = true)
            }
            val hitStop = candle.low <= stopPrice
            val hitTarget = candle.high >= targetPrice
            if (hitStop && hitTarget) {
                return TradeExit(candle.candleDate, stopPrice, OUTCOME_STOP_LOSS, ambiguous = true)
            }
            if (hitStop) {
                return TradeExit(candle.candleDate, stopPrice, OUTCOME_STOP_LOSS)
            }
            if (hitTarget) {
                return TradeExit(candle.candleDate, targetPrice, OUTCOME_TARGET_HIT)
            }
        }

        val fridayExitCandle = exitCandles.lastOrNull() ?: candles[entryIndex]
        return TradeExit(fridayExitCandle.candleDate, fridayExitCandle.close, OUTCOME_FRIDAY_EXIT)
    }

    private fun ineligibleRow(date: LocalDate, reason: String, baseFloor: Double? = null): WeeklyFloorReboundRow =
        WeeklyFloorReboundRow(
            setupDate = date.toString(),
            outcome = OUTCOME_NOT_ELIGIBLE,
            eligibilityReason = reason,
            baseFloor = baseFloor,
            entryDate = null,
            entryPrice = null,
            stopPrice = null,
            targetPrice = null,
            exitDate = null,
            exitPrice = null,
            returnPct = null,
            gapEntry = false,
            gapStop = false,
            exitWasAmbiguous = false,
        )

    private fun summarize(rows: List<WeeklyFloorReboundRow>): WeeklyFloorReboundSummary {
        val eligibleRows = rows.filter { row -> row.outcome != OUTCOME_NOT_ELIGIBLE }
        val enteredRows = rows.filter { row -> row.entryPrice != null }
        val returns = enteredRows.mapNotNull(WeeklyFloorReboundRow::returnPct)
        val positiveReturns = returns.filter { value -> value > 0.0 }
        val negativeReturns = returns.filter { value -> value < 0.0 }

        return WeeklyFloorReboundSummary(
            reviewedWeeks = rows.size,
            eligibleSetups = eligibleRows.size,
            filledTrades = enteredRows.size,
            noEntryCount = rows.count { row -> row.outcome == OUTCOME_NO_ENTRY },
            targetHitCount = rows.count { row -> row.outcome == OUTCOME_TARGET_HIT },
            stopLossCount = rows.count { row -> row.outcome == OUTCOME_STOP_LOSS },
            fridayExitCount = rows.count { row -> row.outcome == OUTCOME_FRIDAY_EXIT },
            winRatePct = returns.takeIf { values -> values.isNotEmpty() }?.let { values -> positiveReturns.size.toDouble() / values.size * 100.0 },
            averageReturnPct = returns.takeIf { values -> values.isNotEmpty() }?.average(),
            expectancyPct = returns.takeIf { values -> values.isNotEmpty() }?.average(),
            profitFactor = profitFactor(positiveReturns, negativeReturns),
            maxDrawdownPct = maxDrawdownPct(returns),
        )
    }

    private fun profitFactor(positiveReturns: List<Double>, negativeReturns: List<Double>): Double? {
        val grossProfit = positiveReturns.sum()
        val grossLoss = negativeReturns.sumOf { value -> -value }
        return when {
            grossProfit == 0.0 && grossLoss == 0.0 -> null
            grossLoss == 0.0 -> null
            else -> grossProfit / grossLoss
        }
    }

    private fun maxDrawdownPct(returns: List<Double>): Double? {
        if (returns.isEmpty()) return null
        var equity = 1.0
        var peakEquity = 1.0
        var maximumDrawdown = 0.0
        returns.forEach { returnPct ->
            equity *= 1.0 + (returnPct / 100.0)
            peakEquity = maxOf(peakEquity, equity)
            maximumDrawdown = minOf(maximumDrawdown, ((equity / peakEquity) - 1.0) * 100.0)
        }
        return maximumDrawdown
    }

    private fun weekKey(date: LocalDate): Pair<Int, Int> =
        date.get(WeekFields.ISO.weekBasedYear()) to date.get(WeekFields.ISO.weekOfWeekBasedYear())

    private data class TradeExit(
        val date: LocalDate,
        val price: Double,
        val outcome: String,
        val gapStop: Boolean = false,
        val ambiguous: Boolean = false,
    )

    companion object {
        const val OUTCOME_NOT_ELIGIBLE = "NOT_ELIGIBLE"
        const val OUTCOME_NO_ENTRY = "NO_ENTRY"
        const val OUTCOME_TARGET_HIT = "TARGET_HIT"
        const val OUTCOME_STOP_LOSS = "STOP_LOSS"
        const val OUTCOME_FRIDAY_EXIT = "FRIDAY_EXIT"

        private const val REQUIRED_CONTEXT_TRADING_DAYS = 252
        private const val FIFTY_TWO_WEEK_LOOKBACK_DAYS = 252
        private const val PRIOR_PEAK_LOOKBACK_DAYS = 60
        private const val PRIOR_PEAK_EXCLUSION_DAYS = 15
        private const val MAX_FLOOR_TIGHTNESS_PCT = 2.0
        private const val PRIOR_PEAK_FACTOR = 1.10
        private const val MAX_CLOSE_TO_FIFTY_TWO_WEEK_HIGH_FACTOR = 0.90
        private const val ENTRY_TRIGGER_FACTOR = 1.01
        private const val STOP_FACTOR = 0.995
        private const val TARGET_FACTOR = 1.05
    }
}
