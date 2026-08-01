package com.tradingtool.core.strategy.weeklylowlimit

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeeklyLowLimitBacktestEngineTest {
    private val engine = WeeklyLowLimitBacktestEngine()

    @Test
    fun `fills previous week low and exits at target before week close`() {
        val report = engine.run(
            symbol = "TEST",
            companyName = null,
            candles = listOf(
                candle("2025-09-29", open = 102.0, high = 105.0, low = 100.0, close = 103.0),
                candle("2025-10-06", open = 101.5, high = 103.0, low = 100.0, close = 100.5),
                candle("2025-10-07", open = 100.5, high = 107.0, low = 100.0, close = 104.0),
                candle("2025-10-13", open = 104.0, high = 106.0, low = 103.0, close = 105.0),
            ),
            testFrom = LocalDate.parse("2025-09-01"),
            toDate = LocalDate.parse("2025-10-18"),
        )

        val trade = report.trades.single()
        assertEquals("TARGET_HIT", trade.outcome)
        assertEquals("2025-10-06", trade.entryDate)
        assertEquals("2025-09-29", trade.previousWeekLowDate)
        assertEquals(101.0, trade.limitPrice, absoluteTolerance = 0.0001)
        assertEquals(0.49505, trade.entryOpenDeviationPct ?: 0.0, absoluteTolerance = 0.0001)
        assertEquals(101.0, trade.entryPrice ?: 0.0, absoluteTolerance = 0.0001)
        assertEquals("2025-10-07", trade.exitDate)
        assertEquals(5.0, trade.returnPct ?: 0.0, absoluteTolerance = 0.0001)
        assertEquals(1, trade.holdingTradingDays)
    }

    @Test
    fun `records no fill and uses stop first for an ambiguous candle`() {
        val report = engine.run(
            symbol = "TEST",
            companyName = null,
            candles = listOf(
                candle("2025-09-29", open = 102.0, high = 105.0, low = 100.0, close = 103.0),
                candle("2025-10-06", open = 103.0, high = 105.0, low = 102.0, close = 104.0),
                candle("2025-10-13", open = 102.0, high = 104.0, low = 101.0, close = 102.0),
                candle("2025-10-14", open = 102.0, high = 110.0, low = 95.0, close = 105.0),
                candle("2025-10-20", open = 105.0, high = 106.0, low = 104.0, close = 105.0),
            ),
            testFrom = LocalDate.parse("2025-09-01"),
            toDate = LocalDate.parse("2025-10-25"),
        )

        assertEquals(2, report.summary.setupCount)
        assertEquals(1, report.summary.noFillCount)
        assertEquals(1, report.summary.stopLossCount)
        assertEquals(1, report.summary.ambiguousExitCount)
        assertTrue(report.trades.any { trade -> trade.outcome == "NO_FILL" })
        assertTrue(report.trades.any { trade -> trade.outcome == "STOP_LOSS" && trade.exitWasAmbiguous })
    }

    @Test
    fun `excludes the current incomplete week`() {
        val report = engine.run(
            symbol = "TEST",
            companyName = null,
            candles = listOf(
                candle("2025-09-29", open = 102.0, high = 105.0, low = 100.0, close = 103.0),
                candle("2025-10-06", open = 103.0, high = 104.0, low = 102.0, close = 102.0),
                candle("2025-10-13", open = 102.0, high = 110.0, low = 100.0, close = 109.0),
            ),
            testFrom = LocalDate.parse("2025-09-01"),
            toDate = LocalDate.parse("2025-10-18"),
        )

        assertEquals(listOf("2025-10-06"), report.trades.map { trade -> trade.entryWeekStartDate })
    }

    @Test
    fun `first three days rule ignores a Thursday fill`() {
        val report = engine.run(
            symbol = "TEST",
            companyName = null,
            candles = listOf(
                candle("2025-09-29", open = 102.0, high = 103.0, low = 100.0, close = 102.0),
                candle("2025-10-06", open = 102.0, high = 103.0, low = 102.0, close = 102.0),
                candle("2025-10-07", open = 102.0, high = 103.0, low = 102.0, close = 102.0),
                candle("2025-10-08", open = 102.0, high = 103.0, low = 102.0, close = 102.0),
                candle("2025-10-09", open = 100.0, high = 101.0, low = 99.0, close = 100.0),
                candle("2025-10-10", open = 100.0, high = 101.0, low = 99.0, close = 100.0),
                candle("2025-10-13", open = 100.0, high = 101.0, low = 99.0, close = 100.0),
            ),
            testFrom = LocalDate.parse("2025-09-01"),
            toDate = LocalDate.parse("2025-10-18"),
            entryRule = WeeklyLowLimitBacktestEntryRules.FIRST_3_DAYS_WEEK_CLOSE,
        )

        val trade = report.trades.single()
        assertEquals(WeeklyLowLimitBacktestEntryRules.FIRST_3_DAYS_WEEK_CLOSE, report.entryRule)
        assertEquals("NO_FILL", trade.outcome)
        assertEquals("2025-10-08", trade.orderEndDate)
    }

    @Test
    fun `skips setup before market open when prior week closed below the limit`() {
        val report = engine.run(
            symbol = "TEST",
            companyName = null,
            candles = listOf(
                candle("2025-09-29", open = 102.0, high = 103.0, low = 100.0, close = 100.5),
                candle("2025-10-06", open = 99.0, high = 102.0, low = 98.0, close = 101.0),
                candle("2025-10-13", open = 101.0, high = 102.0, low = 100.0, close = 101.0),
            ),
            testFrom = LocalDate.parse("2025-09-01"),
            toDate = LocalDate.parse("2025-10-18"),
        )

        val trade = report.trades.single()
        assertEquals("PREMARKET_FILTER_SKIP", trade.outcome)
        assertEquals(100.5, trade.previousWeekLastClose, absoluteTolerance = 0.0001)
        assertEquals(101.0, trade.limitPrice, absoluteTolerance = 0.0001)
        assertEquals(1, report.summary.premarketFilterSkipCount)
        assertEquals(0, report.summary.noFillCount)
        assertEquals(null, trade.entryDate)
    }

    @Test
    fun `skips entry when candidate open is more than one percent from the limit`() {
        val report = engine.run(
            symbol = "TEST",
            companyName = null,
            candles = listOf(
                candle("2025-09-29", open = 102.0, high = 103.0, low = 100.0, close = 102.0),
                candle("2025-10-06", open = 98.0, high = 102.0, low = 97.0, close = 101.0),
                candle("2025-10-13", open = 101.0, high = 102.0, low = 100.0, close = 101.0),
            ),
            testFrom = LocalDate.parse("2025-09-01"),
            toDate = LocalDate.parse("2025-10-18"),
        )

        val trade = report.trades.single()
        assertEquals("OPEN_DEVIATION_SKIP", trade.outcome)
        assertEquals(2.970297, trade.entryOpenDeviationPct ?: 0.0, absoluteTolerance = 0.0001)
        assertEquals(1, report.summary.openDeviationSkipCount)
        assertEquals(null, trade.entryDate)
    }

    @Test
    fun `any day rule holds for five trading days across a week boundary`() {
        val report = engine.run(
            symbol = "TEST",
            companyName = null,
            candles = listOf(
                candle("2025-09-29", open = 102.0, high = 103.0, low = 100.0, close = 102.0),
                candle("2025-10-06", open = 102.0, high = 103.0, low = 102.0, close = 102.0),
                candle("2025-10-07", open = 102.0, high = 103.0, low = 102.0, close = 102.0),
                candle("2025-10-08", open = 102.0, high = 103.0, low = 102.0, close = 102.0),
                candle("2025-10-09", open = 102.0, high = 103.0, low = 102.0, close = 102.0),
                candle("2025-10-10", open = 101.0, high = 102.0, low = 101.0, close = 101.0),
                candle("2025-10-13", open = 100.0, high = 104.0, low = 99.0, close = 104.0),
                candle("2025-10-14", open = 104.0, high = 107.0, low = 103.0, close = 105.0),
                candle("2025-10-15", open = 105.0, high = 106.0, low = 104.0, close = 105.0),
            ),
            testFrom = LocalDate.parse("2025-09-01"),
            toDate = LocalDate.parse("2025-10-25"),
            entryRule = WeeklyLowLimitBacktestEntryRules.ANY_DAY_MAX_5_TRADING_DAYS,
        )

        val firstTrade = report.trades.first()
        assertEquals("TARGET_HIT", firstTrade.outcome)
        assertEquals("2025-10-14", firstTrade.exitDate)
        assertEquals(2, firstTrade.holdingTradingDays)
        assertEquals(1, report.summary.positionOpenSkipCount)
        assertEquals("POSITION_OPEN_SKIP", report.trades[1].outcome)
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
