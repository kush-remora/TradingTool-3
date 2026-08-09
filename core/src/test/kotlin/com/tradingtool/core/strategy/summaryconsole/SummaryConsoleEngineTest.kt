package com.tradingtool.core.strategy.summaryconsole

import com.tradingtool.core.candle.DailyCandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SummaryConsoleEngineTest {
    @Test
    fun `evaluates the latest five completed sessions separately`() {
        val start = LocalDate.of(2026, 1, 1)
        val candles = (0..7).map { index ->
            candle(
                date = start.plusDays(index.toLong()),
                close = 100.0 + index,
                volume = 100L,
            )
        }

        val evaluations = SummaryConsoleEngine.evaluateRecent(candles, start.plusDays(7), 5)

        assertEquals(
            listOf(start.plusDays(3), start.plusDays(4), start.plusDays(5), start.plusDays(6), start.plusDays(7)),
            evaluations.map(SummaryConsoleEvaluation::asOfDate),
        )
    }

    @Test
    fun `detects movement volume sma and both breakout states`() {
        val baseline = (1..200).map { index ->
            candle(
                date = LocalDate.of(2025, 1, 1).plusDays(index.toLong()),
                close = if (index <= 140) 100.0 else 110.0,
                volume = if (index > 195) 100L else 50L,
            )
        }
        val currentDate = LocalDate.of(2025, 1, 1).plusDays(200)
        val candles = baseline + candle(
            date = currentDate,
            open = 109.0,
            high = 116.0,
            low = 99.0,
            close = 115.0,
            volume = 250L,
        )

        val evaluation = requireNotNull(SummaryConsoleEngine.evaluate(candles, currentDate))

        assertTrue(evaluation.largeMove)
        assertTrue(evaluation.volumeAnomaly)
        assertTrue(evaluation.sma200Crossed)
        assertTrue(evaluation.breakout20LevelCrossed)
        assertTrue(evaluation.breakout20CloseConfirmed)
        assertTrue(evaluation.breakout40LevelCrossed)
        assertTrue(evaluation.breakout40CloseConfirmed)
        assertTrue(evaluation.breakout60LevelCrossed)
        assertTrue(evaluation.breakout60CloseConfirmed)
    }

    @Test
    fun `uses only preceding sessions for baselines and does not flag a touch below threshold`() {
        val start = LocalDate.of(2026, 1, 1)
        val candles = (1..200).map { index ->
            candle(
                date = start.plusDays(index.toLong()),
                close = 100.0,
                volume = 100L,
            )
        } + candle(
            date = start.plusDays(201),
            open = 99.0,
            high = 100.0,
            low = 99.5,
            close = 100.0,
            volume = 199L,
        )

        val evaluation = requireNotNull(SummaryConsoleEngine.evaluate(candles, start.plusDays(201)))

        assertFalse(evaluation.largeMove)
        assertFalse(evaluation.volumeAnomaly)
        assertEquals(100.0, evaluation.sma200)
        assertEquals(100.0, evaluation.averageVolume5)
        assertFalse(evaluation.breakout20LevelCrossed)
    }

    private fun candle(
        date: LocalDate,
        close: Double,
        volume: Long,
        open: Double = close,
        high: Double = close,
        low: Double = close,
    ): DailyCandle = DailyCandle(
        instrumentToken = 101L,
        symbol = "TEST",
        candleDate = date,
        open = open,
        high = high,
        low = low,
        close = close,
        volume = volume,
    )
}
