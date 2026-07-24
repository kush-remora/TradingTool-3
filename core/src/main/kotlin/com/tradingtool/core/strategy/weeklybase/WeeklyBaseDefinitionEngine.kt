package com.tradingtool.core.strategy.weeklybase

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate
import java.time.temporal.WeekFields

class WeeklyBaseDefinitionEngine {
    fun run(
        symbol: String,
        candles: List<DailyCandle>,
        config: WeeklyBaseDefinitionConfig,
        backtestTradingDays: Int = 200,
    ): WeeklyBaseDefinitionReport {
        val sortedCandles = candles.sortedBy(DailyCandle::candleDate)
        require(sortedCandles.isNotEmpty()) { "No daily candle data available for $symbol." }
        val testStartIndex = (sortedCandles.size - backtestTradingDays).coerceAtLeast(0)
        val rows = sortedCandles.indices
            .filter { index -> index >= testStartIndex }
            .mapNotNull { index -> evaluateDay(sortedCandles, index, config) }

        return WeeklyBaseDefinitionReport(
            symbol = symbol,
            testedFromDate = sortedCandles[testStartIndex].candleDate.toString(),
            testedToDate = sortedCandles.last().candleDate.toString(),
            validBaseCount = rows.count(WeeklyBaseDefinitionRow::isValid),
            rows = rows,
        )
    }

    private fun evaluateDay(
        candles: List<DailyCandle>,
        index: Int,
        config: WeeklyBaseDefinitionConfig,
    ): WeeklyBaseDefinitionRow? {
        if (index + 1 < config.smaWindowTradingDays) return null
        val evaluationCandle = candles[index]
        val evaluationWeek = weekKey(evaluationCandle.candleDate)
        val completedWeeks = candles.take(index)
            .filter { candle -> weekKey(candle.candleDate) != evaluationWeek }
            .groupBy { candle -> weekKey(candle.candleDate) }
            .values
            .toList()
            .takeLast(REQUIRED_WEEKS)
        if (completedWeeks.size < REQUIRED_WEEKS) return null

        val weekLows = completedWeeks.map { week -> WeekLow(week.minOf(DailyCandle::candleDate), week.minOf(DailyCandle::low)) }
        val floor = weekLows.minOf(WeekLow::low)
        val ceiling = weekLows.maxOf(WeekLow::low)
        val widthPct = ((ceiling - floor) / floor) * 100
        val smaCandles = candles.subList(index + 1 - config.smaWindowTradingDays, index + 1)
        val sma200 = smaCandles.map(DailyCandle::close).average()
        val distanceFromSma200Pct = ((evaluationCandle.close / sma200) - 1) * 100
        val isWithinSma200Range = distanceFromSma200Pct in config.minimumSmaDistancePct..config.maximumSmaDistancePct
        val isWithinZoneWidth = widthPct <= config.maximumZoneWidthPct
        return WeeklyBaseDefinitionRow(
            evaluationDate = evaluationCandle.candleDate.toString(),
            firstWeekStartDate = weekLows[0].startDate.toString(),
            firstWeekLow = weekLows[0].low,
            secondWeekStartDate = weekLows[1].startDate.toString(),
            secondWeekLow = weekLows[1].low,
            thirdWeekStartDate = weekLows[2].startDate.toString(),
            thirdWeekLow = weekLows[2].low,
            zoneFloor = floor,
            zoneCeiling = ceiling,
            zoneWidthPct = widthPct,
            sma200 = sma200,
            distanceFromSma200Pct = distanceFromSma200Pct,
            isWithinSma200Range = isWithinSma200Range,
            isValid = isWithinZoneWidth && isWithinSma200Range,
            validityReason = when {
                !isWithinZoneWidth -> "TOO_WIDE"
                !isWithinSma200Range -> "OUTSIDE_SMA_RANGE"
                else -> "VALID"
            },
        )
    }

    private fun weekKey(date: LocalDate): Pair<Int, Int> =
        date.get(WeekFields.ISO.weekBasedYear()) to date.get(WeekFields.ISO.weekOfWeekBasedYear())

    private data class WeekLow(val startDate: LocalDate, val low: Double)

    private companion object {
        const val REQUIRED_WEEKS = 3
    }
}
