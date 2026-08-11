package com.tradingtool.core.strategy.rsioversold

import com.tradingtool.core.candle.DailyCandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RsiOversoldScannerEngineTest {
    private val engine = RsiOversoldScannerEngine()

    @Test
    fun `finds the most recent signal in the twenty day window against the prior two hundred days`() {
        val dates = tradingDates(220)
        val candles = dates.mapIndexed { index, date ->
            candle(date = date, close = 100.0 + index, volume = 1_000L + index)
        }
        val rsiValues = List(220) { index ->
            when (index) {
                10 -> 20.0
                205, 210 -> 19.0
                else -> 40.0
            }
        }

        val row = engine.evaluate(
            symbol = "TEST",
            companyName = "Test Company",
            watchlistKeys = listOf("NIFTY 50", "Growth"),
            candles = candles,
            rsiValues = rsiValues,
            asOfDate = dates.last(),
        )

        requireNotNull(row)
        assertEquals(dates[210].toString(), row.signalDate)
        assertEquals(19.0, row.signalRsi)
        assertEquals(310.0, row.signalPrice)
        assertEquals(1_210L, row.signalVolume)
        assertEquals(20.0, row.baselineRsiLow)
        assertEquals(dates.last().toString(), row.latestDate)
        assertEquals(319.0, row.latestClose)
        assertEquals(1_219L, row.latestVolume)
        assertEquals(listOf("Growth", "NIFTY 50"), row.watchlistKeys)
    }

    @Test
    fun `does not use the signal window when calculating the two hundred day minimum`() {
        val dates = tradingDates(220)
        val rsiValues = List(220) { index ->
            when (index) {
                10 -> 20.0
                210 -> 19.0
                else -> 40.0
            }
        }

        val row = engine.evaluate(
            symbol = "TEST",
            companyName = null,
            watchlistKeys = emptyList(),
            candles = dates.map { date -> candle(date, close = 100.0, volume = 1_000L) },
            rsiValues = rsiValues,
            asOfDate = dates.last(),
        )

        requireNotNull(row)
        assertEquals(20.0, row.baselineRsiLow)
        assertEquals(19.0, row.signalRsi)
    }

    @Test
    fun `returns no row when fewer than two hundred twenty sessions are available`() {
        val dates = tradingDates(219)
        val row = engine.evaluate(
            symbol = "TEST",
            companyName = null,
            watchlistKeys = emptyList(),
            candles = dates.map { date -> candle(date, close = 100.0, volume = 1_000L) },
            rsiValues = dates.map { 30.0 },
            asOfDate = dates.last(),
        )

        assertNull(row)
    }

    private fun tradingDates(count: Int): List<LocalDate> = (0 until count).map { offset ->
        LocalDate.of(2026, 1, 1).plusDays(offset.toLong())
    }

    private fun candle(date: LocalDate, close: Double, volume: Long): DailyCandle = DailyCandle(
        instrumentToken = 1,
        symbol = "TEST",
        candleDate = date,
        open = close,
        high = close,
        low = close,
        close = close,
        volume = volume,
    )
}
