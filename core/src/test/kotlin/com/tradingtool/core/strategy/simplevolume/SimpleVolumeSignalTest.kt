package com.tradingtool.core.strategy.simplevolume

import com.tradingtool.core.candle.DailyCandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.time.LocalDate

class SimpleVolumeSignalTest {
    @Test
    fun `classifies pocket pivot against prior down day volume`() {
        val candles = baselineCandles(50) + listOf(
            candle(51, open = 100.0, close = 95.0, volume = 1_500L),
            candle(52, open = 95.0, close = 101.0, volume = 1_600L),
        )

        val signal = calculateSimpleVolumeSignals(candles).last()

        assertEquals(SimpleVolumeClassification.POCKET_PIVOT, signal.classification)
        assertTrue(signal.pocketPivot)
        assertEquals(1.5841584158415842, signal.relativeVolume)
    }

    @Test
    fun `classifies dry and high volume direction bars`() {
        val candles = baselineCandles(50) + listOf(
            candle(51, open = 100.0, close = 101.0, volume = 100L),
            candle(52, open = 101.0, close = 99.0, volume = 2_000L),
            candle(53, open = 99.0, close = 102.0, volume = 1_500L),
        )

        val signals = calculateSimpleVolumeSignals(candles).takeLast(3)

        assertEquals(SimpleVolumeClassification.DRY, signals[0].classification)
        assertEquals(SimpleVolumeClassification.HIGH_VOLUME_DOWN, signals[1].classification)
        assertEquals(SimpleVolumeClassification.HIGH_VOLUME_UP, signals[2].classification)
    }

    @Test
    fun `flags bull snort only with three times volume and strong close`() {
        val candles = baselineCandles(50) + listOf(
            candle(51, open = 100.0, high = 110.0, low = 99.0, close = 109.0, volume = 3_100L),
            candle(52, open = 109.0, high = 111.0, low = 100.0, close = 104.0, volume = 3_100L),
        )

        val signals = calculateSimpleVolumeSignals(candles).takeLast(2)

        assertTrue(signals[0].bullSnort)
        assertFalse(signals[1].bullSnort)
    }

    @Test
    fun `keeps early bars explicitly insufficient`() {
        val signal = calculateSimpleVolumeSignals(baselineCandles(5)).last()

        assertEquals(SimpleVolumeClassification.INSUFFICIENT_DATA, signal.classification)
        assertEquals(null, signal.averageVolume)
    }

    private fun baselineCandles(count: Int): List<DailyCandle> = (1..count).map { index ->
        candle(index, volume = 1_000L)
    }

    private fun candle(
        index: Int,
        open: Double = 100.0,
        high: Double = 105.0,
        low: Double = 95.0,
        close: Double = 102.0,
        volume: Long,
    ): DailyCandle = DailyCandle(
        instrumentToken = 1L,
        symbol = "TEST",
        candleDate = LocalDate.of(2026, 1, 1).plusDays(index.toLong()),
        open = open,
        high = high,
        low = low,
        close = close,
        volume = volume,
    )
}
