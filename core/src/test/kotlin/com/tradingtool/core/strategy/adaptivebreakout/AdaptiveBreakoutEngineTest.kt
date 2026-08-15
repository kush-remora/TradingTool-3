package com.tradingtool.core.strategy.adaptivebreakout

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdaptiveBreakoutEngineTest {
    @Test
    fun `finds a fresh breakout only after the rejected ceiling is cleared`() {
        val evaluation = AdaptiveBreakoutEngine.evaluate(candles(82.0, 85.0, 80.0, 82.0, 86.0, 82.0, 85.0, 86.0, 89.0))

        assertNotNull(evaluation)
        assertEquals(AdaptiveBreakoutStatus.FRESH_BREAKOUT, evaluation.status)
        assertEquals(87.0, evaluation.ceiling?.anchorPrice)
        assertTrue(evaluation.ceiling?.upperBoundary?.let { boundary -> boundary in 87.0..89.0 } == true)
        assertEquals(AdaptiveBreakoutDecision.CEILING_CANDIDATE, evaluation.rawSteps.first { step -> step.close == 80.0 && step.date > START_DATE.plusDays(19).toString() }.decision)
        assertEquals(AdaptiveBreakoutDecision.FRESH_BREAKOUT, evaluation.rawSteps.last().decision)
    }

    @Test
    fun `labels an uninterrupted two ATR rise as strong rebound rather than breakout`() {
        val evaluation = AdaptiveBreakoutEngine.evaluate(candlesFromPriorTop(80.0, 81.0, 82.0, 83.0, 84.0, 85.0))

        assertNotNull(evaluation)
        assertEquals(AdaptiveBreakoutStatus.STRONG_REBOUND, evaluation.status)
        assertNull(evaluation.ceiling)
        assertTrue(evaluation.rawSteps.none { step -> step.decision == AdaptiveBreakoutDecision.FRESH_BREAKOUT })
    }

    @Test
    fun `labels later closes as continuation instead of repeating the fresh breakout`() {
        val evaluation = AdaptiveBreakoutEngine.evaluate(candles(82.0, 85.0, 80.0, 82.0, 86.0, 82.0, 85.0, 86.0, 89.0, 90.0))

        assertNotNull(evaluation)
        assertEquals(AdaptiveBreakoutStatus.BREAKOUT_CONTINUATION, evaluation.status)
        assertEquals(AdaptiveBreakoutDecision.BREAKOUT_CONTINUATION, evaluation.rawSteps.last().decision)
        assertEquals(1, evaluation.rawSteps.count { step -> step.decision == AdaptiveBreakoutDecision.FRESH_BREAKOUT })
        val breakoutStep = evaluation.rawSteps.single { step -> step.decision == AdaptiveBreakoutDecision.FRESH_BREAKOUT }
        val breakoutEvidence = assertNotNull(evaluation.breakoutEvidence)
        assertEquals(breakoutStep.date, breakoutEvidence.date)
        assertEquals(50.0, breakoutEvidence.closePositionPct)
        assertNotNull(breakoutEvidence.volumeVsTenDayAverage)
        assertNotNull(breakoutEvidence.distanceFromFiftyTwoWeekHighPct)
    }

    @Test
    fun `keeps nearby rejected peaks in one ceiling area`() {
        val evaluation = AdaptiveBreakoutEngine.evaluate(candles(82.0, 85.0, 80.0, 82.0, 86.0, 82.0))

        assertNotNull(evaluation)
        assertEquals(86.0, evaluation.ceiling?.anchorPrice)
        assertEquals(AdaptiveBreakoutStatus.BELOW_CEILING, evaluation.status)
    }

    @Test
    fun `HFCL forms a local ceiling and breaks it while retaining old resistance as major overhead`() {
        val evaluation = AdaptiveBreakoutEngine.evaluate(hfclCandles())

        assertNotNull(evaluation)
        assertEquals(AdaptiveBreakoutStatus.FRESH_BREAKOUT, evaluation.status)
        assertEquals(195.53, evaluation.ceiling?.anchorPrice)
        assertEquals("2026-07-31", evaluation.ceiling?.confirmedDate)
        assertEquals("2026-08-03", evaluation.ceiling?.breakoutDate)
        assertEquals(231.41, evaluation.majorCeiling?.anchorPrice)
        assertTrue(evaluation.ceiling?.upperBoundary?.let { boundary -> boundary < 202.52 } == true)
        assertEquals(
            AdaptiveBreakoutDecision.FRESH_BREAKOUT,
            evaluation.rawSteps.single { step -> step.date == "2026-08-03" }.decision,
        )
    }

    private fun candles(vararg closes: Double): List<DailyCandle> {
        val warmup = (0 until 20).map { index -> candle(index, 80.0) }
        val path = closes.mapIndexed { index, close -> candle(index + warmup.size, close) }
        return warmup + path
    }

    private fun candlesFromPriorTop(vararg closes: Double): List<DailyCandle> {
        val warmup = (0 until 20).map { index -> candle(index, 100.0) }
        val path = closes.mapIndexed { index, close -> candle(index + warmup.size, close) }
        return warmup + path
    }

    private fun hfclCandles(): List<DailyCandle> {
        val warmup = (0 until 20).map { index ->
            marketCandle(LocalDate.of(2026, 7, 1).plusDays(index.toLong()), 205.0, 209.0, 201.0, 205.0)
        }
        return warmup + listOf(
            marketCandle(LocalDate.of(2026, 7, 22), 220.0, 231.41, 218.0, 225.0),
            marketCandle(LocalDate.of(2026, 7, 23), 222.0, 225.0, 205.0, 208.0),
            marketCandle(LocalDate.of(2026, 7, 24), 211.0, 230.0, 210.0, 218.0),
            marketCandle(LocalDate.of(2026, 7, 27), 208.0, 210.0, 190.0, 194.0),
            marketCandle(LocalDate.of(2026, 7, 28), 190.0, 193.0, 181.05, 188.39),
            marketCandle(LocalDate.of(2026, 7, 29), 184.0, 195.53, 181.05, 193.56),
            marketCandle(LocalDate.of(2026, 7, 30), 193.0, 195.0, 182.92, 184.69),
            marketCandle(LocalDate.of(2026, 7, 31), 185.0, 194.0, 184.0, 193.92),
            marketCandle(LocalDate.of(2026, 8, 3), 195.0, 203.61, 194.0, 202.52),
        )
    }

    private fun marketCandle(
        date: LocalDate,
        open: Double,
        high: Double,
        low: Double,
        close: Double,
    ): DailyCandle = DailyCandle(
        instrumentToken = 1L,
        symbol = "HFCL",
        candleDate = date,
        open = open,
        high = high,
        low = low,
        close = close,
        volume = 1_000L,
    )

    private fun candle(index: Int, close: Double): DailyCandle = DailyCandle(
        instrumentToken = 1L,
        symbol = "TEST",
        candleDate = START_DATE.plusDays(index.toLong()),
        open = close,
        high = close + 1.0,
        low = close - 1.0,
        close = close,
        volume = 1_000L + index,
    )

    private companion object {
        val START_DATE: LocalDate = LocalDate.of(2026, 1, 1)
    }
}
