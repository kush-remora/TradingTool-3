package com.tradingtool.core.strategy.priceacceptance

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.indexconstituents.dao.IndexConstituentUpsertRow
import com.tradingtool.core.model.screener.UniverseOption
import com.tradingtool.core.model.screener.UniverseOptionsResponse
import com.tradingtool.core.strategy.wyckoff.deliverythreshold.normalizeIndexKeyInCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate

@Singleton
class PriceAcceptanceScannerService @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val candleCacheService: CandleCacheService,
) {
    suspend fun listUniverseOptions(): UniverseOptionsResponse {
        val options = indexConstituentHandler.read { dao -> dao.listUniqueIndices() }
            .map { summary -> UniverseOption(summary.indexKey, summary.indexKey, summary.count) }
            .sortedBy(UniverseOption::label)
        return UniverseOptionsResponse(options)
    }

    suspend fun scan(indexKey: String, asOfDate: LocalDate): PriceAcceptanceScanResponse {
        val requestedKey = indexKey.trim()
        require(requestedKey.isNotEmpty()) { "indexKey is required." }

        val resolvedKey = resolveIndexKey(requestedKey)
            ?: throw IllegalArgumentException("Unknown index or watchlist: $indexKey")
        val members = indexConstituentHandler.read { dao -> dao.listActiveByIndex(resolvedKey) }
            .distinctBy { member -> member.symbol.trim().uppercase() }

        val rows = coroutineScope {
            val semaphore = Semaphore(MAX_PARALLEL_CANDLE_READS)
            members.map { member ->
                async(Dispatchers.IO) {
                    semaphore.withPermit { buildRow(member, resolvedKey, asOfDate) }
                }
            }.awaitAll().filterNotNull()
        }

        return PriceAcceptanceScanResponse(
            selectedIndexKey = resolvedKey,
            requestedAsOfDate = asOfDate.toString(),
            scannedStockCount = members.size,
            resultCount = rows.size,
            rows = rows.sortedWith(
                compareByDescending<PriceAcceptanceRow> { row -> row.closeHits100 }
                    .thenByDescending { row -> row.closeHitRate100Pct }
                    .thenBy { row -> row.symbol },
            ),
        )
    }

    private suspend fun resolveIndexKey(requestedKey: String): String? {
        return indexConstituentHandler.read { dao ->
            dao.listUniqueIndices()
                .firstOrNull { summary -> normalizeIndexKeyInCore(summary.indexKey) == normalizeIndexKeyInCore(requestedKey) }
                ?.indexKey
        }
    }

    private suspend fun buildRow(
        member: IndexConstituentUpsertRow,
        resolvedIndexKey: String,
        asOfDate: LocalDate,
    ): PriceAcceptanceRow? {
        val candles = candleCacheService.getDailyCandles(
            token = member.instrumentToken,
            symbol = member.symbol,
            from = asOfDate.minusDays(HISTORY_CALENDAR_DAYS),
            to = asOfDate,
        )
        val evaluation = PriceAcceptanceScannerEngine.evaluate(candles, asOfDate) ?: return null

        return PriceAcceptanceRow(
            symbol = member.symbol.trim().uppercase(),
            companyName = member.companyName,
            indexKey = resolvedIndexKey,
            instrumentToken = member.instrumentToken,
            anchorDate = evaluation.anchorDate.toString(),
            open = evaluation.open,
            close = evaluation.close,
            bodyLow = evaluation.bodyLow,
            bodyHigh = evaluation.bodyHigh,
            bodyRangePct = evaluation.bodyRangePct,
            priorSessionCount = evaluation.priorSessionCount,
            closeHits20 = evaluation.closeHits20,
            closeHitRate20Pct = evaluation.closeHitRate20Pct,
            closeHits40 = evaluation.closeHits40,
            closeHitRate40Pct = evaluation.closeHitRate40Pct,
            closeHits60 = evaluation.closeHits60,
            closeHitRate60Pct = evaluation.closeHitRate60Pct,
            closeHits80 = evaluation.closeHits80,
            closeHitRate80Pct = evaluation.closeHitRate80Pct,
            closeHits100 = evaluation.closeHits100,
            closeHitRate100Pct = evaluation.closeHitRate100Pct,
        )
    }

    private companion object {
        const val HISTORY_CALENDAR_DAYS = 240L
        const val MAX_PARALLEL_CANDLE_READS = 12
    }
}
