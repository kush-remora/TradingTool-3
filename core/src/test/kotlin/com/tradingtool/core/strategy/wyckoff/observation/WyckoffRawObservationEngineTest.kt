package com.tradingtool.core.strategy.wyckoff.observation

import com.tradingtool.core.candle.DailyCandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class WyckoffRawObservationEngineTest {

    @Test
    fun `builds raw price, atr, and volume observations without a verdict`() {
        val candles = (0 until 35).map { offset ->
            val close = 100.0 + offset
            DailyCandle(
                instrumentToken = 1L,
                symbol = "TEST",
                candleDate = LocalDate.of(2026, 1, 1).plusDays(offset.toLong()),
                open = close - 0.5,
                high = close + 1.0,
                low = close - 1.0,
                close = close,
                volume = 1_000L + offset,
            )
        }

        val stock = WyckoffRawObservationEngine().buildStock(
            symbol = "TEST",
            companyName = "Test Company",
            watchlists = listOf("TEST_LIST"),
            candles = candles,
            asOfDate = LocalDate.of(2026, 2, 4),
            lookbackCalendarDays = 30,
        )

        assertNotNull(stock)
        assertEquals(31, stock?.observations?.size)
        assertEquals(28.85, stock?.periodReturnPct)

        val latest = stock?.observations?.last()
        assertNotNull(latest)
        assertEquals(2.0, latest?.atr14)
        assertEquals(1.49, latest?.rangePct)
        assertEquals(1.0, latest?.movementVsAtr)
        assertNotNull(latest?.volumeVs20dAverage)
        assertNotNull(latest?.priceChange20dPct)
    }

    @Test
    fun `marks candidate periods and explains a possible phase d launch`() {
        val candles = buildList {
            repeat(25) { offset ->
                val close = 150.0 - (offset * 2.0)
                add(candle(offset, close, volume = 1_000L))
            }
            repeat(25) { offset ->
                val close = 100.0 + (offset % 3)
                add(candle(25 + offset, close, volume = 1_000L))
            }
            add(candle(50, 120.0, high = 121.0, low = 99.0, volume = 4_000L))
            repeat(10) { offset ->
                add(candle(51 + offset, 121.0 + offset, volume = 1_200L))
            }
        }

        val stock = WyckoffRawObservationEngine().buildStock(
            symbol = "TEST",
            companyName = "Test Company",
            watchlists = listOf("TEST_LIST"),
            candles = candles,
            asOfDate = candles.last().candleDate,
            lookbackCalendarDays = 90,
        )

        assertNotNull(stock)
        val phases = stock?.observations?.map { row -> row.phase }.orEmpty()
        assertTrue(WyckoffRawObservationPhase.PRIOR_DECLINE in phases)
        assertTrue(WyckoffRawObservationPhase.CANDIDATE_ACCUMULATION in phases)
        assertTrue(WyckoffRawObservationPhase.POSSIBLE_PHASE_D in phases)
        assertTrue(stock?.periods?.any { period -> period.phase == WyckoffRawObservationPhase.CANDIDATE_ACCUMULATION } == true)
        assertTrue(stock?.observations?.any { row -> row.annotation?.contains("possible Phase D launch") == true } == true)
    }

    private fun candle(
        offset: Int,
        close: Double,
        high: Double = close + 1.0,
        low: Double = close - 1.0,
        volume: Long,
    ): DailyCandle = DailyCandle(
        instrumentToken = 1L,
        symbol = "TEST",
        candleDate = LocalDate.of(2026, 1, 1).plusDays(offset.toLong()),
        open = close,
        high = high,
        low = low,
        close = close,
        volume = volume,
    )
}
