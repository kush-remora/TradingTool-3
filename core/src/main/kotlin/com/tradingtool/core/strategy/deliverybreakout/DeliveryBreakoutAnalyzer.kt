package com.tradingtool.core.strategy.deliverybreakout

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import com.tradingtool.core.technical.roundTo2
import java.time.LocalDate

internal object DeliveryBreakoutAnalyzer {

    fun buildEvents(
        symbol: String,
        instrumentToken: Long,
        history: List<StockDeliveryDaily>,
        evaluationDates: List<LocalDate>,
        baselineSessions: Int,
        shockMultiplier: Double,
    ): List<DeliveryBreakoutEvent> {
        val rowsByDate = history.associateBy { row -> row.tradingDate }

        return evaluationDates.mapNotNull { eventDate ->
            val current = rowsByDate[eventDate] ?: return@mapNotNull null
            val previousRows = history
                .filter { row -> row.tradingDate.isBefore(eventDate) }
                .sortedBy { row -> row.tradingDate }
                .takeLast(baselineSessions)

            val averageVolume = averageOf(
                previousRows.mapNotNull { row -> row.ttlTrdQnty?.takeIf { value -> value > 0L } },
                baselineSessions,
            )
            val averageDeliveryQuantity = averageOf(
                previousRows.mapNotNull { row -> row.delivQty?.takeIf { value -> value > 0L } },
                baselineSessions,
            )
            val volumeRatio = ratio(current.ttlTrdQnty, averageVolume)
            val deliveryRatio = ratio(current.delivQty, averageDeliveryQuantity)
            val volumeShock = volumeRatio?.let { ratio -> ratio >= shockMultiplier } == true
            val deliveryShock = deliveryRatio?.let { ratio -> ratio >= shockMultiplier } == true

            if (!volumeShock && !deliveryShock) {
                return@mapNotNull null
            }

            DeliveryBreakoutEvent(
                instrumentToken = instrumentToken,
                symbol = symbol,
                eventDate = eventDate.toString(),
                eventType = when {
                    volumeShock && deliveryShock -> "BOTH"
                    deliveryShock -> "DELIVERY_ONLY"
                    else -> "VOLUME_ONLY"
                },
                volume = current.ttlTrdQnty,
                deliveryQuantity = current.delivQty,
                deliveryPercentage = current.delivPer?.roundTo2(),
                averageVolume10d = averageVolume,
                averageDeliveryQuantity10d = averageDeliveryQuantity,
                volumeRatio = volumeRatio,
                deliveryRatio = deliveryRatio,
            )
        }
    }

    private fun averageOf(values: List<Long>, requiredSize: Int): Double? {
        if (values.size < requiredSize) {
            return null
        }
        return values.average()
    }

    private fun ratio(value: Long?, average: Double?): Double? {
        if (value == null || average == null || average <= 0.0) {
            return null
        }
        return (value.toDouble() / average).roundTo2()
    }

    fun calculatePctChange(candles: List<DailyCandle>, tradeDate: LocalDate): Double? {
        val candleIndex = candles.indexOfFirst { candle -> candle.candleDate == tradeDate }
        if (candleIndex <= 0) {
            return null
        }

        val previousClose = candles[candleIndex - 1].close
        if (previousClose <= 0.0) {
            return null
        }

        return (((candles[candleIndex].close - previousClose) / previousClose) * 100.0).roundTo2()
    }

}
