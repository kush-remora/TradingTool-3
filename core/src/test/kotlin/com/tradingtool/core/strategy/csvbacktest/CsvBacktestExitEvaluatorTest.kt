package com.tradingtool.core.strategy.csvbacktest

import com.tradingtool.core.candle.DailyCandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CsvBacktestExitEvaluatorTest {

    @Test
    fun `entry day target hit exits at target`() {
        val exit = CsvBacktestExitEvaluator.findFixedExit(
            candles = listOf(candle(high = 106.0, low = 99.0)),
            stopLossPrice = 95.0,
            targetPrice = 105.0,
        )

        assertNotNull(exit)
        assertEquals(105.0, exit?.price)
        assertFalse(exit?.slHit ?: true)
    }

    @Test
    fun `entry day target and stop collision exits at stop loss`() {
        val exit = CsvBacktestExitEvaluator.findFixedExit(
            candles = listOf(candle(high = 106.0, low = 94.0)),
            stopLossPrice = 95.0,
            targetPrice = 105.0,
        )

        assertNotNull(exit)
        assertEquals(95.0, exit?.price)
        assertTrue(exit?.slHit ?: false)
    }

    @Test
    fun `gap down exits at the opening price`() {
        val exit = CsvBacktestExitEvaluator.findFixedExit(
            candles = listOf(candle(open = 92.0, high = 98.0, low = 90.0)),
            stopLossPrice = 95.0,
            targetPrice = 105.0,
        )

        assertNotNull(exit)
        assertEquals(92.0, exit?.price)
        assertTrue(exit?.slHit ?: false)
    }

    @Test
    fun `trailing strategy exits at target before its trailing stop`() {
        val exit = CsvBacktestExitEvaluator.findTrailingExit(
            candles = listOf(
                candle(close = 105.0, high = 106.0, low = 99.0),
                candle(date = LocalDate.of(2026, 1, 6), close = 118.0, high = 121.0, low = 110.0),
            ),
            initialStopLossPrice = 90.0,
            targetPrice = 120.0,
            initialStopLossSessions = 5,
            trailingStopLossPct = 10.0,
        )

        assertNotNull(exit)
        assertEquals(120.0, exit?.price)
        assertFalse(exit?.slHit ?: true)
    }

    @Test
    fun `trailing stop uses a close only from completed candles`() {
        val exit = CsvBacktestExitEvaluator.findTrailingExit(
            candles = listOf(candle(close = 120.0, high = 125.0, low = 95.0)),
            initialStopLossPrice = 90.0,
            targetPrice = 150.0,
            initialStopLossSessions = 1,
            trailingStopLossPct = 10.0,
        )

        assertEquals(null, exit)
    }

    @Test
    fun `trailing stop activates after the configured initial sessions`() {
        val entryDate = LocalDate.of(2026, 1, 5)
        val exit = CsvBacktestExitEvaluator.findTrailingExit(
            candles = listOf(
                candle(date = entryDate, open = 100.0, high = 111.0, low = 99.0, close = 110.0),
                candle(date = entryDate.plusDays(1), open = 105.0, high = 106.0, low = 100.0, close = 102.0),
                candle(date = entryDate.plusDays(2), open = 101.0, high = 103.0, low = 99.0, close = 100.0),
                candle(date = entryDate.plusDays(3), open = 103.0, high = 104.0, low = 100.0, close = 101.0),
            ),
            initialStopLossPrice = 90.0,
            targetPrice = 150.0,
            initialStopLossSessions = 3,
            trailingStopLossPct = 5.0,
        )

        assertNotNull(exit)
        assertEquals(entryDate.plusDays(3), exit?.candle?.candleDate)
        assertEquals(103.0, exit?.price)
        assertTrue(exit?.slHit ?: false)
    }

    @Test
    fun `fixed strategy closes at the close on the fortieth calendar day when still open`() {
        val entryDate = LocalDate.of(2026, 1, 5)
        val exit = CsvBacktestExitEvaluator.findFixedExit(
            candles = listOf(
                candle(date = entryDate, high = 101.0, low = 99.0),
                candle(date = entryDate.plusDays(40), high = 103.0, low = 98.0, close = 102.0),
            ),
            stopLossPrice = 95.0,
            targetPrice = 105.0,
        )

        assertNotNull(exit)
        assertEquals(entryDate.plusDays(40), exit?.candle?.candleDate)
        assertEquals(102.0, exit?.price)
        assertFalse(exit?.slHit ?: true)
    }

    @Test
    fun `trailing strategy closes at the close on the fortieth calendar day when still open`() {
        val entryDate = LocalDate.of(2026, 1, 5)
        val exit = CsvBacktestExitEvaluator.findTrailingExit(
            candles = listOf(
                candle(date = entryDate, high = 101.0, low = 99.0),
                candle(date = entryDate.plusDays(40), high = 103.0, low = 98.0, close = 102.0),
            ),
            initialStopLossPrice = 95.0,
            targetPrice = 105.0,
            initialStopLossSessions = 5,
            trailingStopLossPct = 10.0,
        )

        assertNotNull(exit)
        assertEquals(entryDate.plusDays(40), exit?.candle?.candleDate)
        assertEquals(102.0, exit?.price)
        assertFalse(exit?.slHit ?: true)
    }

    private fun candle(
        date: LocalDate = LocalDate.of(2026, 1, 5),
        open: Double = 100.0,
        high: Double,
        low: Double,
        close: Double = 100.0,
    ): DailyCandle = DailyCandle(
        instrumentToken = 1L,
        symbol = "ABC",
        candleDate = date,
        open = open,
        high = high,
        low = low,
        close = close,
        volume = 1L,
    )
}
