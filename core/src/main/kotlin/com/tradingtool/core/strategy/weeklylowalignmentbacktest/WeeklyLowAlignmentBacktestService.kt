package com.tradingtool.core.strategy.weeklylowalignmentbacktest

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.DailyCandle
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
class WeeklyLowAlignmentBacktestService @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val candleCacheService: CandleCacheService,
    private val engine: WeeklyLowAlignmentBacktestEngine,
) {
    suspend fun run(config: WeeklyLowAlignmentBacktestRunConfig): WeeklyLowAlignmentBacktestReport {
        require(config.watchlistKey.isNotBlank()) { "watchlistKey is required." }
        require(config.targetPct > 0.0) { "targetPct must be positive." }
        require(config.maxHoldingTradingDays > 0) { "maxHoldingTradingDays must be positive." }
        val members = resolveWatchlist(config.watchlistKey)
        require(members.isNotEmpty()) { "No stocks were found for this watchlist." }

        val reports = coroutineScope {
            val semaphore = Semaphore(MAX_PARALLEL_CANDLE_READS)
            members.map { member ->
                async(Dispatchers.IO) {
                    semaphore.withPermit { runMember(member, config) }
                }
            }.awaitAll()
        }.sortedBy(WeeklyLowAlignmentBacktestSymbolReport::symbol)
        val trades = reports.flatMap(WeeklyLowAlignmentBacktestSymbolReport::trades)
        val testFrom = config.toDate.minusMonths(TEST_WINDOW_MONTHS)
        return WeeklyLowAlignmentBacktestReport(
            watchlistKey = config.watchlistKey.trim(),
            testedFromDate = testFrom.toString(),
            testedToDate = reports.maxOf(WeeklyLowAlignmentBacktestSymbolReport::testedToDate),
            targetPct = config.targetPct,
            maxHoldingTradingDays = config.maxHoldingTradingDays,
            minimumRetestGapTradingDays = MINIMUM_RETEST_GAP_TRADING_DAYS,
            retestTolerancePct = RETEST_TOLERANCE_PCT,
            summary = summarize(trades),
            symbols = reports,
        )
    }

    private suspend fun runMember(
        member: WeeklyLowAlignmentMember,
        config: WeeklyLowAlignmentBacktestRunConfig,
    ): WeeklyLowAlignmentBacktestSymbolReport {
        val testFrom = config.toDate.minusMonths(TEST_WINDOW_MONTHS)
        val candles = candleCacheService.getDailyCandles(
            token = member.instrumentToken,
            symbol = member.symbol,
            from = testFrom.minusDays(DATA_BUFFER_DAYS),
            to = config.toDate,
        )
        return engine.run(
            symbol = member.symbol,
            companyName = member.companyName,
            candles = candles,
            testFrom = testFrom,
            toDate = config.toDate,
            targetPct = config.targetPct,
            maxHoldingTradingDays = config.maxHoldingTradingDays,
        )
    }

    private suspend fun resolveWatchlist(watchlistKey: String): List<WeeklyLowAlignmentMember> {
        val requestedKey = watchlistKey.trim()
        val resolvedKey = indexConstituentHandler.read { dao ->
            dao.listUniqueIndices()
                .firstOrNull { summary -> summary.indexKey.equals(requestedKey, ignoreCase = true) }
                ?.indexKey
        } ?: throw IllegalArgumentException("Unknown watchlist: $watchlistKey")
        return indexConstituentHandler.read { dao -> dao.listActiveByIndex(resolvedKey) }
            .filter { member -> member.instrumentToken > 0 && member.symbol.isNotBlank() }
            .distinctBy { member -> member.symbol.trim().uppercase() }
            .map(::toMember)
    }

    private fun toMember(member: IndexConstituentUpsertRow): WeeklyLowAlignmentMember = WeeklyLowAlignmentMember(
        symbol = member.symbol.trim().uppercase(),
        companyName = member.companyName,
        instrumentToken = member.instrumentToken,
    )

    private fun summarize(trades: List<WeeklyLowAlignmentBacktestTrade>): WeeklyLowAlignmentBacktestSummary {
        val returns = trades.mapNotNull(WeeklyLowAlignmentBacktestTrade::returnPct)
        return WeeklyLowAlignmentBacktestSummary(
            setupCount = trades.size,
            noRetestCount = trades.count { trade -> trade.outcome == WeeklyLowAlignmentBacktestOutcomes.NO_RETEST },
            tooSoonRetestCount = trades.count { trade -> trade.outcome == WeeklyLowAlignmentBacktestOutcomes.TOO_SOON_RETEST },
            filledTradeCount = returns.size,
            targetHitCount = trades.count { trade -> trade.outcome == WeeklyLowAlignmentBacktestOutcomes.TARGET_HIT },
            timeExitCount = trades.count { trade -> trade.outcome == WeeklyLowAlignmentBacktestOutcomes.TIME_EXIT },
            positionOpenSkipCount = trades.count { trade -> trade.outcome == WeeklyLowAlignmentBacktestOutcomes.POSITION_OPEN_SKIP },
            averageReturnPct = returns.takeIf(List<Double>::isNotEmpty)?.average()?.let { value -> kotlin.math.round(value * 100.0) / 100.0 },
        )
    }

    private companion object {
        const val TEST_WINDOW_MONTHS = 6L
        const val DATA_BUFFER_DAYS = 35L
        const val MAX_PARALLEL_CANDLE_READS = 12
        const val MINIMUM_RETEST_GAP_TRADING_DAYS = 5
        const val RETEST_TOLERANCE_PCT = 1.0
    }
}
