package com.tradingtool.core.strategy.fridaystrengthbacktest

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FridayCloseStrengthBacktestEngineTest {
    private val engine = FridayCloseStrengthBacktestEngine()
    private val member = FridayCloseStrengthMember("INFY", "Infosys", 1L)

    @Test
    fun `records next week's maximum upside for a qualifying Friday`() {
        val observations = engine.run(
            member = member,
            candles = listOf(
                candle("2026-07-02", 99.0, 101.0, 98.0, 100.0),
                candle("2026-07-03", 102.0, 106.0, 102.0, 105.0),
                candle("2026-07-06", 110.0, 112.0, 108.0, 111.0),
                candle("2026-07-07", 111.0, 115.0, 110.0, 114.0),
                candle("2026-07-08", 114.0, 120.0, 113.0, 119.0),
                candle("2026-07-09", 118.0, 118.0, 115.0, 116.0),
                candle("2026-07-10", 116.0, 117.0, 114.0, 115.0),
            ),
            testFrom = LocalDate.parse("2026-06-01"),
            toDate = LocalDate.parse("2026-07-31"),
        )

        assertEquals(1, observations.size)
        val observation = observations.single()
        assertEquals("2026-07-03", observation.signalDate)
        assertEquals("2026-07-06", observation.entryDate)
        assertEquals(110.0, observation.entryPrice)
        assertEquals("2026-07-08", observation.followingWeekHighDate)
        assertEquals(120.0, observation.followingWeekHigh)
        assertEquals(9.09, observation.maximumUpsidePct)
        assertEquals(75.0, observation.fridayClosePositionPct)
        assertEquals(5.0, observation.fridayMovePct)
    }

    @Test
    fun `uses the first available session after a Monday holiday`() {
        val observations = engine.run(
            member = member,
            candles = listOf(
                candle("2026-07-02", 99.0, 101.0, 98.0, 100.0),
                candle("2026-07-03", 102.0, 106.0, 102.0, 105.0),
                candle("2026-07-07", 110.0, 113.0, 109.0, 112.0),
                candle("2026-07-08", 112.0, 114.0, 111.0, 113.0),
            ),
            testFrom = LocalDate.parse("2026-06-01"),
            toDate = LocalDate.parse("2026-07-31"),
        )

        assertEquals("2026-07-07", observations.single().entryDate)
        assertEquals(3.64, observations.single().maximumUpsidePct)
    }

    @Test
    fun `requires a move strictly above two percent and a completed following week`() {
        val exactTwoPercent = engine.run(
            member = member,
            candles = listOf(
                candle("2026-07-02", 99.0, 101.0, 98.0, 100.0),
                candle("2026-07-03", 102.0, 106.0, 102.0, 102.0),
                candle("2026-07-06", 103.0, 104.0, 102.0, 103.0),
                candle("2026-07-10", 103.0, 104.0, 102.0, 103.0),
            ),
            testFrom = LocalDate.parse("2026-06-01"),
            toDate = LocalDate.parse("2026-07-31"),
        )
        assertTrue(exactTwoPercent.isEmpty())

        val incompleteFollowingWeek = engine.run(
            member = member,
            candles = listOf(
                candle("2026-07-02", 99.0, 101.0, 98.0, 100.0),
                candle("2026-07-03", 102.0, 106.0, 102.0, 105.0),
                candle("2026-07-06", 110.0, 112.0, 108.0, 111.0),
            ),
            testFrom = LocalDate.parse("2026-06-01"),
            toDate = LocalDate.parse("2026-07-07"),
        )
        assertTrue(incompleteFollowingWeek.isEmpty())
    }

    private fun candle(
        date: String,
        open: Double,
        high: Double,
        low: Double,
        close: Double,
    ): DailyCandle = DailyCandle(
        instrumentToken = 1L,
        symbol = "INFY",
        candleDate = LocalDate.parse(date),
        open = open,
        high = high,
        low = low,
        close = close,
        volume = 100L,
    )
}
