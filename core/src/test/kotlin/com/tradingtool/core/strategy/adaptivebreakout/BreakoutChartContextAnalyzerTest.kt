package com.tradingtool.core.strategy.adaptivebreakout

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BreakoutChartContextAnalyzerTest {
    @Test
    fun `rejects price below a falling 200 SMA`() {
        val candles = (0 until 240).map { index ->
            candle(index, 300.0 - index * 0.5)
        }
        val report = BreakoutChartContextAnalyzer.analyze(
            candles = candles,
            close = candles.last().close,
            atr = 10.0,
            majorCeiling = 400.0,
        )

        assertEquals(BreakoutQualityDecision.REJECT, report.overallDecision)
        assertTrue(report.decisionSummary.contains("below a falling 200 SMA"))
    }

    @Test
    fun `rejects when the next obstacle is less than one ATR away`() {
        val history = (0 until 230).map { index -> candle(index, 100.0) }
        val candles = history + candle(230, 110.0)
        val report = BreakoutChartContextAnalyzer.analyze(
            candles = candles,
            close = 110.0,
            atr = 10.0,
            majorCeiling = 115.0,
        )

        assertEquals(BreakoutQualityDecision.REJECT, report.overallDecision)
        assertEquals(0.5, report.roomToObstacleAtr)
        assertEquals("Major ceiling", report.nextObstacleLabel)
    }

    private fun candle(index: Int, close: Double): DailyCandle = DailyCandle(
        instrumentToken = 1L,
        symbol = "TEST",
        candleDate = LocalDate.of(2025, 1, 1).plusDays(index.toLong()),
        open = close,
        high = close + 1.0,
        low = close - 1.0,
        close = close,
        volume = 1_000L,
    )
}
