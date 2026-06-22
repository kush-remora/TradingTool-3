package com.tradingtool.core.delivery.config

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.indexconstituents.IndexConstituentKeys

@Singleton
class DeliveryUniverseService @Inject constructor(
    private val configService: DeliveryConfigService,
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
) {
    suspend fun resolveTargetSymbols(): Set<String> {
        return configService.resolveConfiguredUniverseSymbols(loadNseWatchlistSymbols())
    }

    suspend fun loadNseWatchlistSymbolsForDelivery(): List<String> {
        return loadNseWatchlistSymbols()
    }

    private suspend fun loadNseWatchlistSymbols(): List<String> {
        return indexConstituentHandler.read { dao ->
            dao.listActiveByIndex(IndexConstituentKeys.GROWW_WATCHLIST)
        }.map { row -> row.symbol }
    }
}
