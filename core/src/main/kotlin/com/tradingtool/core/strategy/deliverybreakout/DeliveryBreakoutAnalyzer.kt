package com.tradingtool.core.strategy.deliverybreakout

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import com.tradingtool.core.technical.roundTo2
import java.time.LocalDate

internal object DeliveryBreakoutAnalyzer {
    private const val SHORTLIST_LOOKBACK_DAYS = 10
    private const val URGENT_ACCUMULATION_THRESHOLD = 2.0
    private const val BREAKOUT_PCT_THRESHOLD = 6.0
    private const val BUY_ZONE_UPPER_PCT = 2.0

    fun buildStage1Candidate(
        row: StockDeliveryDaily,
        history: List<StockDeliveryDaily>,
        config: DeliveryBreakoutConfig,
    ): DeliveryBreakoutStage1Candidate? {
        val currentVolume = row.ttlTrdQnty ?: return null
        val currentDeliveryQuantity = row.delivQty ?: return null
        val priorRows = history
            .filter { historyRow -> historyRow.tradingDate.isBefore(row.tradingDate) }
            .takeLast(SHORTLIST_LOOKBACK_DAYS)

        if (priorRows.size < SHORTLIST_LOOKBACK_DAYS) {
            return null
        }

        val priorVolumes = priorRows.mapNotNull { historyRow -> historyRow.ttlTrdQnty }
        val priorDeliveries = priorRows.mapNotNull { historyRow -> historyRow.delivQty }
        if (priorVolumes.size < SHORTLIST_LOOKBACK_DAYS || priorDeliveries.size < SHORTLIST_LOOKBACK_DAYS) {
            return null
        }

        val priorMaxVolume = priorVolumes.maxOrNull() ?: return null
        val priorMaxDelivery = priorDeliveries.maxOrNull() ?: return null
        if (priorMaxVolume <= 0L || priorMaxDelivery <= 0L) {
            return null
        }
        val requiredVolume = (priorMaxVolume * config.volumeMultiplier).toLong()
        val requiredDelivery = (priorMaxDelivery * config.deliveryMultiplier).toLong()
        if (currentVolume <= requiredVolume || currentDeliveryQuantity <= requiredDelivery) {
            return null
        }

        return DeliveryBreakoutStage1Candidate(
            instrumentToken = row.instrumentToken,
            symbol = row.symbol,
            tradeDate = row.tradingDate.toString(),
            volume = currentVolume,
            deliveryQuantity = currentDeliveryQuantity,
            deliveryPercentage = row.delivPer?.roundTo2(),
            prior10dMaxVolume = priorMaxVolume,
            prior10dMaxDeliveryQuantity = priorMaxDelivery,
            volumeRatioVs10dMax = (currentVolume.toDouble() / priorMaxVolume.toDouble()).roundTo2(),
            deliveryRatioVs10dMax = (currentDeliveryQuantity.toDouble() / priorMaxDelivery.toDouble()).roundTo2(),
        )
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

    fun calculateSma200(candles: List<DailyCandle>, tradeDate: LocalDate): Double? {
        val candlesUntilDate = candles.filter { candle -> !candle.candleDate.isAfter(tradeDate) }
        if (candlesUntilDate.size < 200) {
            return null
        }
        return candlesUntilDate.takeLast(200).map { candle -> candle.close }.average().roundTo2()
    }

    fun calculateDistanceFromSma200(close: Double?, sma200: Double?): Double? {
        if (close == null || sma200 == null || sma200 <= 0.0) {
            return null
        }
        return (((close / sma200) - 1.0) * 100.0).roundTo2()
    }

    fun isNearSma200(distanceFromSma200Pct: Double?): Boolean? {
        return distanceFromSma200Pct?.let { distance -> distance <= BUY_ZONE_UPPER_PCT }
    }

    fun isConfirmedBreakoutToday(closePctChange: Double?): Boolean {
        return closePctChange?.let { change -> change > BREAKOUT_PCT_THRESHOLD } ?: false
    }

    fun isUrgentAccumulationToday(closePctChange: Double?): Boolean {
        return closePctChange?.let { change -> change <= URGENT_ACCUMULATION_THRESHOLD } ?: false
    }

    fun resolveQuietClueDay(
        deliveries: List<StockDeliveryDaily>,
        candles: List<DailyCandle>,
        tradeDate: LocalDate,
        config: DeliveryBreakoutConfig,
    ): LocalDate? {
        val clueDates = deliveries
            .filter { delivery -> delivery.tradingDate.isBefore(tradeDate) }
            .takeLast(3)
            .map { delivery -> delivery.tradingDate }
            .sortedDescending()

        return clueDates.firstOrNull { candidateDate ->
            isQuietClueDate(
                deliveries = deliveries,
                candles = candles,
                candidateDate = candidateDate,
                config = config,
            )
        }
    }

    fun resolveLabel(
        closePctChange: Double?,
        hasQuietClue: Boolean,
    ): String {
        val isBreakout = isConfirmedBreakoutToday(closePctChange)
        val isUrgent = isUrgentAccumulationToday(closePctChange)

        if (isBreakout) {
            return if (hasQuietClue) "BREAKOUT_WITH_CLUE" else "BREAKOUT"
        }
        if (isUrgent) {
            return "URGENT_ACCUMULATION"
        }
        return "DEVELOPING_BREAKOUT"
    }

    private fun isQuietClueDate(
        deliveries: List<StockDeliveryDaily>,
        candles: List<DailyCandle>,
        candidateDate: LocalDate,
        config: DeliveryBreakoutConfig,
    ): Boolean {
        val deliveryIndex = deliveries.indexOfFirst { delivery -> delivery.tradingDate == candidateDate }
        if (deliveryIndex < SHORTLIST_LOOKBACK_DAYS) {
            return false
        }

        val delivery = deliveries[deliveryIndex]
        val volume = delivery.ttlTrdQnty ?: return false
        val deliveryQuantity = delivery.delivQty ?: return false
        val priorRows = deliveries.subList(deliveryIndex - SHORTLIST_LOOKBACK_DAYS, deliveryIndex)
        val priorMaxVolume = priorRows.mapNotNull { row -> row.ttlTrdQnty }.maxOrNull() ?: return false
        val priorMaxDelivery = priorRows.mapNotNull { row -> row.delivQty }.maxOrNull() ?: return false
        val pctChange = calculatePctChange(candles, candidateDate) ?: return false

        val requiredVolume = (priorMaxVolume * config.volumeMultiplier).toLong()
        val requiredDelivery = (priorMaxDelivery * config.deliveryMultiplier).toLong()

        return volume > requiredVolume &&
            deliveryQuantity > requiredDelivery &&
            pctChange <= URGENT_ACCUMULATION_THRESHOLD
    }
}
