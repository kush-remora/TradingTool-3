package com.tradingtool.core.screener

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.candle.IntradayCandle
import com.tradingtool.core.candle.CandleSource
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.InstrumentTokenResolverService
import com.tradingtool.core.kite.KiteConnectClient
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException
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

        val history = fetchDailyHistory(
            token = resolvedToken,
            symbol = normalizedSymbol,
            fromDate = fromDate,
            toDate = toDate,
        )

        return history
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

    private suspend fun fetchDailyHistory(
        token: Long,
        symbol: String,
        fromDate: LocalDate,
        toDate: LocalDate,
    ): List<com.zerodhatech.models.HistoricalData> {
        val now = LocalDateTime.now(ist)
        require(!toDate.isAfter(now.toLocalDate())) { "toDate cannot be in the future." }

        return buildList {
            for (range in dailyCandleRequestRanges(fromDate, toDate)) {
                try {
                    addAll(
                        callKite {
                            kiteClient.client().getHistoricalData(
                                range.from.toJavaDate(),
                                range.to.toKiteEndDate(now),
                                token.toString(),
                                "day",
                                false,
                                false,
                            ).dataArrayList
                        },
                    )
                } catch (error: KiteException) {
                    throw IllegalArgumentException(
                        "Kite rejected daily candles for $symbol (token=$token, " +
                            "from=${range.from}, to=${range.to}): ${error.message ?: "input rejected"}",
                        error,
                    )
                }
            }
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

    private fun LocalDate.toKiteEndDate(now: LocalDateTime): Date {
        val end = dailyCandleRequestEnd(this, now)
        return Date.from(end.atZone(ist).toInstant())
    }

    private companion object {
        const val KITE_REQUEST_DELAY_MS = 350L
    }
}

internal data class DailyCandleRequestRange(
    val from: LocalDate,
    val to: LocalDate,
)

internal fun dailyCandleRequestRanges(fromDate: LocalDate, toDate: LocalDate): List<DailyCandleRequestRange> {
    require(!fromDate.isAfter(toDate)) { "fromDate must be on or before toDate." }

    val ranges = mutableListOf<DailyCandleRequestRange>()
    var rangeFrom = fromDate
    while (!rangeFrom.isAfter(toDate)) {
        val rangeTo = minOf(rangeFrom.plusDays(MAX_DAILY_REQUEST_DAYS - 1), toDate)
        ranges += DailyCandleRequestRange(rangeFrom, rangeTo)
        rangeFrom = rangeTo.plusDays(1)
    }
    return ranges
}

internal fun dailyCandleRequestEnd(toDate: LocalDate, now: LocalDateTime): LocalDateTime =
    if (toDate.isBefore(now.toLocalDate())) toDate.plusDays(1).atStartOfDay() else now

internal const val MAX_DAILY_REQUEST_DAYS: Long = 1_800L
