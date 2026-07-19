package com.tradingtool.core.strategy.csvbacktest

import com.tradingtool.core.candle.DailyCandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CsvBacktestV2ValidatorTest {

    @Test
    fun `validates a fresh breakout and returns its strength metrics`() {
        val candles = validCandles()
        val signalDate = candles.last().candleDate

        val result = CsvBacktestV2Validator.validate(candles, signalDate)

        assertNotNull(result)
        assertEquals(100.0, result?.breakoutLevel)
        assertEquals(2.0, result?.maxPreBreakoutVolumeRatio)
        assertEquals(1, result?.failedResistanceAttempts)
        assertEquals(95.0, result?.recentRunBasePrice)
        assertEquals(5.263, result?.moveFromRecentBasePct ?: 0.0, 0.001)
    }

    @Test
    fun `rejects a breakout when the breakout close is more than six percent above the prior close`() {
        val candles = validCandles().toMutableList()
        candles[candles.lastIndex] = candles.last().copy(close = 107.0)

        assertNull(CsvBacktestV2Validator.validate(candles, candles.last().candleDate))
    }

    @Test
    fun `rejects a repeated breakout inside the fresh breakout window`() {
        val candles = validCandles().toMutableList()
        val earlierBreakoutIndex = candles.lastIndex - 10
        candles[earlierBreakoutIndex] = candles[earlierBreakoutIndex].copy(high = 101.0)
        candles[candles.lastIndex] = candles.last().copy(high = 102.0)

        assertNull(CsvBacktestV2Validator.validate(candles, candles.last().candleDate))
    }

    @Test
    fun `rejects a breakout without a two times pre-breakout volume spike`() {
        val candles = validCandles().toMutableList()
        candles[candles.lastIndex - 10] = candles[candles.lastIndex - 10].copy(volume = 1_999L)

        assertNull(CsvBacktestV2Validator.validate(candles, candles.last().candleDate))
    }

    private fun validCandles(): List<DailyCandle> {
        val firstDate = LocalDate.of(2025, 1, 1)
        return (0..220).map { index ->
            val isSignalDay = index == 220
            val isNearResistance = index == 190 || index == 191
            val isRecentLow = index == 205
            DailyCandle(
                instrumentToken = 1L,
                symbol = "ABC",
                candleDate = firstDate.plusDays(index.toLong()),
                open = 95.0,
                high = when {
                    isSignalDay -> 101.0
                    isNearResistance -> 98.0
                    index == 120 -> 100.0
                    else -> 96.0
                },
                low = if (isRecentLow) 90.0 else 94.0,
                close = if (isSignalDay) 100.0 else if (isNearResistance) 97.0 else 95.0,
                volume = if (index == 210) 2_000L else 1_000L,
            )
        }
    }
}
