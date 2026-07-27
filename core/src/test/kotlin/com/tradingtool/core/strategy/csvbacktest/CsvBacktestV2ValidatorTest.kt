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
    fun `rejects a repeated breakout in the prior fifty nine sessions for a sixty session lookback`() {
        val candles = validCandles().toMutableList()
        for (index in candles.lastIndex - 59 until candles.lastIndex) {
            candles[index] = candles[index].copy(high = 95.0, close = 95.0)
        }
        val earlierBreakoutIndex = candles.lastIndex - 10
        candles[earlierBreakoutIndex] = candles[earlierBreakoutIndex].copy(high = 97.0)
        candles[candles.lastIndex] = candles.last().copy(high = 98.0)

        val result = CsvBacktestV2Validator.validate(
            candles = candles,
            signalDate = candles.last().candleDate,
            breakoutLookbackSessions = 60,
        )

        assertNull(result)
    }

    @Test
    fun `fresh breakout allows only the first breakout inside a ten session window`() {
        val firstDate = LocalDate.of(2026, 3, 1)
        val candles = (0..24).map { index ->
            DailyCandle(
                instrumentToken = 1L,
                symbol = "ABC",
                candleDate = firstDate.plusDays(index.toLong()),
                open = 99.0,
                high = when (index) {
                    20 -> 101.0
                    24 -> 102.0
                    else -> 99.0
                },
                low = 98.0,
                close = 99.0,
                volume = 1_000L,
            )
        }

        val firstBreakoutLevel = CsvBacktestV2Validator.freshBreakoutLevel(
            candles = candles,
            signalDate = candles[20].candleDate,
            breakoutLookbackSessions = 10,
        )
        val repeatedBreakoutLevel = CsvBacktestV2Validator.freshBreakoutLevel(
            candles = candles,
            signalDate = candles[24].candleDate,
            breakoutLookbackSessions = 10,
        )

        assertEquals(99.0, firstBreakoutLevel)
        assertNull(repeatedBreakoutLevel)
    }

    @Test
    fun `breakout compares signal high with prior closes instead of prior highs`() {
        val firstDate = LocalDate.of(2026, 1, 1)
        val candles = (0..69).map { index ->
            DailyCandle(
                instrumentToken = 1L,
                symbol = "ABC",
                candleDate = firstDate.plusDays(index.toLong()),
                open = 100.0,
                high = when (index) {
                    9 -> 110.0
                    69 -> 105.0
                    else -> 100.0
                },
                low = 99.0,
                close = 100.0,
                volume = 1_000L,
            )
        }

        val breakoutLevel = CsvBacktestV2Validator.freshBreakoutLevel(
            candles = candles,
            signalDate = candles.last().candleDate,
            breakoutLookbackSessions = 60,
        )

        assertEquals(100.0, breakoutLevel)
    }

    @Test
    fun `uses the configured breakout lookback for the breakout level`() {
        val candles = validCandles().toMutableList()
        for (index in candles.lastIndex - 59 until candles.lastIndex) {
            candles[index] = candles[index].copy(high = 95.0, close = 95.0)
        }

        val result = CsvBacktestV2Validator.validate(
            candles = candles,
            signalDate = candles.last().candleDate,
            breakoutLookbackSessions = 60,
        )

        assertNotNull(result)
        assertEquals(95.0, result?.breakoutLevel)
    }

    @Test
    fun `rejects a breakout without a two times pre-breakout volume spike`() {
        val candles = validCandles().toMutableList()
        candles[candles.lastIndex - 10] = candles[candles.lastIndex - 10].copy(volume = 1_999L)

        assertNull(CsvBacktestV2Validator.validate(candles, candles.last().candleDate))
    }

    @Test
    fun `uses the lowest price from the previous fifty sessions for the V2 run base`() {
        val candles = validCandles().toMutableList()
        val lowIndex = candles.lastIndex - 40

        for (index in lowIndex - 5..lowIndex) {
            candles[index] = candles[index].copy(close = 85.0)
        }
        candles[lowIndex] = candles[lowIndex].copy(low = 80.0)

        val result = CsvBacktestV2Validator.validate(candles, candles.last().candleDate)

        assertNotNull(result)
        assertEquals(85.0, result?.recentRunBasePrice)
        assertEquals(17.647, result?.moveFromRecentBasePct ?: 0.0, 0.001)
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
                    index == 120 || index == 121 -> 100.0
                    else -> 96.0
                },
                low = if (isRecentLow) 90.0 else 94.0,
                close = when {
                    isSignalDay -> 100.0
                    isNearResistance -> 97.0
                    index == 120 || index == 121 -> 100.0
                    else -> 95.0
                },
                volume = if (index == 210) 2_000L else 1_000L,
            )
        }
    }
}
