package com.tradingtool.core.technical

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate

data class RollingRsiBounds(
    val current: Double,
    val lowest: Double,
    val highest: Double,
)

object StrategyTechnicalSignals {
    const val FIFTY_DAY_RSI_WINDOW: Int = 50
    // 3 months ~ 63 trading sessions (fixed for now across strategies).
    const val THREE_MONTH_RSI_WINDOW: Int = 63

    fun buildRollingRsiBoundsMap(
        candles: List<DailyCandle>,
        rsiPeriod: Int = 14,
        windowSize: Int = THREE_MONTH_RSI_WINDOW,
        fallback: Double = 50.0,
    ): Map<LocalDate, RollingRsiBounds> {
        if (candles.isEmpty()) return emptyMap()
        val rsiValues = candles.calculateRsiValues(period = rsiPeriod, fallback = fallback)
        val result = mutableMapOf<LocalDate, RollingRsiBounds>()

        val maxDeque = ArrayDeque<Int>()
        val minDeque = ArrayDeque<Int>()

        for (i in rsiValues.indices) {
            val current = rsiValues[i]
            while (maxDeque.isNotEmpty() && maxDeque.first() <= i - windowSize) maxDeque.removeFirst()
            while (minDeque.isNotEmpty() && minDeque.first() <= i - windowSize) minDeque.removeFirst()

            while (maxDeque.isNotEmpty() && rsiValues[maxDeque.last()] <= current) maxDeque.removeLast()
            maxDeque.addLast(i)

            while (minDeque.isNotEmpty() && rsiValues[minDeque.last()] >= current) minDeque.removeLast()
            minDeque.addLast(i)

            val highest = rsiValues[maxDeque.first()]
            val lowest = rsiValues[minDeque.first()]
            result[candles[i].candleDate] = RollingRsiBounds(
                current = current,
                lowest = lowest,
                highest = highest,
            )
        }
        return result
    }

    fun latestAtr14(candles: List<DailyCandle>): Double {
        val series = candles.toTa4jSeries()
        if (series.barCount < 14) return 0.0
        return series.calculateAtr(14).getDoubleValue(series.endIndex)
    }
}
