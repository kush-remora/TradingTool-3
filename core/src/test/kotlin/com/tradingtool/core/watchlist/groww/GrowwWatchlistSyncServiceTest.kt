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
        val upsertedIndexKeys = mutableListOf<String>()
        val gateway = object : GrowwWatchlistStockGateway {
            override suspend fun upsertGrowwStock(stock: GrowwWatchlistStock, indexKey: String): Int {
                upsertedSymbols += stock.symbol
                upsertedIndexKeys += indexKey
                return 1
            }
        }

        val service = GrowwWatchlistSyncService(source, gateway)
        val result = service.sync(GrowwWatchlistSyncRequest())

        assertEquals(2, result.fetchedCount)
        assertEquals(2, result.syncedCount)
        assertEquals(listOf("INFY", "TCS"), upsertedSymbols)
        assertEquals(listOf("groww", "groww"), upsertedIndexKeys)
    }
}
