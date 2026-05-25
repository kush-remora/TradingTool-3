package com.tradingtool.core.indexconstituents

import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.InstrumentTokenResolution
import com.tradingtool.core.kite.InstrumentTokenResolverService
import com.tradingtool.core.kite.KiteConnectClient

class KiteIndexConstituentTokenResolver(
    private val kiteClient: KiteConnectClient,
    private val instrumentCache: InstrumentCache,
) : IndexConstituentTokenResolver {
    private val resolver = InstrumentTokenResolverService(kiteClient, instrumentCache)

    override suspend fun resolveDetailed(exchange: String, symbol: String): InstrumentTokenResolution {
        return resolver.resolveDetailed(exchange, symbol)
    }
}
