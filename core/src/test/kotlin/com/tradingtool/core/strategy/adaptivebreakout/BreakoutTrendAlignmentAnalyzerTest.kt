package com.tradingtool.core.strategy.adaptivebreakout

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BreakoutTrendAlignmentAnalyzerTest {

    @Test
    fun `marks a rising stock that outperforms a rising market as aligned`() {
        val market = candles { index -> 100.0 + index }
        val stock = candles { index -> 100.0 + index * 1.5 }

        val result = BreakoutTrendAlignmentAnalyzer.analyze(stock, market)

        assertTrue(result.available)
        assertTrue(result.marketAligned == true)
        assertTrue(result.stockAligned == true)
        assertTrue(result.aligned == true)
        assertTrue(result.stockRelativeStrength3mPct!! > 0.0)
        assertTrue(result.stockRelativeStrength6mPct!! > 0.0)
    }

    @Test
    fun `rejects a stock below a falling trend even when the market is healthy`() {
        val market = candles { index -> 100.0 + index }
        val stock = candles { index -> 700.0 - index * 1.5 }

        val result = BreakoutTrendAlignmentAnalyzer.analyze(stock, market)

        assertTrue(result.available)
        assertTrue(result.marketAligned == true)
        assertFalse(result.stockAligned == true)
        assertFalse(result.aligned == true)
    }

    @Test
    fun `returns unavailable when the moving-average history is too short`() {
        val candles = candles { index -> 100.0 + index }.take(200)

        val result = BreakoutTrendAlignmentAnalyzer.analyze(candles, candles)

        assertFalse(result.available)
        assertEquals(null, result.aligned)
    }

    private fun candles(price: (Int) -> Double): List<DailyCandle> = (0 until 400).map { index ->
        val close = price(index)
        DailyCandle(
            instrumentToken = 1L,
            symbol = "TEST",
            candleDate = LocalDate.of(2024, 1, 1).plusDays(index.toLong()),
            open = close,
            high = close,
            low = close,
            close = close,
            volume = 1_000L,
        )
    }
}
