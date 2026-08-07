package com.tradingtool.core.strategy.twodaygreen

import com.tradingtool.core.candle.DailyCandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TwoDayGreenCandleBacktestEngineTest {
    private val engine = TwoDayGreenCandleBacktestEngine()
    private val member = TwoDayGreenCandleMember("TEST", "Test Company", 1L)

    @Test
    fun `buys on third day after two green days above one percent`() {
        val candles = listOf(
            candle(0, open = 100.0, close = 100.0, volume = 100),
            candle(1, open = 100.0, close = 102.0, high = 103.0, volume = 110),
            candle(2, open = 102.0, close = 104.5, high = 108.0, volume = 120),
            candle(3, open = 104.0, close = 108.0, high = 110.0, volume = 130),
        )

        val report = engine.run(member, candles, testSessionCount = 4)

        assertEquals(1, report.summary.setupCount)
        assertEquals("2025-01-04", report.trades.single().buyDay.date)
        assertEquals(104.0, report.trades.single().entryPrice)
        assertEquals("TARGET_HIT", report.trades.single().outcome)
        assertEquals(1, report.trades.single().holdingTradingDays)
        assertEquals(true, report.trades.single().setupVolumeRising)
        assertEquals(true, report.trades.single().setupMoveRising)
    }

    @Test
    fun `rejects a setup when either prior candle is not green`() {
        val candles = listOf(
            candle(0, open = 100.0, close = 100.0),
            candle(1, open = 100.0, close = 102.0),
            candle(2, open = 102.0, close = 101.0),
            candle(3, open = 101.0, close = 106.0, high = 106.0),
        )

        val report = engine.run(member, candles, testSessionCount = 4)

        assertEquals(0, report.summary.setupCount)
    }

    @Test
    fun `marks a trade unresolved when target is not reached`() {
        val candles = listOf(
            candle(0, open = 100.0, close = 100.0),
            candle(1, open = 100.0, close = 102.0),
            candle(2, open = 102.0, close = 104.0, high = 104.0),
            candle(3, open = 104.0, close = 105.0, high = 105.0),
        )

        val trade = engine.run(member, candles, testSessionCount = 4).trades.single()

        assertEquals("UNRESOLVED", trade.outcome)
        assertNull(trade.exitDate)
        assertEquals(0.96, trade.unresolvedCloseReturnPct)
        assertEquals(2.0, trade.setupDayOne.openToClosePct)
        assertEquals(2.0, trade.setupDayOne.lowToHighPct)
    }

    @Test
    fun `limits signals to the latest forty trading sessions`() {
        val candles = (0 until 45).map { index ->
            candle(
                index,
                open = 100.0 + (index * 2.0),
                close = (100.0 + (index * 2.0)) * 1.03,
                high = (100.0 + (index * 2.0)) * 1.03,
            )
        }

        val report = engine.run(member, candles, testSessionCount = 40)

        assertEquals("2025-01-06", report.testedFromDate)
        assertEquals(38, report.summary.setupCount)
    }

    private fun candle(
        index: Int,
        open: Double,
        close: Double,
        high: Double = maxOf(open, close),
        low: Double = minOf(open, close),
        volume: Long = 100,
    ): DailyCandle = DailyCandle(
        instrumentToken = 1,
        symbol = "TEST",
        candleDate = LocalDate.of(2025, 1, 1).plusDays(index.toLong()),
        open = open,
        high = high,
        low = low,
        close = close,
        volume = volume,
    )
}
