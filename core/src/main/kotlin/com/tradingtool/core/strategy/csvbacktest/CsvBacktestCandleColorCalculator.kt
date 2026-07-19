package com.tradingtool.core.strategy.csvbacktest

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate

object CsvBacktestCandleColorCalculator {
    private const val OBSERVATION_SESSION_COUNT = 3

    fun countRedCandles(candles: List<DailyCandle>, entryDate: LocalDate): Int? {
        val entryIndex = candles.indexOfFirst { it.candleDate == entryDate }
        if (entryIndex <= 0) return null

        val lastObservationIndex = minOf(candles.lastIndex, entryIndex + OBSERVATION_SESSION_COUNT - 1)
        return (entryIndex..lastObservationIndex).count { index ->
            candles[index].close < candles[index - 1].close
        }
    }
}
