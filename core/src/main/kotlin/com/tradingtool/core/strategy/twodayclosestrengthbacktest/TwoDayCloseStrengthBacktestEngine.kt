package com.tradingtool.core.strategy.twodayclosestrengthbacktest

import com.tradingtool.core.candle.DailyCandle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.round

class TwoDayCloseStrengthBacktestEngine {
    internal fun run(
        member: TwoDayCloseStrengthMember,
        candles: List<DailyCandle>,
        testFrom: LocalDate,
        toDate: LocalDate,
    ): List<TwoDayCloseStrengthObservation> {
        val currentWeekStart = toDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val firstSignalWeekStart = testFrom.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
        val weeks = candles
            .filter { candle -> candle.candleDate <= toDate }
            .groupBy { candle -> weekStart(candle.candleDate) }
            .mapValues { (_, weekCandles) -> weekCandles.sortedBy(DailyCandle::candleDate) }

        return weeks
            .filterKeys { weekStart -> weekStart >= firstSignalWeekStart && weekStart < currentWeekStart }
            .toSortedMap()
            .mapNotNull { (signalWeekStart, signalWeek) ->
                buildObservation(member, signalWeekStart, signalWeek, weeks)
            }
    }

    private fun buildObservation(
        member: TwoDayCloseStrengthMember,
        signalWeekStart: LocalDate,
        signalWeek: List<DailyCandle>,
        weeks: Map<LocalDate, List<DailyCandle>>,
    ): TwoDayCloseStrengthObservation? {
        if (signalWeek.size < REQUIRED_PATTERN_SESSIONS) return null
        val patternCandles = signalWeek.take(REQUIRED_PATTERN_SESSIONS)
        val closePositions = patternCandles.map(::closePositionPct)
        val firstThreeAreWeak = closePositions.take(WEAK_SESSIONS).all { value -> value < CLOSE_POSITION_THRESHOLD_PCT }
        val lastTwoAreStrong = closePositions.drop(WEAK_SESSIONS).all { value -> value >= CLOSE_POSITION_THRESHOLD_PCT }
        if (!firstThreeAreWeak || !lastTwoAreStrong) return null

        val entryWeekStart = signalWeekStart.plusWeeks(1)
        val entryWeek = weeks[entryWeekStart] ?: return null
        val entryCandle = entryWeek.firstOrNull() ?: return null
        val targetPrice = entryCandle.open * (1.0 + TARGET_PCT / 100.0)
        val targetWindowEnd = entryWeekStart.plusDays(3)
        val targetCandle = entryWeek.firstOrNull { candle ->
            candle.candleDate < targetWindowEnd && candle.high >= targetPrice
        }
        val exit = if (targetCandle != null) {
            Exit(
                date = targetCandle.candleDate,
                price = targetPrice,
                reason = TwoDayCloseStrengthExitReasons.TARGET_HIT,
            )
        } else {
            val thursdayCloseCandle = entryWeek.firstOrNull { candle -> candle.candleDate >= targetWindowEnd }
                ?: return null
            Exit(
                date = thursdayCloseCandle.candleDate,
                price = thursdayCloseCandle.close,
                reason = TwoDayCloseStrengthExitReasons.THURSDAY_CLOSE_EXIT,
            )
        }

        return TwoDayCloseStrengthObservation(
            symbol = member.symbol,
            companyName = member.companyName,
            instrumentToken = member.instrumentToken,
            patternStartDate = patternCandles.first().candleDate.toString(),
            patternEndDate = patternCandles.last().candleDate.toString(),
            patternClosePositionPct = closePositions.map(::roundTo2),
            entryDate = entryCandle.candleDate.toString(),
            entryPrice = roundTo2(entryCandle.open),
            targetPrice = roundTo2(targetPrice),
            exitDate = exit.date.toString(),
            exitPrice = roundTo2(exit.price),
            exitReason = exit.reason,
            realizedReturnPct = roundTo2(movePct(exit.price, entryCandle.open)),
        )
    }

    private fun closePositionPct(candle: DailyCandle): Double {
        val range = candle.high - candle.low
        return if (range <= 0.0) 0.0 else ((candle.close - candle.low) / range) * 100.0
    }

    private fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun movePct(exitPrice: Double, entryPrice: Double): Double = ((exitPrice / entryPrice) - 1.0) * 100.0

    private fun roundTo2(value: Double): Double = round(value * 100.0) / 100.0

    private data class Exit(
        val date: LocalDate,
        val price: Double,
        val reason: String,
    )

    private companion object {
        const val CLOSE_POSITION_THRESHOLD_PCT = 80.0
        const val TARGET_PCT = 5.0
        const val REQUIRED_PATTERN_SESSIONS = 5
        const val WEAK_SESSIONS = 3
    }
}
