package com.tradingtool.core.strategy.phasedbreakout

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate
import java.util.Locale

data class PhaseCFreshFieldSnapshot(
    val closePrice: Double,
    val pctChange: String,
    val volume: Long,
    val high52w: Double,
    val low52w: Double,
    val dist200dHighPct: Double,
    val dist200dLowPct: Double,
    val marketFieldsUpdatedOn: LocalDate,
)

object PhaseCFreshFieldCalculator {
    private const val LOOKBACK_52W = 252
    private const val LOOKBACK_200D = 200

    fun calculate(candles: List<DailyCandle>): PhaseCFreshFieldSnapshot {
        val sortedCandles = candles.sortedBy { candle -> candle.candleDate }
        require(sortedCandles.size >= LOOKBACK_52W) {
            "At least 252 daily candles are required to refresh fresh market fields."
        }

        val latestCandle = sortedCandles.last()
        val previousCandle = sortedCandles.getOrNull(sortedCandles.lastIndex - 1)
            ?: error("At least 2 daily candles are required to calculate daily percentage change.")
        require(previousCandle.close > 0.0) {
            "Previous close must be positive to calculate daily percentage change."
        }

        val candles52w = sortedCandles.takeLast(LOOKBACK_52W)
        val candles200d = sortedCandles.takeLast(LOOKBACK_200D)
        val high52w = candles52w.maxOf { candle -> candle.high }
        val low52w = candles52w.minOf { candle -> candle.low }
        val high200d = candles200d.maxOf { candle -> candle.high }

        require(high200d > 0.0) { "200-day high must be positive." }
        require(low52w > 0.0) { "52-week low must be positive." }

        val pctChangeValue = ((latestCandle.close - previousCandle.close) / previousCandle.close) * 100.0
        val dist200dHighPct = ((latestCandle.close - high200d) / high200d) * 100.0
        val dist200dLowPct = ((latestCandle.close - low52w) / low52w) * 100.0

        return PhaseCFreshFieldSnapshot(
            closePrice = latestCandle.close,
            pctChange = formatPercent(pctChangeValue),
            volume = latestCandle.volume,
            high52w = high52w,
            low52w = low52w,
            dist200dHighPct = dist200dHighPct,
            dist200dLowPct = dist200dLowPct,
            marketFieldsUpdatedOn = latestCandle.candleDate,
        )
    }

    private fun formatPercent(value: Double): String {
        val normalizedValue = if (value == -0.0) 0.0 else value
        return String.format(Locale.US, "%.2f%%", normalizedValue)
    }
}
