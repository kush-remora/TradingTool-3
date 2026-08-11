package com.tradingtool.core.strategy.weeklylowalignmentbacktest

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeeklyLowAlignmentBacktestEngineTest {
    private val engine = WeeklyLowAlignmentBacktestEngine()

    @Test
    fun `ignores a retest before five trading sessions`() {
        val report = engine.run(
            symbol = "DMART",
            companyName = null,
            candles = listOf(
                candle("2025-09-29", 110.0, 112.0, 105.0, 109.0),
                candle("2025-10-03", 106.0, 107.0, 100.0, 101.0),
                candle("2025-10-06", 101.0, 103.0, 100.5, 102.0),
                candle("2025-10-07", 102.0, 104.0, 101.0, 103.0),
            ),
            testFrom = LocalDate.parse("2025-09-01"),
            toDate = LocalDate.parse("2025-10-18"),
            targetPct = 5.0,
            maxHoldingTradingDays = 5,
        )

        val trade = report.trades.single()
        assertEquals(WeeklyLowAlignmentBacktestOutcomes.TOO_SOON_RETEST, trade.outcome)
        assertEquals("2025-10-06", trade.retestDate)
        assertEquals(1, trade.retestGapTradingDays)
        assertEquals(1, report.summary.tooSoonRetestCount)
    }

    @Test
    fun `uses a five session retest and configurable target`() {
        val report = engine.run(
            symbol = "TEST",
            companyName = null,
            candles = listOf(
                candle("2025-09-29", 102.0, 104.0, 100.0, 101.0),
                candle("2025-09-30", 101.0, 102.0, 101.0, 101.0),
                candle("2025-10-01", 101.0, 102.0, 101.0, 101.0),
                candle("2025-10-02", 101.0, 102.0, 101.0, 101.0),
                candle("2025-10-03", 101.0, 102.0, 101.0, 101.0),
                candle("2025-10-06", 100.0, 103.0, 100.0, 102.0),
                candle("2025-10-07", 102.0, 106.0, 101.0, 105.0),
            ),
            testFrom = LocalDate.parse("2025-09-01"),
            toDate = LocalDate.parse("2025-10-18"),
            targetPct = 4.0,
            maxHoldingTradingDays = 5,
        )

        val trade = report.trades.single()
        assertEquals(WeeklyLowAlignmentBacktestOutcomes.TARGET_HIT, trade.outcome)
        assertEquals(5, trade.retestGapTradingDays)
        assertEquals(101.0, trade.entryPrice, absoluteTolerance = 0.0001)
        assertEquals(105.04, trade.targetPrice, absoluteTolerance = 0.0001)
        assertEquals("2025-10-07", trade.exitDate)
        assertEquals(4.0, trade.returnPct ?: 0.0, absoluteTolerance = 0.01)
    }

    @Test
    fun `time exits without a stop loss`() {
        val report = engine.run(
            symbol = "TEST",
            companyName = null,
            candles = listOf(
                candle("2025-09-29", 102.0, 104.0, 100.0, 101.0),
                candle("2025-09-30", 101.0, 102.0, 101.0, 101.0),
                candle("2025-10-01", 101.0, 102.0, 101.0, 101.0),
                candle("2025-10-02", 101.0, 102.0, 101.0, 101.0),
                candle("2025-10-03", 101.0, 102.0, 101.0, 101.0),
                candle("2025-10-06", 100.0, 103.0, 100.0, 102.0),
                candle("2025-10-07", 102.0, 103.0, 90.0, 95.0),
            ),
            testFrom = LocalDate.parse("2025-09-01"),
            toDate = LocalDate.parse("2025-10-18"),
            targetPct = 5.0,
            maxHoldingTradingDays = 1,
        )

        val trade = report.trades.single()
        assertEquals(WeeklyLowAlignmentBacktestOutcomes.TIME_EXIT, trade.outcome)
        assertEquals(-5.94, trade.returnPct ?: 0.0, absoluteTolerance = 0.01)
        assertTrue(trade.exitPrice != null)
    }

    private fun candle(date: String, open: Double, high: Double, low: Double, close: Double): DailyCandle = DailyCandle(
        instrumentToken = 1L,
        symbol = "TEST",
        candleDate = LocalDate.parse(date),
        open = open,
        high = high,
        low = low,
        close = close,
        volume = 100L,
    )
}
