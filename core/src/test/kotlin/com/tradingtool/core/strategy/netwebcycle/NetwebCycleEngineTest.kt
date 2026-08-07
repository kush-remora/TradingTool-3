package com.tradingtool.core.strategy.netwebcycle

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NetwebCycleEngineTest {
    private val engine = NetwebCycleEngine()

    @Test
    fun `keeps a five percent move inside the active base as weekly rotation`() {
        val report = engine.run("NETWEB", candles(baseCloses() + 105.0), config())

        assertEquals(NetwebCyclePhase.WEEKLY_ROTATION, report.current.phase)
        assertEquals(105.0, report.current.currentPrice)
        assertEquals(100.0, report.current.baseLow)
        assertEquals(110.0, report.current.baseHigh)
        assertTrue(report.current.fivePercentMoveCount > 0)
        assertTrue(report.current.evidence.any { evidence -> evidence.contains("within the active base") })
    }

    @Test
    fun `moves from weekly rotation into bull run after a confirmed breakout`() {
        val report = engine.run(
            "NETWEB",
            candles(baseCloses() + listOf(105.0, 112.0, 118.0)),
            config(),
        )

        assertEquals(NetwebCyclePhase.BULL_RUN, report.current.phase)
        assertTrue(report.current.breakoutAboveBase)
        assertTrue(report.segments.any { segment -> segment.phase == NetwebCyclePhase.BULL_RUN })
    }

    @Test
    fun `moves from bull run into drawdown after a sustained decline from the peak`() {
        val report = engine.run(
            "NETWEB",
            candles(baseCloses() + listOf(105.0, 112.0, 118.0, 120.0, 115.0, 108.0)),
            config(),
        )

        assertEquals(NetwebCyclePhase.DRAWDOWN, report.current.phase)
        assertTrue((report.current.drawdownFromPeakPct ?: 0.0) <= -8.0)
    }

    private fun baseCloses(): List<Double> = buildList {
        repeat(5) {
            addAll(listOf(100.0, 110.0, 100.0, 110.0, 100.0))
        }
    }

    private fun candles(closes: List<Double>): List<DailyCandle> = closes.mapIndexed { index, close ->
        DailyCandle(
            instrumentToken = 1L,
            symbol = "NETWEB",
            candleDate = LocalDate.of(2026, 1, 1).plusDays(index.toLong()),
            open = close,
            high = close + 1.0,
            low = close - 1.0,
            close = close,
            volume = 100L,
        )
    }

    private fun config(): NetwebCycleConfig = NetwebCycleConfig(
        minimumHistoryTradingDays = 25,
        minimumNewBaseTradingDays = 3,
    )
}
