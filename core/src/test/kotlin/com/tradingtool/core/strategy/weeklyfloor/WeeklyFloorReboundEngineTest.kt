package com.tradingtool.core.strategy.weeklyfloor

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WeeklyFloorReboundEngineTest {
    private val engine = WeeklyFloorReboundEngine()

    @Test
    fun `audits every active day and exits a same-day rebound at its target`() {
        val report = engine.run(
            symbol = "NETWEB",
            candles = listOf(
                candle("2025-10-09", high = 3_200.0, low = 3_100.0),
                candle("2025-10-10", high = 3_250.0, low = 3_015.0),
                candle("2025-10-13", high = 3_130.0, low = 3_090.0),
            ),
            floor = 3_015.0,
            ceiling = 3_080.0,
            activeFrom = LocalDate.parse("2025-10-10"),
        )

        assertEquals(2, report.dailyData.size)
        assertEquals("ENTRY_AND_TARGET_SAME_DAY", report.dailyData[0].decision)
        assertEquals("LOW_OUTSIDE_MANUAL_ZONE", report.dailyData[1].decision)
        assertEquals(1, report.trades.size)
        assertEquals("TARGET_HIT", report.trades.single().outcome)
        assertEquals(3_045.15, report.trades.single().entryPrice)
        assertEquals(0, report.trades.single().holdingTradingDays)
    }

    @Test
    fun `holds until a later trading day reaches the target`() {
        val report = engine.run(
            symbol = "NETWEB",
            candles = listOf(
                candle("2025-10-10", high = 3_100.0, low = 3_015.0),
                candle("2025-10-13", high = 3_140.0, low = 3_090.0),
                candle("2025-10-14", high = 3_198.0, low = 3_150.0),
            ),
            floor = 3_015.0,
            ceiling = 3_080.0,
            activeFrom = LocalDate.parse("2025-10-10"),
        )

        val trade = report.trades.single()
        assertEquals("TARGET_HIT", trade.outcome)
        assertEquals("2025-10-14", trade.exitDate)
        assertEquals(2, trade.holdingTradingDays)
        assertEquals(5.0, trade.returnPct)
        assertNull(trade.stopPrice)
        assertEquals("ENTRY_TRIGGERED", report.dailyData[0].decision)
        assertEquals("POSITION_OPEN_WAITING_FOR_TARGET", report.dailyData[1].decision)
        assertEquals("TARGET_HIT", report.dailyData[2].decision)
    }

    private fun candle(date: String, high: Double, low: Double): DailyCandle = DailyCandle(
        instrumentToken = 1L,
        symbol = "NETWEB",
        candleDate = LocalDate.parse(date),
        open = low,
        high = high,
        low = low,
        close = high,
        volume = 100L,
    )
}
