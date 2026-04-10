package com.tradingtool.core.watchlist

import com.fasterxml.jackson.databind.ObjectMapper
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.config.IndicatorConfig
import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.database.RedisHandler
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.model.stock.Stock
import com.tradingtool.core.model.watchlist.ComputedIndicators
import com.tradingtool.core.technical.toTa4jSeries
import com.tradingtool.core.technical.toTa4jSeriesFromKite
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date

/**
 * Orchestrates the two-tier indicator cache: Redis (L1) → Daily Candles (L2).
 *
 * Cache architecture:
 *   L0 (Caffeine, 10 s)  — live market quotes   — managed by [LiveMarketService]
 *   L1 (Redis, 24–48 h)  — OHLCV + indicators   — managed here
 *   L2 (PostgreSQL)      — daily candles        — source of truth for compute
 *   L3 (Kite API)        — on-demand fetch if L2 is missing or stale
 */
class IndicatorService(
    private val candleHandler: CandleJdbiHandler,
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
    private val indicatorWarmupYears: Long = 2 // 2 years is enough for SMA200 + RSI bounds
    private val computedIndicatorsListSerializer = ListSerializer(serializer<ComputedIndicators>())

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns computed indicators for all stocks under [tag], or ALL stocks if tag is null.
     * L1: Redis → L2: Postgres (daily_candles) → L3: Kite (fetch if missing).
     */
    suspend fun getIndicatorsForTag(tag: String?): List<ComputedIndicators> {
        val redisKey = if (tag == null) "watchlist:indicators:ALL" else config.tagIndicatorsKey(tag)

        val cached = redis.get(redisKey)
        if (cached != null) {
            val cachedIndicators = deserializeOrLog<List<ComputedIndicators>>(cached, "redis:$redisKey") {
                json.decodeFromString(computedIndicatorsListSerializer, it)
            }
            if (cachedIndicators != null && !isStale(cachedIndicators)) {
                return cachedIndicators
            }

            log.info("Redis indicator cache for key={} is stale or unreadable. Refreshing.", redisKey)
            redis.delete(redisKey)
        }

        val stocks = if (tag == null) stockHandler.read { it.listAll() } else stockHandler.read { it.listByTagName(tag) }
        val indicators = loadAndComputeIndicators(stocks)
        
        if (indicators.isNotEmpty()) {
            redis.set(redisKey, json.encodeToString(computedIndicatorsListSerializer, indicators), config.indicatorsTtlSeconds)
        }
        
        return indicators
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
        val stock = stockHandler.read { it.getByInstrumentToken(instrumentToken) } ?: return null
        syncStockCandles(kiteClient, stock)
        
        return redis.get(config.ohlcvKey(instrumentToken))
    }

    /** Refreshes indicators for all stocks. Ensures daily_candles are fresh. */
    suspend fun refreshAll(kiteClient: KiteConnectClient, onlyNeedsRefresh: Boolean = false) {
        val stocks = stockHandler.read { it.listAll() }
        log.info("Starting indicator sync for ${stocks.size} stocks")

        syncStocksSequentially(kiteClient, stocks)
        
        val results = loadAndComputeIndicators(stocks)
        pushAllIndicatorsToRedis(results)
        pushTagIndicatorsToRedis(stocks, results)

        log.info("Indicator sync complete: ${results.size}/${stocks.size} stocks succeeded")
    }

    /** Refreshes indicators for all stocks under [tag]. */
    suspend fun refreshTag(kiteClient: KiteConnectClient, tag: String) {
        val stocks = stockHandler.read { it.listByTagName(tag) }
        log.info("Starting indicator sync for tag=$tag (${stocks.size} stocks)")

        syncStocksSequentially(kiteClient, stocks)
        
        val results = loadAndComputeIndicators(stocks)
        pushTagIndicatorsToRedis(stocks, results)

        log.info("Indicator sync complete for tag=$tag: ${results.size}/${stocks.size} stocks succeeded")
    }

    /** Refreshes indicators for a single stock by instrument token. */
    suspend fun refreshStock(kiteClient: KiteConnectClient, instrumentToken: Long) {
        val stock = stockHandler.read { it.getByInstrumentToken(instrumentToken) }
            ?: throw IllegalArgumentException("Stock with instrument token $instrumentToken not found")

        log.info("Starting indicator sync for ${stock.symbol} (token=$instrumentToken)")

        syncStockCandles(kiteClient, stock)
        
        val results = loadAndComputeIndicators(listOf(stock))
        pushTagIndicatorsToRedis(listOf(stock), results)

        log.info("Indicator sync complete for ${stock.symbol}")
    }

    // ── Private pipeline ──────────────────────────────────────────────────────

    private suspend fun loadAndComputeIndicators(stocks: List<Stock>): List<ComputedIndicators> = coroutineScope {
        val today = LocalDate.now(ist)
        val from = today.minusYears(indicatorWarmupYears)
        
        stocks.map { stock ->
            async {
                var candles = candleHandler.read { it.getDailyCandles(stock.instrumentToken, from, today) }
                
                // If missing or potentially stale (last candle not today/yesterday depending on time)
                if (candles.isEmpty() || isCandleDataStale(candles)) {
                    log.info("Candles for ${stock.symbol} are missing or stale. Fetching from Kite.")
                    syncStockCandles(kiteClient, stock)
                    candles = candleHandler.read { it.getDailyCandles(stock.instrumentToken, from, today) }
                }

                if (candles.isEmpty()) return@async null

                val series = candles.toTa4jSeries(stock.symbol)
                Ta4jIndicatorCalculator.calculate(series).copy(instrumentToken = stock.instrumentToken)
            }
        }.awaitAll().filterNotNull()
    }

    private fun isCandleDataStale(candles: List<DailyCandle>): Boolean {
        if (candles.isEmpty()) return true
        val lastDate = candles.maxOf { it.candleDate }
        val today = LocalDate.now(ist)
        
        // If it's a weekend or before market open today, yesterday's/Friday's data might be fine.
        // Simplification: if last candle is before yesterday, it's probably stale.
        return lastDate.isBefore(today.minusDays(1))
    }

    private suspend fun syncStocksSequentially(
        kiteClient: KiteConnectClient,
        stocks: List<Stock>,
    ) {
        for (stock in stocks) {
            delay(config.kiteRateLimitDelayMs)
            try {
                syncStockCandles(kiteClient, stock)
                stockHandler.write { it.setNeedsRefresh(stock.instrumentToken, false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Failed to sync ${stock.symbol}: ${e.message}")
            }
        }
    }

    private suspend fun syncStockCandles(
        kiteClient: KiteConnectClient,
        stock: Stock,
    ) {
        log.info("Fetching history for ${stock.symbol} (token=${stock.instrumentToken})")

        val (fromDate, toDate) = buildDateRange()

        // Kite SDK uses a blocking HTTP client — dispatch on IO
        val history = withContext(Dispatchers.IO) {
            kiteClient.client()
                .getHistoricalData(fromDate, toDate, stock.instrumentToken.toString(), "day", false, false)
        }

        // Cache raw OHLCV in Redis
        redis.set(config.ohlcvKey(stock.instrumentToken), mapper.writeValueAsString(history.dataArrayList), config.ohlcvTtlSeconds)

        val candles = history.dataArrayList.mapNotNull { bar ->
            if (bar == null) return@mapNotNull null
            try {
                val hk = bar as com.zerodhatech.models.HistoricalData
                val date = LocalDateTime.parse(hk.timeStamp.substring(0, 19)).toLocalDate()
                DailyCandle(
                    instrumentToken = stock.instrumentToken,
                    symbol = stock.symbol,
                    candleDate = date,
                    open = hk.open,
                    high = hk.high,
                    low = hk.low,
                    close = hk.close,
                    volume = hk.volume.toLong(),
                )
            } catch (e: Exception) {
                null
            }
        }

        if (candles.isNotEmpty()) {
            candleHandler.write { it.upsertDailyCandles(candles) }
        }
    }

    private suspend fun pushTagIndicatorsToRedis(
        stocks: List<Stock>,
        computedResults: List<ComputedIndicators>,
    ) {
        val resultsMap = computedResults.associateBy { it.instrumentToken }
        val allTags = stocks.flatMap { s -> s.tags.map { it.name } }.distinct()

        coroutineScope {
            allTags.map { tag ->
                async {
                    val tagIndicators = stocks
                        .filter { s -> s.tags.any { it.name == tag } }
                        .mapNotNull { resultsMap[it.instrumentToken] }

                    if (tagIndicators.isNotEmpty()) {
                        redis.set(
                            config.tagIndicatorsKey(tag),
                            json.encodeToString(computedIndicatorsListSerializer, tagIndicators),
                            config.indicatorsTtlSeconds,
                        )
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun pushAllIndicatorsToRedis(computedResults: List<ComputedIndicators>) {
        if (computedResults.isEmpty()) return
        redis.set(
            "watchlist:indicators:ALL",
            json.encodeToString(computedIndicatorsListSerializer, computedResults),
            config.indicatorsTtlSeconds,
        )
    }

    suspend fun loadIndicatorsForTokens(tokens: List<Long>): List<ComputedIndicators> {
        val stocks = coroutineScope {
            tokens.map { token ->
                async { stockHandler.read { it.getByInstrumentToken(token) } }
            }.awaitAll().filterNotNull()
        }
        return loadAndComputeIndicators(stocks)
    }

    private suspend fun loadTagIndicatorsFromDb(tag: String): List<ComputedIndicators> {
        val stocks = stockHandler.read { it.listByTagName(tag) }
        return loadAndComputeIndicators(stocks)
    }

    private suspend fun loadAllIndicatorsFromDb(): List<ComputedIndicators> {
        val stocks = stockHandler.read { it.listAll() }
        return loadAndComputeIndicators(stocks)
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
}
