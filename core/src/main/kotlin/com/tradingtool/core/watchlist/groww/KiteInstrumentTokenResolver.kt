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
        return instrumentCache.token(exchange, symbol)
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
}

