package com.tradingtool.core.delivery.validation

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.database.StockJdbiHandler

@Singleton
class DeliveryTargetSymbolResolver @Inject constructor(
    private val stockHandler: StockJdbiHandler,
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
) {
    suspend fun resolveUniverseCounts(): Map<String, Int> {
        val watchlistCount = stockHandler.read { dao ->
            dao.listAll().count { stock -> stock.exchange.equals(NSE_EXCHANGE, ignoreCase = true) }
        }
        val indexCounts = indexConstituentHandler.read { dao ->
            dao.listUniqueIndices().associate { summary -> summary.indexKey to summary.count }
        }
        return linkedMapOf(WATCHLIST_KEY to watchlistCount) + indexCounts
    }

    suspend fun resolveTargetSymbols(): Set<String> {
        val watchlistSymbols = stockHandler.read { dao ->
            dao.listAll()
                .asSequence()
                .filter { stock -> stock.exchange.equals(NSE_EXCHANGE, ignoreCase = true) }
                .map { stock -> stock.symbol.trim().uppercase() }
                .filter { symbol -> symbol.isNotBlank() }
                .toSet()
        }

        val indexSymbols = indexConstituentHandler.read { dao ->
            val summaries = dao.listUniqueIndices()
            summaries
                .flatMap { summary -> dao.listActiveByIndex(summary.indexKey) }
                .asSequence()
                .map { row -> row.symbol.trim().uppercase() }
                .filter { symbol -> symbol.isNotBlank() }
                .toSet()
        }

        return watchlistSymbols + indexSymbols
    }

    companion object {
        private const val NSE_EXCHANGE: String = "NSE"
        private const val WATCHLIST_KEY: String = "WATCHLIST"
    }
}
