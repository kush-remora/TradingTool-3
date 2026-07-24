package com.tradingtool.core.strategy.csvbacktest

import com.tradingtool.core.candle.DailyCandle

data class CsvBacktestExit(
    val candle: DailyCandle,
    val price: Double,
    val slHit: Boolean,
)

object CsvBacktestExitEvaluator {
    private const val MAXIMUM_HOLDING_DAYS = 40L

    fun findFixedExit(
        candles: List<DailyCandle>,
        stopLossPrice: Double,
        targetPrice: Double,
    ): CsvBacktestExit? {
        val maximumHoldingDate = candles.firstOrNull()?.candleDate?.plusDays(MAXIMUM_HOLDING_DAYS)
            ?: return null

        for (candle in candles) {
            if (candle.open <= stopLossPrice) {
                return CsvBacktestExit(candle, candle.open, slHit = true)
            }
            if (candle.open >= targetPrice) {
                return CsvBacktestExit(candle, candle.open, slHit = false)
            }
            if (candle.low <= stopLossPrice) {
                return CsvBacktestExit(candle, stopLossPrice, slHit = true)
            }
            if (candle.high >= targetPrice) {
                return CsvBacktestExit(candle, targetPrice, slHit = false)
            }
            if (!candle.candleDate.isBefore(maximumHoldingDate)) {
                return CsvBacktestExit(candle, candle.close, slHit = false)
            }
        }
        return null
    }

    fun findTrailingExit(
        candles: List<DailyCandle>,
        initialStopLossPrice: Double,
        targetPrice: Double,
        trailingStopLossPct: Double,
    ): CsvBacktestExit? {
        val maximumHoldingDate = candles.firstOrNull()?.candleDate?.plusDays(MAXIMUM_HOLDING_DAYS)
            ?: return null
        var highestClose = Double.NEGATIVE_INFINITY
        var currentStopLossPrice = initialStopLossPrice

        for (candle in candles) {
            if (candle.open <= currentStopLossPrice) {
                return CsvBacktestExit(candle, candle.open, slHit = true)
            }
            if (candle.open >= targetPrice) {
                return CsvBacktestExit(candle, candle.open, slHit = false)
            }
            if (candle.low <= currentStopLossPrice) {
                return CsvBacktestExit(candle, currentStopLossPrice, slHit = true)
            }
            if (candle.high >= targetPrice) {
                return CsvBacktestExit(candle, targetPrice, slHit = false)
            }
            if (!candle.candleDate.isBefore(maximumHoldingDate)) {
                return CsvBacktestExit(candle, candle.close, slHit = false)
            }

            highestClose = maxOf(highestClose, candle.close)
            val nextStopLossPrice = highestClose * (1.0 - trailingStopLossPct / 100.0)
            currentStopLossPrice = maxOf(currentStopLossPrice, nextStopLossPrice)
        }
        return null
    }
}
