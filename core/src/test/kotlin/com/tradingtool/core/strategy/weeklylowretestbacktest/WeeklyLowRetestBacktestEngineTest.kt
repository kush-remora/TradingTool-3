package com.tradingtool.core.strategy.weeklylowretestbacktest

import com.tradingtool.core.candle.DailyCandle
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeeklyLowRetestBacktestEngineTest {
    private val engine = WeeklyLowRetestBacktestEngine()
    private val member = WeeklyLowRetestMember("INFY", "Infosys", 1L)

    @Test
    fun `creates independent day six and day seven orders and fills both on day seven`() {
        val observations = runScenario(
            candle("2026-07-06", 101.0, 102.0, 100.0, 102.0),
            candle("2026-07-07", 102.0, 103.0, 102.0, 103.0),
            candle("2026-07-08", 102.0, 103.0, 101.0, 103.0),
            candle("2026-07-09", 102.0, 103.0, 101.0, 103.0),
            candle("2026-07-10", 103.0, 106.1, 103.0, 106.0),
            candle("2026-07-13", 106.0, 106.0, 101.0, 101.0),
            candle("2026-07-14", 102.0, 102.0, 100.0, 102.0),
            candle("2026-07-15", 102.0, 105.5, 101.0, 105.0),
            candle("2026-07-16", 105.0, 106.2, 104.0, 106.0),
            candle("2026-07-17", 106.0, 106.5, 104.0, 106.0),
        )

        val daySix = observations.first { observation -> observation.limitOrderDate == "2026-07-13" }
        val daySeven = observations.first { observation -> observation.limitOrderDate == "2026-07-14" }

        assertEquals("2026-07-06", daySix.anchorDate)
        assertEquals(100.0, daySix.anchorLow)
        assertEquals("2026-07-10", daySix.triggerDate)
        assertEquals(106.1, daySix.triggerHigh)
        assertEquals("2026-07-13", daySix.limitOrderDate)
        assertEquals(100.5, daySix.limitPrice)
        assertEquals("2026-07-14", daySix.fillDate)
        assertEquals(100.5, daySix.fillPrice)
        assertEquals(105.0, daySix.targetPrice)
        assertEquals(WeeklyLowRetestOutcomes.TARGET_HIT, daySix.outcome)
        assertEquals(2, daySix.holdingSessions)

        assertEquals("2026-07-06", daySeven.anchorDate)
        assertEquals(100.0, daySeven.anchorLow)
        assertEquals("2026-07-08", daySeven.recentCycleLowDate)
        assertEquals(101.0, daySeven.recentCycleLow)
        assertEquals("2026-07-14", daySeven.limitOrderDate)
        assertEquals(100.5, daySeven.limitPrice)
        assertEquals("2026-07-14", daySeven.fillDate)
        assertEquals(100.5, daySeven.fillPrice)
        assertEquals(105.0, daySeven.targetPrice)
        assertEquals(4.48, daySeven.realizedReturnPct)
        assertEquals(2, daySeven.holdingSessions)
        assertTrue(daySeven.triggerMovePct >= 5.0)
    }

    @Test
    fun `uses trading sessions rather than calendar days for fourth session exit`() {
        val observation = runFridayScenario().first { it.limitOrderDate == "2026-07-10" }

        assertEquals("2026-07-15", observation.limitOrderExpiryDate)
        assertEquals(WeeklyLowRetestOutcomes.FOURTH_SESSION_EXIT, observation.outcome)
        assertEquals("2026-07-15", observation.exitDate)
    }

    @Test
    fun `records no fill and hypothetical fourth close pnl`() {
        val observation = runScenario(
            candle("2026-07-06", 101.0, 102.0, 100.0, 102.0),
            candle("2026-07-07", 102.0, 103.0, 102.0, 103.0),
            candle("2026-07-08", 102.0, 103.0, 101.0, 103.0),
            candle("2026-07-09", 102.0, 103.0, 101.0, 103.0),
            candle("2026-07-10", 103.0, 105.0, 103.0, 105.0),
            candle("2026-07-13", 105.0, 105.0, 101.0, 102.0),
            candle("2026-07-14", 102.0, 103.0, 101.0, 102.0),
            candle("2026-07-15", 102.0, 104.0, 101.0, 103.0),
            candle("2026-07-16", 103.0, 104.0, 101.0, 103.5),
        ).first { it.limitOrderDate == "2026-07-13" }

        assertEquals("2026-07-13", observation.limitOrderDate)
        assertEquals(WeeklyLowRetestOutcomes.NO_FILL, observation.outcome)
        assertEquals(101.0, observation.orderWindowLow)
        assertEquals("2026-07-16", observation.fourthSessionCloseDate)
        assertEquals(103.5, observation.fourthSessionClose)
        assertEquals(2.99, observation.noFillFourthSessionPnlPct)
        assertNull(observation.fillDate)
        assertNull(observation.realizedReturnPct)
    }

    @Test
    fun `exits a filled trade at fourth session close when target is not reached`() {
        val observation = runScenario(
            candle("2026-07-06", 101.0, 102.0, 100.0, 102.0),
            candle("2026-07-07", 102.0, 103.0, 102.0, 103.0),
            candle("2026-07-08", 102.0, 103.0, 101.0, 103.0),
            candle("2026-07-09", 102.0, 103.0, 101.0, 103.0),
            candle("2026-07-10", 103.0, 105.0, 103.0, 105.0),
            candle("2026-07-13", 101.0, 104.0, 100.5, 103.0),
            candle("2026-07-14", 103.0, 104.0, 102.0, 103.0),
            candle("2026-07-15", 103.0, 104.0, 102.0, 103.0),
            candle("2026-07-16", 103.0, 104.0, 102.0, 103.0),
        ).first { it.limitOrderDate == "2026-07-13" }

        assertEquals(WeeklyLowRetestOutcomes.FOURTH_SESSION_EXIT, observation.outcome)
        assertEquals("2026-07-16", observation.exitDate)
        assertEquals(103.0, observation.exitPrice)
        assertEquals(2.49, observation.realizedReturnPct)
    }

    @Test
    fun `uses green candle assumption for same day low then high`() {
        val observation = runScenario(
            candle("2026-07-06", 105.0, 105.0, 105.0, 105.0),
            candle("2026-07-07", 105.0, 105.0, 105.0, 105.0),
            candle("2026-07-08", 105.0, 105.0, 105.0, 105.0),
            candle("2026-07-09", 100.0, 106.0, 100.0, 105.0),
            candle("2026-07-10", 105.0, 106.0, 104.0, 105.0),
            candle("2026-07-13", 105.0, 106.0, 104.0, 105.0),
            candle("2026-07-14", 105.0, 106.0, 104.0, 105.0),
            candle("2026-07-15", 105.0, 106.0, 104.0, 105.0),
            candle("2026-07-16", 105.0, 106.0, 104.0, 105.0),
            candle("2026-07-17", 105.0, 106.0, 104.0, 105.0),
        ).first { it.limitOrderDate == "2026-07-14" }

        assertEquals("SAME_DAY_GREEN_LOW_THEN_HIGH", observation.cycleSequence)
        assertEquals("2026-07-14", observation.limitOrderDate)
    }

    @Test
    fun `uses configured one percent limit offset`() {
        val observation = runScenario(
            candle("2026-07-06", 101.0, 102.0, 100.0, 102.0),
            candle("2026-07-07", 102.0, 103.0, 102.0, 103.0),
            candle("2026-07-08", 102.0, 103.0, 101.0, 103.0),
            candle("2026-07-09", 102.0, 103.0, 101.0, 103.0),
            candle("2026-07-10", 103.0, 105.0, 103.0, 105.0),
            candle("2026-07-13", 103.0, 105.0, 101.0, 104.0),
            candle("2026-07-14", 104.0, 106.0, 101.0, 105.0),
            candle("2026-07-15", 105.0, 106.0, 104.0, 105.0),
            candle("2026-07-16", 105.0, 106.0, 104.0, 105.0),
            limitOffsetPct = 1.0,
        ).first { it.limitOrderDate == "2026-07-13" }

        assertEquals(101.0, observation.limitPrice)
        assertNotNull(observation.fillDate)
        assertEquals(101.0, observation.fillPrice)
    }

    @Test
    fun `uses configured target percentage from buying day low`() {
        val observation = runScenario(
            candle("2026-07-06", 101.0, 102.0, 100.0, 102.0),
            candle("2026-07-07", 102.0, 103.0, 102.0, 103.0),
            candle("2026-07-08", 102.0, 103.0, 101.0, 103.0),
            candle("2026-07-09", 102.0, 103.0, 101.0, 103.0),
            candle("2026-07-10", 103.0, 105.0, 103.0, 105.0),
            candle("2026-07-13", 101.0, 102.5, 100.0, 102.0),
            candle("2026-07-14", 102.0, 103.0, 102.0, 103.0),
            candle("2026-07-15", 103.0, 104.0, 102.0, 103.0),
            candle("2026-07-16", 103.0, 104.0, 102.0, 103.0),
            targetPct = 3.0,
        ).first { it.limitOrderDate == "2026-07-13" }

        assertEquals(100.0, observation.fillLow)
        assertEquals(103.0, observation.targetPrice)
        assertEquals(WeeklyLowRetestOutcomes.TARGET_HIT, observation.outcome)
    }

    private fun runScenario(
        vararg scenarioCandles: DailyCandle,
        limitOffsetPct: Double = 0.5,
        targetPct: Double = 5.0,
    ): List<WeeklyLowRetestObservation> {
        return engine.run(
            member = member,
            candles = historyCandles() + scenarioCandles,
            testFrom = LocalDate.parse("2026-06-01"),
            toDate = LocalDate.parse("2026-07-31"),
            limitOffsetPct = limitOffsetPct,
            targetPct = targetPct,
        )
    }

    private fun runFridayScenario(): List<WeeklyLowRetestObservation> {
        return engine.run(
            member = member,
            candles = historyCandles() + listOf(
                candle("2026-06-26", 100.0, 100.0, 100.0, 100.0),
                candle("2026-06-29", 102.0, 102.0, 102.0, 102.0),
                candle("2026-06-30", 102.0, 102.0, 102.0, 102.0),
                candle("2026-07-01", 102.0, 102.0, 102.0, 102.0),
                candle("2026-07-02", 102.0, 102.0, 102.0, 102.0),
                candle("2026-07-03", 102.0, 103.0, 101.0, 102.0),
                candle("2026-07-06", 102.0, 104.0, 101.0, 103.0),
                candle("2026-07-07", 103.0, 105.0, 101.0, 104.0),
                candle("2026-07-08", 104.0, 105.0, 101.0, 104.0),
                candle("2026-07-09", 104.0, 106.1, 101.0, 105.0),
                candle("2026-07-10", 102.0, 104.0, 100.0, 102.0),
                candle("2026-07-13", 102.0, 104.0, 101.0, 103.0),
                candle("2026-07-14", 103.0, 104.0, 102.0, 103.0),
                candle("2026-07-15", 103.0, 104.0, 102.0, 103.0),
            ),
            testFrom = LocalDate.parse("2026-06-01"),
            toDate = LocalDate.parse("2026-07-31"),
            limitOffsetPct = 0.5,
            targetPct = 5.0,
        )
    }

    private fun historyCandles(): List<DailyCandle> = generateSequence(LocalDate.parse("2025-07-01")) { date ->
        date.plusDays(1)
    }
        .filter { date -> date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY }
        .take(252)
        .map { date -> candle(date.toString(), 120.0, 120.0, 120.0, 120.0) }
        .toList()

    private fun candle(
        date: String,
        open: Double,
        high: Double,
        low: Double,
        close: Double,
        volume: Long = 100L,
    ): DailyCandle = DailyCandle(
        instrumentToken = 1L,
        symbol = "INFY",
        candleDate = LocalDate.parse(date),
        open = open,
        high = high,
        low = low,
        close = close,
        volume = volume,
    )
}
