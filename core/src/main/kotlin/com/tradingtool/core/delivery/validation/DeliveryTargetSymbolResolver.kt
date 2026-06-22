package com.tradingtool.core.delivery.validation

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.indexconstituents.IndexConstituentKeys

@Singleton
class DeliveryTargetSymbolResolver @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
) {
    suspend fun resolveUniverseCounts(): Map<String, Int> {
        val summaries = indexConstituentHandler.read { dao -> dao.listUniqueIndices() }
        val watchlistCount = summaries
            .firstOrNull { summary -> summary.indexKey.equals(IndexConstituentKeys.GROWW_WATCHLIST, ignoreCase = true) }
            ?.count
            ?: 0
        val indexCounts = summaries
            .filterNot { summary -> summary.indexKey.equals(IndexConstituentKeys.GROWW_WATCHLIST, ignoreCase = true) }
            .associate { summary -> summary.indexKey to summary.count }
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
        private const val WATCHLIST_KEY: String = "WATCHLIST"
    }
}
