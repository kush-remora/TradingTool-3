package com.tradingtool.core.strategy.fridaystrengthbacktest

import com.tradingtool.core.candle.DailyCandle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.round

class FridayCloseStrengthBacktestEngine {
    internal fun run(
        member: FridayCloseStrengthMember,
        candles: List<DailyCandle>,
        testFrom: LocalDate,
        toDate: LocalDate,
    ): List<FridayCloseStrengthObservation> {
        val sortedCandles = candles.sortedBy(DailyCandle::candleDate)
        val candlesByDate = sortedCandles.associateBy(DailyCandle::candleDate)
        val currentWeekStart = toDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        return sortedCandles
            .filter { candle -> candle.candleDate >= testFrom && candle.candleDate.dayOfWeek == DayOfWeek.FRIDAY }
            .mapNotNull { friday ->
                val thursday = candlesByDate[friday.candleDate.minusDays(1)] ?: return@mapNotNull null
                if (!qualifies(friday, thursday)) return@mapNotNull null

                val followingWeekStart = friday.candleDate.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                if (followingWeekStart >= currentWeekStart) return@mapNotNull null

                val followingWeek = sortedCandles.filter { candle ->
                    candle.candleDate >= followingWeekStart && candle.candleDate < followingWeekStart.plusWeeks(1)
                }
                val entryCandle = followingWeek.firstOrNull() ?: return@mapNotNull null
                if (entryCandle.open <= 0.0) return@mapNotNull null

                val highCandle = followingWeek.maxByOrNull(DailyCandle::high) ?: return@mapNotNull null
                val entryPrice = entryCandle.open
                FridayCloseStrengthObservation(
                    symbol = member.symbol,
                    companyName = member.companyName,
                    instrumentToken = member.instrumentToken,
                    signalDate = friday.candleDate.toString(),
                    thursdayClose = roundTo2(thursday.close),
                    fridayHigh = roundTo2(friday.high),
                    fridayLow = roundTo2(friday.low),
                    fridayClose = roundTo2(friday.close),
                    fridayClosePositionPct = roundTo2(closePositionPct(friday)),
                    fridayMovePct = roundTo2(movePct(friday.close, thursday.close)),
                    entryDate = entryCandle.candleDate.toString(),
                    entryPrice = roundTo2(entryPrice),
                    followingWeekHighDate = highCandle.candleDate.toString(),
                    followingWeekHigh = roundTo2(highCandle.high),
                    maximumUpsidePct = roundTo2(movePct(highCandle.high, entryPrice)),
                )
            }
    }

    private fun qualifies(friday: DailyCandle, thursday: DailyCandle): Boolean =
        thursday.close > 0.0 &&
            friday.high > friday.low &&
            closePositionPct(friday) >= CLOSE_POSITION_THRESHOLD_PCT &&
            movePct(friday.close, thursday.close) > FRIDAY_MOVE_THRESHOLD_PCT

    private fun closePositionPct(candle: DailyCandle): Double =
        ((candle.close - candle.low) / (candle.high - candle.low)) * 100.0

    private fun movePct(current: Double, previous: Double): Double =
        ((current / previous) - 1.0) * 100.0

    private fun roundTo2(value: Double): Double = round(value * 100.0) / 100.0

    private companion object {
        const val CLOSE_POSITION_THRESHOLD_PCT = 70.0
        const val FRIDAY_MOVE_THRESHOLD_PCT = 2.0
    }
}
