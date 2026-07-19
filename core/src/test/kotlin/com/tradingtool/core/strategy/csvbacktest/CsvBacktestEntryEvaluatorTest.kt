package com.tradingtool.core.strategy.csvbacktest

import com.tradingtool.core.candle.DailyCandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CsvBacktestEntryEvaluatorTest {

    @Test
    fun `retest enters at the attainable limit price`() {
        val signalDate = LocalDate.of(2026, 1, 20)
        val entry = CsvBacktestEntryEvaluator.findEntry(
            candles = listOf(
                candle(date = signalDate.minusDays(1), high = 100.0, low = 95.0),
                candle(date = signalDate.plusDays(1), open = 105.0, high = 106.0, low = 100.5),
            ),
            signalDate = signalDate,
            strategy = CsvBacktestEntryStrategy.RETEST,
            retestWindowDays = 5,
            retestTolerancePct = 1.0,
        )

        assertNotNull(entry)
        assertEquals(100.0, entry?.breakoutLevel)
        assertEquals(101.0, entry?.price)
    }

    @Test
    fun `confirmed retest enters at the next open after confirmation`() {
        val signalDate = LocalDate.of(2026, 1, 20)
        val entry = CsvBacktestEntryEvaluator.findEntry(
            candles = listOf(
                candle(date = signalDate.minusDays(1), high = 100.0, low = 95.0),
                candle(date = signalDate.plusDays(1), open = 103.0, high = 104.0, low = 100.0, close = 99.0),
                candle(date = signalDate.plusDays(2), open = 100.0, high = 102.0, low = 99.0, close = 101.0),
                candle(date = signalDate.plusDays(3), open = 102.0, high = 103.0, low = 101.0),
            ),
            signalDate = signalDate,
            strategy = CsvBacktestEntryStrategy.CONFIRMED_RETEST,
            retestWindowDays = 5,
            retestTolerancePct = 1.0,
        )

        assertNotNull(entry)
        assertEquals(signalDate.plusDays(3), entry?.candle?.candleDate)
        assertEquals(102.0, entry?.price)
    }

    @Test
    fun `retest has no entry when price never returns to the zone`() {
        val signalDate = LocalDate.of(2026, 1, 20)
        val entry = CsvBacktestEntryEvaluator.findEntry(
            candles = listOf(
                candle(date = signalDate.minusDays(1), high = 100.0, low = 95.0),
                candle(date = signalDate.plusDays(1), open = 110.0, high = 112.0, low = 105.0),
            ),
            signalDate = signalDate,
            strategy = CsvBacktestEntryStrategy.RETEST,
            retestWindowDays = 5,
            retestTolerancePct = 1.0,
        )

        assertNull(entry)
    }

    @Test
    fun `two green candles buys at the t plus two open`() {
        val signalDate = LocalDate.of(2026, 1, 20)
        val entry = CsvBacktestEntryEvaluator.findEntry(
            candles = listOf(
                candle(date = signalDate.minusDays(1), high = 100.0, low = 95.0, close = 100.0),
                candle(date = signalDate, high = 105.0, low = 99.0, close = 101.0),
                candle(date = signalDate.plusDays(1), high = 106.0, low = 100.0, close = 102.0),
                candle(date = signalDate.plusDays(2), open = 103.0, high = 107.0, low = 101.0, close = 104.0),
            ),
            signalDate = signalDate,
            strategy = CsvBacktestEntryStrategy.TWO_GREEN_CANDLES,
            retestWindowDays = 5,
            retestTolerancePct = 1.0,
        )

        assertNotNull(entry)
        assertEquals(signalDate.plusDays(2), entry?.candle?.candleDate)
        assertEquals(103.0, entry?.price)
    }

    private fun candle(
        date: LocalDate,
        open: Double = 100.0,
        high: Double,
        low: Double,
        close: Double = 100.0,
    ): DailyCandle = DailyCandle(
        instrumentToken = 1L,
        symbol = "ABC",
        candleDate = date,
        open = open,
        high = high,
        low = low,
        close = close,
        volume = 1L,
    )
}
