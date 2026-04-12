package com.tradingtool.core.strategy.volume

import com.tradingtool.core.candle.DailyCandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class VolumeAnalyzerTest {
    @Test
    fun `analyze computes recent windows and breakout metrics`() {
        val candles = buildCandles(
            volumes = List(25) { 100L } + listOf(100L, 100L, 200L, 200L, 220L),
            closes = List(25) { 100.0 } + listOf(100.0, 101.0, 102.0, 103.0, 106.0),
            highs = List(25) { 101.0 } + listOf(101.0, 102.0, 103.0, 104.0, 105.0),
        )

        val result = VolumeAnalyzer.analyze(candles)

        assertNotNull(result)
        assertEquals(110.0, result?.avgVolume20d)
        assertEquals(2.0, result?.todayVolumeRatio)
        assertEquals(2.07, result?.recent3dAvgVolumeRatio)
        assertEquals(2.2, result?.recent5dMaxVolumeRatio)
        assertEquals(3, result?.spikePersistenceDays5d)
        assertEquals(1.32, result?.recent10dAvgVolumeRatio)
        assertEquals(3, result?.elevatedVolumeDays10d)
        assertEquals(2.91, result?.todayPriceChangePct)
        assertEquals(4.95, result?.priceReturn3dPct)
        assertTrue(result?.breakoutAbove20dHigh == true)
    }

    @Test
    fun `analyze returns null when there is insufficient history`() {
        val candles = buildCandles(volumes = List(29) { 100L }, closes = List(29) { 100.0 }, highs = List(29) { 101.0 })
        assertEquals(null, VolumeAnalyzer.analyze(candles))
    }

    @Test
    fun `analyze does not mark breakout when close stays below prior 20 day high`() {
        val candles = buildCandles(
            volumes = List(25) { 100L } + listOf(100L, 100L, 180L, 170L, 150L),
            closes = List(25) { 100.0 } + listOf(100.1, 100.3, 100.5, 100.8, 100.7),
            highs = List(30) { 110.0 },
        )

        val result = VolumeAnalyzer.analyze(candles)

        assertNotNull(result)
        assertFalse(result?.breakoutAbove20dHigh == true)
    }

    @Test
    fun `recent 5 day metrics exclude the full recent spike block from baseline`() {
        val candles = buildCandles(
            volumes = List(25) { 100L } + listOf(200L, 220L, 240L, 260L, 280L),
            closes = List(25) { 100.0 } + listOf(101.0, 102.0, 103.0, 104.0, 105.0),
            highs = List(25) { 101.0 } + listOf(102.0, 103.0, 104.0, 105.0, 106.0),
        )

        val result = VolumeAnalyzer.analyze(candles)

        assertNotNull(result)
        assertEquals(2.8, result?.recent5dMaxVolumeRatio)
        assertEquals(5, result?.spikePersistenceDays5d)
        assertEquals(1.7, result?.recent10dAvgVolumeRatio)
        assertEquals(5, result?.elevatedVolumeDays10d)
    }

    @Test
    fun `recent 10 day buildup metrics capture softer accumulation before a breakout`() {
        val candles = buildCandles(
            volumes = List(20) { 100L } + listOf(165L, 210L, 120L, 130L, 170L, 110L, 205L, 115L, 125L, 400L),
            closes = List(20) { 100.0 } + listOf(101.0, 102.0, 101.5, 101.8, 102.5, 102.3, 103.0, 103.2, 103.8, 106.0),
            highs = List(20) { 101.0 } + listOf(102.0, 103.0, 102.0, 102.5, 103.0, 103.0, 104.0, 104.0, 104.5, 105.0),
        )

        val result = VolumeAnalyzer.analyze(candles)

        assertNotNull(result)
        assertEquals(1.75, result?.recent10dAvgVolumeRatio)
        assertEquals(5, result?.elevatedVolumeDays10d)
        assertEquals(1, result?.spikePersistenceDays5d)
    }

    private fun buildCandles(volumes: List<Long>, closes: List<Double>, highs: List<Double>): List<DailyCandle> {
        return volumes.indices.map { index ->
            DailyCandle(
                instrumentToken = 1L,
                symbol = "AAA",
                candleDate = LocalDate.of(2026, 1, 1).plusDays(index.toLong()),
                open = closes[index],
                high = highs[index],
                low = closes[index] - 1.0,
                close = closes[index],
                volume = volumes[index],
            )
        }
    }
}
