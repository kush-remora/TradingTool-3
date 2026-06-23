package com.tradingtool.core.volumeshocker.groww

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import java.time.LocalDate

internal object GrowwVolumeShockerDashboardAnalyzer {
    private const val PRE_EVENT_WINDOW_DAYS = 10
    private const val DETAIL_DAYS_BEFORE_EVENT = 5
    private const val DETAIL_DAYS_AFTER_EVENT = 10
    private const val ACCUMULATION_JUMP_RATIO = 2.0
    private const val QUIET_PRICE_MOVE_MIN_PCT = -2.0
    private const val QUIET_PRICE_MOVE_MAX_PCT = 2.0

    fun calculateAppearanceCount(
        lookbackDates: List<LocalDate>,
        appearedDates: Set<LocalDate>,
    ): Int = lookbackDates.count { date -> appearedDates.contains(date) }

    fun calculateStreakLength(
        lookbackDatesDescending: List<LocalDate>,
        appearedDates: Set<LocalDate>,
    ): Int {
        var streak = 0
        for (date in lookbackDatesDescending) {
            if (!appearedDates.contains(date)) {
                break
            }
            streak += 1
        }
        return streak
    }

    fun calculateSma200Price(candles: List<DailyCandle>, asOfDate: LocalDate): Double? {
        val closes = candles
            .filter { candle -> !candle.candleDate.isAfter(asOfDate) }
            .takeLast(200)
            .map { candle -> candle.close }
        if (closes.size < 200) {
            return null
        }
        return closes.average()
    }

    fun calculateDistancePct(price: Double, referencePrice: Double?): Double? {
        if (referencePrice == null || referencePrice == 0.0) {
            return null
        }
        return ((price - referencePrice) / referencePrice) * 100.0
    }

    fun calculatePreEventMetrics(
        candles: List<DailyCandle>,
        deliveries: List<StockDeliveryDaily>,
        eventDate: LocalDate,
    ): PreEventMetrics {
        val sortedCandles = candles.sortedBy { candle -> candle.candleDate }
        val preEventWindow = sortedCandles
            .filter { candle -> candle.candleDate.isBefore(eventDate) }
            .takeLast(PRE_EVENT_WINDOW_DAYS)
        if (preEventWindow.isEmpty()) {
            return PreEventMetrics()
        }

        val deliveriesByDate = deliveries.associateBy { delivery -> delivery.tradingDate }
        val candidateDays = preEventWindow.mapNotNull { candle ->
            val delivery = deliveriesByDate[candle.candleDate] ?: return@mapNotNull null
            val deliveryQty = delivery.delivQty ?: return@mapNotNull null
            val candleIndex = sortedCandles.indexOfFirst { candidate -> candidate.candleDate == candle.candleDate }
            PreEventCandidateDay(
                date = candle.candleDate,
                deliveryVolume = deliveryQty,
                dailyChangePct = calculateDailyChangePct(candle, sortedCandles, candleIndex),
            )
        }
        if (candidateDays.isEmpty()) {
            return PreEventMetrics()
        }

        val maxDay = candidateDays.maxByOrNull { candidate -> candidate.deliveryVolume }
            ?: return PreEventMetrics()
        val baselineCandidates = candidateDays
            .filter { candidate -> candidate.date != maxDay.date }
            .map { candidate -> candidate.deliveryVolume.toDouble() }
        val baselineAverage = baselineCandidates.averageOrNull()

        val hasQuietPrice = maxDay.dailyChangePct?.let { pct ->
            pct in QUIET_PRICE_MOVE_MIN_PCT..QUIET_PRICE_MOVE_MAX_PCT
        } ?: false

        val hasDeliveryJump = baselineAverage?.let { average ->
            average > 0.0 && maxDay.deliveryVolume >= average * ACCUMULATION_JUMP_RATIO
        } ?: false

        return PreEventMetrics(
            maxDeliveryVolume = maxDay.deliveryVolume,
            hasAccumulationHint = hasQuietPrice && hasDeliveryJump,
        )
    }

    fun buildDetailWindow(
        candles: List<DailyCandle>,
        deliveries: List<StockDeliveryDaily>,
        eventDate: LocalDate,
    ): List<GrowwVolumeShockerDashboardDetailDay> {
        val sortedCandles = candles.sortedBy { candle -> candle.candleDate }
        val eventIndex = sortedCandles.indexOfFirst { candle -> candle.candleDate == eventDate }
        if (eventIndex == -1) {
            return emptyList()
        }

        val startIndex = (eventIndex - DETAIL_DAYS_BEFORE_EVENT).coerceAtLeast(0)
        val endIndex = (eventIndex + DETAIL_DAYS_AFTER_EVENT).coerceAtMost(sortedCandles.lastIndex)
        val deliveriesByDate = deliveries.associateBy { delivery -> delivery.tradingDate }

        return sortedCandles
            .subList(startIndex, endIndex + 1)
            .mapIndexedNotNull { index, candle ->
                val delivery = deliveriesByDate[candle.candleDate] ?: return@mapIndexedNotNull null
                GrowwVolumeShockerDashboardDetailDay(
                    date = candle.candleDate.toString(),
                    open = candle.open,
                    close = candle.close,
                    volume = candle.volume,
                    delivery_volume = delivery.delivQty,
                    delivery_pct = delivery.delivPer,
                    daily_change_pct = calculateDailyChangePct(candle, sortedCandles, startIndex + index),
                    is_event_day = candle.candleDate == eventDate,
                )
            }
    }

    private fun calculateDailyChangePct(
        candle: DailyCandle,
        sortedCandles: List<DailyCandle>,
        index: Int,
    ): Double? {
        if (index <= 0) {
            return null
        }
        val previousClose = sortedCandles[index - 1].close
        if (previousClose == 0.0) {
            return null
        }
        return ((candle.close - previousClose) / previousClose) * 100.0
    }

    private fun List<Double>.averageOrNull(): Double? {
        return if (isEmpty()) null else average()
    }
}

internal data class PreEventMetrics(
    val maxDeliveryVolume: Long? = null,
    val hasAccumulationHint: Boolean = false,
)

private data class PreEventCandidateDay(
    val date: LocalDate,
    val deliveryVolume: Long,
    val dailyChangePct: Double?,
)
