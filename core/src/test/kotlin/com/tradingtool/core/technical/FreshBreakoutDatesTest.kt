package com.tradingtool.core.technical

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.model.stock.FreshBreakoutDates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FreshBreakoutDatesTest {
    @Test
    fun `returns one latest date for each supported horizon`() {
        val candles = baseCandles(101).toMutableList()
        candles[100] = candle(100, high = 101.0, close = 101.0)

        assertEquals(
            FreshBreakoutDates(
                breakout20d = "2026-04-11",
                breakout50d = "2026-04-11",
                breakout52d = "2026-04-11",
                breakout100d = "2026-04-11",
                breakout20dLevel = 100.0,
                breakout50dLevel = 100.0,
                breakout52dLevel = 100.0,
                breakout100dLevel = 100.0,
            ),
            calculateFreshBreakoutDates(candles),
        )
    }

    @Test
    fun `continuation closes do not create repeated fresh dates`() {
        val candles = baseCandles(24).toMutableList()
        candles[20] = candle(20, high = 101.0, close = 101.0)
        candles[21] = candle(21, high = 102.0, close = 102.0)
        candles[22] = candle(22, high = 103.0, close = 103.0)

        assertEquals("2026-01-21", latestFreshBreakoutDate(candles, lookback = 20))
    }

    @Test
    fun `pullback below the rolling high allows a later fresh breakout`() {
        val candles = baseCandles(25).toMutableList()
        candles[20] = candle(20, high = 101.0, close = 101.0)
        candles[21] = candle(21, high = 102.0, close = 102.0)
        // The pullback remains above the original 100 base, but loses the rolling high.
        candles[22] = candle(22, high = 102.0, close = 100.5)
        candles[24] = candle(24, high = 103.0, close = 103.0)

        assertEquals("2026-01-25", latestFreshBreakoutDate(candles, lookback = 20))
    }

    @Test
    fun `insufficient history has no breakout date`() {
        assertNull(latestFreshBreakoutDate(baseCandles(20), lookback = 20))
    }

    private fun baseCandles(size: Int): List<DailyCandle> =
        (0 until size).map { index -> candle(index, high = 100.0, close = 100.0) }

    private fun candle(index: Int, high: Double, close: Double): DailyCandle =
        DailyCandle(
            instrumentToken = 1,
            symbol = "TEST",
            candleDate = java.time.LocalDate.of(2026, 1, 1).plusDays(index.toLong()),
            open = 100.0,
            high = high,
            low = minOf(99.0, close),
            close = close,
            volume = 1_000,
        )
}
