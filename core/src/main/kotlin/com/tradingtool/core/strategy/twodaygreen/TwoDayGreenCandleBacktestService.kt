package com.tradingtool.core.strategy.twodaygreen

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.indexconstituents.dao.IndexConstituentUpsertRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate

@Singleton
class TwoDayGreenCandleBacktestService @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val candleCacheService: CandleCacheService,
    private val engine: TwoDayGreenCandleBacktestEngine,
) {
    suspend fun run(request: TwoDayGreenCandleBacktestRequest, toDate: LocalDate = LocalDate.now()): TwoDayGreenCandleBacktestReport {
        val requestedWatchlist = request.watchlistKey?.trim().orEmpty()
        require(requestedWatchlist.isNotBlank()) { "watchlistKey is required." }

        val resolvedWatchlist = indexConstituentHandler.read { dao ->
            dao.listUniqueIndices()
                .firstOrNull { summary -> summary.indexKey.equals(requestedWatchlist, ignoreCase = true) }
                ?.indexKey
        } ?: throw IllegalArgumentException("Unknown watchlist: $requestedWatchlist")

        val members = indexConstituentHandler.read { dao -> dao.listActiveByIndex(resolvedWatchlist) }
            .filter { member -> member.instrumentToken > 0 && member.symbol.isNotBlank() }
            .distinctBy { member -> member.symbol.trim().uppercase() }
            .map(::toMember)
        require(members.isNotEmpty()) { "No stocks were found for watchlist $resolvedWatchlist." }

        val reports = coroutineScope {
            val semaphore = Semaphore(MAX_PARALLEL_CANDLE_READS)
            members.map { member ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val candles = candleCacheService.getDailyCandles(
                            token = member.instrumentToken,
                            symbol = member.symbol,
                            from = toDate.minusDays(CANDLE_LOOKBACK_CALENDAR_DAYS),
                            to = toDate,
                        )
                        engine.run(member, candles)
                    }
                }
            }.awaitAll()
        }.sortedBy(TwoDayGreenCandleSymbolReport::symbol)

        val allTrades = reports.flatMap(TwoDayGreenCandleSymbolReport::trades)
        return TwoDayGreenCandleBacktestReport(
            watchlistKey = resolvedWatchlist,
            testedFromDate = reports.minOf(TwoDayGreenCandleSymbolReport::testedFromDate),
            testedToDate = reports.maxOf(TwoDayGreenCandleSymbolReport::testedToDate),
            summary = summarize(allTrades),
            symbols = reports,
        )
    }

    private fun toMember(member: IndexConstituentUpsertRow): TwoDayGreenCandleMember = TwoDayGreenCandleMember(
        symbol = member.symbol.trim().uppercase(),
        companyName = member.companyName,
        instrumentToken = member.instrumentToken,
    )

    private fun summarize(trades: List<TwoDayGreenCandleBacktestTrade>): TwoDayGreenCandleBacktestSummary {
        val targetHitCount = trades.count { trade -> trade.outcome == TwoDayGreenCandleOutcomes.TARGET_HIT }
        val holdingDays = trades.mapNotNull(TwoDayGreenCandleBacktestTrade::holdingTradingDays)
        return TwoDayGreenCandleBacktestSummary(
            setupCount = trades.size,
            targetHitCount = targetHitCount,
            unresolvedCount = trades.size - targetHitCount,
            targetHitRatePct = trades.takeIf { it.isNotEmpty() }?.let { targetHitCount * 100.0 / it.size },
            averageHoldingTradingDays = holdingDays.takeIf { it.isNotEmpty() }?.average(),
        )
    }

    private companion object {
        const val MAX_PARALLEL_CANDLE_READS = 12
        const val CANDLE_LOOKBACK_CALENDAR_DAYS = 70L
    }
}
