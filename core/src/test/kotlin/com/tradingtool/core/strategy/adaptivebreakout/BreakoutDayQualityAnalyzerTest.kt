package com.tradingtool.core.strategy.adaptivebreakout

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BreakoutDayQualityAnalyzerTest {

    @Test
    fun `passes a long tight base and a contained breakout move`() {
        val candles = buildCandles(baseSessions = 45, baseWidthPct = 10.0, breakoutMovePct = 2.0)
        val report = analyze(candles, AdaptiveBreakoutDecision.FRESH_BREAKOUT)

        assertEquals(BreakoutQualityDecision.PASS, report.overallDecision)
        assertEquals(2, report.rules.size)
        assertEquals(BreakoutQualityVerdict.PASS, report.rules.single { it.key == "base-quality" }.verdict)
        assertEquals(BreakoutQualityVerdict.PASS, report.rules.single { it.key == "breakout-day-move" }.verdict)
        assertEquals(100.0, report.volumeVsTenDayAveragePct)
        assertEquals(0.0, report.volumeVsPriorTwentyDayHighChangePct)
    }

    @Test
    fun `shows average volume, quiet volume, and change versus the prior twenty day high`() {
        val original = buildCandles(baseSessions = 45, baseWidthPct = 10.0, breakoutMovePct = 2.0)
        val breakoutIndex = original.lastIndex
        val candles = original.mapIndexed { index, candle ->
            when {
                index == breakoutIndex -> candle.copy(volume = 12_000L)
                index == breakoutIndex - 1 -> candle.copy(volume = 10_000L)
                index == breakoutIndex - 11 -> candle.copy(volume = 6_000L)
                index >= breakoutIndex - 10 -> candle.copy(volume = 2_000L)
                else -> candle
            }
        }

        val report = analyze(candles, AdaptiveBreakoutDecision.FRESH_BREAKOUT)

        assertEquals(428.57, report.volumeVsTenDayAveragePct)
        assertEquals(1200.0, report.volumeVsTwentyDayQuietAveragePct)
        assertEquals(20.0, report.volumeVsPriorTwentyDayHighChangePct)
        assertEquals(2, report.recentAbnormalVolumeDays.size)
        assertEquals("2025-02-14", report.recentAbnormalVolumeDays[0].date)
        assertEquals("2025-02-04", report.recentAbnormalVolumeDays[1].date)
        assertEquals(1000.0, report.recentAbnormalVolumeDays[0].volumeVsQuietAveragePct)
    }

    @Test
    fun `waits for a moderate base or breakout move`() {
        val candles = buildCandles(baseSessions = 25, baseWidthPct = 25.0, breakoutMovePct = 4.0)
        val report = analyze(candles, AdaptiveBreakoutDecision.FRESH_BREAKOUT)

        assertEquals(BreakoutQualityDecision.WAIT, report.overallDecision)
        assertEquals(BreakoutQualityVerdict.WAIT, report.rules.single { it.key == "base-quality" }.verdict)
        assertEquals(BreakoutQualityVerdict.WAIT, report.rules.single { it.key == "breakout-day-move" }.verdict)
    }

    @Test
    fun `rejects a short wide base and an excessively large breakout move`() {
        val candles = buildCandles(baseSessions = 10, baseWidthPct = 35.0, breakoutMovePct = 8.0)
        val report = analyze(candles, AdaptiveBreakoutDecision.FRESH_BREAKOUT)

        assertEquals(BreakoutQualityDecision.REJECT, report.overallDecision)
        assertEquals(BreakoutQualityVerdict.REJECT, report.rules.single { it.key == "base-quality" }.verdict)
        assertEquals(BreakoutQualityVerdict.REJECT, report.rules.single { it.key == "breakout-day-move" }.verdict)
    }

    @Test
    fun `keeps non-fresh signals as context only`() {
        val report = analyze(
            buildCandles(baseSessions = 45, baseWidthPct = 10.0, breakoutMovePct = 2.0),
            AdaptiveBreakoutDecision.BREAKOUT_CONTINUATION,
        )

        assertEquals(BreakoutQualityDecision.CONTEXT_ONLY, report.overallDecision)
    }

    @Test
    fun `delivery remains context and does not create a quality rule`() {
        val candles = buildCandles(baseSessions = 45, baseWidthPct = 10.0, breakoutMovePct = 2.0)
        val report = BreakoutDayQualityAnalyzer.analyze(
            symbol = SYMBOL,
            candles = candles,
            deliveries = emptyList(),
            evaluation = evaluation(candles.last(), AdaptiveBreakoutDecision.FRESH_BREAKOUT),
        )

        assertNull(report.deliveryContext)
        assert(report.rules.none { rule -> rule.key.contains("delivery") })
        assertNotNull(report.chartContext)
    }

    @Test
    fun `identifies a cheap high-volume footprint with no later rejection`() {
        val candles = footprintCandles(distributionWarning = false)
        val report = analyze(candles, AdaptiveBreakoutDecision.FRESH_BREAKOUT)

        assertEquals(BreakoutVolumeFootprintDecision.PASS, report.volumeFootprint.decision)
        assertEquals("2025-01-31", report.volumeFootprint.footprintDate)
        assertEquals(6.0, report.volumeFootprint.footprintVolumeRatio)
        assertEquals(true, report.volumeFootprint.footprintLowHeld)
        assertNull(report.volumeFootprint.distributionWarningDate)
    }

    @Test
    fun `rejects a footprint followed by a high-volume weak candle`() {
        val report = analyze(
            footprintCandles(distributionWarning = true),
            AdaptiveBreakoutDecision.FRESH_BREAKOUT,
        )

        assertEquals(BreakoutVolumeFootprintDecision.REJECT, report.volumeFootprint.decision)
        assertNotNull(report.volumeFootprint.distributionWarningDate)
    }

    private fun analyze(
        candles: List<DailyCandle>,
        decision: AdaptiveBreakoutDecision,
    ): BreakoutDayQualityResponse = BreakoutDayQualityAnalyzer.analyze(
        symbol = SYMBOL,
        candles = candles,
        deliveries = emptyList(),
        evaluation = evaluation(candles.last(), decision),
    )

    private fun buildCandles(
        baseSessions: Int,
        baseWidthPct: Double,
        breakoutMovePct: Double,
    ): List<DailyCandle> {
        val start = LocalDate.of(2025, 1, 1)
        val floorPrice = 100.0
        val baseHigh = floorPrice * (1.0 + baseWidthPct / 100.0)
        val base = (0 until baseSessions).map { index ->
            candle(
                date = start.plusDays(index.toLong()),
                open = floorPrice + 1.0,
                high = baseHigh,
                low = floorPrice,
                close = floorPrice + 2.0,
            )
        }
        val previousClose = base.last().close
        val breakoutClose = previousClose * (1.0 + breakoutMovePct / 100.0)
        return base + candle(
            date = start.plusDays(baseSessions.toLong()),
            open = previousClose,
            high = breakoutClose,
            low = previousClose,
            close = breakoutClose,
        )
    }

    private fun evaluation(
        latest: DailyCandle,
        decision: AdaptiveBreakoutDecision,
    ): AdaptiveBreakoutEvaluation = AdaptiveBreakoutEvaluation(
        status = AdaptiveBreakoutStatus.FRESH_BREAKOUT,
        latestDate = latest.candleDate.toString(),
        latestOpen = latest.open,
        latestHigh = latest.high,
        latestLow = latest.low,
        latestClose = latest.close,
        latestVolume = latest.volume,
        latestAtr = 1.0,
        ceiling = null,
        majorCeiling = null,
        ceilingAgeSessions = null,
        closeVsCeilingPct = null,
        closePositionPct = null,
        volumeVsTenDayAverage = null,
        fiftyTwoWeekHigh = null,
        distanceFromFiftyTwoWeekHighPct = null,
        breakoutEvidence = null,
        rawSteps = listOf(
            AdaptiveBreakoutRawStep(
                date = latest.candleDate.toString(),
                open = latest.open,
                high = latest.high,
                low = latest.low,
                close = latest.close,
                volume = latest.volume,
                atr = 1.0,
                candidateFloor = 100.0,
                candidateFloorDate = "2025-01-01",
                candidateFloorAtr = 1.0,
                candidatePeak = latest.high,
                candidatePeakAtr = 1.0,
                ceilingAnchor = null,
                ceilingBaseUpperBoundary = null,
                ceilingUpperBoundary = null,
                ceilingFailedAttemptHigh = null,
                majorCeilingUpperBoundary = null,
                ceilingTestCount = null,
                ceilingType = null,
                breakoutBoundary = null,
                compactCeilingCandidate = null,
                compactCeilingConfirmationCount = null,
                decision = decision,
                explanation = "test",
            ),
        ),
    )

    private fun footprintCandles(distributionWarning: Boolean): List<DailyCandle> {
        val start = LocalDate.of(2025, 1, 1)
        val warmup = (0 until 30).map { index ->
            candleWithVolume(
                date = start.plusDays(index.toLong()),
                open = 101.0,
                high = 104.0,
                low = 98.0,
                close = 101.0,
                volume = 1_000L,
            )
        }
        val footprint = candleWithVolume(
            date = start.plusDays(30),
            open = 99.0,
            high = 102.0,
            low = 98.0,
            close = 101.0,
            volume = 6_000L,
        )
        val followThrough = (31 until 39).map { index ->
            candleWithVolume(
                date = start.plusDays(index.toLong()),
                open = 102.0,
                high = 105.0,
                low = if (distributionWarning && index == 34) 97.0 else 101.0,
                close = if (distributionWarning && index == 34) 98.0 else 103.0,
                volume = if (distributionWarning && index == 34) 3_000L else 1_000L,
            )
        }
        val breakout = candleWithVolume(
            date = start.plusDays(39),
            open = 105.0,
            high = 110.0,
            low = 104.0,
            close = 109.0,
            volume = 3_000L,
        )
        return warmup + footprint + followThrough + breakout
    }

    private fun candleWithVolume(
        date: LocalDate,
        open: Double,
        high: Double,
        low: Double,
        close: Double,
        volume: Long,
    ): DailyCandle = DailyCandle(
        instrumentToken = 1L,
        symbol = SYMBOL,
        candleDate = date,
        open = open,
        high = high,
        low = low,
        close = close,
        volume = volume,
    )

    private fun candle(
        date: LocalDate,
        open: Double,
        high: Double,
        low: Double,
        close: Double,
    ): DailyCandle = DailyCandle(
        instrumentToken = 1L,
        symbol = SYMBOL,
        candleDate = date,
        open = open,
        high = high,
        low = low,
        close = close,
        volume = 1_000L,
    )

    private companion object {
        const val SYMBOL = "TEST"
    }
}
