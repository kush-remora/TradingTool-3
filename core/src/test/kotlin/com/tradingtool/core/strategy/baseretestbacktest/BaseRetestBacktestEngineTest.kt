package com.tradingtool.core.strategy.baseretestbacktest

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BaseRetestBacktestEngineTest {
    private val engine = BaseRetestBacktestEngine()
    private val member = BaseRetestMember("INFY", "Infosys", 1L)

    @Test
    fun `confirms matching lows and buys the third visit on the next session`() {
        val observations = run(
            candle("2026-07-01", 102.0, 102.0, 100.0, 101.0),
            candle("2026-07-02", 102.0, 105.0, 102.0, 104.0),
            candle("2026-07-03", 103.0, 103.0, 100.5, 101.0),
            candle("2026-07-06", 101.0, 105.6, 101.0, 105.0),
            candle("2026-07-07", 104.0, 104.0, 100.8, 102.0),
            candle("2026-07-08", 102.0, 106.1, 102.0, 106.0),
        )

        assertEquals(1, observations.size)
        val observation = observations.single()
        assertEquals(100.0, observation.firstLow)
        assertEquals(100.5, observation.secondLow)
        assertEquals(0.5, observation.lowDifferencePct)
        assertEquals("2026-07-06", observation.confirmationDate)
        assertEquals("2026-07-07", observation.orderActiveDate)
        assertEquals(101.0, observation.limitPrice)
        assertEquals(101.0, observation.fillPrice)
        assertEquals(BaseRetestOutcomes.TARGET_HIT, observation.outcome)
        assertEquals(5.0, observation.pnlPct)
        assertEquals(2, observation.holdingSessions)
    }

    @Test
    fun `does not create a base when the second low differs by two percent`() {
        val observations = run(
            candle("2026-07-01", 102.0, 102.0, 100.0, 101.0),
            candle("2026-07-02", 102.0, 105.0, 102.0, 104.0),
            candle("2026-07-03", 104.0, 104.0, 102.0, 103.0),
            candle("2026-07-06", 104.0, 108.0, 103.0, 107.0),
            candle("2026-07-07", 107.0, 108.0, 104.0, 105.0),
        )

        assertTrue(observations.isEmpty())
    }

    @Test
    fun `requires a five percent rebound between the first and second lows`() {
        val observations = run(
            candle("2026-07-01", 101.0, 101.0, 100.0, 100.5),
            candle("2026-07-02", 101.0, 104.9, 101.0, 104.0),
            candle("2026-07-03", 103.0, 103.0, 100.0, 101.0),
            candle("2026-07-06", 101.0, 105.0, 101.0, 104.0),
            candle("2026-07-07", 104.0, 105.0, 102.0, 104.0),
        )

        assertTrue(observations.isEmpty())
    }

    @Test
    fun `uses configured stop loss and reports one holding session`() {
        val observation = run(
            candle("2026-07-01", 102.0, 102.0, 100.0, 101.0),
            candle("2026-07-02", 102.0, 105.0, 102.0, 104.0),
            candle("2026-07-03", 103.0, 103.0, 100.0, 101.0),
            candle("2026-07-06", 101.0, 105.1, 101.0, 105.0),
            candle("2026-07-07", 104.0, 112.0, 98.0, 100.0),
            targetPct = 10.0,
            stopLossPct = 2.0,
        ).single()

        assertEquals(98.98, observation.stopLossPrice)
        assertEquals(BaseRetestOutcomes.STOP_LOSS, observation.outcome)
        assertEquals(-2.0, observation.pnlPct)
        assertEquals(1, observation.holdingSessions)
    }

    @Test
    fun `keeps an untouched order active through the end of available data`() {
        val observation = run(
            candle("2026-07-01", 102.0, 102.0, 100.0, 101.0),
            candle("2026-07-02", 102.0, 105.0, 102.0, 104.0),
            candle("2026-07-03", 103.0, 103.0, 100.0, 101.0),
            candle("2026-07-06", 101.0, 105.1, 101.0, 105.0),
            candle("2026-07-07", 105.0, 107.0, 103.0, 106.0),
            candle("2026-07-08", 106.0, 108.0, 104.0, 107.0),
        ).single()

        assertEquals(BaseRetestOutcomes.NO_FILL, observation.outcome)
        assertEquals("2026-07-08", observation.orderEndDate)
        assertEquals(null, observation.pnlPct)
        assertEquals(null, observation.holdingSessions)
    }

    @Test
    fun `starts a fresh search when an unconfirmed base breaks below tolerance`() {
        val observation = run(
            candle("2026-07-01", 102.0, 102.0, 100.0, 101.0),
            candle("2026-07-02", 102.0, 105.0, 102.0, 104.0),
            candle("2026-07-03", 103.0, 103.0, 100.0, 101.0),
            candle("2026-07-06", 100.0, 101.0, 98.0, 99.0),
            candle("2026-07-07", 100.0, 103.0, 100.0, 102.0),
            candle("2026-07-08", 102.0, 102.0, 98.5, 99.0),
            candle("2026-07-09", 99.0, 103.5, 99.0, 103.0),
            candle("2026-07-10", 103.0, 104.0, 98.9, 100.0),
        ).single()

        assertEquals("2026-07-06", observation.firstLowDate)
        assertEquals(98.0, observation.basePrice)
        assertEquals("2026-07-10", observation.fillDate)
    }

    @Test
    fun `takes a known target gap before a later intraday stop touch`() {
        val observation = run(
            candle("2026-07-01", 102.0, 102.0, 100.0, 101.0),
            candle("2026-07-02", 102.0, 105.0, 102.0, 104.0),
            candle("2026-07-03", 103.0, 103.0, 100.0, 101.0),
            candle("2026-07-06", 101.0, 105.1, 101.0, 105.0),
            candle("2026-07-07", 104.0, 104.0, 100.8, 102.0),
            candle("2026-07-08", 107.0, 108.0, 94.0, 100.0),
        ).single()

        assertEquals(BaseRetestOutcomes.TARGET_HIT, observation.outcome)
        assertEquals(107.0, observation.exitPrice)
        assertEquals(5.94, observation.pnlPct)
    }

    @Test
    fun `discovers the same bases regardless of target exit timing`() {
        val candles = listOf(
            candle("2026-07-01", 102.0, 102.0, 100.0, 101.0),
            candle("2026-07-02", 102.0, 105.0, 102.0, 104.0),
            candle("2026-07-03", 103.0, 103.0, 100.0, 101.0),
            candle("2026-07-06", 101.0, 105.1, 101.0, 105.0),
            candle("2026-07-07", 104.0, 104.0, 100.8, 102.0),
            candle("2026-07-08", 102.0, 106.1, 102.0, 106.0),
            // This low rebounds but is never revisited. It must not hide the later, higher base.
            candle("2026-07-09", 108.0, 108.0, 106.0, 107.0),
            candle("2026-07-10", 108.0, 112.0, 108.0, 111.0),
            candle("2026-07-13", 121.0, 122.0, 120.0, 121.0),
            candle("2026-07-14", 122.0, 126.0, 122.0, 125.0),
            candle("2026-07-15", 124.0, 124.0, 120.5, 121.0),
            candle("2026-07-16", 121.0, 126.6, 121.0, 126.0),
            candle("2026-07-17", 125.0, 125.0, 121.0, 123.0),
            candle("2026-07-20", 123.0, 146.0, 123.0, 145.0),
        )

        val fivePercent = engine.run(member, candles, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"), 5.0, 5.0)
        val twentyPercent = engine.run(member, candles, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"), 20.0, 5.0)

        assertEquals(2, fivePercent.size)
        assertEquals(2, twentyPercent.size)
        assertEquals(
            fivePercent.map(BaseRetestObservation::confirmationDate),
            twentyPercent.map(BaseRetestObservation::confirmationDate),
        )
    }

    private fun run(
        vararg candles: DailyCandle,
        targetPct: Double = 5.0,
        stopLossPct: Double = 5.0,
    ): List<BaseRetestObservation> = engine.run(
        member = member,
        candles = candles.toList(),
        testFrom = LocalDate.parse("2026-07-01"),
        toDate = LocalDate.parse("2026-07-31"),
        targetPct = targetPct,
        stopLossPct = stopLossPct,
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
