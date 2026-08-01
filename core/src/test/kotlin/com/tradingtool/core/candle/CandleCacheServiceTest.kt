package com.tradingtool.core.candle

import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class CandleCacheServiceTest {
    @Test
    fun `daily cache miss fetches Kite source and caches for three hours`() = runBlocking {
        val from = LocalDate.parse("2026-07-01")
        val to = LocalDate.parse("2026-07-02")
        val expected = listOf(dailyCandle(to))
        val cache = InMemoryKeyValueCache()
        val source = FakeCandleSource(dailyCandles = expected)
        val service = testCandleCacheService(cache, source)

        assertEquals(expected, service.getDailyCandles(123L, " infy ", from, to))
        assertEquals(expected, service.getDailyCandles(123L, "INFY", from, to))
        assertEquals(1, source.dailyRequests)
        assertEquals(CandleCacheService.CANDLE_CACHE_TTL_SECONDS, cache.lastTtlSeconds)
        assertEquals(10_800L, cache.lastTtlSeconds)
    }

    @Test
    fun `intraday cache uses the requested range and three hour ttl`() = runBlocking {
        val from = LocalDateTime.parse("2026-07-01T09:15:00")
        val to = LocalDateTime.parse("2026-07-01T15:30:00")
        val expected = listOf(intradayCandle(from))
        val cache = InMemoryKeyValueCache()
        val source = FakeCandleSource(intradayCandles = expected)
        val service = testCandleCacheService(cache, source)

        assertEquals(expected, service.getIntradayCandles(123L, "INFY", "15minute", from, to))
        assertEquals(expected, service.getIntradayCandles(123L, "INFY", "15minute", from, to))
        assertEquals(1, source.intradayRequests)
        assertEquals(10_800L, cache.lastTtlSeconds)
    }

    private fun dailyCandle(date: LocalDate): DailyCandle = DailyCandle(
        instrumentToken = 123L,
        symbol = "INFY",
        candleDate = date,
        open = 100.0,
        high = 110.0,
        low = 95.0,
        close = 105.0,
        volume = 1_000L,
    )

    private fun intradayCandle(timestamp: LocalDateTime): IntradayCandle = IntradayCandle(
        instrumentToken = 123L,
        symbol = "INFY",
        interval = "15minute",
        candleTimestamp = timestamp,
        open = 100.0,
        high = 102.0,
        low = 99.0,
        close = 101.0,
        volume = 100L,
    )
}
