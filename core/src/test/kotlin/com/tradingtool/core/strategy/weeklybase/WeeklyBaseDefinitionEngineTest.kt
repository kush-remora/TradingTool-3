package com.tradingtool.core.strategy.weeklybase

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeeklyBaseDefinitionEngineTest {
    private val engine = WeeklyBaseDefinitionEngine()

    @Test
    fun `creates a valid two percent zone from the three prior weekly lows`() {
        val report = engine.run("NETWEB", candles(listOf(100.0, 102.0, 101.0), currentWeekLow = 99.0), config(), backtestTradingDays = 1)

        val row = report.rows.single()
        assertEquals("2025-01-27", row.evaluationDate)
        assertEquals(100.0, row.firstWeekLow)
        assertEquals(102.0, row.secondWeekLow)
        assertEquals(101.0, row.thirdWeekLow)
        assertEquals(100.0, row.zoneFloor)
        assertEquals(102.0, row.zoneCeiling)
        assertEquals(2.0, row.zoneWidthPct)
        assertTrue(row.sma200 in 110.0..111.0)
        assertTrue(row.isWithinSma200Range)
        assertTrue(row.isValid)
    }

    @Test
    fun `rejects a zone wider than two percent`() {
        val report = engine.run("NETWEB", candles(listOf(100.0, 103.0, 101.0), currentWeekLow = 99.0), config(), backtestTradingDays = 1)

        assertFalse(report.rows.single().isValid)
    }

    @Test
    fun `rejects a narrow base outside its configured 200 SMA range`() {
        val report = engine.run("NETWEB", candles(listOf(100.0, 102.0, 101.0), currentWeekLow = 80.0), config(), backtestTradingDays = 1)

        val row = report.rows.single()
        assertFalse(row.isWithinSma200Range)
        assertFalse(row.isValid)
    }

    private fun candles(weeklyLows: List<Double>, currentWeekLow: Double): List<DailyCandle> {
        val weeks = weeklyLows.mapIndexed { weekIndex, low ->
            weeklyCandles(LocalDate.of(2025, 1, 6).plusWeeks(weekIndex.toLong()), low)
        }
        return historicalCandles() + weeks.flatten() + candle(LocalDate.of(2025, 1, 27), currentWeekLow)
    }

    private fun historicalCandles(): List<DailyCandle> =
        (0L until 200L).map { offset -> candle(LocalDate.of(2024, 1, 1).plusDays(offset), low = 110.0, high = 120.0) }

    private fun weeklyCandles(startDate: LocalDate, low: Double): List<DailyCandle> =
        (0L..4L).map { offset -> candle(startDate.plusDays(offset), if (offset == 2L) low else low + 5.0) }

    private fun candle(date: LocalDate, low: Double, high: Double = low + 2.0): DailyCandle = DailyCandle(
        instrumentToken = 1L,
        symbol = "NETWEB",
        candleDate = date,
        open = low + 1.0,
        high = high,
        low = low,
        close = low + 1.0,
        volume = 100L,
    )

    private fun config(): WeeklyBaseDefinitionConfig = WeeklyBaseDefinitionConfig()
}
