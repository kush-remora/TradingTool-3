package com.tradingtool.core.watchlist.groww

import com.tradingtool.core.indexconstituents.IndexConstituentKeys

data class GrowwWatchlistStock(
    val symbol: String,
    val instrumentToken: Long,
    val companyName: String,
    val exchange: String = "NSE",
)

data class GrowwWatchlistSyncRequest(
    val indexKey: String = IndexConstituentKeys.GROWW_WATCHLIST,
)

data class GrowwWatchlistSyncResult(
    val fetchedCount: Int,
    val syncedCount: Int,
)

interface GrowwWatchlistSource {
    suspend fun fetchStocks(request: GrowwWatchlistSyncRequest): List<GrowwWatchlistStock>
}

interface GrowwWatchlistStockGateway {
    suspend fun upsertGrowwStock(stock: GrowwWatchlistStock, indexKey: String): Int

    suspend fun deactivateMissingGrowwStocks(indexKey: String, activeSymbols: List<String>): Int
}

interface GrowwWatchlistInstrumentTokenResolver {
    suspend fun resolve(exchange: String, symbol: String): Long?
}
