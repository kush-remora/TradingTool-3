package com.tradingtool.core.strategy.twodayclosestrengthbacktest

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TwoDayCloseStrengthBacktestEngineTest {
    private val engine = TwoDayCloseStrengthBacktestEngine()
    private val member = TwoDayCloseStrengthMember("INFY", "Infosys", 1L)

    @Test
    fun `buys after three weak sessions and two strong sessions then hits target`() {
        val observations = engine.run(
            member = member,
            candles = signalWeek() + listOf(
                candle("2026-07-13", 100.0, 104.0, 99.0, 103.0),
                candle("2026-07-14", 100.0, 106.0, 99.0, 104.0),
                candle("2026-07-15", 104.0, 105.0, 101.0, 103.0),
                candle("2026-07-16", 103.0, 104.0, 100.0, 102.0),
                candle("2026-07-17", 102.0, 103.0, 99.0, 101.0),
            ),
            testFrom = LocalDate.parse("2026-06-01"),
            toDate = LocalDate.parse("2026-07-31"),
        )

        val observation = observations.single()
        assertEquals("2026-07-06", observation.patternStartDate)
        assertEquals("2026-07-10", observation.patternEndDate)
        assertEquals(listOf(50.0, 60.0, 75.0, 85.0, 90.0), observation.patternClosePositionPct)
        assertEquals("2026-07-13", observation.entryDate)
        assertEquals(105.0, observation.targetPrice)
        assertEquals("2026-07-14", observation.exitDate)
        assertEquals(105.0, observation.exitPrice)
        assertEquals(TwoDayCloseStrengthExitReasons.TARGET_HIT, observation.exitReason)
        assertEquals(5.0, observation.realizedReturnPct)
    }

    @Test
    fun `exits at Thursday close when target is not reached`() {
        val observations = engine.run(
            member = member,
            candles = signalWeek() + listOf(
                candle("2026-07-13", 100.0, 104.0, 99.0, 103.0),
                candle("2026-07-14", 103.0, 104.0, 100.0, 102.0),
                candle("2026-07-15", 102.0, 104.0, 100.0, 103.0),
                candle("2026-07-16", 103.0, 104.0, 101.0, 98.0),
                candle("2026-07-17", 98.0, 100.0, 96.0, 97.0),
            ),
            testFrom = LocalDate.parse("2026-06-01"),
            toDate = LocalDate.parse("2026-07-31"),
        )

        val observation = observations.single()
        assertEquals("2026-07-16", observation.exitDate)
        assertEquals(98.0, observation.exitPrice)
        assertEquals(TwoDayCloseStrengthExitReasons.THURSDAY_CLOSE_EXIT, observation.exitReason)
        assertEquals(-2.0, observation.realizedReturnPct)
    }

    @Test
    fun `requires the first three sessions below threshold`() {
        val candles = signalWeek().mapIndexed { index, candle ->
            if (index == 2) candle.copy(close = 108.0) else candle
        }

        assertTrue(
            engine.run(
                member = member,
                candles = candles + nextWeekCandles(),
                testFrom = LocalDate.parse("2026-06-01"),
                toDate = LocalDate.parse("2026-07-31"),
            ).isEmpty(),
        )
    }

    private fun signalWeek(): List<DailyCandle> = listOf(
        candle("2026-07-06", 100.0, 110.0, 100.0, 105.0),
        candle("2026-07-07", 100.0, 110.0, 100.0, 106.0),
        candle("2026-07-08", 100.0, 110.0, 100.0, 107.5),
        candle("2026-07-09", 100.0, 110.0, 100.0, 108.5),
        candle("2026-07-10", 100.0, 110.0, 100.0, 109.0),
    )

    private fun nextWeekCandles(): List<DailyCandle> = listOf(
        candle("2026-07-13", 100.0, 104.0, 99.0, 103.0),
        candle("2026-07-14", 100.0, 104.0, 99.0, 103.0),
        candle("2026-07-15", 100.0, 104.0, 99.0, 103.0),
        candle("2026-07-16", 100.0, 104.0, 99.0, 103.0),
        candle("2026-07-17", 100.0, 104.0, 99.0, 103.0),
    )

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
