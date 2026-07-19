package com.tradingtool.core.strategy.csvbacktest

import com.tradingtool.core.candle.DailyCandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CsvBacktestEarlyDipCalculatorTest {

    @Test
    fun `uses the entry candle plus four following sessions`() {
        val dip = CsvBacktestEarlyDipCalculator.calculate(
            entryPrice = 100.0,
            candles = listOf(
                candle(0, low = 98.0),
                candle(1, low = 97.0),
                candle(2, low = 95.0),
                candle(3, low = 96.0),
                candle(4, low = 94.0),
                candle(5, low = 90.0),
            ),
        )

        assertNotNull(dip)
        assertEquals(94.0, dip?.lowestPrice)
        assertEquals(6.0, dip?.dropAmount)
        assertEquals(6.0, dip?.dropPct)
    }

    private fun candle(dayOffset: Long, low: Double): DailyCandle = DailyCandle(
        instrumentToken = 1L,
        symbol = "ABC",
        candleDate = LocalDate.of(2026, 1, 5).plusDays(dayOffset),
        open = 100.0,
        high = 101.0,
        low = low,
        close = 100.0,
        volume = 1L,
    )
}
