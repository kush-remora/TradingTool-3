package com.tradingtool.core.watchlist.groww

import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.InstrumentTokenResolverService
import com.tradingtool.core.kite.KiteConnectClient

class KiteInstrumentTokenResolver(
    private val kiteClient: KiteConnectClient,
    private val instrumentCache: InstrumentCache,
) : GrowwWatchlistInstrumentTokenResolver {
    private val resolver = InstrumentTokenResolverService(kiteClient, instrumentCache)

    override suspend fun resolve(exchange: String, symbol: String): Long? {
        return resolver.resolve(exchange, symbol)
    }
}
