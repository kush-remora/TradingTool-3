package com.tradingtool.core.screener

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.candle.IntradayCandle
import com.tradingtool.core.candle.CandleSource
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.InstrumentTokenResolverService
import com.tradingtool.core.kite.KiteConnectClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

/** Fetches candle ranges directly from Kite. Persistence and caching belong to CandleCacheService. */
class CandleDataService(
    private val instrumentCache: InstrumentCache,
    private val tokenResolver: InstrumentTokenResolverService,
    private val kiteClient: KiteConnectClient,
) : CandleSource {
    private val log = LoggerFactory.getLogger(CandleDataService::class.java)
    private val ist = ZoneId.of("Asia/Kolkata")
    private val kiteRequestMutex = Mutex()

    override suspend fun getDailyCandles(
        token: Long?,
        symbol: String,
        fromDate: LocalDate,
        toDate: LocalDate,
    ): List<DailyCandle> {
        require(!fromDate.isAfter(toDate)) { "fromDate must be on or before toDate." }
        val normalizedSymbol = symbol.trim().uppercase()
        val resolvedToken = resolveInstrumentToken(normalizedSymbol, token)
            ?: error("No Kite instrument token available for $normalizedSymbol.")

        val history = callKite {
            kiteClient.client().getHistoricalData(
                fromDate.toJavaDate(),
                toDate.toJavaDate(),
                resolvedToken.toString(),
                "day",
                false,
                false,
            )
        }

        return history.dataArrayList
            .mapNotNull { bar -> parseDailyCandle(bar, resolvedToken, normalizedSymbol) }
            .distinctBy(DailyCandle::candleDate)
            .sortedBy(DailyCandle::candleDate)
    }

    override suspend fun getIntradayCandles(
        token: Long?,
        symbol: String,
        interval: String,
        from: LocalDateTime,
        to: LocalDateTime,
    ): List<IntradayCandle> {
        require(!from.isAfter(to)) { "from must be on or before to." }
        val normalizedSymbol = symbol.trim().uppercase()
        val resolvedToken = resolveInstrumentToken(normalizedSymbol, token)
            ?: error("No Kite instrument token available for $normalizedSymbol.")

        val history = callKite {
            kiteClient.client().getHistoricalData(
                from.atZone(ist).toInstant().let(Date::from),
                to.atZone(ist).toInstant().let(Date::from),
                resolvedToken.toString(),
                interval,
                false,
                false,
            )
        }

        return history.dataArrayList
            .mapNotNull { bar -> parseIntradayCandle(bar, resolvedToken, normalizedSymbol, interval) }
            .distinctBy(IntradayCandle::candleTimestamp)
            .sortedBy(IntradayCandle::candleTimestamp)
    }

    private suspend fun <T> callKite(request: suspend () -> T): T = kiteRequestMutex.withLock {
        try {
            withContext(Dispatchers.IO) { request() }
        } finally {
            delay(KITE_REQUEST_DELAY_MS)
        }
    }

    internal suspend fun resolveInstrumentToken(symbol: String, token: Long?): Long? {
        ensureInstrumentCacheLoaded()
        val currentToken = tokenResolver.resolve(exchange = "NSE", symbol = symbol)
        if (currentToken != null && token != null && currentToken != token) {
            log.warn(
                "Ignoring stale instrument token {} for {}; current Kite token is {}",
                token,
                symbol,
                currentToken,
            )
        }
        return currentToken
    }

    private suspend fun ensureInstrumentCacheLoaded() {
        if (!instrumentCache.isEmpty()) return
        val instruments = callKite { kiteClient.client().getInstruments("NSE") }
        instrumentCache.refresh(instruments)
    }

    private fun parseDailyCandle(bar: Any?, token: Long, symbol: String): DailyCandle? {
        if (bar == null) return null
        return runCatching {
            val historicalData = bar as com.zerodhatech.models.HistoricalData
            DailyCandle(
                instrumentToken = token,
                symbol = symbol,
                candleDate = LocalDateTime.parse(historicalData.timeStamp.substring(0, 19)).toLocalDate(),
                open = historicalData.open,
                high = historicalData.high,
                low = historicalData.low,
                close = historicalData.close,
                volume = historicalData.volume,
            )
        }.onFailure { error ->
            log.warn("Skipping unparseable daily candle for {}: {}", symbol, error.message)
        }.getOrNull()
    }

    private fun parseIntradayCandle(bar: Any?, token: Long, symbol: String, interval: String): IntradayCandle? {
        if (bar == null) return null
        return runCatching {
            val historicalData = bar as com.zerodhatech.models.HistoricalData
            IntradayCandle(
                instrumentToken = token,
                symbol = symbol,
                interval = interval,
                candleTimestamp = LocalDateTime.parse(historicalData.timeStamp.substring(0, 19)),
                open = historicalData.open,
                high = historicalData.high,
                low = historicalData.low,
                close = historicalData.close,
                volume = historicalData.volume,
            )
        }.onFailure { error ->
            log.warn("Skipping unparseable intraday candle for {}/{}: {}", symbol, interval, error.message)
        }.getOrNull()
    }

    private fun LocalDate.toJavaDate(): Date = Date.from(atStartOfDay(ist).toInstant())

    private companion object {
        const val KITE_REQUEST_DELAY_MS = 350L
    }
}
