package com.tradingtool.core.kite

import com.github.benmanes.caffeine.cache.Caffeine
import com.zerodhatech.models.Quote
import java.util.concurrent.TimeUnit

/**
 * Service to fetch live market data from Kite Connect API.
 * Uses a Level 0 (L0) local memory Caffeine cache to debounce rapid frontend polling.
 */
class LiveMarketService(private val kiteClient: KiteConnectClient) {

    // L0 Cache: 10 seconds TTL
    // If 100 users refresh the dashboard within 10 seconds for the same watchlist,
    // only the first request hits Kite's rate limits.
    private val quoteCache = Caffeine.newBuilder()
        .expireAfterWrite(10, TimeUnit.SECONDS)
        .maximumSize(100)
        .build<String, Map<String, Quote>>()

    /**
     * Fetches live quotes including LTP, Volume, and Change.
     * @param instruments list of trading symbols in Kite format, e.g. ["NSE:INFY", "BSE:RELIANCE"]
     */
    fun getQuotes(instruments: List<String>): Map<String, Quote> {
        if (!kiteClient.isAuthenticated || instruments.isEmpty()) return emptyMap()

        // Cache key based on sorted instruments to ensure cache hits for identical watchlists
        val cacheKey = instruments.sorted().joinToString(",")

        return quoteCache.get(cacheKey) { _ ->
            try {
                kiteClient.client().getQuote(instruments.toTypedArray())
            } catch (e: Exception) {
                // Return empty map on failure so dashboard gracefully degrades to
                // showing cached indicators without live LTP injected.
                emptyMap()
            }
        } ?: emptyMap()
    }
}
