package com.tradingtool.core.strategy.phasedbreakout

import com.tradingtool.core.candle.DailyCandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PhaseCFreshFieldCalculatorTest {
    @Test
    fun `calculates fresh market fields from the latest daily candles`() {
        val startDate = LocalDate.of(2025, 6, 1)
        val candles = (0 until 252).map { index ->
            val close = 100.0 + index
            DailyCandle(
                instrumentToken = 101L,
                symbol = "TEST",
                candleDate = startDate.plusDays(index.toLong()),
                open = close - 1.0,
                high = close + 5.0,
                low = close - 3.0,
                close = close,
                volume = 1_000L + index,
            )
        }

        val result = PhaseCFreshFieldCalculator.calculate(candles)

        assertEquals(351.0, result.closePrice)
        assertEquals("0.29%", result.pctChange)
        assertEquals(1_251L, result.volume)
        assertEquals(1_250L, result.previousDayVolume)
        assertEquals(356.0, result.high52w)
        assertEquals(97.0, result.low52w)
        assertEquals(-1.404494382022472, result.dist200dHighPct, 0.0000001)
        assertEquals(261.8556701030928, result.dist200dLowPct, 0.0000001)
        assertEquals(startDate.plusDays(251), result.marketFieldsUpdatedOn)
    }

    @Test
    fun `fails when fewer than 252 candles are available`() {
        val candles = (0 until 251).map { index ->
            val close = 100.0 + index
            DailyCandle(
                instrumentToken = 101L,
                symbol = "TEST",
                candleDate = LocalDate.of(2025, 6, 1).plusDays(index.toLong()),
                open = close,
                high = close + 1.0,
                low = close - 1.0,
                close = close,
                volume = 1_000L,
            )
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            PhaseCFreshFieldCalculator.calculate(candles)
        }

        assertEquals("At least 252 daily candles are required to refresh fresh market fields.", error.message)
    }
}
