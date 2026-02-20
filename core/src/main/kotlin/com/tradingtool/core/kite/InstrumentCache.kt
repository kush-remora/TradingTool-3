package com.tradingtool.core.kite

import com.zerodhatech.models.Instrument

/**
 * In-memory lookup cache for Kite instruments.
 *
 * The instruments list (~500k rows) is fetched from Kite once at startup
 * and refreshed daily via the cron-job. Keyed by "EXCHANGE:TRADINGSYMBOL"
 * (e.g. "NSE:RELIANCE") for O(1) lookups.
 *
 * Thread safety: [refresh] and the read methods use @Volatile + a single
 * atomic swap, so readers always see a consistent snapshot.
 */
class InstrumentCache {

    @Volatile
    private var index: Map<String, Instrument> = emptyMap()

    /** True if the cache has been populated at least once. */
    fun isEmpty(): Boolean = index.isEmpty()

    /**
     * Replace the cache with a fresh instruments list for the given [exchanges].
     * Call this at startup and then once daily ~8:30 AM IST before markets open.
     */
    fun refresh(instruments: List<Instrument>) {
        index = instruments.associateBy { inst -> "${inst.exchange}:${inst.tradingsymbol}" }
    }

    /**
     * Look up the numeric [Instrument.instrumentToken] for a symbol.
     * Returns null if the cache is empty or the symbol is not found.
     */
    fun token(exchange: String, symbol: String): Long? =
        find(exchange, symbol)?.instrumentToken

    /** Full instrument record lookup by exchange + symbol. */
    fun find(exchange: String, symbol: String): Instrument? =
        index["$exchange:$symbol"]

    /** All instruments currently in the cache. */
    fun all(): Collection<Instrument> = index.values

    /** Number of instruments currently cached. */
    fun size(): Int = index.size
}
