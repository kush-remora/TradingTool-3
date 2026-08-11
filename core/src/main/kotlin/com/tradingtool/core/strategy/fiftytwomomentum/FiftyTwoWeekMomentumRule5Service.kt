package com.tradingtool.core.strategy.fiftytwomomentum

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.database.IndexConstituentJdbiHandler
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
class FiftyTwoWeekMomentumRule5Service @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val candleCacheService: CandleCacheService,
) {
    suspend fun listWatchlists(): UniverseOptionsResponse {
        val options = indexConstituentHandler.read { dao -> dao.listUniqueIndices() }
            .map { summary -> UniverseOption(summary.indexKey, summary.indexKey, summary.count) }
            .sortedBy(UniverseOption::label)
        return UniverseOptionsResponse(options)
    }

    suspend fun scan(
        requestedWatchlists: List<String>,
        requestedAsOfDate: LocalDate,
        breakoutPeriodSessions: Int,
        nearHighTolerancePct: Double,
    ): Rule5ApiResponse {
        require(breakoutPeriodSessions in SUPPORTED_BREAKOUT_PERIODS) {
            "breakoutPeriodSessions must be one of: ${SUPPORTED_BREAKOUT_PERIODS.joinToString()}"
        }
        validateNearHighTolerance(nearHighTolerancePct)
        val normalizedWatchlists = requestedWatchlists.map(String::trim).filter(String::isNotEmpty).distinct()
        require(normalizedWatchlists.isNotEmpty()) { "At least one watchlist is required." }

        val resolvedWatchlists = resolveWatchlists(normalizedWatchlists)
        val members = resolveMembers(resolvedWatchlists)
        val results = coroutineScope {
            val semaphore = Semaphore(MAX_PARALLEL_CANDLE_READS)
            members.map { member ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        scanMember(member, requestedAsOfDate, breakoutPeriodSessions, nearHighTolerancePct)
                    }
                }
            }.awaitAll().filterNotNull()
        }.sortedWith(compareByDescending<Rule5SymbolResult> { it.latestBreakoutDate }.thenBy { it.symbol })

        return Rule5ApiResponse(
            requestedAsOfDate = requestedAsOfDate.toString(),
            lookbackSessions = LOOKBACK_SESSIONS,
            breakoutPeriodSessions = breakoutPeriodSessions,
            nearHighTolerancePct = nearHighTolerancePct,
            watchlists = resolvedWatchlists,
            scannedCount = members.size,
            breakoutStockCount = results.size,
            results = results,
        )
    }

    suspend fun backtest(
        requestedWatchlists: List<String>,
        requestedAsOfDate: LocalDate,
        breakoutPeriodSessions: Int,
        nearHighTolerancePct: Double,
        targetPct: Double,
    ): Rule5BacktestResponse {
        require(breakoutPeriodSessions in SUPPORTED_BREAKOUT_PERIODS) {
            "breakoutPeriodSessions must be one of: ${SUPPORTED_BREAKOUT_PERIODS.joinToString()}"
        }
        validateNearHighTolerance(nearHighTolerancePct)
        require(targetPct.isFinite() && targetPct > 0.0) { "targetPct must be a positive number." }
        val normalizedWatchlists = requestedWatchlists.map(String::trim).filter(String::isNotEmpty).distinct()
        require(normalizedWatchlists.isNotEmpty()) { "At least one watchlist is required." }

        val resolvedWatchlists = resolveWatchlists(normalizedWatchlists)
        val members = resolveMembers(resolvedWatchlists)
        val periodStartDate = requestedAsOfDate.minusMonths(BACKTEST_MONTHS)
        val evaluations = coroutineScope {
            val semaphore = Semaphore(MAX_PARALLEL_CANDLE_READS)
            members.map { member ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val candles = loadCandles(member, requestedAsOfDate, BACKTEST_HISTORY_CALENDAR_DAYS)
                        FiftyTwoWeekMomentumRule5BacktestEngine.evaluate(
                            symbol = member.symbol,
                            companyName = member.companyName,
                            candles = candles,
                            periodStartDate = periodStartDate,
                            requestedAsOfDate = requestedAsOfDate,
                            breakoutPeriodSessions = breakoutPeriodSessions,
                            nearHighTolerancePct = nearHighTolerancePct,
                            targetPct = targetPct,
                        )
                    }
                }
            }.awaitAll()
        }
        val signals = evaluations.flatMap(Rule5BacktestSymbolEvaluation::signals)
            .sortedWith(compareByDescending<Rule5BacktestSignal> { it.signalDate }.thenBy { it.symbol })
        val trades = evaluations.flatMap(Rule5BacktestSymbolEvaluation::trades)
            .sortedWith(compareByDescending<Rule5BacktestTrade> { it.entryDate }.thenBy { it.symbol })

        return Rule5BacktestResponse(
            requestedAsOfDate = requestedAsOfDate.toString(),
            periodStartDate = periodStartDate.toString(),
            breakoutPeriodSessions = breakoutPeriodSessions,
            nearHighTolerancePct = nearHighTolerancePct,
            targetPct = targetPct,
            scannedCount = members.size,
            signalCount = signals.size,
            enteredTradeCount = trades.size,
            targetHitCount = trades.count { trade -> trade.status == TARGET_HIT },
            openTradeCount = trades.count { trade -> trade.status == OPEN },
            signals = signals,
            trades = trades,
        )
    }

    private suspend fun resolveWatchlists(requestedWatchlists: List<String>): List<String> {
        val available = indexConstituentHandler.read { dao -> dao.listUniqueIndices() }
        return requestedWatchlists.map { requested ->
            available.firstOrNull { summary -> summary.indexKey.equals(requested, ignoreCase = true) }?.indexKey
                ?: throw IllegalArgumentException("Unknown watchlist: $requested")
        }.distinct()
    }

    private suspend fun resolveMembers(watchlists: List<String>): List<Rule5Member> {
        val membersBySymbol = linkedMapOf<String, MutableList<IndexConstituentUpsertRow>>()
        watchlists.forEach { watchlist ->
            indexConstituentHandler.read { dao -> dao.listActiveByIndex(watchlist) }
                .forEach { member -> membersBySymbol.getOrPut(member.symbol.uppercase()) { mutableListOf() }.add(member) }
        }

        return membersBySymbol.values.map { members ->
            val primary = members.first()
            Rule5Member(
                symbol = primary.symbol,
                companyName = primary.companyName,
                instrumentToken = primary.instrumentToken,
                watchlists = members.map(IndexConstituentUpsertRow::indexKey).distinct().sorted(),
            )
        }
    }

    private suspend fun scanMember(
        member: Rule5Member,
        requestedAsOfDate: LocalDate,
        breakoutPeriodSessions: Int,
        nearHighTolerancePct: Double,
    ): Rule5SymbolResult? {
        val candles = loadCandles(member, requestedAsOfDate, SCAN_HISTORY_CALENDAR_DAYS)
        val freshBreakoutDays = FiftyTwoWeekMomentumRule5Engine.findRecentFreshBreakouts(
            candles = candles,
            requestedAsOfDate = requestedAsOfDate,
            lookbackSessions = LOOKBACK_SESSIONS,
            breakoutPeriodSessions = breakoutPeriodSessions,
            nearHighTolerancePct = nearHighTolerancePct,
        )
        val latestBreakout = freshBreakoutDays.firstOrNull() ?: return null

        return Rule5SymbolResult(
            symbol = member.symbol,
            companyName = member.companyName,
            instrumentToken = member.instrumentToken,
            watchlists = member.watchlists,
            latestBreakoutDate = latestBreakout.date,
            latestHigh = latestBreakout.high,
            latestClose = latestBreakout.close,
            latestReferenceHigh = latestBreakout.referenceHigh,
            latestReferenceHighDaysAgo = latestBreakout.referenceHighDaysAgo,
            latestCloseVsReferenceHighPct = latestBreakout.closeVsReferenceHighPct,
            freshBreakoutDays = freshBreakoutDays,
        )
    }

    private suspend fun loadCandles(
        member: Rule5Member,
        requestedAsOfDate: LocalDate,
        historyCalendarDays: Long,
    ) =
        candleCacheService.getDailyCandles(
            token = member.instrumentToken,
            symbol = member.symbol,
            from = requestedAsOfDate.minusDays(historyCalendarDays),
            to = requestedAsOfDate,
        )

    private data class Rule5Member(
        val symbol: String,
        val companyName: String,
        val instrumentToken: Long,
        val watchlists: List<String>,
    )

    private fun validateNearHighTolerance(nearHighTolerancePct: Double) {
        require(nearHighTolerancePct.isFinite() && nearHighTolerancePct in 0.0..100.0) {
            "nearHighTolerancePct must be between 0 and 100."
        }
    }

    private companion object {
        const val SCAN_HISTORY_CALENDAR_DAYS = 420L
        const val BACKTEST_HISTORY_CALENDAR_DAYS = 720L
        const val LOOKBACK_SESSIONS = 5
        const val MAX_PARALLEL_CANDLE_READS = 12
        const val BACKTEST_MONTHS = 6L
        const val TARGET_HIT = "TARGET_HIT"
        const val OPEN = "OPEN"
        val SUPPORTED_BREAKOUT_PERIODS = setOf(20, 40, 60, 100, 200)
    }
}
