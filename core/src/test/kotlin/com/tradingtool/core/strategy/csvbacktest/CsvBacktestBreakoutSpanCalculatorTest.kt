package com.tradingtool.core.strategy.csvbacktest

import com.tradingtool.core.candle.DailyCandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CsvBacktestBreakoutSpanCalculatorTest {

    @Test
    fun `returns sessions since the most recent blocking close`() {
        val candles = candles(
            priorCloses = listOf(120.0) + List(120) { 100.0 },
            signalHigh = 110.0,
        )

        val result = CsvBacktestBreakoutSpanCalculator.calculate(
            candles = candles,
            signalDate = candles.last().candleDate,
        )

        assertEquals(120, result?.sessions)
        assertFalse(result?.isLowerBound ?: true)
    }

    @Test
    fun `marks the result as a lower bound when all available closes are cleared`() {
        val candles = candles(
            priorCloses = List(80) { 100.0 },
            signalHigh = 110.0,
        )

        val result = CsvBacktestBreakoutSpanCalculator.calculate(
            candles = candles,
            signalDate = candles.last().candleDate,
        )

        assertEquals(80, result?.sessions)
        assertTrue(result?.isLowerBound ?: false)
    }

    @Test
    fun `caps the scan at five hundred sessions`() {
        val candles = candles(
            priorCloses = List(600) { 100.0 },
            signalHigh = 110.0,
        )

        val result = CsvBacktestBreakoutSpanCalculator.calculate(
            candles = candles,
            signalDate = candles.last().candleDate,
        )

        assertEquals(500, result?.sessions)
        assertTrue(result?.isLowerBound ?: false)
    }

    private fun candles(
        priorCloses: List<Double>,
        signalHigh: Double,
    ): List<DailyCandle> {
        val firstDate = LocalDate.of(2025, 1, 1)
        val priorCandles = priorCloses.mapIndexed { index, close ->
            candle(firstDate.plusDays(index.toLong()), high = close, close = close)
        }
        return priorCandles + candle(
            date = firstDate.plusDays(priorCloses.size.toLong()),
            high = signalHigh,
            close = signalHigh - 1.0,
        )
    }

    private fun candle(
        date: LocalDate,
        high: Double,
        close: Double,
    ): DailyCandle = DailyCandle(
        instrumentToken = 1L,
        symbol = "ABC",
        candleDate = date,
        open = close,
        high = high,
        low = close,
        close = close,
        volume = 1_000L,
    )
}
