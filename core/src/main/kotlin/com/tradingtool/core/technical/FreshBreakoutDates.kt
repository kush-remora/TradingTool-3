package com.tradingtool.core.technical

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.model.stock.FreshBreakoutDates

private val BREAKOUT_LOOKBACKS = listOf(20, 50, 52, 100)

/**
 * Returns the latest close-confirmed breakout date for each trading-session horizon.
 * A date is recorded only when the close crosses above the prior N-session high;
 * later closes above the rolling high are continuation, not fresh breakouts.
 */
fun calculateFreshBreakoutDates(candles: List<DailyCandle>): FreshBreakoutDates {
    val sortedCandles = candles.sortedBy(DailyCandle::candleDate)

    val breakouts = BREAKOUT_LOOKBACKS.associateWith { lookback -> latestFreshBreakout(sortedCandles, lookback) }
    return FreshBreakoutDates(
        breakout20d = breakouts[20]?.date,
        breakout50d = breakouts[50]?.date,
        breakout52d = breakouts[52]?.date,
        breakout100d = breakouts[100]?.date,
        breakout20dLevel = breakouts[20]?.level,
        breakout50dLevel = breakouts[50]?.level,
        breakout52dLevel = breakouts[52]?.level,
        breakout100dLevel = breakouts[100]?.level,
    )
}

internal fun latestFreshBreakoutDate(candles: List<DailyCandle>, lookback: Int): String? {
    return latestFreshBreakout(candles, lookback)?.date
}

private data class FreshBreakout(
    val date: String,
    val level: Double,
)

private fun latestFreshBreakout(candles: List<DailyCandle>, lookback: Int): FreshBreakout? {
    if (lookback <= 0) return null

    val sortedCandles = candles.sortedBy(DailyCandle::candleDate)
    var breakoutIsActive = false
    var latestBreakout: FreshBreakout? = null

    for (index in lookback until sortedCandles.size) {
        val priorHigh = sortedCandles
            .subList(index - lookback, index)
            .maxOfOrNull(DailyCandle::high)
            ?: continue
        val close = sortedCandles[index].close

        if (breakoutIsActive) {
            if (close <= priorHigh) {
                breakoutIsActive = false
            } else {
                continue
            }
        }

        if (close > priorHigh) {
            latestBreakout = FreshBreakout(
                date = sortedCandles[index].candleDate.toString(),
                level = priorHigh,
            )
            breakoutIsActive = true
        }
    }

    return latestBreakout
}
