package com.tradingtool.core.kite

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.time.ZonedDateTime

class NseMarketClockTest {

    @Test
    fun `returns true during weekday market hours`() {
        val now = ZonedDateTime.of(2026, 6, 24, 10, 30, 0, 0, NseMarketClock.zone)

        assertTrue(NseMarketClock.isMarketOpen(now))
    }

    @Test
    fun `returns false before market open`() {
        val now = ZonedDateTime.of(2026, 6, 24, 9, 13, 59, 0, NseMarketClock.zone)

        assertFalse(NseMarketClock.isMarketOpen(now))
    }

    @Test
    fun `returns false after market close`() {
        val now = ZonedDateTime.of(2026, 6, 24, 15, 31, 0, 0, NseMarketClock.zone)

        assertFalse(NseMarketClock.isMarketOpen(now))
    }

    @Test
    fun `returns false on weekend`() {
        val now = ZonedDateTime.of(2026, 6, 27, 11, 0, 0, 0, NseMarketClock.zone)

        assertFalse(NseMarketClock.isMarketOpen(now))
    }
}
