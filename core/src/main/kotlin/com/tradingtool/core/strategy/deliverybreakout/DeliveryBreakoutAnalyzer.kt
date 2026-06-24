package com.tradingtool.core.strategy.deliverybreakout

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import com.tradingtool.core.technical.roundTo2
import java.time.LocalDate

internal object DeliveryBreakoutAnalyzer {

    fun buildStage1Candidate(
        row: StockDeliveryDaily,
        history: List<StockDeliveryDaily>,
        config: DeliveryBreakoutConfig,
    ): DeliveryBreakoutStage1Candidate? {
        val currentVolume = row.ttlTrdQnty ?: return null
        val currentDeliveryQuantity = row.delivQty ?: return null
        
        val prevRow = history.lastOrNull { historyRow -> historyRow.tradingDate.isBefore(row.tradingDate) }
        if (prevRow == null) {
            return null
        }

        val prevVolume = prevRow.ttlTrdQnty ?: return null
        val prevDelivery = prevRow.delivQty ?: return null
        if (prevVolume <= 0L || prevDelivery <= 0L) {
            return null
        }
        
        val requiredVolume = (prevVolume * config.volumeMultiplier).toLong()
        val requiredDelivery = (prevDelivery * config.deliveryMultiplier).toLong()
        if (currentVolume < requiredVolume || currentDeliveryQuantity < requiredDelivery) {
            return null
        }

        return DeliveryBreakoutStage1Candidate(
            instrumentToken = row.instrumentToken,
            symbol = row.symbol,
            tradeDate = row.tradingDate.toString(),
            volume = currentVolume,
            deliveryQuantity = currentDeliveryQuantity,
            deliveryPercentage = row.delivPer?.roundTo2(),
            prevVolume = prevVolume,
            prevDeliveryQuantity = prevDelivery,
            volumeRatio = (currentVolume.toDouble() / prevVolume.toDouble()).roundTo2(),
            deliveryRatio = (currentDeliveryQuantity.toDouble() / prevDelivery.toDouble()).roundTo2(),
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
}
