package com.tradingtool.core.delivery.validation

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.database.IndexConstituentJdbiHandler

@Singleton
class DeliveryTargetSymbolResolver @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
) {
    suspend fun resolveUniverseCounts(): Map<String, Int> {
        val watchlistCount = 0
        val indexCounts = indexConstituentHandler.read { dao ->
            dao.listUniqueIndices().associate { summary -> summary.indexKey to summary.count }
        }
        return linkedMapOf(WATCHLIST_KEY to watchlistCount) + indexCounts
    }

    suspend fun resolveTargetSymbols(): Set<String> {
        val indexSymbols = indexConstituentHandler.read { dao ->
            val summaries = dao.listUniqueIndices()
            summaries
                .flatMap { summary -> dao.listActiveByIndex(summary.indexKey) }
                .asSequence()
                .map { row -> row.symbol.trim().uppercase() }
                .filter { symbol -> symbol.isNotBlank() }
                .toSet()
        }

        return indexSymbols
    }

    companion object {
        private const val NSE_EXCHANGE: String = "NSE"
        private const val WATCHLIST_KEY: String = "WATCHLIST"
    }
}
