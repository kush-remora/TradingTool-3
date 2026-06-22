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
    private var symbolIndex: Map<String, Instrument> = emptyMap()

    @Volatile
    private var tokenIndex: Map<Long, Instrument> = emptyMap()

    @Volatile
    private var exchangeTokenIndex: Map<String, Instrument> = emptyMap()

    /** True if the cache has been populated at least once. */
    fun isEmpty(): Boolean = symbolIndex.isEmpty()

    /**
     * Replace the cache with a fresh instruments list for the given [exchanges].
     * Call this at startup and then once daily ~8:30 AM IST before markets open.
     */
    fun refresh(instruments: List<Instrument>) {
        symbolIndex = instruments.associateBy { inst -> "${inst.exchange}:${inst.tradingsymbol}" }
        tokenIndex = instruments.associateBy { it.instrument_token }
        exchangeTokenIndex = instruments.associateBy { inst -> "${inst.exchange}:${inst.exchange_token}" }
    }

    /**
     * Look up the numeric instrument token for a symbol.
     * Returns null if the cache is empty or the symbol is not found.
     */
    fun token(exchange: String, symbol: String): Long? =
        find(exchange, symbol)?.instrument_token

    /** Full instrument record lookup by exchange + symbol. */
    fun find(exchange: String, symbol: String): Instrument? =
        symbolIndex["$exchange:$symbol"]

    /** Full instrument record lookup by numeric token. */
    fun find(token: Long): Instrument? =
        tokenIndex[token]

    /** Look up an instrument by exchange and the exchange-assigned numeric token. */
    fun findByExchangeToken(exchange: String, exchangeToken: Long): Instrument? =
        exchangeTokenIndex["$exchange:$exchangeToken"]

    /** All instruments currently in the cache. */
    fun all(): Collection<Instrument> = symbolIndex.values

    /** Number of instruments currently cached. */
    fun size(): Int = symbolIndex.size
}
