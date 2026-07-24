package com.tradingtool.core.strategy.csvbacktest

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate

data class CsvBacktestV2Validation(
    val breakoutLevel: Double,
    val maxPreBreakoutVolumeRatio: Double,
    val failedResistanceAttempts: Int,
    val recentRunBasePrice: Double,
    val moveFromRecentBasePct: Double,
)

object CsvBacktestV2Validator {
    private const val BREAKOUT_LOOKBACK_SESSIONS = 100
    private const val FRESH_BREAKOUT_LOOKBACK_SESSIONS = 99
    private const val PRE_BREAKOUT_VOLUME_SESSIONS = 20
    private const val PRE_BREAKOUT_VOLUME_BASELINE_SESSIONS = 20
    private const val MIN_VOLUME_RATIO = 2.0
    private const val RESISTANCE_TOUCH_RATIO = 0.97
    private const val RECENT_RUN_SESSIONS = 50
    private const val RECENT_RUN_BASE_SESSIONS = 6

    fun validate(
        candles: List<DailyCandle>,
        signalDate: LocalDate,
        maxCloseToCloseGainPct: Double = 6.0,
    ): CsvBacktestV2Validation? {
        val signalIndex = candles.indexOfFirst { it.candleDate == signalDate }
        if (signalIndex < BREAKOUT_LOOKBACK_SESSIONS) return null

        val signalCandle = candles[signalIndex]
        val priorCandles = candles.subList(signalIndex - BREAKOUT_LOOKBACK_SESSIONS, signalIndex)
        val breakoutLevel = priorCandles.maxOf { it.high }
        if (signalCandle.high <= breakoutLevel) return null
        if (signalCandle.close > candles[signalIndex - 1].close * (1.0 + maxCloseToCloseGainPct / 100.0)) return null
        if (!isFreshBreakout(candles, signalIndex)) return null

        val maxVolumeRatio = maxPreBreakoutVolumeRatio(candles, signalIndex) ?: return null
        if (maxVolumeRatio < MIN_VOLUME_RATIO) return null

        val recentRunBasePrice = recentRunBasePrice(candles, signalIndex) ?: return null
        return CsvBacktestV2Validation(
            breakoutLevel = breakoutLevel,
            maxPreBreakoutVolumeRatio = maxVolumeRatio,
            failedResistanceAttempts = failedResistanceAttempts(priorCandles, breakoutLevel),
            recentRunBasePrice = recentRunBasePrice,
            moveFromRecentBasePct = ((signalCandle.close - recentRunBasePrice) / recentRunBasePrice) * 100.0,
        )
    }

    private fun isFreshBreakout(candles: List<DailyCandle>, signalIndex: Int): Boolean {
        val firstPriorSignalIndex = maxOf(BREAKOUT_LOOKBACK_SESSIONS, signalIndex - FRESH_BREAKOUT_LOOKBACK_SESSIONS)
        return (firstPriorSignalIndex until signalIndex).none { index ->
            candles[index].high > candles.subList(index - BREAKOUT_LOOKBACK_SESSIONS, index).maxOf { it.high }
        }
    }

    private fun maxPreBreakoutVolumeRatio(candles: List<DailyCandle>, signalIndex: Int): Double? {
        val firstCandidateIndex = signalIndex - PRE_BREAKOUT_VOLUME_SESSIONS
        if (firstCandidateIndex < PRE_BREAKOUT_VOLUME_BASELINE_SESSIONS) return null

        return (firstCandidateIndex until signalIndex)
            .mapNotNull { index ->
                val averagePriorVolume = candles
                    .subList(index - PRE_BREAKOUT_VOLUME_BASELINE_SESSIONS, index)
                    .map { it.volume.toDouble() }
                    .average()
                averagePriorVolume.takeIf { it > 0.0 }?.let { candles[index].volume / it }
            }
            .maxOrNull()
    }

    private fun failedResistanceAttempts(priorCandles: List<DailyCandle>, breakoutLevel: Double): Int =
        priorCandles.fold(Pair(false, 0)) { (wasNearResistance, count), candle ->
            val isNearResistance = candle.high >= breakoutLevel * RESISTANCE_TOUCH_RATIO &&
                candle.high < breakoutLevel &&
                candle.close < breakoutLevel
            Pair(isNearResistance, if (isNearResistance && !wasNearResistance) count + 1 else count)
        }.second

    private fun recentRunBasePrice(candles: List<DailyCandle>, signalIndex: Int): Double? {
        val recentStartIndex = maxOf(0, signalIndex - RECENT_RUN_SESSIONS + 1)
        val lowestCandleIndex = (recentStartIndex..signalIndex).minByOrNull { candles[it].low } ?: return null
        if (lowestCandleIndex < RECENT_RUN_BASE_SESSIONS - 1) return null

        return candles
            .subList(lowestCandleIndex - RECENT_RUN_BASE_SESSIONS + 1, lowestCandleIndex + 1)
            .map { it.close }
            .average()
    }
}
