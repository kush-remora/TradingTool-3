package com.tradingtool.core.strategy.rsimomentum

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RsiMomentumServiceDateLogicTest {

    @Test
    fun `sunday expects friday candle`() {
        val sunday = LocalDate.of(2026, 4, 12)
        val friday = LocalDate.of(2026, 4, 10)

        assertFalse(isCandleDateStale(friday, sunday))
    }

    @Test
    fun `monday accepts friday candle`() {
        val monday = LocalDate.of(2026, 4, 13)
        val friday = LocalDate.of(2026, 4, 10)

        assertFalse(isCandleDateStale(friday, monday))
    }

    @Test
    fun `tuesday still accepts friday candle for holiday-like gap`() {
        val tuesday = LocalDate.of(2026, 4, 14)
        val friday = LocalDate.of(2026, 4, 10)

        assertFalse(isCandleDateStale(friday, tuesday))
    }

    @Test
    fun `very old candles are stale`() {
        val sunday = LocalDate.of(2026, 4, 12)
        val previousMonday = LocalDate.of(2026, 4, 6)

        assertTrue(isCandleDateStale(previousMonday, sunday))
    }
}

