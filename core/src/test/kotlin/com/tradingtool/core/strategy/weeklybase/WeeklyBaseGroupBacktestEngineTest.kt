package com.tradingtool.core.strategy.weeklybase

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class WeeklyBaseGroupBacktestEngineTest {
    @Test
    fun `enters a valid base rebound and records a same-day target`() {
        val result = WeeklyBaseGroupBacktestEngine().run("NETWEB", candles(), WeeklyBaseDefinitionConfig())

        assertEquals(1, result.trades.size)
        assertEquals("TARGET_HIT", result.trades.single().outcome)
        assertEquals(0, result.trades.single().holdingTradingDays)
    }

    private fun candles(): List<DailyCandle> {
        val history = (0L until 200L).map { offset -> candle(LocalDate.of(2024, 1, 1).plusDays(offset), 110.0, 120.0) }
        val baseWeeks = listOf(100.0, 102.0, 101.0).flatMapIndexed { week, low ->
            (0L..4L).map { offset -> candle(LocalDate.of(2025, 1, 6).plusWeeks(week.toLong()).plusDays(offset), if (offset == 2L) low else low + 5.0) }
        }
        return history + baseWeeks + candle(LocalDate.of(2025, 1, 27), 99.0, 110.0)
    }

    private fun candle(date: LocalDate, low: Double, high: Double = low + 2.0): DailyCandle = DailyCandle(1L, "NETWEB", date, low + 1.0, high, low, low + 1.0, 100L)
}
