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
    fun `breakout close reclaim enters at the open after the first qualifying close`() {
        val signalDate = LocalDate.of(2026, 1, 20)

        val entry = CsvBacktestEntryEvaluator.findEntry(
            candles = listOf(
                candle(date = signalDate.minusDays(1), high = 100.0, low = 95.0, close = 99.0),
                candle(date = signalDate, high = 105.0, low = 99.0, close = 101.0),
                candle(date = signalDate.plusDays(1), high = 102.0, low = 98.0, close = 101.0),
                candle(date = signalDate.plusDays(2), high = 104.0, low = 100.0, close = 102.0),
                candle(date = signalDate.plusDays(3), open = 103.0, high = 105.0, low = 102.0, close = 104.0),
            ),
            signalDate = signalDate,
            strategy = CsvBacktestEntryStrategy.BREAKOUT_CLOSE_RECLAIM,
            retestWindowDays = 5,
            retestTolerancePct = 1.0,
        )

        assertNotNull(entry)
        assertEquals(signalDate.plusDays(3), entry?.candle?.candleDate)
        assertEquals(103.0, entry?.price)
        assertEquals(101.0, entry?.breakoutLevel)
    }

    @Test
    fun `breakout close reclaim rejects a confirmation without a following entry session`() {
        val signalDate = LocalDate.of(2026, 1, 20)

        val entry = CsvBacktestEntryEvaluator.findEntry(
            candles = listOf(
                candle(date = signalDate.minusDays(1), high = 100.0, low = 95.0, close = 99.0),
                candle(date = signalDate, high = 105.0, low = 99.0, close = 101.0),
                candle(date = signalDate.plusDays(1), high = 104.0, low = 100.0, close = 102.0),
            ),
            signalDate = signalDate,
            strategy = CsvBacktestEntryStrategy.BREAKOUT_CLOSE_RECLAIM,
            retestWindowDays = 5,
            retestTolerancePct = 1.0,
        )

        assertNull(entry)
    }

    @Test
    fun `breakout close reclaim accepts a confirmation on the thirtieth session`() {
        val signalDate = LocalDate.of(2026, 1, 20)
        val postSignalCandles = (1..30).map { session ->
            candle(
                date = signalDate.plusDays(session.toLong()),
                open = 100.0,
                high = if (session == 30) 103.0 else 101.0,
                low = 98.0,
                close = if (session == 30) 102.0 else 100.0,
            )
        }
        val entryCandle = candle(
            date = signalDate.plusDays(31),
            open = 103.0,
            high = 104.0,
            low = 101.0,
            close = 103.0,
        )

        val entry = CsvBacktestEntryEvaluator.findEntry(
            candles = listOf(
                candle(date = signalDate.minusDays(1), high = 100.0, low = 95.0, close = 99.0),
                candle(date = signalDate, high = 105.0, low = 99.0, close = 101.0),
            ) + postSignalCandles + entryCandle,
            signalDate = signalDate,
            strategy = CsvBacktestEntryStrategy.BREAKOUT_CLOSE_RECLAIM,
            retestWindowDays = 5,
            retestTolerancePct = 1.0,
        )

        assertNotNull(entry)
        assertEquals(entryCandle.candleDate, entry?.candle?.candleDate)
        assertEquals(entryCandle.open, entry?.price)
    }

    @Test
    fun `breakout close reclaim rejects a close above the breakout close after thirty sessions`() {
        val signalDate = LocalDate.of(2026, 1, 20)
        val postSignalCandles = (1..31).map { session ->
            candle(
                date = signalDate.plusDays(session.toLong()),
                high = 103.0,
                low = 98.0,
                close = if (session == 31) 102.0 else 100.0,
            )
        }

        val entry = CsvBacktestEntryEvaluator.findEntry(
            candles = listOf(
                candle(date = signalDate.minusDays(1), high = 100.0, low = 95.0, close = 99.0),
                candle(date = signalDate, high = 105.0, low = 99.0, close = 101.0),
            ) + postSignalCandles,
            signalDate = signalDate,
            strategy = CsvBacktestEntryStrategy.BREAKOUT_CLOSE_RECLAIM,
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

    @Test
    fun `two green candles rejects an overextended confirmation candle`() {
        val signalDate = LocalDate.of(2026, 1, 20)
        val entry = CsvBacktestEntryEvaluator.findEntry(
            candles = listOf(
                candle(date = signalDate.minusDays(1), high = 100.0, low = 95.0, close = 100.0),
                candle(date = signalDate, high = 105.0, low = 99.0, close = 101.0),
                candle(date = signalDate.plusDays(1), high = 110.0, low = 100.0, close = 108.0),
                candle(date = signalDate.plusDays(2), open = 107.0, high = 109.0, low = 101.0, close = 104.0),
                candle(date = signalDate.plusDays(3), open = 105.0, high = 107.0, low = 103.0, close = 106.0),
                candle(date = signalDate.plusDays(4), open = 106.0, high = 108.0, low = 104.0, close = 107.0),
            ),
            signalDate = signalDate,
            strategy = CsvBacktestEntryStrategy.TWO_GREEN_CANDLES,
            retestWindowDays = 5,
            retestTolerancePct = 1.0,
            maxCloseToCloseGainPct = 6.0,
        )

        assertNull(entry)
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
