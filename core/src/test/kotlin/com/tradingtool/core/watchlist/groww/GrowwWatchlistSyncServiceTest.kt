package com.tradingtool.core.watchlist.groww

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GrowwWatchlistSyncServiceTest {

    @Test
    fun `sync deduplicates symbols and upserts each unique stock`() = runBlocking {
        val source = object : GrowwWatchlistSource {
            override suspend fun fetchStocks(request: GrowwWatchlistSyncRequest): List<GrowwWatchlistStock> {
                return listOf(
                    GrowwWatchlistStock("INFY", 408065L, "Infosys Ltd"),
                    GrowwWatchlistStock("INFY", 408065L, "Infosys Ltd"),
                    GrowwWatchlistStock("TCS", 2953217L, "Tata Consultancy Services"),
                )
            }
        }

        val upsertedSymbols = mutableListOf<String>()
        val gateway = object : GrowwWatchlistStockGateway {
            override suspend fun upsertGrowwStock(stock: GrowwWatchlistStock): Int {
                upsertedSymbols += stock.symbol
                return 1
            }
        }

        val service = GrowwWatchlistSyncService(source, gateway)
        val result = service.sync(GrowwWatchlistSyncRequest(watchlistId = "GWL_1"))

        assertEquals(2, result.fetchedCount)
        assertEquals(2, result.syncedCount)
        assertEquals(listOf("INFY", "TCS"), upsertedSymbols)
    }
}
