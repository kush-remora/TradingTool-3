package com.tradingtool.core.strategy.priceacceptance

import com.tradingtool.core.candle.DailyCandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PriceAcceptanceScannerEngineTest {

    @Test
    fun `counts prior closes inside anchor body for each lookback`() {
        val candles = (0 until 100).map { index ->
            candle(
                date = LocalDate.of(2026, 1, 1).plusDays(index.toLong()),
                close = if (index >= 80) 101.0 else 110.0,
            )
        } + candle(
            date = LocalDate.of(2026, 1, 1).plusDays(100),
            open = 100.0,
            close = 102.0,
        )

        val evaluation = PriceAcceptanceScannerEngine.evaluate(candles, LocalDate.of(2026, 4, 11))

        requireNotNull(evaluation)
        assertEquals(LocalDate.of(2026, 4, 11), evaluation.anchorDate)
        assertEquals(100.0, evaluation.bodyLow)
        assertEquals(102.0, evaluation.bodyHigh)
        assertEquals(20, evaluation.closeHits20)
        assertEquals(20, evaluation.closeHits40)
        assertEquals(20, evaluation.closeHits60)
        assertEquals(20, evaluation.closeHits80)
        assertEquals(20, evaluation.closeHits100)
        assertEquals(100.0, evaluation.closeHitRate20Pct)
        assertEquals(20.0, evaluation.closeHitRate100Pct)
    }

    @Test
    fun `excludes candles after requested as of date and excludes anchor from counts`() {
        val candles = listOf(
            candle(LocalDate.of(2026, 1, 1), close = 101.0),
            candle(LocalDate.of(2026, 1, 2), close = 110.0),
            candle(LocalDate.of(2026, 1, 3), open = 100.0, close = 102.0),
            candle(LocalDate.of(2026, 1, 4), close = 101.0),
        )

        val evaluation = PriceAcceptanceScannerEngine.evaluate(candles, LocalDate.of(2026, 1, 3))

        requireNotNull(evaluation)
        assertEquals(LocalDate.of(2026, 1, 3), evaluation.anchorDate)
        assertEquals(2, evaluation.priorSessionCount)
        assertEquals(1, evaluation.closeHits20)
    }

    @Test
    fun `returns no evaluation when there is no prior session`() {
        val evaluation = PriceAcceptanceScannerEngine.evaluate(
            candles = listOf(candle(LocalDate.of(2026, 1, 1), close = 101.0)),
            asOfDate = LocalDate.of(2026, 1, 1),
        )

        assertNull(evaluation)
    }

    private fun candle(
        date: LocalDate,
        open: Double = 109.0,
        close: Double,
    ): DailyCandle = DailyCandle(
        instrumentToken = 101L,
        symbol = "TEST",
        candleDate = date,
        open = open,
        high = maxOf(open, close),
        low = minOf(open, close),
        close = close,
        volume = 1_000L,
    )
}
