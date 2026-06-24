package com.tradingtool.core.watchlist.groww

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
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
        var capturedActiveSymbols = emptyList<String>()
        val gateway = object : GrowwWatchlistStockGateway {
            override suspend fun upsertGrowwStock(stock: GrowwWatchlistStock, indexKey: String): Int {
                upsertedSymbols += stock.symbol
                upsertedIndexKeys += indexKey
                return 1
            }

            override suspend fun deactivateMissingGrowwStocks(indexKey: String, activeSymbols: List<String>): Int {
                assertEquals("groww_HIGH_QUALITY", indexKey)
                capturedActiveSymbols = activeSymbols
                return 0
            }
        }

        val service = GrowwWatchlistSyncService(source, gateway)
        val result = service.sync(GrowwWatchlistSyncRequest())

        assertEquals(2, result.fetchedCount)
        assertEquals(2, result.syncedCount)
        assertEquals(listOf("INFY", "TCS"), upsertedSymbols)
        assertEquals(listOf("groww_HIGH_QUALITY", "groww_HIGH_QUALITY"), upsertedIndexKeys)
        assertEquals(listOf("INFY", "TCS"), capturedActiveSymbols)
    }

    @Test
    fun `sync rejects an empty source without changing the existing membership`() {
        val source = object : GrowwWatchlistSource {
            override suspend fun fetchStocks(request: GrowwWatchlistSyncRequest): List<GrowwWatchlistStock> {
                return emptyList()
            }
        }
        var deactivationCalled = false
        val gateway = object : GrowwWatchlistStockGateway {
            override suspend fun upsertGrowwStock(stock: GrowwWatchlistStock, indexKey: String): Int = 1

            override suspend fun deactivateMissingGrowwStocks(indexKey: String, activeSymbols: List<String>): Int {
                deactivationCalled = true
                return 0
            }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                GrowwWatchlistSyncService(source, gateway).sync(GrowwWatchlistSyncRequest())
            }
        }

        assertEquals(
            "Groww watchlist contains no NSE stocks; existing membership was not changed.",
            error.message,
        )
        assertFalse(deactivationCalled)
    }
}
