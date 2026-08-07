package com.tradingtool.core.strategy.momentum

import com.tradingtool.core.candle.DailyCandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

class MomentumEvidenceCalculatorTest {
    @Test
    fun `calculates trend weekly returns and participation evidence from completed candles`() {
        val candles = buildWeekdayCandles(220)
        val eventIndex = 210
        val eventCandle = candles[eventIndex].copy(volume = 2_500L)
        val testCandles = candles.mapIndexed { index, candle -> if (index == eventIndex) eventCandle else candle }

        val evidence = calculateMomentumEvidence(
            candles = testCandles,
            asOfDate = testCandles.last().candleDate,
            deliveryPercentageByDate = mapOf(eventCandle.candleDate to 58.4),
        )

        assertEquals(MomentumDataStatus.AVAILABLE, evidence.dataStatus)
        assertEquals(true, evidence.aboveSma200)
        assertEquals(320.0, evidence.fiftyTwoWeekHigh)
        assertEquals(-0.31, evidence.distanceFromFiftyTwoWeekHighPct)
        assertEquals(4, evidence.weeklyReturns.size)
        assertTrue((evidence.weeklyRoc?.currentRocPct ?: 0.0) > 0.0)
        assertTrue(evidence.weeklyRoc?.changePctPoints != null)
        assertEquals(1, evidence.participationEvents.size)
        assertEquals(310.0, evidence.participationEvents.single().close)
        assertEquals(2_500L, evidence.participationEvents.single().volume)
        assertEquals(2.5, evidence.participationEvents.single().volumeRatio)
        assertEquals(58.4, evidence.participationEvents.single().deliveryPercentage)
        assertEquals(90, evidence.participationLookbackDays)
        assertEquals(eventCandle.candleDate.toString(), evidence.participationEvents.single().eventDate)
        assertTrue(evidence.participationEvents.single().priceSinceEventPct > 0.0)
    }

    @Test
    fun `reports insufficient history without inventing the 200 dma`() {
        val candles = buildWeekdayCandles(40)

        val evidence = calculateMomentumEvidence(candles, candles.last().candleDate)

        assertEquals(MomentumDataStatus.INSUFFICIENT_HISTORY, evidence.dataStatus)
        assertNull(evidence.sma200)
        assertNull(evidence.aboveSma200)
        assertTrue(evidence.weeklyReturns.isNotEmpty())
    }

    @Test
    fun `does not use candles after the requested as of date`() {
        val candles = buildWeekdayCandles(220)
        val asOfDate = candles[200].candleDate

        val evidence = calculateMomentumEvidence(candles, asOfDate)

        assertEquals(asOfDate.toString(), evidence.asOfDate)
        assertEquals(candles[200].close, evidence.currentClose)
        assertTrue(evidence.participationEvents.all { it.eventDate <= asOfDate.toString() })
    }

    @Test
    fun `marks roc rising from negative when the latest three week speed improves`() {
        val candles = buildWeeklyCloseCandles(listOf(100.0, 110.0, 100.0, 95.0, 90.0, 90.0, 120.0))

        val evidence = calculateMomentumEvidence(candles, candles.last().candleDate)

        assertEquals(MomentumRocState.RISING_FROM_NEGATIVE, evidence.weeklyRoc?.state)
        assertEquals(26.32, evidence.weeklyRoc?.currentRocPct)
        assertEquals(-10.0, evidence.weeklyRoc?.previousRocPct)
        assertEquals(36.32, evidence.weeklyRoc?.changePctPoints)
    }

    private fun buildWeekdayCandles(count: Int): List<DailyCandle> {
        val startDate = LocalDate.of(2025, 9, 1)
        return generateSequence(startDate) { it.plusDays(1) }
            .filter { date -> date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY }
            .take(count)
            .mapIndexed { index, date ->
                DailyCandle(
                    instrumentToken = 1L,
                    symbol = "TEST",
                    candleDate = date,
                    open = 100.0 + index,
                    high = 101.0 + index,
                    low = 99.0 + index,
                    close = 100.0 + index,
                    volume = 1_000L,
                )
            }
            .toList()
    }

    private fun buildWeeklyCloseCandles(weeklyCloses: List<Double>): List<DailyCandle> {
        val startDate = LocalDate.of(2025, 9, 1)
        return weeklyCloses.flatMapIndexed { weekIndex, close ->
            (0..4).map { dayOffset ->
                val date = startDate.plusWeeks(weekIndex.toLong()).plusDays(dayOffset.toLong())
                DailyCandle(
                    instrumentToken = 1L,
                    symbol = "TEST",
                    candleDate = date,
                    open = close,
                    high = close + 1.0,
                    low = close - 1.0,
                    close = close,
                    volume = 1_000L,
                )
            }
        }
    }
}
