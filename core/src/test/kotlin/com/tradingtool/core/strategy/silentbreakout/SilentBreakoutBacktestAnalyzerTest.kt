package com.tradingtool.core.strategy.silentbreakout

import com.tradingtool.core.candle.DailyCandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.time.LocalDate

class SilentBreakoutBacktestAnalyzerTest {
    @Test
    fun `flags a signal that has advanced at least 20 percent in 20 sessions`() {
        val start = LocalDate.of(2024, 1, 1)
        val candles = (0..291).map { index ->
            val close = when {
                index < 231 -> 100.0
                index <= 251 -> 100.0 + ((index - 231) * 1.5)
                index == 252 -> 130.0
                index < 266 -> 140.0
                else -> 200.0
            }
            candle(start.plusDays(index.toLong()), close)
        }

        val row = SilentBreakoutBacktestAnalyzer.analyze(
            SilentBreakoutSignal("TEST", start.plusDays(251)),
            candles, 20.0, mapOf(start.plusDays(250) to 78.0),
        )

        assertEquals(SilentBreakoutDataStatus.AVAILABLE, row.dataStatus)
        assertTrue(row.lateStageRisk == true)
        assertTrue(row.roc20Pct!! >= 20.0)
        assertTrue(row.distanceFromSma200Pct!! >= 20.0)
        assertEquals(53.84615384615385, row.forward40SessionReturnPct)
        assertEquals(78.0, row.priorFiveSessionsMaxDeliveryPct)
        assertEquals(130.0, row.nextFiveSessionsLow)
        assertEquals(1, row.nextFiveSessionsLowDays)
        assertTrue(row.targetAchieved == true)
        assertEquals(15, row.targetAchievedDays)
    }

    @Test
    fun `does not label a non extended signal as late stage risk`() {
        val start = LocalDate.of(2024, 1, 1)
        val candles = (0..251).map { index -> candle(start.plusDays(index.toLong()), 100.0) }

        val row = SilentBreakoutBacktestAnalyzer.analyze(
            SilentBreakoutSignal("TEST", start.plusDays(251)),
            candles, 20.0, emptyMap(),
        )

        assertEquals(SilentBreakoutDataStatus.AVAILABLE, row.dataStatus)
        assertFalse(row.lateStageRisk == true)
        assertNull(row.forward20SessionReturnPct)
    }

    @Test
    fun `uses partial history for context and forward outcomes`() {
        val start = LocalDate.of(2024, 1, 1)
        val candles = (0..140).map { index -> candle(start.plusDays(index.toLong()), if (index >= 120) 120.0 else 100.0) }

        val row = SilentBreakoutBacktestAnalyzer.analyze(
            SilentBreakoutSignal("TEST", start.plusDays(100)),
            candles, 20.0, emptyMap(),
        )

        assertEquals(SilentBreakoutDataStatus.PARTIAL_HISTORY, row.dataStatus)
        assertEquals(0.0, row.distanceFromFiftyTwoWeekHighPct)
        assertEquals(0.0, row.distanceFromSma200Pct)
        assertFalse(row.lateStageRisk == true)
        assertEquals(20.0, row.forward20SessionReturnPct)
    }

    private fun candle(date: LocalDate, close: Double): DailyCandle = DailyCandle(
        instrumentToken = 1,
        symbol = "TEST",
        candleDate = date,
        open = close,
        high = close,
        low = close,
        close = close,
        volume = 1,
    )
}
