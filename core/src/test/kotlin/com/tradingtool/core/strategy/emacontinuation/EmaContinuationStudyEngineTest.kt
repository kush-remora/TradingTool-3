package com.tradingtool.core.strategy.emacontinuation

import com.tradingtool.core.candle.DailyCandle
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EmaContinuationStudyEngineTest {
    @Test
    fun `detects close crossing above both emas`() {
        assertTrue(
            EmaContinuationStudyEngine.isUpwardEmaCross(
                previousClose = 99.0,
                currentClose = 105.0,
                previousEma10 = 100.0,
                previousEma20 = 102.0,
                currentEma10 = 101.0,
                currentEma20 = 103.0,
            ),
        )
        assertFalse(
            EmaContinuationStudyEngine.isUpwardEmaCross(
                previousClose = 103.0,
                currentClose = 105.0,
                previousEma10 = 100.0,
                previousEma20 = 102.0,
                currentEma10 = 101.0,
                currentEma20 = 103.0,
            ),
        )
    }

    @Test
    fun `base breakout requires close above base high and volume`() {
        val base = EmaContinuationStudyEngine.BaseRange(
            startDate = LocalDate.of(2025, 1, 2),
            endDate = LocalDate.of(2025, 1, 6),
            high = 105.0,
            low = 100.0,
            widthPct = 5.0,
        )
        val breakout = point(day = 10, close = 106.0, low = 103.0, ema10 = 102.0, ema20 = 101.0, sma200 = 90.0, volumeConfirmed = true)
        val noBreakout = point(day = 11, close = 104.0, low = 101.0, ema10 = 102.0, ema20 = 101.0, sma200 = 90.0, volumeConfirmed = true)
        val lowVolume = point(day = 12, close = 106.0, low = 103.0, ema10 = 102.0, ema20 = 101.0, sma200 = 90.0, volumeConfirmed = false)

        assertTrue(EmaContinuationStudyEngine.isBaseBreakout(breakout, base))
        assertFalse(EmaContinuationStudyEngine.isBaseBreakout(noBreakout, base))
        assertTrue(EmaContinuationStudyEngine.isBaseBreakout(lowVolume, base))
    }

    @Test
    fun `ema cross requires at least one point five times prior twenty session volume`() {
        val baseline = List(20) { 1_000L }

        assertTrue(EmaContinuationStudyEngine.isVolumeConfirmed(1_500L, baseline))
        assertFalse(EmaContinuationStudyEngine.isVolumeConfirmed(1_499L, baseline))
        assertFalse(EmaContinuationStudyEngine.isVolumeConfirmed(1_500L, baseline.take(19)))
    }

    @Test
    fun `target timing uses first daily high to reach target`() {
        assertEquals(2, EmaContinuationStudyEngine.firstTargetHitDays(100.0, listOf(101.0, 105.0, 110.0), 5.0))
        assertEquals(null, EmaContinuationStudyEngine.firstTargetHitDays(100.0, listOf(101.0, 104.9), 5.0))
    }

    @Test
    fun `reports close location and ema20 distance in atr units`() {
        val closeNearHigh = EmaContinuationStudyEngine.closeNearHighPct(high = 110.0, low = 100.0, close = 105.0)
        val closeAboveEma20Atr = EmaContinuationStudyEngine.closeAboveEma20Atr(close = 105.0, ema20 = 100.0, atr = 10.0)

        assertTrue(closeNearHigh?.let { value -> abs(value - 50.0) < 0.001 } == true)
        assertTrue(closeAboveEma20Atr?.let { value -> abs(value - 0.5) < 0.001 } == true)
        assertEquals(null, EmaContinuationStudyEngine.closeNearHighPct(high = 100.0, low = 100.0, close = 100.0))
        assertEquals(null, EmaContinuationStudyEngine.closeAboveEma20Atr(close = 105.0, ema20 = 100.0, atr = 0.0))
    }

    @Test
    fun `finds the longest qualifying base after the ema setup`() {
        val points = listOf(
            point(day = 0, close = 100.0, low = 99.0, ema10 = 100.0, ema20 = 99.0, sma200 = 90.0),
            point(day = 1, close = 101.0, low = 99.5, ema10 = 100.0, ema20 = 99.0, sma200 = 90.0),
            point(day = 2, close = 102.0, low = 100.0, ema10 = 100.0, ema20 = 99.0, sma200 = 90.0),
            point(day = 3, close = 101.5, low = 99.8, ema10 = 100.0, ema20 = 99.0, sma200 = 90.0),
            point(day = 4, close = 102.0, low = 100.2, ema10 = 100.0, ema20 = 99.0, sma200 = 90.0),
            point(day = 5, close = 101.8, low = 100.1, ema10 = 100.0, ema20 = 99.0, sma200 = 90.0),
            point(day = 6, close = 103.0, low = 101.0, ema10 = 100.0, ema20 = 99.0, sma200 = 90.0),
        )

        val base = EmaContinuationStudyEngine.findBase(breakoutIndex = 6, setupIndex = 0, points = points)

        assertEquals(LocalDate.of(2025, 1, 2), base?.startDate)
        assertEquals(LocalDate.of(2025, 1, 6), base?.endDate)
        assertTrue((base?.widthPct ?: 100.0) <= EmaContinuationStudyEngine.MAXIMUM_BASE_WIDTH_PCT)
    }

    @Test
    fun `rejects bases shorter than five sessions or wider than twelve percent`() {
        val shortBasePoints = (0..5).map { day ->
            point(day = day, close = 103.0, low = 100.0, ema10 = 100.0, ema20 = 99.0, sma200 = 90.0)
        }
        val wideBasePoints = (0..6).map { day ->
            point(day = day, close = 120.0, low = 90.0, ema10 = 100.0, ema20 = 99.0, sma200 = 90.0)
        }

        assertEquals(null, EmaContinuationStudyEngine.findBase(breakoutIndex = 5, setupIndex = 0, points = shortBasePoints))
        assertEquals(null, EmaContinuationStudyEngine.findBase(breakoutIndex = 6, setupIndex = 0, points = wideBasePoints))
    }

    @Test
    fun `rejects study without enough warmup history`() {
        val candles = (1..219).map { day -> candle(day, 100.0, 99.0) }

        assertFailsWith<IllegalArgumentException> {
            EmaContinuationStudyEngine.run(
                candles = candles,
                testFromDate = LocalDate.of(2026, 1, 1),
                testToDate = LocalDate.of(2026, 2, 1),
                watchlistKey = "leaders",
            )
        }
    }

    private fun point(
        day: Int,
        close: Double,
        low: Double = close - 1.0,
        ema10: Double,
        ema20: Double,
        sma200: Double,
        volumeConfirmed: Boolean = false,
    ): EmaContinuationStudyEngine.IndicatorPoint = EmaContinuationStudyEngine.IndicatorPoint(
        candle = candle(day, high = close, low = low, close = close),
        ema10 = ema10,
        ema20 = ema20,
        sma200 = sma200,
        volumeConfirmed = volumeConfirmed,
        isUpwardEmaCross = false,
    )

    private fun candle(day: Int, high: Double, low: Double, close: Double = (high + low) / 2.0): DailyCandle = DailyCandle(
        instrumentToken = 123L,
        symbol = "TEST",
        candleDate = LocalDate.of(2025, 1, 1).plusDays(day.toLong()),
        open = low,
        high = high,
        low = low,
        close = close,
        volume = 1_000L,
    )
}
