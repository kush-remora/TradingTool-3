package com.tradingtool.core.strategy.sma200backtest

import com.tradingtool.core.candle.DailyCandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate

class Sma200BacktestServiceTest {

    @Test
    fun `selects one trade and ignores touches during the 40 session holding period`() {
        val candles = (0 until 90).map { index -> candle(index, low = 99.0) }
        val sma200 = List(candles.size) { 100.0 }

        val (entries, touchCount) = selectSmaEntryIndices(candles, sma200, firstTestIndex = 0)

        assertEquals(90, touchCount)
        assertEquals(listOf(0, 41, 82), entries)
    }

    @Test
    fun `does not count a day above SMA200 as a touch`() {
        val candles = listOf(candle(0, low = 100.01), candle(1, low = 99.99), candle(2, low = 101.0))
        val sma200 = List(candles.size) { 100.0 }

        val (entries, touchCount) = selectSmaEntryIndices(candles, sma200, firstTestIndex = 0)

        assertEquals(1, touchCount)
        assertEquals(listOf(1), entries)
    }

    @Test
    fun `uses the selected SMA period as the entry condition`() {
        val sma50 = listOf(50.0)
        val sma100 = listOf(100.0)
        val sma200 = listOf(200.0)

        assertEquals(sma50, resolveEntrySmaValues(50, sma50, sma100, sma200))
        assertEquals(sma100, resolveEntrySmaValues(100, sma50, sma100, sma200))
        assertEquals(sma200, resolveEntrySmaValues(200, sma50, sma100, sma200))
    }

    @Test
    fun `rejects unsupported entry SMA periods`() {
        assertThrows(IllegalArgumentException::class.java) { validateEntrySmaPeriod(20) }
    }

    private fun candle(index: Int, low: Double): DailyCandle = DailyCandle(
        instrumentToken = 1,
        symbol = "TEST",
        candleDate = LocalDate.of(2025, 1, 1).plusDays(index.toLong()),
        open = 100.0,
        high = 101.0,
        low = low,
        close = 100.0,
        volume = 1_000,
    )
}
