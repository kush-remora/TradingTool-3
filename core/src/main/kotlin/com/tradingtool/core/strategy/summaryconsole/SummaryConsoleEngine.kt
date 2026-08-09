package com.tradingtool.core.strategy.summaryconsole

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate
import kotlin.math.abs

internal data class SummaryConsoleEvaluation(
    val asOfDate: LocalDate,
    val close: Double,
    val previousClose: Double?,
    val dailyMovePct: Double?,
    val largeMove: Boolean,
    val sma200: Double?,
    val sma200Crossed: Boolean,
    val volume: Long,
    val averageVolume5: Double?,
    val volumeRatio: Double?,
    val volumeAnomaly: Boolean,
    val breakout20Level: Double?,
    val breakout20LevelCrossed: Boolean,
    val breakout20CloseConfirmed: Boolean,
    val breakout40Level: Double?,
    val breakout40LevelCrossed: Boolean,
    val breakout40CloseConfirmed: Boolean,
    val breakout60Level: Double?,
    val breakout60LevelCrossed: Boolean,
    val breakout60CloseConfirmed: Boolean,
) {
    val hasEvent: Boolean
        get() = largeMove || sma200Crossed || volumeAnomaly ||
            breakout20LevelCrossed || breakout20CloseConfirmed ||
            breakout40LevelCrossed || breakout40CloseConfirmed ||
            breakout60LevelCrossed || breakout60CloseConfirmed
}

internal object SummaryConsoleEngine {
    fun evaluateRecent(
        candles: List<DailyCandle>,
        requestedAsOfDate: LocalDate,
        lookbackSessions: Int,
    ): List<SummaryConsoleEvaluation> {
        require(lookbackSessions > 0) { "lookbackSessions must be greater than zero." }
        val orderedCandles = candles
            .filter { candle -> !candle.candleDate.isAfter(requestedAsOfDate) }
            .sortedBy(DailyCandle::candleDate)

        return orderedCandles
            .takeLast(lookbackSessions)
            .mapNotNull { candle -> evaluate(orderedCandles, candle.candleDate) }
    }

    fun evaluate(candles: List<DailyCandle>, requestedAsOfDate: LocalDate): SummaryConsoleEvaluation? {
        val orderedCandles = candles
            .filter { candle -> !candle.candleDate.isAfter(requestedAsOfDate) }
            .sortedBy(DailyCandle::candleDate)
        val currentIndex = orderedCandles.lastIndex
        if (currentIndex < 0) return null

        val current = orderedCandles[currentIndex]
        val previous = orderedCandles.getOrNull(currentIndex - 1)
        val previousCandles = orderedCandles.take(currentIndex)
        val previousClose = previous?.close
        val dailyMovePct = previousClose?.takeIf { close -> close != 0.0 }
            ?.let { close -> ((current.close - close) / close) * 100.0 }
        val averageVolume5 = averageVolume(previousCandles, VOLUME_BASELINE_SESSIONS)
        val volumeRatio = averageVolume5?.takeIf { average -> average > 0.0 }
            ?.let { average -> current.volume / average }
        val sma200 = averageClose(previousCandles, SMA_WINDOW)
        val breakout20 = evaluateBreakout(previousCandles, current, BREAKOUT_20_WINDOW)
        val breakout40 = evaluateBreakout(previousCandles, current, BREAKOUT_40_WINDOW)
        val breakout60 = evaluateBreakout(previousCandles, current, BREAKOUT_60_WINDOW)

        return SummaryConsoleEvaluation(
            asOfDate = current.candleDate,
            close = current.close,
            previousClose = previousClose,
            dailyMovePct = dailyMovePct,
            largeMove = dailyMovePct?.let { move -> abs(move) > LARGE_MOVE_THRESHOLD_PCT } ?: false,
            sma200 = sma200,
            sma200Crossed = sma200?.let { value -> current.low <= value && current.high >= value } ?: false,
            volume = current.volume,
            averageVolume5 = averageVolume5,
            volumeRatio = volumeRatio,
            volumeAnomaly = volumeRatio?.let { ratio -> ratio >= VOLUME_ANOMALY_RATIO } ?: false,
            breakout20Level = breakout20.level,
            breakout20LevelCrossed = breakout20.levelCrossed,
            breakout20CloseConfirmed = breakout20.closeConfirmed,
            breakout40Level = breakout40.level,
            breakout40LevelCrossed = breakout40.levelCrossed,
            breakout40CloseConfirmed = breakout40.closeConfirmed,
            breakout60Level = breakout60.level,
            breakout60LevelCrossed = breakout60.levelCrossed,
            breakout60CloseConfirmed = breakout60.closeConfirmed,
        )
    }

    private fun averageClose(candles: List<DailyCandle>, sessions: Int): Double? =
        candles.takeLast(sessions).takeIf { values -> values.size == sessions }?.map(DailyCandle::close)?.average()

    private fun averageVolume(candles: List<DailyCandle>, sessions: Int): Double? =
        candles.takeLast(sessions).takeIf { values -> values.size == sessions }?.map(DailyCandle::volume)?.average()

    private fun evaluateBreakout(
        previousCandles: List<DailyCandle>,
        current: DailyCandle,
        sessions: Int,
    ): BreakoutEvaluation {
        val level = previousCandles.takeLast(sessions).takeIf { values -> values.size == sessions }
            ?.maxOfOrNull(DailyCandle::close)
        return BreakoutEvaluation(
            level = level,
            levelCrossed = level?.let { value -> current.high > value } ?: false,
            closeConfirmed = level?.let { value -> current.close > value } ?: false,
        )
    }

    private data class BreakoutEvaluation(
        val level: Double?,
        val levelCrossed: Boolean,
        val closeConfirmed: Boolean,
    )

    private const val SMA_WINDOW = 200
    private const val VOLUME_BASELINE_SESSIONS = 5
    private const val LARGE_MOVE_THRESHOLD_PCT = 3.0
    private const val VOLUME_ANOMALY_RATIO = 2.0
    private const val BREAKOUT_20_WINDOW = 20
    private const val BREAKOUT_40_WINDOW = 40
    private const val BREAKOUT_60_WINDOW = 60
}
