package com.tradingtool.core.strategy.adaptivebreakout

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdaptiveBreakoutBacktestEngineTest {
    @Test
    fun `enters on the next session open and exits at the five percent target`() {
        val candles = breakoutPath(
            nextSession = candle(29, open = 90.0, high = 95.0, low = 89.0, close = 94.0),
        )

        val result = AdaptiveBreakoutBacktestEngine.run(
            candles = candles,
            testFromDate = DATE_START,
            testToDate = DATE_START.plusDays(29),
            targetPct = 5.0,
            stopLossPct = 5.0,
        )

        val trade = result.trades.single()
        assertEquals("2026-01-30", trade.entryDate)
        assertEquals(90.0, trade.entryPrice)
        assertEquals(94.5, trade.exitPrice)
        assertEquals(AdaptiveBreakoutBacktestExitReason.TARGET_HIT, trade.exitReason)
        assertEquals(1, trade.holdingSessions)
        assertEquals(1, result.summary.targetHitCount)
    }

    @Test
    fun `uses conservative stop first when daily candle touches both levels`() {
        val candles = breakoutPath(
            nextSession = candle(29, open = 90.0, high = 96.0, low = 84.0, close = 90.0),
        )

        val result = AdaptiveBreakoutBacktestEngine.run(
            candles = candles,
            testFromDate = DATE_START,
            testToDate = DATE_START.plusDays(29),
            targetPct = 5.0,
            stopLossPct = 5.0,
        )

        val trade = result.trades.single()
        assertEquals(AdaptiveBreakoutBacktestExitReason.STOP_LOSS_SAME_CANDLE, trade.exitReason)
        assertTrue(trade.ambiguousSameCandle)
        assertEquals(-5.0, trade.returnPct, 0.000001)
    }

    @Test
    fun `closes an open position at the last available close`() {
        val candles = breakoutPath(
            nextSession = candle(29, open = 90.0, high = 93.0, low = 89.0, close = 92.0),
        )

        val result = AdaptiveBreakoutBacktestEngine.run(
            candles = candles,
            testFromDate = DATE_START,
            testToDate = DATE_START.plusDays(29),
            targetPct = 5.0,
            stopLossPct = 5.0,
        )

        val trade = result.trades.single()
        assertEquals(AdaptiveBreakoutBacktestExitReason.END_OF_TEST, trade.exitReason)
        assertEquals(92.0, trade.exitPrice)
        assertEquals(1, trade.holdingSessions)
    }

    @Test
    fun `skips a locked next session and enters on the first tradable open`() {
        val lockedSession = candle(29, open = 94.0, high = 94.0, low = 94.0, close = 94.0)
        val firstTradableSession = candle(30, open = 92.0, high = 94.0, low = 91.0, close = 93.0)
        val candles = breakoutPath(nextSession = lockedSession) + firstTradableSession

        val result = AdaptiveBreakoutBacktestEngine.run(
            candles = candles,
            testFromDate = DATE_START,
            testToDate = DATE_START.plusDays(30),
            targetPct = 5.0,
            stopLossPct = 5.0,
        )

        val trade = result.trades.single()
        assertEquals("2026-01-31", trade.entryDate)
        assertEquals(92.0, trade.entryPrice)
        assertTrue(result.entryRule.contains("Locked zero-range sessions are skipped"))
    }

    private fun breakoutPath(nextSession: DailyCandle): List<DailyCandle> {
        val warmup = (0 until 20).map { index -> candle(index, 80.0, 80.0, 79.0, 80.0) }
        val path = listOf(
            candle(20, 82.0, 83.0, 81.0, 82.0),
            candle(21, 85.0, 86.0, 84.0, 85.0),
            candle(22, 80.0, 86.0, 79.0, 80.0),
            candle(23, 82.0, 83.0, 81.0, 82.0),
            candle(24, 86.0, 87.0, 85.0, 86.0),
            candle(25, 82.0, 83.0, 81.0, 82.0),
            candle(26, 85.0, 86.0, 84.0, 85.0),
            candle(27, 86.0, 87.0, 85.0, 86.0),
            candle(28, 89.0, 90.0, 88.0, 89.0),
            nextSession,
        )
        return warmup + path
    }

    private fun candle(
        index: Int,
        open: Double,
        high: Double,
        low: Double,
        close: Double,
    ): DailyCandle = DailyCandle(
        instrumentToken = 1L,
        symbol = "TEST",
        candleDate = DATE_START.plusDays(index.toLong()),
        open = open,
        high = high,
        low = low,
        close = close,
        volume = 1_000L + index,
    )

    private companion object {
        val DATE_START: LocalDate = LocalDate.of(2026, 1, 1)
    }
}
