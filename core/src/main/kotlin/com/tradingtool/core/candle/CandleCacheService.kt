package com.tradingtool.core.candle

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.tradingtool.core.database.KeyValueCache
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime

/** Redis cache-aside access for Kite candle data. */
class CandleCacheService(
    private val cache: KeyValueCache,
    private val objectMapper: ObjectMapper,
    private val candleSource: CandleSource,
) {
    private val log = LoggerFactory.getLogger(CandleCacheService::class.java)

    suspend fun getDailyCandles(
        token: Long?,
        symbol: String,
        from: LocalDate,
        to: LocalDate,
    ): List<DailyCandle> {
        val normalizedSymbol = symbol.trim().uppercase()
        val key = "$CACHE_KEY_PREFIX:$normalizedSymbol:day:$from:$to"
        readDailyCandles(key, normalizedSymbol)?.let { return it }

        val candles = candleSource.getDailyCandles(token, normalizedSymbol, from, to)
        writeCandles(key, normalizedSymbol, candles)
        return candles
    }

    suspend fun getDailyCandles(
        symbol: String,
        from: LocalDate,
        to: LocalDate,
    ): List<DailyCandle> = getDailyCandles(token = null, symbol = symbol, from = from, to = to)

    suspend fun getIntradayCandles(
        token: Long?,
        symbol: String,
        interval: String,
        from: LocalDateTime,
        to: LocalDateTime,
    ): List<IntradayCandle> {
        val normalizedSymbol = symbol.trim().uppercase()
        val key = "$CACHE_KEY_PREFIX:$normalizedSymbol:$interval:$from:$to"
        readIntradayCandles(key, normalizedSymbol, interval)?.let { return it }

        val candles = candleSource.getIntradayCandles(token, normalizedSymbol, interval, from, to)
        writeCandles(key, "$normalizedSymbol/$interval", candles)
        return candles
    }

    private suspend fun readDailyCandles(key: String, symbol: String): List<DailyCandle>? =
        readCandles(key, symbol, object : TypeReference<List<DailyCandle>>() {})

    private suspend fun readIntradayCandles(
        key: String,
        symbol: String,
        interval: String,
    ): List<IntradayCandle>? = readCandles(
        key,
        "$symbol/$interval",
        object : TypeReference<List<IntradayCandle>>() {},
    )

    private suspend fun <T> readCandles(key: String, label: String, type: TypeReference<List<T>>): List<T>? {
        return try {
            val json = cache.get(key) ?: return null
            objectMapper.readValue(json, type).also { candles ->
                log.debug("Candle cache hit for {} ({} candles)", label, candles.size)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.warn("Candle cache read failed for {}: {}", label, error.message)
            null
        }
    }

    private suspend fun writeCandles(key: String, label: String, candles: List<*>) {
        try {
            cache.set(key, objectMapper.writeValueAsString(candles), CANDLE_CACHE_TTL_SECONDS)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.warn("Candle cache write failed for {}: {}", label, error.message)
        }
    }

    companion object {
        const val CANDLE_CACHE_TTL_SECONDS: Long = 3 * 60 * 60L
        private const val CACHE_KEY_PREFIX = "candles:v2"
    }
}
