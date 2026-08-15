package com.tradingtool.core.strategy.weeklylowretestbacktest

import com.tradingtool.core.candle.DailyCandle
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class WeeklyLowRetestBacktestEngine {
    internal fun run(
        member: WeeklyLowRetestMember,
        candles: List<DailyCandle>,
        testFrom: LocalDate,
        toDate: LocalDate,
        limitOffsetPct: Double,
        targetPct: Double,
    ): List<WeeklyLowRetestObservation> {
        val sortedCandles = candles
            .filter { candle -> candle.candleDate <= toDate }
            .sortedBy(DailyCandle::candleDate)
        val firstEntryIndex = sortedCandles.indexOfFirst { candle -> candle.candleDate >= testFrom }
        if (firstEntryIndex < LOOKBACK_SESSIONS) return emptyList()

        return (firstEntryIndex until sortedCandles.size)
            .mapNotNull { entryIndex ->
                buildObservation(member, sortedCandles, entryIndex, limitOffsetPct, targetPct)
            }
    }

    private fun buildObservation(
        member: WeeklyLowRetestMember,
        candles: List<DailyCandle>,
        entryIndex: Int,
        limitOffsetPct: Double,
        targetPct: Double,
    ): WeeklyLowRetestObservation? {
        val lookbackStartIndex = entryIndex - LOOKBACK_SESSIONS
        val lookback = candles.subList(lookbackStartIndex, entryIndex)
        val earlierCycle = lookback.subList(0, CYCLE_SESSIONS)
        val recentCycle = lookback.subList(CYCLE_SESSIONS, LOOKBACK_SESSIONS)
        val earlierCycleLowCandle = earlierCycle.minWith(compareBy<DailyCandle> { it.low }.thenBy { it.candleDate })
        val recentCycleLowCandle = recentCycle.minWith(compareBy<DailyCandle> { it.low }.thenBy { it.candleDate })
        val triggerCandle = recentCycle.maxWith(compareBy<DailyCandle> { it.high }.thenByDescending { it.candleDate })
        val sequence = cycleSequence(recentCycleLowCandle, triggerCandle) ?: return null
        val triggerMovePct = movePct(triggerCandle.high, recentCycleLowCandle.low)
        if (triggerMovePct + MOVE_COMPARISON_TOLERANCE < CONFIRMATION_MOVE_PCT) return null

        val anchorCandle = listOf(earlierCycleLowCandle, recentCycleLowCandle)
            .minWith(compareBy<DailyCandle> { it.low }.thenBy { it.candleDate })

        val orderEndIndex = entryIndex + HOLDING_SESSIONS - 1
        if (orderEndIndex >= candles.size) return null

        val orderWindow = candles.subList(entryIndex, orderEndIndex + 1)
        val limitPrice = roundTo2(anchorCandle.low * (1.0 + limitOffsetPct / 100.0))
        val orderWindowLowCandle = orderWindow.minBy(DailyCandle::low)
        val fillIndex = orderWindow.indexOfFirst { candle ->
            candle.low <= limitPrice + PRICE_COMPARISON_TOLERANCE
        }
        val fillCandle = fillIndex.takeUnless { it < 0 }?.let(orderWindow::get)
        val fillPrice = fillCandle?.let { candle ->
            if (candle.open <= limitPrice) candle.open else limitPrice
        }
        val targetPrice = roundTo2((fillCandle?.low ?: anchorCandle.low) * (1.0 + targetPct / 100.0))
        val peakStartIndex = if (fillIndex < 0) 0 else fillIndex
        val peakCandle = orderWindow.drop(peakStartIndex).maxBy(DailyCandle::high)
        val targetCandle = if (fillCandle == null || fillPrice == null) {
            null
        } else {
            orderWindow.drop(fillIndex).firstOrNull { candle ->
                candle.high >= targetPrice && canReachTargetAfterFill(candle, fillCandle)
            }
        }
        val exit = when {
            fillCandle == null || fillPrice == null -> null
            targetCandle != null -> Exit(targetCandle.candleDate, targetPrice, WeeklyLowRetestOutcomes.TARGET_HIT)
            else -> {
                val exitCandle = orderWindow.last()
                Exit(exitCandle.candleDate, exitCandle.close, WeeklyLowRetestOutcomes.FOURTH_SESSION_EXIT)
            }
        }
        val exitIndex = exit?.let { selectedExit ->
            orderWindow.indexOfFirst { candle -> candle.candleDate == selectedExit.date }
        }
        val holdingSessions = if (fillIndex >= 0 && exitIndex != null && exitIndex >= fillIndex) {
            exitIndex - fillIndex + 1
        } else {
            null
        }
        val fourthSessionCandle = orderWindow.getOrNull(FOURTH_SESSION_INDEX)
        val targetReachedInOrderWindow = peakCandle.high >= targetPrice
        val peakBasePrice = fillPrice ?: limitPrice

        return WeeklyLowRetestObservation(
            symbol = member.symbol,
            companyName = member.companyName,
            instrumentToken = member.instrumentToken,
            lookbackStartDate = lookback.first().candleDate.toString(),
            lookbackEndDate = lookback.last().candleDate.toString(),
            anchorDate = anchorCandle.candleDate.toString(),
            anchorLow = roundTo2(anchorCandle.low),
            anchorVolumeVs10DayAveragePct = volumeVs10DayAveragePct(candles, candles.indexOf(anchorCandle)),
            anchorCloseNearHighPct = closeNearHighPct(anchorCandle),
            recentCycleLowDate = recentCycleLowCandle.candleDate.toString(),
            recentCycleLow = roundTo2(recentCycleLowCandle.low),
            triggerDate = triggerCandle.candleDate.toString(),
            triggerHigh = roundTo2(triggerCandle.high),
            triggerMovePct = roundTo2(triggerMovePct),
            cycleSequence = sequence,
            limitOrderDate = orderWindow.first().candleDate.toString(),
            limitOrderExpiryDate = orderWindow.last().candleDate.toString(),
            limitPrice = limitPrice,
            orderWindowLowDate = orderWindowLowCandle.candleDate.toString(),
            orderWindowLow = roundTo2(orderWindowLowCandle.low),
            orderWindowLowVolumeVs10DayAveragePct = volumeVs10DayAveragePct(candles, candles.indexOf(orderWindowLowCandle)),
            orderWindowLowCloseNearHighPct = closeNearHighPct(orderWindowLowCandle),
            fillDate = fillCandle?.candleDate?.toString(),
            fillLow = fillCandle?.low?.let(::roundTo2),
            fillPrice = fillPrice?.let(::roundTo2),
            fillVolumeVs10DayAveragePct = fillCandle?.let { volumeVs10DayAveragePct(candles, candles.indexOf(it)) },
            fillCloseNearHighPct = fillCandle?.let(::closeNearHighPct),
            targetPrice = targetPrice,
            peakHighDate = peakCandle.candleDate.toString(),
            peakHigh = roundTo2(peakCandle.high),
            peakReturnPct = roundTo2(movePct(peakCandle.high, peakBasePrice)),
            fourthSessionCloseDate = fourthSessionCandle?.candleDate?.toString(),
            fourthSessionClose = fourthSessionCandle?.close?.let(::roundTo2),
            noFillFourthSessionPnlPct = if (fillCandle == null && fourthSessionCandle != null) {
                roundTo2(movePct(fourthSessionCandle.close, limitPrice))
            } else {
                null
            },
            targetReachedInOrderWindow = targetReachedInOrderWindow,
            exitDate = exit?.date?.toString(),
            exitPrice = exit?.price?.let(::roundTo2),
            outcome = exit?.outcome ?: WeeklyLowRetestOutcomes.NO_FILL,
            realizedReturnPct = if (exit == null || fillPrice == null) {
                null
            } else {
                roundTo2(movePct(exit.price, fillPrice))
            },
            holdingSessions = holdingSessions,
        )
    }

    private fun cycleSequence(anchorCandle: DailyCandle, triggerCandle: DailyCandle): String? = when {
        anchorCandle.candleDate < triggerCandle.candleDate -> CYCLE_LOW_BEFORE_HIGH
        anchorCandle.candleDate > triggerCandle.candleDate -> null
        triggerCandle.close > triggerCandle.open -> CYCLE_SAME_DAY_GREEN
        else -> null
    }

    private fun canReachTargetAfterFill(candle: DailyCandle, fillCandle: DailyCandle): Boolean {
        if (candle.candleDate > fillCandle.candleDate) return true
        if (candle.candleDate < fillCandle.candleDate) return false
        return candle.close > candle.open
    }

    private fun volumeVs10DayAveragePct(candles: List<DailyCandle>, candleIndex: Int): Double? {
        if (candleIndex < TEN_SESSION_VOLUME_LOOKBACK) return null
        val averageVolume = candles
            .subList(candleIndex - TEN_SESSION_VOLUME_LOOKBACK, candleIndex)
            .map(DailyCandle::volume)
            .average()
        if (averageVolume == 0.0) return null
        return roundTo2(candles[candleIndex].volume / averageVolume * 100.0)
    }

    private fun closeNearHighPct(candle: DailyCandle): Double? {
        val range = candle.high - candle.low
        if (range <= 0.0) return null
        return roundTo2((candle.close - candle.low) / range * 100.0)
    }

    private fun movePct(value: Double, base: Double): Double = ((value / base) - 1.0) * 100.0

    private fun roundTo2(value: Double): Double = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()

    private data class Exit(
        val date: LocalDate,
        val price: Double,
        val outcome: String,
    )

    private companion object {
        const val LOOKBACK_SESSIONS = 10
        const val CYCLE_SESSIONS = 5
        const val HOLDING_SESSIONS = 4
        const val FOURTH_SESSION_INDEX = HOLDING_SESSIONS - 1
        const val TEN_SESSION_VOLUME_LOOKBACK = 10
        const val CONFIRMATION_MOVE_PCT = 5.0
        const val MOVE_COMPARISON_TOLERANCE = 1.0e-8
        const val PRICE_COMPARISON_TOLERANCE = 1.0e-8
        const val CYCLE_LOW_BEFORE_HIGH = "LOW_BEFORE_HIGH"
        const val CYCLE_SAME_DAY_GREEN = "SAME_DAY_GREEN_LOW_THEN_HIGH"
    }
}
