package com.tradingtool.core.config

/**
 * Configuration for the indicator pipeline: cache TTLs, rate limits, and Redis key prefixes.
 *
 * Pass a custom instance to [IndicatorService] to change caching behaviour per workflow.
 * For example, a momentum screener might use a shorter indicators TTL and a different
 * key prefix so its cache doesn't collide with the watchlist dashboard's cache.
 *
 * Usage:
 *   // Default — daily sync, standard Kite rate limit
 *   val service = IndicatorService(..., config = IndicatorConfig.DEFAULT)
 *
 *   // Screener — shorter TTL, different key namespace
 *   val screenerConfig = IndicatorConfig(indicatorsTtlSeconds = 6 * 3600, watchlistKeyPrefix = "screener")
 *   val screenerService = IndicatorService(..., config = screenerConfig)
 */
data class IndicatorConfig(

    /** TTL for raw OHLCV history in Redis. 48h bridges the weekend gap (Fri close → Mon open). */
    val ohlcvTtlSeconds: Long = 48 * 3600,

    /** TTL for computed indicators per tag in Redis. Matches the daily sync cadence. */
    val indicatorsTtlSeconds: Long = 24 * 3600,

    /**
     * Delay between consecutive Kite historical-data API calls (milliseconds).
     * Kite's documented limit is 3 req/sec; 350 ms gives a comfortable margin.
     */
    val kiteRateLimitDelayMs: Long = 350,

    /**
     * Maximum number of stocks processed in parallel while refreshing history or
     * recomputing indicators. Keep this small to avoid overloading Kite or Postgres.
     */
    val stockParallelism: Int = 3,

    /**
     * Redis key prefix for per-stock OHLCV data.
     * Key pattern: "{ohlcvKeyPrefix}:{instrumentToken}:ohlcv"
     */
    val ohlcvKeyPrefix: String = "stock",

    /**
     * Redis key prefix for per-tag indicator aggregates.
     * Key pattern: "{watchlistKeyPrefix}:{tag}:indicators"
     */
    val watchlistKeyPrefix: String = "watchlist",
) {
    fun ohlcvKey(instrumentToken: Long): String = "$ohlcvKeyPrefix:$instrumentToken:ohlcv"
    fun tagIndicatorsKey(tag: String): String = "$watchlistKeyPrefix:$tag:indicators"

    companion object {
        val DEFAULT = IndicatorConfig()
    }
}
