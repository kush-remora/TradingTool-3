package com.tradingtool.core.candle

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.tradingtool.core.database.KeyValueCache
import java.time.LocalDate
import java.time.LocalDateTime

class InMemoryKeyValueCache : KeyValueCache {
    val values = mutableMapOf<String, String>()
    var lastTtlSeconds: Long? = null

    override suspend fun get(key: String): String? = values[key]

    override suspend fun set(key: String, value: String, ttlSeconds: Long) {
        values[key] = value
        lastTtlSeconds = ttlSeconds
    }
}

class FakeCandleSource(
    var dailyCandles: List<DailyCandle> = emptyList(),
    var intradayCandles: List<IntradayCandle> = emptyList(),
) : CandleSource {
    var dailyRequests = 0
    var intradayRequests = 0

    override suspend fun getDailyCandles(
        token: Long?,
        symbol: String,
        fromDate: LocalDate,
        toDate: LocalDate,
    ): List<DailyCandle> {
        dailyRequests++
        return dailyCandles
    }

    override suspend fun getIntradayCandles(
        token: Long?,
        symbol: String,
        interval: String,
        from: LocalDateTime,
        to: LocalDateTime,
    ): List<IntradayCandle> {
        intradayRequests++
        return intradayCandles
    }
}

fun testCandleCacheService(
    cache: InMemoryKeyValueCache = InMemoryKeyValueCache(),
    source: FakeCandleSource = FakeCandleSource(),
): CandleCacheService {
    val objectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())
    return CandleCacheService(cache, objectMapper, source)
}
