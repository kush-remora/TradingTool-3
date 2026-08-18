package com.tradingtool.core.strategy.adaptivebreakout

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.delivery.model.DeliveryReconciliationStatus
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BreakoutDayQualityAnalyzerTest {
    @Test
    fun `passes a fresh breakout when all five selected-day checks pass`() {
        val candles = candles()
        val report = BreakoutDayQualityAnalyzer.analyze(
            symbol = SYMBOL,
            candles = candles,
            deliveries = deliveries(candles.last().candleDate),
            evaluation = evaluation(candles.last(), AdaptiveBreakoutDecision.FRESH_BREAKOUT),
        )

        assertEquals(BreakoutQualityDecision.PASS, report.overallDecision)
        assertEquals(5, report.rules.size)
        assertEquals(listOf(BreakoutQualityVerdict.PASS), report.rules.map(BreakoutQualityRuleResult::verdict).distinct())
        assertEquals(100.0, report.breakoutLine)
        assertNotNull(report.sma200)
        assertEquals(6, report.chartContext.rules.size)
        assertNotNull(report.chartContext.sma200ChangePctTwentySessions)
        assertEquals("Major ceiling", report.chartContext.nextObstacleLabel)
        assertEquals(
            BreakoutQualityVerdict.PASS,
            report.chartContext.rules.single { rule -> rule.key == "sma200-direction" }.verdict,
        )
    }

    @Test
    fun `waits rather than inventing delivery confirmation when delivery is missing`() {
        val candles = candles()
        val report = BreakoutDayQualityAnalyzer.analyze(
            symbol = SYMBOL,
            candles = candles,
            deliveries = emptyList(),
            evaluation = evaluation(candles.last(), AdaptiveBreakoutDecision.FRESH_BREAKOUT),
        )

        assertEquals(BreakoutQualityDecision.WAIT, report.overallDecision)
        assertEquals(
            BreakoutQualityVerdict.UNAVAILABLE,
            report.rules.single { rule -> rule.key == "delivery" }.verdict,
        )
    }

    @Test
    fun `labels a non-breakout session as context only even when its candle is strong`() {
        val candles = candles()
        val report = BreakoutDayQualityAnalyzer.analyze(
            symbol = SYMBOL,
            candles = candles,
            deliveries = deliveries(candles.last().candleDate),
            evaluation = evaluation(candles.last(), AdaptiveBreakoutDecision.BREAKOUT_CONTINUATION),
        )

        assertEquals(BreakoutQualityDecision.CONTEXT_ONLY, report.overallDecision)
    }

    @Test
    fun `labels early breakout as watch-only context`() {
        val candles = candles()
        val report = BreakoutDayQualityAnalyzer.analyze(
            symbol = SYMBOL,
            candles = candles,
            deliveries = deliveries(candles.last().candleDate),
            evaluation = evaluation(candles.last(), AdaptiveBreakoutDecision.EARLY_BREAKOUT),
        )

        assertEquals(AdaptiveBreakoutStatus.EARLY_BREAKOUT, report.structureStatus)
        assertEquals(BreakoutQualityDecision.CONTEXT_ONLY, report.overallDecision)
        assertEquals(
            "Early breakout is a watch-only signal from an unconfirmed compact ceiling. Use the checks as context; it is not a fresh-breakout entry day.",
            report.decisionSummary,
        )
    }

    private fun candles(): List<DailyCandle> {
        val start = LocalDate.of(2025, 10, 1)
        val history = (0 until 230).map { index ->
            candle(start.plusDays(index.toLong()), close = 100.0, volume = 1_000L)
        }
        return history + DailyCandle(
            instrumentToken = 1L,
            symbol = SYMBOL,
            candleDate = start.plusDays(230),
            open = 100.0,
            high = 105.0,
            low = 100.0,
            close = 104.0,
            volume = 2_000L,
        )
    }

    private fun evaluation(
        latest: DailyCandle,
        decision: AdaptiveBreakoutDecision,
    ): AdaptiveBreakoutEvaluation {
        val step = AdaptiveBreakoutRawStep(
            date = latest.candleDate.toString(),
            open = latest.open,
            high = latest.high,
            low = latest.low,
            close = latest.close,
            volume = latest.volume,
            atr = 10.0,
            candidateFloor = 90.0,
            candidateFloorDate = latest.candleDate.toString(),
            candidateFloorAtr = 10.0,
            candidatePeak = 105.0,
            candidatePeakAtr = 10.0,
            ceilingAnchor = 95.0,
            ceilingBaseUpperBoundary = 100.0,
            ceilingUpperBoundary = 100.0,
            ceilingFailedAttemptHigh = null,
            majorCeilingUpperBoundary = 120.0,
            ceilingTestCount = 2,
            ceilingType = AdaptiveBreakoutCeilingType.STRONG_REJECTION,
            breakoutBoundary = 100.0,
            compactCeilingCandidate = null,
            compactCeilingConfirmationCount = null,
            decision = decision,
            explanation = "Selected-day structure explanation.",
        )
        return AdaptiveBreakoutEvaluation(
            status = when (decision) {
                AdaptiveBreakoutDecision.FRESH_BREAKOUT -> AdaptiveBreakoutStatus.FRESH_BREAKOUT
                AdaptiveBreakoutDecision.EARLY_BREAKOUT -> AdaptiveBreakoutStatus.EARLY_BREAKOUT
                else -> AdaptiveBreakoutStatus.BREAKOUT_CONTINUATION
            },
            latestDate = latest.candleDate.toString(),
            latestOpen = latest.open,
            latestHigh = latest.high,
            latestLow = latest.low,
            latestClose = latest.close,
            latestVolume = latest.volume,
            latestAtr = 10.0,
            ceiling = null,
            majorCeiling = null,
            ceilingAgeSessions = null,
            closeVsCeilingPct = null,
            closePositionPct = 80.0,
            volumeVsTenDayAverage = 2.0,
            fiftyTwoWeekHigh = 120.0,
            distanceFromFiftyTwoWeekHighPct = -13.33,
            breakoutEvidence = null,
            rawSteps = listOf(step),
        )
    }

    private fun deliveries(selectedDate: LocalDate): List<StockDeliveryDaily> {
        val prior = (20 downTo 1).map { daysBefore ->
            delivery(selectedDate.minusDays(daysBefore.toLong()), 100L)
        }
        return prior + delivery(selectedDate, 150L)
    }

    private fun delivery(date: LocalDate, quantity: Long): StockDeliveryDaily = StockDeliveryDaily(
        instrumentToken = 1L,
        symbol = SYMBOL,
        exchange = "NSE",
        universe = "test",
        tradingDate = date,
        reconciliationStatus = DeliveryReconciliationStatus.PRESENT,
        series = "EQ",
        ttlTrdQnty = 200L,
        delivQty = quantity,
        delivPer = quantity / 2.0,
        sourceFileName = null,
        sourceUrl = null,
    )

    private fun candle(date: LocalDate, close: Double, volume: Long): DailyCandle = DailyCandle(
        instrumentToken = 1L,
        symbol = SYMBOL,
        candleDate = date,
        open = close,
        high = close + 1.0,
        low = close - 1.0,
        close = close,
        volume = volume,
    )

    private companion object {
        const val SYMBOL = "TEST"
    }
}
