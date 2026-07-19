package com.tradingtool.core.strategy.csvbacktest

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate

enum class CsvBacktestEntryStrategy {
    NEXT_DAY_OPEN,
    TWO_GREEN_CANDLES,
    RETEST,
    CONFIRMED_RETEST,
    ;

    companion object {
        fun from(value: String): CsvBacktestEntryStrategy =
            entries.firstOrNull { it.name == value } ?: NEXT_DAY_OPEN
    }
}

data class CsvBacktestEntry(
    val candle: DailyCandle,
    val price: Double,
    val breakoutLevel: Double?,
)

object CsvBacktestEntryEvaluator {
    private const val BREAKOUT_LOOKBACK_SESSIONS = 20
    private const val SECOND_GREEN_CANDLE_WINDOW_SESSIONS = 20

    fun findEntry(
        candles: List<DailyCandle>,
        signalDate: LocalDate,
        strategy: CsvBacktestEntryStrategy,
        retestWindowDays: Int,
        retestTolerancePct: Double,
    ): CsvBacktestEntry? {
        if (retestWindowDays <= 0 || retestTolerancePct < 0.0) return null

        val postSignalCandles = candles.filter { it.candleDate.isAfter(signalDate) }
        if (strategy == CsvBacktestEntryStrategy.NEXT_DAY_OPEN) {
            return postSignalCandles.firstOrNull()?.let { candle ->
                CsvBacktestEntry(candle, candle.open, breakoutLevel = null)
            }
        }
        if (strategy == CsvBacktestEntryStrategy.TWO_GREEN_CANDLES) {
            return findTwoGreenCandleEntry(candles, signalDate)
        }

        val breakoutLevel = candles
            .filter { it.candleDate.isBefore(signalDate) }
            .takeLast(BREAKOUT_LOOKBACK_SESSIONS)
            .maxOfOrNull { it.high }
            ?: return null
        val retestLimit = breakoutLevel * (1.0 + retestTolerancePct / 100.0)
        val retestCandles = postSignalCandles.take(retestWindowDays)
        val retestIndex = retestCandles.indexOfFirst { it.low <= retestLimit }
        if (retestIndex < 0) return null

        val retestCandle = retestCandles[retestIndex]
        if (strategy == CsvBacktestEntryStrategy.RETEST) {
            val entryPrice = if (retestCandle.open <= retestLimit) retestCandle.open else retestLimit
            return CsvBacktestEntry(retestCandle, entryPrice, breakoutLevel)
        }

        val confirmationCandle = retestCandles
            .drop(retestIndex)
            .firstOrNull { it.close >= breakoutLevel }
            ?: return null
        val entryCandle = postSignalCandles.firstOrNull { it.candleDate.isAfter(confirmationCandle.candleDate) }
            ?: return null
        return CsvBacktestEntry(entryCandle, entryCandle.open, breakoutLevel)
    }

    private fun findTwoGreenCandleEntry(candles: List<DailyCandle>, signalDate: LocalDate): CsvBacktestEntry? {
        val signalIndex = candles.indexOfFirst { it.candleDate == signalDate }
        if (signalIndex <= 0 || !isGreenCandle(candles, signalIndex)) return null

        val lastSecondGreenIndex = minOf(candles.lastIndex, signalIndex + SECOND_GREEN_CANDLE_WINDOW_SESSIONS)
        val secondGreenIndex = (signalIndex + 1..lastSecondGreenIndex)
            .firstOrNull { index -> isGreenCandle(candles, index) }
            ?: return null
        val entryCandle = candles.getOrNull(secondGreenIndex + 1) ?: return null
        return CsvBacktestEntry(entryCandle, entryCandle.open, breakoutLevel = null)
    }

    private fun isGreenCandle(candles: List<DailyCandle>, index: Int): Boolean =
        candles[index].close > candles[index - 1].close
}
