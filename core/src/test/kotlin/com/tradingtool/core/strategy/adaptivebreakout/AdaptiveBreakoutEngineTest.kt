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
        assertEquals(86.0, evaluation.ceiling?.anchorPrice)
        assertTrue(evaluation.ceiling?.upperBoundary?.let { boundary -> boundary in 87.0..89.0 } == true)
        val firstCeiling = evaluation.rawSteps.first { step ->
            step.decision == AdaptiveBreakoutDecision.CEILING_CONFIRMED
        }
        assertEquals(firstCeiling.date, evaluation.rawSteps.first { step -> step.ceilingAnchor != null }.date)
        assertTrue(assertNotNull(evaluation.ceiling).testCount >= 1)
        assertEquals(AdaptiveBreakoutDecision.FRESH_BREAKOUT, evaluation.rawSteps.last().decision)
    }

    @Test
    fun `labels an uninterrupted two ATR rise as strong rebound rather than breakout`() {
        val evaluation = AdaptiveBreakoutEngine.evaluate(candlesFromPriorTop(80.0, 81.0, 82.0, 83.0, 84.0, 85.0, 86.0))

        assertNotNull(evaluation)
        assertEquals(AdaptiveBreakoutStatus.STRONG_REBOUND, evaluation.status)
        assertNull(evaluation.ceiling)
        assertTrue(evaluation.rawSteps.none { step -> step.decision == AdaptiveBreakoutDecision.FRESH_BREAKOUT })
        val confirmedFloor = evaluation.rawSteps.single { step ->
            step.decision == AdaptiveBreakoutDecision.FLOOR_CONFIRMED
        }
        assertTrue(confirmedFloor.candidateFloorAtr != confirmedFloor.atr)
        assertTrue(confirmedFloor.close - confirmedFloor.candidateFloor >= confirmedFloor.candidateFloorAtr)
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
        assertEquals(breakoutStep.candidateFloorDate, breakoutEvidence.floorDate)
        assertEquals(breakoutStep.candidateFloor, breakoutEvidence.floorPrice)
        assertTrue(breakoutEvidence.floorToBreakoutPct > 0.0)
        assertNotNull(breakoutEvidence.floorToBreakoutAtr)
        assertEquals(false, breakoutEvidence.rangeLocked)
    }

    @Test
    fun `keeps nearby rejected peaks in one ceiling area`() {
        val evaluation = AdaptiveBreakoutEngine.evaluate(candles(82.0, 85.0, 80.0, 82.0, 86.0, 82.0))

        assertNotNull(evaluation)
        assertEquals(86.0, evaluation.ceiling?.anchorPrice)
        assertEquals(AdaptiveBreakoutStatus.BELOW_CEILING, evaluation.status)
    }

    @Test
    fun `failed wick becomes strict cap without receiving another ATR buffer`() {
        val warmup = (0 until 20).map { index -> candle(index, 100.0) }
        val path = listOf(
            marketCandle(START_DATE.plusDays(20), 100.0, 101.0, 90.0, 92.0),
            marketCandle(START_DATE.plusDays(21), 92.0, 98.0, 91.0, 97.0),
            marketCandle(START_DATE.plusDays(22), 97.0, 110.0, 96.0, 109.0),
            marketCandle(START_DATE.plusDays(23), 109.0, 109.0, 102.0, 103.0),
            marketCandle(START_DATE.plusDays(24), 103.0, 106.0, 101.0, 104.0),
            marketCandle(START_DATE.plusDays(25), 110.0, 116.0, 108.0, 109.0),
            marketCandle(START_DATE.plusDays(26), 111.0, 115.0, 110.0, 114.0),
            marketCandle(START_DATE.plusDays(27), 114.0, 119.0, 113.0, 117.0),
        )

        val evaluation = assertNotNull(AdaptiveBreakoutEngine.evaluate(warmup + path))
        val failedAttempt = evaluation.rawSteps.single { step -> step.date == START_DATE.plusDays(25).toString() }
        val reclaim = evaluation.rawSteps.single { step -> step.date == START_DATE.plusDays(26).toString() }
        val breakout = evaluation.rawSteps.last()

        assertEquals(AdaptiveBreakoutDecision.FAILED_BREAKOUT, failedAttempt.decision)
        assertEquals(116.0, failedAttempt.ceilingFailedAttemptHigh)
        assertEquals(116.0, failedAttempt.ceilingUpperBoundary)
        assertTrue(assertNotNull(failedAttempt.ceilingBaseUpperBoundary) < 116.0)
        assertEquals(AdaptiveBreakoutDecision.CEILING_RECLAIM, reclaim.decision)
        assertEquals(AdaptiveBreakoutDecision.FRESH_BREAKOUT, breakout.decision)
        assertEquals(116.0, breakout.breakoutBoundary)
    }

    @Test
    fun `marks a zero range breakout candle as execution locked`() {
        val priorCandles = candles(82.0, 85.0, 80.0, 82.0, 86.0, 82.0, 85.0, 86.0)
        val lockedBreakout = marketCandle(
            date = START_DATE.plusDays(priorCandles.size.toLong()),
            open = 90.0,
            high = 90.0,
            low = 90.0,
            close = 90.0,
        )

        val evaluation = assertNotNull(AdaptiveBreakoutEngine.evaluate(priorCandles + lockedBreakout))

        assertEquals(AdaptiveBreakoutDecision.FRESH_BREAKOUT, evaluation.rawSteps.last().decision)
        assertEquals(true, assertNotNull(evaluation.breakoutEvidence).rangeLocked)
    }

    @Test
    fun `strict one ATR rejection does not invent the former HFCL local ceiling`() {
        val evaluation = AdaptiveBreakoutEngine.evaluate(hfclCandles())

        assertNotNull(evaluation)
        assertEquals(AdaptiveBreakoutStatus.EARLY_BREAKOUT, evaluation.status)
        assertNull(evaluation.ceiling)
        assertEquals(231.41, evaluation.majorCeiling?.anchorPrice)
        val rejection = evaluation.rawSteps.single { step -> step.date == "2026-07-30" }
        val rejectionAtrMultiple = (rejection.candidatePeak - rejection.close) / rejection.candidatePeakAtr
        assertTrue(rejectionAtrMultiple < 1.0)
        assertEquals(AdaptiveBreakoutDecision.EARLY_BREAKOUT, evaluation.rawSteps.last().decision)
        assertTrue(evaluation.rawSteps.none { step -> step.decision == AdaptiveBreakoutDecision.FRESH_BREAKOUT })
    }

    @Test
    fun `marks an unresolved first outside day as ambiguous`() {
        val warmup = (0 until 20).map { index -> candle(index, 100.0) }
        val outsideDay = marketCandle(
            date = START_DATE.plusDays(20),
            open = 100.0,
            high = 115.0,
            low = 85.0,
            close = 100.0,
        )

        val evaluation = AdaptiveBreakoutEngine.evaluate(warmup + outsideDay)

        assertNotNull(evaluation)
        assertEquals(AdaptiveBreakoutDecision.AMBIGUOUS_OUTSIDE_DAY, evaluation.rawSteps.last().decision)
        assertNull(evaluation.ceiling)
    }

    @Test
    fun `does not use an extreme from the ATR warmup as live structure`() {
        val warmupExtreme = marketCandle(
            date = START_DATE,
            open = 100.0,
            high = 150.0,
            low = 100.0,
            close = 100.0,
        )
        val remainingWarmup = (1 until 14).map { index -> candle(index, 100.0) }
        val firstReadyCandle = candle(14, 100.0)

        val evaluation = AdaptiveBreakoutEngine.evaluate(listOf(warmupExtreme) + remainingWarmup + firstReadyCandle)

        assertNotNull(evaluation)
        assertEquals(AdaptiveBreakoutStatus.NO_CEILING, evaluation.status)
        assertNull(evaluation.ceiling)
        assertEquals(101.0, evaluation.rawSteps.last().candidatePeak)
    }

    @Test
    fun `records a breakout even when the same candle confirms a newer ceiling`() {
        val priorCandles = candles(82.0, 85.0, 80.0, 82.0, 86.0, 82.0, 85.0, 86.0)
        val breakoutDate = START_DATE.plusDays(priorCandles.size.toLong())
        val breakoutAndRejection = marketCandle(
            date = breakoutDate,
            open = 89.0,
            high = 95.0,
            low = 88.0,
            close = 89.0,
        )

        val evaluation = AdaptiveBreakoutEngine.evaluate(priorCandles + breakoutAndRejection)

        assertNotNull(evaluation)
        assertEquals(AdaptiveBreakoutStatus.FRESH_BREAKOUT, evaluation.status)
        assertEquals(AdaptiveBreakoutDecision.FRESH_BREAKOUT, evaluation.rawSteps.last().decision)
        assertEquals(95.0, evaluation.ceiling?.anchorPrice)
        assertEquals(95.0, evaluation.ceiling?.upperBoundary)
        assertEquals(AdaptiveBreakoutCeilingType.POST_BREAKOUT_SWING, evaluation.ceiling?.type)
        assertNull(evaluation.ceiling?.breakoutDate)
        assertEquals(breakoutDate.toString(), evaluation.breakoutEvidence?.date)
    }

    @Test
    fun `confirms KRN compact ceiling after two contained sessions and breaks it on 11 August`() {
        val evaluation = AdaptiveBreakoutEngine.evaluate(krnCompactCeilingCandles())

        assertNotNull(evaluation)
        val candidate = evaluation.rawSteps.single { step -> step.date == "2026-08-04" }
        assertEquals(AdaptiveBreakoutDecision.COMPACT_CEILING_CANDIDATE, candidate.decision)
        assertEquals(1_230.0, candidate.compactCeilingCandidate)
        assertEquals(0, candidate.compactCeilingConfirmationCount)

        val firstContainedSession = evaluation.rawSteps.single { step -> step.date == "2026-08-05" }
        assertEquals(AdaptiveBreakoutDecision.COMPACT_CEILING_CANDIDATE, firstContainedSession.decision)
        assertEquals(1, firstContainedSession.compactCeilingConfirmationCount)

        val confirmation = evaluation.rawSteps.single { step -> step.date == "2026-08-06" }
        assertEquals(AdaptiveBreakoutDecision.CEILING_CONFIRMED, confirmation.decision)
        assertEquals(AdaptiveBreakoutCeilingType.COMPACT_RANGE, confirmation.ceilingType)
        assertEquals(2, confirmation.compactCeilingConfirmationCount)
        assertEquals(1_230.0, confirmation.ceilingAnchor)

        val breakout = evaluation.rawSteps.single { step -> step.date == "2026-08-11" }
        assertEquals(AdaptiveBreakoutDecision.FRESH_BREAKOUT, breakout.decision)
        assertNotNull(breakout.breakoutBoundary)
        assertTrue(breakout.close > breakout.breakoutBoundary)
        assertEquals("2026-08-11", evaluation.breakoutEvidence?.date)
        val ceiling = assertNotNull(evaluation.ceiling)
        assertEquals(1_230.0, ceiling.anchorPrice)
        assertTrue(ceiling.upperBoundary in 1_250.0..1_260.0)
        assertEquals(AdaptiveBreakoutCeilingType.COMPACT_RANGE, ceiling.type)
        assertEquals(1_319.0, evaluation.majorCeiling?.anchorPrice)
    }

    @Test
    fun `higher high resets compact ceiling confirmation`() {
        val candles = krnCompactCeilingCandles().filter { candle -> candle.candleDate <= LocalDate.of(2026, 8, 4) } +
            listOf(
                marketCandle(LocalDate.of(2026, 8, 5), 1_210.0, 1_235.0, 1_200.0, 1_205.0),
                marketCandle(LocalDate.of(2026, 8, 6), 1_215.0, 1_225.0, 1_198.0, 1_210.0),
            )

        val evaluation = AdaptiveBreakoutEngine.evaluate(candles)

        assertNotNull(evaluation)
        val resetDay = evaluation.rawSteps.single { step -> step.date == "2026-08-05" }
        assertEquals(1_235.0, resetDay.candidatePeak)
        assertEquals(0, resetDay.compactCeilingConfirmationCount)
        assertTrue(evaluation.rawSteps.none { step ->
            step.date == "2026-08-06" && step.decision == AdaptiveBreakoutDecision.CEILING_CONFIRMED
        })
    }

    @Test
    fun `close above a live compact candidate emits watch-only early breakout`() {
        val candidateCandles = krnCompactCeilingCandles()
            .filter { candle -> candle.candleDate <= LocalDate.of(2026, 8, 4) }
        val candidateEvaluation = assertNotNull(AdaptiveBreakoutEngine.evaluate(candidateCandles))
        val candidateStep = candidateEvaluation.rawSteps.last()
        val candidatePeak = assertNotNull(candidateStep.compactCeilingCandidate)
        val earlyBoundary = candidatePeak + candidateStep.candidatePeakAtr * 0.1
        val earlyClose = earlyBoundary + 1.0
        val earlyCandle = marketCandle(
            date = LocalDate.of(2026, 8, 5),
            open = earlyBoundary - 2.0,
            high = earlyClose + 2.0,
            low = earlyBoundary - 4.0,
            close = earlyClose,
        )

        val evaluation = assertNotNull(AdaptiveBreakoutEngine.evaluate(candidateCandles + earlyCandle))
        val earlyStep = evaluation.rawSteps.last()

        assertEquals(AdaptiveBreakoutStatus.EARLY_BREAKOUT, evaluation.status)
        assertEquals(AdaptiveBreakoutDecision.EARLY_BREAKOUT, earlyStep.decision)
        assertEquals(earlyBoundary, assertNotNull(earlyStep.breakoutBoundary), 0.0001)
        assertEquals(candidatePeak, earlyStep.compactCeilingCandidate)
        assertNull(evaluation.breakoutEvidence)
        assertTrue(evaluation.rawSteps.none { step -> step.decision == AdaptiveBreakoutDecision.FRESH_BREAKOUT })
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

    private fun krnCompactCeilingCandles(): List<DailyCandle> {
        val warmup = (0 until 20).map { index ->
            marketCandle(
                date = LocalDate.of(2026, 7, 1).plusDays(index.toLong()),
                open = 1_294.0,
                high = 1_319.0,
                low = 1_269.0,
                close = 1_294.0,
            )
        }
        return warmup + listOf(
            marketCandle(LocalDate.of(2026, 7, 31), 1_194.0, 1_194.0, 1_150.0, 1_170.0),
            marketCandle(LocalDate.of(2026, 8, 3), 1_174.0, 1_218.0, 1_168.0, 1_212.0),
            marketCandle(LocalDate.of(2026, 8, 4), 1_220.0, 1_230.0, 1_191.0, 1_196.60),
            marketCandle(LocalDate.of(2026, 8, 5), 1_205.0, 1_219.90, 1_196.0, 1_213.70),
            marketCandle(LocalDate.of(2026, 8, 6), 1_212.0, 1_222.0, 1_195.0, 1_214.70),
            marketCandle(LocalDate.of(2026, 8, 7), 1_214.0, 1_224.0, 1_200.0, 1_208.40),
            marketCandle(LocalDate.of(2026, 8, 10), 1_210.0, 1_242.0, 1_206.0, 1_226.40),
            marketCandle(LocalDate.of(2026, 8, 11), 1_240.0, 1_287.70, 1_233.40, 1_287.70),
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
