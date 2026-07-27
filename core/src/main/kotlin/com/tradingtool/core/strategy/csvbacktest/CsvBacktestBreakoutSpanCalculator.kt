package com.tradingtool.core.strategy.csvbacktest

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate

data class CsvBacktestBreakoutSpan(
    val sessions: Int,
    val isLowerBound: Boolean,
)

object CsvBacktestBreakoutSpanCalculator {
    const val MAX_LOOKBACK_SESSIONS = 500

    fun calculate(
        candles: List<DailyCandle>,
        signalDate: LocalDate,
    ): CsvBacktestBreakoutSpan? {
        val signalIndex = candles.indexOfFirst { it.candleDate == signalDate }
        if (signalIndex <= 0) return null

        val firstPriorIndex = maxOf(0, signalIndex - MAX_LOOKBACK_SESSIONS)
        val priorCandles = candles.subList(firstPriorIndex, signalIndex)
        val sessionsSinceBlockingClose = priorCandles
            .asReversed()
            .indexOfFirst { candle -> candle.close >= candles[signalIndex].high }

        return if (sessionsSinceBlockingClose >= 0) {
            CsvBacktestBreakoutSpan(
                sessions = sessionsSinceBlockingClose,
                isLowerBound = false,
            )
        } else {
            CsvBacktestBreakoutSpan(
                sessions = priorCandles.size,
                isLowerBound = true,
            )
        }
    }
}
