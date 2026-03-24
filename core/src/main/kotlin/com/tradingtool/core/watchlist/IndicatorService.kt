package com.tradingtool.core.watchlist

import com.fasterxml.jackson.databind.ObjectMapper
import com.tradingtool.core.config.IndicatorConfig
import com.tradingtool.core.database.RedisHandler
import com.tradingtool.core.database.StockIndicatorsJdbiHandler
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.model.stock.Stock
import com.tradingtool.core.model.watchlist.ComputedIndicators
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBarSeriesBuilder
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.logging.Logger

/**
 * Orchestrates the two-tier indicator cache: Redis (L1) → PostgreSQL (L2).
 *
 * Designed to be plug-in-play across dashboards and strategies:
 * - Inject a custom [IndicatorConfig] to change TTLs, rate limits, or Redis key namespaces.
 * - [Ta4jIndicatorCalculator] is stateless and reusable outside this service.
 * - All methods are [suspend] and safe to call from any coroutine scope.
 *
 * Cache architecture:
 *   L0 (Caffeine, 10 s)  — live market quotes   — managed by [LiveMarketService]
 *   L1 (Redis, 24–48 h)  — OHLCV + indicators   — managed here
 *   L2 (PostgreSQL)      — indicator snapshots  — source of truth, heals L1 on miss
 */
class IndicatorService(
    private val stockIndicatorsHandler: StockIndicatorsJdbiHandler,
    private val stockHandler: StockJdbiHandler,
    private val redis: RedisHandler,
    private val config: IndicatorConfig = IndicatorConfig.DEFAULT,
) {
    private val log = Logger.getLogger(IndicatorService::class.java.name)

    // kotlinx.Json for our own @Serializable models (ComputedIndicators)
    private val json = Json { ignoreUnknownKeys = true }

    // Jackson ObjectMapper for Kite SDK POJOs (HistoricalData — not @Serializable)
    private val mapper = ObjectMapper()

    private val ist = ZoneId.of("Asia/Kolkata")

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns computed indicators for all stocks under [tag].
     * L1: Redis → L2: Postgres (heals Redis on miss).
     */
    suspend fun getIndicatorsForTag(tag: String): List<ComputedIndicators> {
        val redisKey = config.tagIndicatorsKey(tag)

        val cached = redis.get(redisKey)
        if (cached != null) {
            return deserializeOrLog(cached, "tag=$tag") {
                json.decodeFromString<List<ComputedIndicators>>(it)
            } ?: emptyList()
        }

        val indicators = loadTagIndicatorsFromDb(tag)
        if (indicators.isNotEmpty()) {
            redis.set(redisKey, json.encodeToString(indicators), config.indicatorsTtlSeconds)
        }
        return indicators
    }

    /**
     * Returns raw OHLCV JSON for a stock from Redis (L1 only).
     * On cache miss, flags the stock for the next cron run and returns null.
     * The caller should handle null gracefully (show stale/empty state to user).
     */
    suspend fun getHistoricalOhlcv(instrumentToken: Long): String? {
        val cached = redis.get(config.ohlcvKey(instrumentToken))
        if (cached != null) return cached

        // No DB fallback for OHLCV — schedule cron to re-fetch from Kite.
        stockHandler.write { it.setNeedsRefresh(instrumentToken, true) }
        return null
    }

    /**
     * Daily cron entry point: fetches 1-year Kite history for all stocks,
     * computes indicators, persists to Postgres, and rebuilds Redis caches.
     *
     * Rate-limited to [IndicatorConfig.kiteRateLimitDelayMs] between API calls.
     * Must be called from a coroutine (use [kotlinx.coroutines.runBlocking] in cron jobs).
     */
    suspend fun refreshAll(kiteClient: KiteConnectClient, onlyNeedsRefresh: Boolean = false) {
        val stocks = stockHandler.read { it.listAll() }
        log.info("Starting indicator sync for ${stocks.size} stocks (onlyNeedsRefresh=$onlyNeedsRefresh)")

        val results = syncStocksSequentially(kiteClient, stocks)
        pushTagIndicatorsToRedis(stocks, results)

        log.info("Indicator sync complete: ${results.size}/${stocks.size} stocks succeeded")
    }

    /** Refreshes indicators for all stocks under [tag] only. */
    suspend fun refreshTag(kiteClient: KiteConnectClient, tag: String) {
        val stocks = stockHandler.read { it.listByTagName(tag) }
        log.info("Starting indicator sync for tag=$tag (${stocks.size} stocks)")

        val results = syncStocksSequentially(kiteClient, stocks)
        pushTagIndicatorsToRedis(stocks, results)

        log.info("Indicator sync complete for tag=$tag: ${results.size}/${stocks.size} stocks succeeded")
    }

    // ── Private pipeline ──────────────────────────────────────────────────────

    private suspend fun syncStocksSequentially(
        kiteClient: KiteConnectClient,
        stocks: List<Stock>,
    ): Map<Long, ComputedIndicators> {
        val results = mutableMapOf<Long, ComputedIndicators>()
        val dateRange = buildDateRange()

        for (stock in stocks) {
            // Non-blocking rate limit — yields the thread instead of blocking it.
            delay(config.kiteRateLimitDelayMs)
            try {
                val indicators = fetchAndProcessStock(kiteClient, stock, dateRange)
                if (indicators != null) {
                    results[stock.instrumentToken] = indicators
                    stockHandler.write { it.setNeedsRefresh(stock.instrumentToken, false) }
                }
            } catch (e: CancellationException) {
                throw e  // never swallow coroutine cancellation
            } catch (e: Exception) {
                log.warning("Failed to sync ${stock.symbol}: ${e.message}")
            }
        }

        return results
    }

    private suspend fun fetchAndProcessStock(
        kiteClient: KiteConnectClient,
        stock: Stock,
        dateRange: Pair<Date, Date>,
    ): ComputedIndicators? {
        log.info("Fetching 1yr history for ${stock.symbol} (token=${stock.instrumentToken})")

        val (fromDate, toDate) = dateRange

        // Kite SDK uses a blocking HTTP client — dispatch on IO to avoid pinning the coroutine thread.
        val history = withContext(Dispatchers.IO) {
            kiteClient.client()
                .getHistoricalData(fromDate, toDate, stock.instrumentToken.toString(), "day", false, false)
        }

        // Cache raw OHLCV in Redis — 48h TTL bridges Fri close → Mon open.
        redis.set(config.ohlcvKey(stock.instrumentToken), mapper.writeValueAsString(history.dataArrayList), config.ohlcvTtlSeconds)

        val series = buildBarSeries(history.dataArrayList, stock.symbol)
        if (series.barCount == 0) {
            log.warning("No bars returned for ${stock.symbol} — skipping indicator calculation")
            return null
        }

        val indicators = Ta4jIndicatorCalculator.calculate(series).copy(instrumentToken = stock.instrumentToken)
        stockIndicatorsHandler.write {
            it.upsertIndicators(stock.instrumentToken, json.encodeToString(indicators))
        }
        return indicators
    }

    /**
     * Converts Kite's historical data list into a ta4j [BarSeries].
     *
     * Kite timestamps are always IST (e.g. "2023-01-02T09:15:00+0530").
     * We parse only the local-datetime part (first 19 chars) and attach the IST
     * zone explicitly — this avoids the offset-format ambiguity in DateTimeFormatter
     * (+0530 vs +05:30) and makes the IST assumption visible in the code.
     */
    private fun buildBarSeries(historicalData: List<*>, symbol: String): BarSeries {
        val series = BaseBarSeriesBuilder().withName(symbol).build()

        for (bar in historicalData) {
            if (bar == null) continue
            // Reflection-free access via the Kite SDK's HistoricalData fields.
            val hk = bar as com.zerodhatech.models.HistoricalData
            val localDt: LocalDateTime = LocalDateTime.parse(hk.timeStamp.substring(0, 19))
            val zdt: ZonedDateTime = localDt.atZone(ist)
            series.addBar(zdt, hk.open, hk.high, hk.low, hk.close, hk.volume)
        }

        return series
    }

    private suspend fun pushTagIndicatorsToRedis(
        stocks: List<Stock>,
        computedResults: Map<Long, ComputedIndicators>,
    ) {
        val allTags = stocks.flatMap { s -> s.tags.map { it.name } }.distinct()

        for (tag in allTags) {
            val tagIndicators = stocks
                .filter { s -> s.tags.any { it.name == tag } }
                .mapNotNull { computedResults[it.instrumentToken] }

            if (tagIndicators.isNotEmpty()) {
                redis.set(config.tagIndicatorsKey(tag), json.encodeToString(tagIndicators), config.indicatorsTtlSeconds)
            }
        }
    }

    private suspend fun loadTagIndicatorsFromDb(tag: String): List<ComputedIndicators> {
        val stocks = stockHandler.read { it.listByTagName(tag) }
        return stocks.mapNotNull { stock ->
            val jsonStr = stockIndicatorsHandler.read { it.getIndicatorsJson(stock.instrumentToken) }
                ?: return@mapNotNull null
            deserializeOrLog(jsonStr, "stock:${stock.instrumentToken}") {
                json.decodeFromString<ComputedIndicators>(it).copy(instrumentToken = stock.instrumentToken)
            }
        }
    }

    /** Returns 1-year date range as a [Pair] of [Date] for the Kite SDK (requires java.util.Date). */
    private fun buildDateRange(): Pair<Date, Date> {
        val today = java.time.LocalDate.now(ist)
        val oneYearAgo = today.minusYears(1)
        return Pair(
            Date.from(oneYearAgo.atStartOfDay(ist).toInstant()),
            Date.from(today.atStartOfDay(ist).toInstant()),
        )
    }

    /** Deserializes [raw] JSON, logging a warning on failure instead of swallowing silently. */
    private fun <T> deserializeOrLog(raw: String, context: String, block: (String) -> T): T? =
        try {
            block(raw)
        } catch (e: Exception) {
            log.warning("Deserialization failed for '$context': ${e.message}")
            null
        }
}
