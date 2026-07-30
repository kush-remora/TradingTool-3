package com.tradingtool.core.strategy.csvbacktest

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate

enum class CsvBacktestEntryStrategy {
    NEXT_DAY_OPEN,
    BREAKOUT_CLOSE_RECLAIM,
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
    private const val BREAKOUT_CLOSE_RECLAIM_WINDOW_SESSIONS = 30
    private const val SECOND_GREEN_CANDLE_WINDOW_SESSIONS = 20

    fun findEntry(
        candles: List<DailyCandle>,
        signalDate: LocalDate,
        strategy: CsvBacktestEntryStrategy,
        retestWindowDays: Int,
        retestTolerancePct: Double,
        maxCloseToCloseGainPct: Double = 6.0,
    ): CsvBacktestEntry? {
        if (retestWindowDays <= 0 || retestTolerancePct < 0.0) return null

        val postSignalCandles = candles.filter { it.candleDate.isAfter(signalDate) }
        if (strategy == CsvBacktestEntryStrategy.NEXT_DAY_OPEN) {
            return postSignalCandles.firstOrNull()?.let { candle ->
                CsvBacktestEntry(candle, candle.open, breakoutLevel = null)
            }
        }
        if (strategy == CsvBacktestEntryStrategy.BREAKOUT_CLOSE_RECLAIM) {
            return findBreakoutCloseReclaimEntry(candles, signalDate)
        }
        if (strategy == CsvBacktestEntryStrategy.TWO_GREEN_CANDLES) {
            return findTwoGreenCandleEntry(candles, signalDate, maxCloseToCloseGainPct)
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

    private fun findBreakoutCloseReclaimEntry(
        candles: List<DailyCandle>,
        signalDate: LocalDate,
    ): CsvBacktestEntry? {
        val breakoutClose = candles.firstOrNull { it.candleDate == signalDate }?.close ?: return null
        val confirmationCandle = candles
            .asSequence()
            .filter { it.candleDate.isAfter(signalDate) }
            .take(BREAKOUT_CLOSE_RECLAIM_WINDOW_SESSIONS)
            .firstOrNull { it.close > breakoutClose }
            ?: return null
        val entryCandle = candles.firstOrNull { it.candleDate.isAfter(confirmationCandle.candleDate) }
            ?: return null

        return CsvBacktestEntry(entryCandle, entryCandle.open, breakoutLevel = breakoutClose)
    }

    private fun findTwoGreenCandleEntry(
        candles: List<DailyCandle>,
        signalDate: LocalDate,
        maxCloseToCloseGainPct: Double,
    ): CsvBacktestEntry? {
        val signalIndex = candles.indexOfFirst { it.candleDate == signalDate }
        if (signalIndex <= 0 || !isGreenCandle(candles, signalIndex, maxCloseToCloseGainPct)) return null

        val lastSecondGreenIndex = minOf(candles.lastIndex, signalIndex + SECOND_GREEN_CANDLE_WINDOW_SESSIONS)
        val secondGreenIndex = findSecondGreenCandleIndex(
            candles = candles,
            firstIndex = signalIndex + 1,
            lastIndex = lastSecondGreenIndex,
            maxCloseToCloseGainPct = maxCloseToCloseGainPct,
        ) ?: return null
        val entryCandle = candles.getOrNull(secondGreenIndex + 1) ?: return null
        return CsvBacktestEntry(entryCandle, entryCandle.open, breakoutLevel = null)
    }

    private fun findSecondGreenCandleIndex(
        candles: List<DailyCandle>,
        firstIndex: Int,
        lastIndex: Int,
        maxCloseToCloseGainPct: Double,
    ): Int? {
        for (index in firstIndex..lastIndex) {
            if (candles[index].close <= candles[index - 1].close) continue
            if (!isGreenCandle(candles, index, maxCloseToCloseGainPct)) return null
            return index
        }
        return null
    }

    private fun isGreenCandle(candles: List<DailyCandle>, index: Int, maxCloseToCloseGainPct: Double): Boolean {
        val previousClose = candles[index - 1].close
        return candles[index].close > previousClose &&
            candles[index].close <= previousClose * (1.0 + maxCloseToCloseGainPct / 100.0)
    }
}
