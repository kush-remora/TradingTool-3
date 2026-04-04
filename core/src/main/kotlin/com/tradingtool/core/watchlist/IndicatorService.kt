package com.tradingtool.core.watchlist

import com.fasterxml.jackson.databind.ObjectMapper
import com.tradingtool.core.config.IndicatorConfig
import com.tradingtool.core.database.RedisHandler
import com.tradingtool.core.database.StockIndicatorsJdbiHandler
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.model.stock.Stock
import com.tradingtool.core.model.watchlist.ComputedIndicators
import com.tradingtool.core.technical.toTa4jSeriesFromKite
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date

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
    private val kiteClient: KiteConnectClient,
    private val config: IndicatorConfig = IndicatorConfig.DEFAULT,
) {
    private val log = LoggerFactory.getLogger(IndicatorService::class.java)

    // kotlinx.Json for our own @Serializable models (ComputedIndicators)
    private val json = Json { ignoreUnknownKeys = true }

    // Jackson ObjectMapper for Kite SDK POJOs (HistoricalData — not @Serializable)
    private val mapper = ObjectMapper()

    private val ist = ZoneId.of("Asia/Kolkata")
    private val indicatorWarmupYears: Long = 5

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns computed indicators for all stocks under [tag], or ALL stocks if tag is null.
     * L1: Redis → L2: Postgres (heals Redis on miss) → L3: Kite (on-demand compute if missing or stale).
     */
    suspend fun getIndicatorsForTag(tag: String?): List<ComputedIndicators> {
        val redisKey = if (tag == null) "watchlist:indicators:ALL" else config.tagIndicatorsKey(tag)

        val cached = redis.get(redisKey)
        if (cached != null) {
            val cachedIndicators = deserializeOrLog(cached, "redis:$redisKey") {
                json.decodeFromString<List<ComputedIndicators>>(it)
            }
            if (cachedIndicators != null && !isStale(cachedIndicators)) {
                return cachedIndicators
            }

            log.info("Redis indicator cache for key={} is stale or unreadable. Refreshing.", redisKey)
            redis.delete(redisKey)
        }

        val indicators = loadIndicatorsFromDbAndCache(tag, redisKey)
        if (indicators.isNotEmpty() && !isStale(indicators)) {
            return indicators
        }

        // No data or stale data — trigger an on-demand refresh.
        log.info("Indicators for tag={} are missing or stale. Triggering on-demand refresh.", tag)
        if (tag == null) {
            refreshAll(kiteClient)
        } else {
            refreshTag(kiteClient, tag)
        }

        return loadIndicatorsFromDbAndCache(tag, redisKey)
    }

    private fun isStale(indicators: List<ComputedIndicators>): Boolean {
        if (indicators.isEmpty()) return true
        // If the latest compute was before today's 9:15 AM (market open), and it's now after 9:15 AM, it's stale.
        val now = ZonedDateTime.now(ist)
        val todayMarketOpen = now.withHour(9).withMinute(15).withSecond(0).withNano(0)
        
        val lastComputed = indicators.maxOf { it.computedAt }
        val lastComputedDt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(lastComputed), ist)

        // If it's after 9:15 AM today, we want data computed after today's 9:15 AM.
        if (now.isAfter(todayMarketOpen)) {
            return lastComputedDt.isBefore(todayMarketOpen)
        }
        
        // If it's before 9:15 AM today, we are fine with yesterday's data (computed after yesterday 9:15 AM).
        val yesterdayMarketOpen = todayMarketOpen.minusDays(1)
        return lastComputedDt.isBefore(yesterdayMarketOpen)
    }

    /**
     * Returns raw OHLCV JSON for a stock from Redis (L1 only).
     * On cache miss, fetches it directly from Kite, caches it, and returns.
     */
    suspend fun getHistoricalOhlcv(instrumentToken: Long): String? {
        val cached = redis.get(config.ohlcvKey(instrumentToken))
        if (cached != null) return cached

        log.info("Cache miss for OHLCV token=$instrumentToken. Fetching from Kite on-demand.")
        refreshStock(kiteClient, instrumentToken)
        
        return redis.get(config.ohlcvKey(instrumentToken))
    }

    /**
     * Daily cron entry point: fetches warmup-aware Kite history for all stocks,
     * computes indicators, persists to Postgres, and rebuilds Redis caches.
     *
     * Rate-limited to [IndicatorConfig.kiteRateLimitDelayMs] between API calls.
     * Must be called from a coroutine (use [kotlinx.coroutines.runBlocking] in cron jobs).
     */
    suspend fun refreshAll(kiteClient: KiteConnectClient, onlyNeedsRefresh: Boolean = false) {
        val stocks = stockHandler.read { it.listAll() }
        log.info("Starting indicator sync for ${stocks.size} stocks (onlyNeedsRefresh=$onlyNeedsRefresh)")

        val results = syncStocksSequentially(kiteClient, stocks)
        pushAllIndicatorsToRedis(results)
        pushTagIndicatorsToRedis(stocks, results)

        log.info("Indicator sync complete: ${results.size}/${stocks.size} stocks succeeded")
        requireSuccessfulSync(stocks.size, results.size, "all stocks")
    }

    /** Refreshes indicators for all stocks under [tag] only. */
    suspend fun refreshTag(kiteClient: KiteConnectClient, tag: String) {
        val stocks = stockHandler.read { it.listByTagName(tag) }
        log.info("Starting indicator sync for tag=$tag (${stocks.size} stocks)")

        val results = syncStocksSequentially(kiteClient, stocks)
        pushTagIndicatorsToRedis(stocks, results)

        log.info("Indicator sync complete for tag=$tag: ${results.size}/${stocks.size} stocks succeeded")
        requireSuccessfulSync(stocks.size, results.size, "tag=$tag")
    }

    /** Refreshes indicators for a single stock by instrument token. */
    suspend fun refreshStock(kiteClient: KiteConnectClient, instrumentToken: Long) {
        val stock = stockHandler.read { it.getByInstrumentToken(instrumentToken) }
            ?: throw IllegalArgumentException("Stock with instrument token $instrumentToken not found")

        log.info("Starting indicator sync for ${stock.symbol} (token=$instrumentToken)")

        val results = syncStocksSequentially(kiteClient, listOf(stock))
        pushTagIndicatorsToRedis(listOf(stock), results)

        log.info("Indicator sync complete for ${stock.symbol}: ${if (results.isNotEmpty()) "succeeded" else "failed"}")
        requireSuccessfulSync(1, results.size, "stock ${stock.symbol} (token=$instrumentToken)")
    }

    // ── Private pipeline ──────────────────────────────────────────────────────

    private suspend fun loadIndicatorsFromDbAndCache(
        tag: String?,
        redisKey: String,
    ): List<ComputedIndicators> {
        val indicators = if (tag == null) loadAllIndicatorsFromDb() else loadTagIndicatorsFromDb(tag)
        if (indicators.isNotEmpty()) {
            redis.set(redisKey, json.encodeToString(indicators), config.indicatorsTtlSeconds)
        }
        return indicators
    }

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
                log.warn("Failed to sync ${stock.symbol}: ${e.message}")
            }
        }

        return results
    }

    private suspend fun fetchAndProcessStock(
        kiteClient: KiteConnectClient,
        stock: Stock,
        dateRange: Pair<Date, Date>,
    ): ComputedIndicators? {
        log.info("Fetching indicator warmup history for ${stock.symbol} (token=${stock.instrumentToken})")

        val (fromDate, toDate) = dateRange

        // Kite SDK uses a blocking HTTP client — dispatch on IO to avoid pinning the coroutine thread.
        val history = withContext(Dispatchers.IO) {
            kiteClient.client()
                .getHistoricalData(fromDate, toDate, stock.instrumentToken.toString(), "day", false, false)
        }

        // Cache raw OHLCV in Redis — 48h TTL bridges Fri close → Mon open.
        redis.set(config.ohlcvKey(stock.instrumentToken), mapper.writeValueAsString(history.dataArrayList), config.ohlcvTtlSeconds)

        val series = history.dataArrayList.toTa4jSeriesFromKite(stock.symbol)
        if (series.barCount == 0) {
            log.warn("No bars returned for ${stock.symbol} — skipping indicator calculation")
            return null
        }

        val indicators = Ta4jIndicatorCalculator.calculate(series).copy(instrumentToken = stock.instrumentToken)
        stockIndicatorsHandler.write {
            it.upsertIndicators(stock.instrumentToken, json.encodeToString(indicators))
        }
        return indicators
    }

    private suspend fun pushTagIndicatorsToRedis(
        stocks: List<Stock>,
        computedResults: Map<Long, ComputedIndicators>,
    ) {
        val allTags = stocks.flatMap { s -> s.tags.map { it.name } }.distinct()

        // Each tag write is independent — push all in parallel.
        coroutineScope {
            allTags.map { tag ->
                async {
                    val tagIndicators = stocks
                        .filter { s -> s.tags.any { it.name == tag } }
                        .mapNotNull { computedResults[it.instrumentToken] }

                    if (tagIndicators.isNotEmpty()) {
                        redis.set(config.tagIndicatorsKey(tag), json.encodeToString(tagIndicators), config.indicatorsTtlSeconds)
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun pushAllIndicatorsToRedis(computedResults: Map<Long, ComputedIndicators>) {
        if (computedResults.isEmpty()) {
            return
        }

        redis.set(
            "watchlist:indicators:ALL",
            json.encodeToString(computedResults.values.toList()),
            config.indicatorsTtlSeconds,
        )
    }

    suspend fun loadIndicatorsForTokens(tokens: List<Long>): List<ComputedIndicators> {
        return coroutineScope {
            tokens.map { token ->
                async {
                    val jsonStr = stockIndicatorsHandler.read { it.getIndicatorsJson(token) }
                        ?: return@async null
                    deserializeOrLog(jsonStr, "stock:$token") {
                        json.decodeFromString<ComputedIndicators>(it).copy(instrumentToken = token)
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun loadTagIndicatorsFromDb(tag: String): List<ComputedIndicators> {
        val stocks = stockHandler.read { it.listByTagName(tag) }
        // Each stock is an independent DB read — fetch all in parallel.
        return coroutineScope {
            stocks.map { stock ->
                async {
                    val jsonStr = stockIndicatorsHandler.read { it.getIndicatorsJson(stock.instrumentToken) }
                        ?: return@async null
                    deserializeOrLog(jsonStr, "stock:${stock.instrumentToken}") {
                        json.decodeFromString<ComputedIndicators>(it).copy(instrumentToken = stock.instrumentToken)
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun loadAllIndicatorsFromDb(): List<ComputedIndicators> {
        val stocks = stockHandler.read { it.listAll() }
        return coroutineScope {
            stocks.map { stock ->
                async {
                    val jsonStr = stockIndicatorsHandler.read { it.getIndicatorsJson(stock.instrumentToken) }
                        ?: return@async null
                    deserializeOrLog(jsonStr, "stock:${stock.instrumentToken}") {
                        json.decodeFromString<ComputedIndicators>(it).copy(instrumentToken = stock.instrumentToken)
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    /** Returns warmup-aware date range as a [Pair] of [Date] for the Kite SDK. */
    private fun buildDateRange(): Pair<Date, Date> {
        val today = java.time.LocalDate.now(ist)
        val warmupStart = today.minusYears(indicatorWarmupYears)
        return Pair(
            Date.from(warmupStart.atStartOfDay(ist).toInstant()),
            Date.from(today.atStartOfDay(ist).toInstant()),
        )
    }

    /** Deserializes [raw] JSON, logging a warning on failure instead of swallowing silently. */
    private fun <T> deserializeOrLog(raw: String, context: String, block: (String) -> T): T? =
        try {
            block(raw)
        } catch (e: Exception) {
            log.warn("Deserialization failed for '$context': ${e.message}")
            null
        }

    private fun requireSuccessfulSync(totalStocks: Int, successfulStocks: Int, context: String) {
        if (totalStocks == 0) {
            return
        }

        check(successfulStocks > 0) {
            "Indicator sync failed for $context: 0/$totalStocks stocks succeeded."
        }
    }
}
