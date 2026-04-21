package com.tradingtool.core.watchlist.groww

data class GrowwWatchlistStock(
    val symbol: String,
    val instrumentToken: Long,
    val companyName: String,
    val exchange: String = "NSE",
)

data class GrowwWatchlistSyncRequest(
    val watchlistId: String,
)

data class GrowwWatchlistSyncResult(
    val watchlistId: String,
    val fetchedCount: Int,
    val syncedCount: Int,
)

interface GrowwWatchlistSource {
    suspend fun fetchStocks(request: GrowwWatchlistSyncRequest): List<GrowwWatchlistStock>
}

interface GrowwWatchlistStockGateway {
    suspend fun upsertGrowwStock(stock: GrowwWatchlistStock): Int
}

interface GrowwWatchlistInstrumentTokenResolver {
    suspend fun resolve(exchange: String, symbol: String): Long?
}
