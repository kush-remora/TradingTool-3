package com.tradingtool.core.watchlist.groww

class GrowwWatchlistSyncService(
    private val source: GrowwWatchlistSource,
    private val stockGateway: GrowwWatchlistStockGateway,
) {
    suspend fun sync(request: GrowwWatchlistSyncRequest): GrowwWatchlistSyncResult {
        val stocks = source.fetchStocks(request)
            .distinctBy { stock -> stock.symbol to stock.exchange }
        require(stocks.isNotEmpty()) {
            "Groww watchlist contains no NSE stocks; existing membership was not changed."
        }

        var syncedCount = 0
        for (stock in stocks) {
            syncedCount += stockGateway.upsertGrowwStock(stock, request.indexKey)
        }
        stockGateway.deactivateMissingGrowwStocks(
            indexKey = request.indexKey,
            activeSymbols = stocks.map { stock -> stock.symbol },
        )

        return GrowwWatchlistSyncResult(
            fetchedCount = stocks.size,
            syncedCount = syncedCount,
        )
    }
}
