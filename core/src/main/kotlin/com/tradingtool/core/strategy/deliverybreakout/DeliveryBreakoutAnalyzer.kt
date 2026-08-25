package com.tradingtool.core.strategy.deliverybreakout

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import com.tradingtool.core.technical.roundTo2
import java.time.LocalDate

internal object DeliveryBreakoutAnalyzer {
    private const val ACCUMULATION_LOOKBACK_SESSIONS = 20
    private const val ACCUMULATION_MIN_QUIET_DAYS = 10
    private const val TREND_LOOKBACK_SESSIONS = 10
    private const val QUIET_DAY_RANGE_MAX_PCT = 2.0
    private const val ACCUMULATION_RANGE_MAX_PCT = 10.0

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

    fun calculatePreEventAccumulationMetrics(
        candles: List<DailyCandle>,
        eventDate: LocalDate,
    ): PreEventAccumulationMetrics {
        val sortedPreEventCandles = candles
            .sortedBy { candle -> candle.candleDate }
            .filter { candle -> candle.candleDate.isBefore(eventDate) }
        val accumulationCandles = sortedPreEventCandles.takeLast(ACCUMULATION_LOOKBACK_SESSIONS)
        val trendCandles = sortedPreEventCandles.takeLast(TREND_LOOKBACK_SESSIONS)

        val lowestLow = accumulationCandles.minOfOrNull { candle -> candle.low }
        val highestHigh = accumulationCandles.maxOfOrNull { candle -> candle.high }
        val rawRangePct = if (lowestLow != null && lowestLow > 0.0 && highestHigh != null) {
            ((highestHigh - lowestLow) / lowestLow) * 100.0
        } else {
            null
        }
        val rangePct = rawRangePct?.roundTo2()
        val quietDays = accumulationCandles.count { candle ->
            candle.low > 0.0 && (((candle.high - candle.low) / candle.low) * 100.0) <= QUIET_DAY_RANGE_MAX_PCT
        }
        val firstTrendClose = trendCandles.firstOrNull()?.close
        val lastTrendClose = trendCandles.lastOrNull()?.close
        val hasTrendWindow = trendCandles.size == TREND_LOOKBACK_SESSIONS
        val rawTrendChangePct = if (hasTrendWindow) {
            firstTrendClose
                ?.takeIf { close -> close > 0.0 }
                ?.let { firstClose ->
                    lastTrendClose?.let { lastClose ->
                        ((lastClose - firstClose) / firstClose) * 100.0
                    }
                }
        } else null

        return PreEventAccumulationMetrics(
            quietDays20d = quietDays.takeIf { accumulationCandles.size == ACCUMULATION_LOOKBACK_SESSIONS },
            range20dPct = rangePct.takeIf { accumulationCandles.size == ACCUMULATION_LOOKBACK_SESSIONS },
            preEventChange10dPct = rawTrendChangePct?.roundTo2(),
            preEventUpward10d = rawTrendChangePct != null && rawTrendChangePct > 0.0,
            isCandidate20d = accumulationCandles.size == ACCUMULATION_LOOKBACK_SESSIONS &&
                quietDays >= ACCUMULATION_MIN_QUIET_DAYS &&
                rawRangePct != null && rawRangePct <= ACCUMULATION_RANGE_MAX_PCT,
        )
    }

}

internal data class PreEventAccumulationMetrics(
    val quietDays20d: Int? = null,
    val range20dPct: Double? = null,
    val preEventChange10dPct: Double? = null,
    val preEventUpward10d: Boolean = false,
    val isCandidate20d: Boolean = false,
)
