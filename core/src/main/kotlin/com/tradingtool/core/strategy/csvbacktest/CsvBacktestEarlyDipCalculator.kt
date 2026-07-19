package com.tradingtool.core.strategy.csvbacktest

import com.tradingtool.core.candle.DailyCandle

data class CsvBacktestEarlyDip(
    val lowestPrice: Double,
    val dropAmount: Double,
    val dropPct: Double,
)

object CsvBacktestEarlyDipCalculator {
    private const val EARLY_DIP_SESSION_COUNT = 5

    fun calculate(entryPrice: Double, candles: List<DailyCandle>): CsvBacktestEarlyDip? {
        val lowestPrice = candles.take(EARLY_DIP_SESSION_COUNT).minOfOrNull { it.low } ?: return null
        val dropAmount = entryPrice - lowestPrice
        return CsvBacktestEarlyDip(
            lowestPrice = lowestPrice,
            dropAmount = dropAmount,
            dropPct = (dropAmount / entryPrice) * 100.0,
        )
    }
}
