package com.tradingtool.core.watchlist.groww

import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.KiteConnectClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class KiteInstrumentTokenResolver(
    private val kiteClient: KiteConnectClient,
    private val instrumentCache: InstrumentCache,
) : GrowwWatchlistInstrumentTokenResolver {
    private val refreshMutex = Mutex()

    override suspend fun resolve(exchange: String, symbol: String): Long? {
        ensureCacheLoaded(exchange)
        val normalizedExchange = exchange.trim().uppercase()
        val normalizedSymbol = symbol.trim().uppercase()

        instrumentCache.token(normalizedExchange, normalizedSymbol)?.let { return it }

        if (normalizedExchange == NSE_EXCHANGE) {
            instrumentCache.token(normalizedExchange, "$normalizedSymbol-BE")?.let { return it }
            return findByNseVariantPattern(normalizedSymbol)
        }

        return null
    }

    private fun findByNseVariantPattern(symbol: String): Long? {
        val pattern = Regex("^${Regex.escape(symbol)}-[A-Z0-9]+$")
        val candidates = instrumentCache.all().asSequence()
            .filter { instrument -> instrument.exchange.equals(NSE_EXCHANGE, ignoreCase = true) }
            .filter { instrument -> pattern.matches(instrument.tradingsymbol.uppercase()) }
            .toList()

        if (candidates.size == 1) {
            return candidates.first().instrument_token
        }

        return null
    }

    private suspend fun ensureCacheLoaded(exchange: String) {
        if (!instrumentCache.isEmpty()) {
            return
        }
        refreshMutex.withLock {
            if (!instrumentCache.isEmpty()) {
                return
            }
            val instruments = withContext(Dispatchers.IO) {
                kiteClient.client().getInstruments(exchange)
            }
            instrumentCache.refresh(instruments)
        }
    }

    private companion object {
        const val NSE_EXCHANGE: String = "NSE"
    }
}
