package com.tradingtool.core.watchlist.groww

class GrowwWatchlistSyncService(
    private val source: GrowwWatchlistSource,
    private val stockGateway: GrowwWatchlistStockGateway,
) {
    suspend fun sync(request: GrowwWatchlistSyncRequest): GrowwWatchlistSyncResult {
        val stocks = source.fetchStocks(request)
            .distinctBy { stock -> stock.symbol to stock.exchange }

        var syncedCount = 0
        for (stock in stocks) {
            syncedCount += stockGateway.upsertGrowwStock(stock, request.indexKey)
        }

        return GrowwWatchlistSyncResult(
            watchlistId = request.watchlistId,
            fetchedCount = stocks.size,
            syncedCount = syncedCount,
        )
    }
}
