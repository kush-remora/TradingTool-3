package com.tradingtool.core.strategy.csvbacktest

import com.tradingtool.core.candle.DailyCandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CsvBacktestCandleColorCalculatorTest {

    @Test
    fun `counts red candles on the entry day and next two sessions`() {
        val entryDate = LocalDate.of(2026, 1, 20)
        val candles = listOf(
            candle(entryDate.minusDays(1), close = 100.0),
            candle(entryDate, close = 99.0),
            candle(entryDate.plusDays(1), close = 101.0),
            candle(entryDate.plusDays(2), close = 100.0),
            candle(entryDate.plusDays(3), close = 98.0),
        )

        assertEquals(2, CsvBacktestCandleColorCalculator.countRedCandles(candles, entryDate))
    }

    private fun candle(date: LocalDate, close: Double): DailyCandle = DailyCandle(
        instrumentToken = 1L,
        symbol = "ABC",
        candleDate = date,
        open = close,
        high = close,
        low = close,
        close = close,
        volume = 1L,
    )
}
