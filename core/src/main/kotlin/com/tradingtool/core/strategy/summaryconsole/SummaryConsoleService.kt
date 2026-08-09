package com.tradingtool.core.strategy.summaryconsole

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.database.StockDeliveryJdbiHandler
import com.tradingtool.core.indexconstituents.dao.IndexConstituentUpsertRow
import com.tradingtool.core.model.screener.UniverseOption
import com.tradingtool.core.model.screener.UniverseOptionsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate

@Singleton
class SummaryConsoleService @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val candleCacheService: CandleCacheService,
    private val deliveryHandler: StockDeliveryJdbiHandler,
) {
    suspend fun listWatchlists(): UniverseOptionsResponse {
        val options = indexConstituentHandler.read { dao -> dao.listUniqueIndices() }
            .map { summary -> UniverseOption(summary.indexKey, summary.indexKey, summary.count) }
            .sortedBy(UniverseOption::label)
        return UniverseOptionsResponse(options)
    }

    suspend fun scan(requestedWatchlists: List<String>, requestedAsOfDate: LocalDate): SummaryConsoleResponse {
        val normalizedWatchlists = requestedWatchlists.map(String::trim).filter(String::isNotEmpty).distinct()
        require(normalizedWatchlists.isNotEmpty()) { "At least one watchlist is required." }

        val resolvedWatchlists = resolveWatchlists(normalizedWatchlists)
        val members = resolveMembers(resolvedWatchlists)
        val evaluations = coroutineScope {
            val semaphore = Semaphore(MAX_PARALLEL_CANDLE_READS)
            members.map { member ->
                async(Dispatchers.IO) {
                    semaphore.withPermit { buildRows(member, requestedAsOfDate) }
                }
            }.awaitAll().flatten()
        }.filter { row -> row.evaluation.hasEvent }
            .sortedWith(compareByDescending<EvaluatedMember> { row -> row.evaluation.asOfDate }.thenBy { row -> row.member.symbol })

        return SummaryConsoleResponse(
            requestedAsOfDate = requestedAsOfDate.toString(),
            lookbackSessions = LOOKBACK_SESSIONS,
            watchlists = resolvedWatchlists,
            scannedCount = members.size,
            eventCount = evaluations.size,
            uniqueStockCount = evaluations.map { row -> row.member.symbol }.distinct().size,
            rows = evaluations.map { evaluated -> evaluated.toRow() },
        )
    }

    private suspend fun resolveWatchlists(requestedWatchlists: List<String>): List<String> {
        val available = indexConstituentHandler.read { dao -> dao.listUniqueIndices() }
        return requestedWatchlists.map { requested ->
            available.firstOrNull { summary -> summary.indexKey.equals(requested, ignoreCase = true) }?.indexKey
                ?: throw IllegalArgumentException("Unknown watchlist: $requested")
        }.distinct()
    }

    private suspend fun resolveMembers(watchlists: List<String>): List<SummaryConsoleMember> {
        val membersBySymbol = linkedMapOf<String, MutableList<IndexConstituentUpsertRow>>()
        watchlists.forEach { watchlist ->
            indexConstituentHandler.read { dao -> dao.listActiveByIndex(watchlist) }
                .forEach { member -> membersBySymbol.getOrPut(member.symbol.uppercase()) { mutableListOf() }.add(member) }
        }
        return membersBySymbol.values.map { members ->
            val primary = members.first()
            SummaryConsoleMember(
                symbol = primary.symbol,
                companyName = primary.companyName,
                instrumentToken = primary.instrumentToken,
                watchlists = members.map(IndexConstituentUpsertRow::indexKey).distinct().sorted(),
            )
        }
    }

    private suspend fun buildRows(member: SummaryConsoleMember, requestedAsOfDate: LocalDate): List<EvaluatedMember> {
        val candles = candleCacheService.getDailyCandles(
            token = member.instrumentToken,
            symbol = member.symbol,
            from = requestedAsOfDate.minusDays(HISTORY_CALENDAR_DAYS),
            to = requestedAsOfDate,
        )
        val evaluations = SummaryConsoleEngine.evaluateRecent(candles, requestedAsOfDate, LOOKBACK_SESSIONS)
        if (evaluations.isEmpty()) return emptyList()

        val deliveryByDate = deliveryHandler.read { dao ->
            dao.findByInstrumentTokenBetweenDates(
                instrumentToken = member.instrumentToken,
                fromDate = evaluations.first().asOfDate,
                toDate = evaluations.last().asOfDate,
            ).associate { delivery -> delivery.tradingDate to delivery.delivPer }
        }
        return evaluations.map { evaluation ->
            EvaluatedMember(member, evaluation, deliveryByDate[evaluation.asOfDate])
        }
    }

    private data class SummaryConsoleMember(
        val symbol: String,
        val companyName: String,
        val instrumentToken: Long,
        val watchlists: List<String>,
    )

    private data class EvaluatedMember(
        val member: SummaryConsoleMember,
        val evaluation: SummaryConsoleEvaluation,
        val deliveryPercentage: Double?,
    ) {
        fun toRow(): SummaryConsoleRow = SummaryConsoleRow(
            symbol = member.symbol,
            companyName = member.companyName,
            instrumentToken = member.instrumentToken,
            watchlists = member.watchlists,
            asOfDate = evaluation.asOfDate.toString(),
            close = evaluation.close,
            previousClose = evaluation.previousClose,
            dailyMovePct = evaluation.dailyMovePct,
            largeMove = evaluation.largeMove,
            sma200 = evaluation.sma200,
            sma200Crossed = evaluation.sma200Crossed,
            volume = evaluation.volume,
            averageVolume5 = evaluation.averageVolume5,
            volumeRatio = evaluation.volumeRatio,
            volumeAnomaly = evaluation.volumeAnomaly,
            deliveryPercentage = deliveryPercentage,
            breakout20Level = evaluation.breakout20Level,
            breakout20LevelCrossed = evaluation.breakout20LevelCrossed,
            breakout20CloseConfirmed = evaluation.breakout20CloseConfirmed,
            breakout40Level = evaluation.breakout40Level,
            breakout40LevelCrossed = evaluation.breakout40LevelCrossed,
            breakout40CloseConfirmed = evaluation.breakout40CloseConfirmed,
            breakout60Level = evaluation.breakout60Level,
            breakout60LevelCrossed = evaluation.breakout60LevelCrossed,
            breakout60CloseConfirmed = evaluation.breakout60CloseConfirmed,
        )
    }

    private companion object {
        const val HISTORY_CALENDAR_DAYS = 420L
        const val MAX_PARALLEL_CANDLE_READS = 12
        const val LOOKBACK_SESSIONS = 5
    }
}
