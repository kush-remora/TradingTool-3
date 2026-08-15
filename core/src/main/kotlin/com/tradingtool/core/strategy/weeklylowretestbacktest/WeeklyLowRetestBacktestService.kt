package com.tradingtool.core.strategy.weeklylowretestbacktest

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
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Singleton
class WeeklyLowRetestBacktestService @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val candleCacheService: CandleCacheService,
    private val engine: WeeklyLowRetestBacktestEngine,
) {
    suspend fun run(config: WeeklyLowRetestBacktestRunConfig): WeeklyLowRetestBacktestReport {
        require(config.watchlistKey.isNotBlank()) { "watchlistKey is required." }
        require(config.limitOffsetPct in 0.5..1.0) { "limitOffsetPct must be between 0.5 and 1.0." }
        require(config.targetPct in 0.1..100.0) { "targetPct must be between 0.1 and 100.0." }
        val members = resolveWatchlist(config.watchlistKey, config.symbol)
        require(members.isNotEmpty()) { "No stocks were found for this watchlist." }

        val reports = coroutineScope {
            val semaphore = Semaphore(MAX_PARALLEL_CANDLE_READS)
            members.map { member ->
                async(Dispatchers.IO) {
                    semaphore.withPermit { runMember(member, config) }
                }
            }.awaitAll()
        }
        val observations = reports
            .flatten()
            .sortedWith(compareByDescending<WeeklyLowRetestObservation> { it.limitOrderDate }.thenBy { it.symbol }.thenByDescending { it.anchorDate })
        val testFrom = config.toDate.minusMonths(TEST_WINDOW_MONTHS)

        return WeeklyLowRetestBacktestReport(
            watchlistKey = config.watchlistKey.trim(),
            selectedSymbol = config.symbol?.trim()?.uppercase(),
            testedFromDate = testFrom.toString(),
            testedToDate = config.toDate.toString(),
            limitOffsetPct = config.limitOffsetPct,
            orderWindowSessions = HOLDING_SESSIONS,
            targetPct = config.targetPct,
            summary = summarize(observations),
            observations = observations,
        )
    }

    private suspend fun runMember(
        member: WeeklyLowRetestMember,
        config: WeeklyLowRetestBacktestRunConfig,
    ): List<WeeklyLowRetestObservation> {
        val testFrom = config.toDate.minusMonths(TEST_WINDOW_MONTHS)
        val candles = candleCacheService.getDailyCandles(
            token = member.instrumentToken,
            symbol = member.symbol,
            from = testFrom.minusDays(HISTORICAL_DATA_BUFFER_DAYS),
            to = config.toDate,
        )
        return engine.run(member, candles, testFrom, config.toDate, config.limitOffsetPct, config.targetPct)
    }

    private suspend fun resolveWatchlist(watchlistKey: String, symbol: String?): List<WeeklyLowRetestMember> {
        val requestedKey = watchlistKey.trim()
        val resolvedKey = indexConstituentHandler.read { dao ->
            dao.listUniqueIndices()
                .firstOrNull { summary -> summary.indexKey.equals(requestedKey, ignoreCase = true) }
                ?.indexKey
        } ?: throw IllegalArgumentException("Unknown watchlist: $watchlistKey")

        val members = indexConstituentHandler.read { dao -> dao.listActiveByIndex(resolvedKey) }
            .filter { member -> member.instrumentToken > 0 && member.symbol.isNotBlank() }
            .distinctBy { member -> member.symbol.trim().uppercase() }
            .map(::toMember)
        val requestedSymbol = symbol?.trim()?.uppercase()
        return if (requestedSymbol == null) members else members.filter { member -> member.symbol == requestedSymbol }
    }

    private fun toMember(member: IndexConstituentUpsertRow): WeeklyLowRetestMember = WeeklyLowRetestMember(
        symbol = member.symbol.trim().uppercase(),
        companyName = member.companyName,
        instrumentToken = member.instrumentToken,
    )

    private fun summarize(observations: List<WeeklyLowRetestObservation>): WeeklyLowRetestBacktestSummary {
        val returns = observations.mapNotNull(WeeklyLowRetestObservation::realizedReturnPct)
        val filledTradeCount = observations.count { observation -> observation.fillDate != null }
        val targetHitCount = observations.count { observation -> observation.outcome == WeeklyLowRetestOutcomes.TARGET_HIT }
        return WeeklyLowRetestBacktestSummary(
            signalCount = observations.size,
            noFillCount = observations.count { observation -> observation.outcome == WeeklyLowRetestOutcomes.NO_FILL },
            filledTradeCount = filledTradeCount,
            targetHitCount = targetHitCount,
            fourthSessionExitCount = observations.count { observation -> observation.outcome == WeeklyLowRetestOutcomes.FOURTH_SESSION_EXIT },
            profitableExitCount = returns.count { value -> value > 0.0 },
            lossExitCount = returns.count { value -> value < 0.0 },
            targetHitRatePct = if (filledTradeCount == 0) null else roundTo2(targetHitCount * 100.0 / filledTradeCount),
            averageRealizedReturnPct = returns.takeIf(List<Double>::isNotEmpty)?.average()?.let(::roundTo2),
            medianRealizedReturnPct = median(returns),
            worstRealizedReturnPct = returns.minOrNull()?.let(::roundTo2),
            totalRealizedReturnPct = returns.takeIf(List<Double>::isNotEmpty)?.sum()?.let(::roundTo2),
            totalHoldingSessions = observations.mapNotNull(WeeklyLowRetestObservation::holdingSessions).sum(),
        )
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return roundTo2(if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle])
    }

    private fun roundTo2(value: Double): Double = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()

    private companion object {
        const val TEST_WINDOW_MONTHS = 6L
        const val HISTORICAL_DATA_BUFFER_DAYS = 400L
        const val MAX_PARALLEL_CANDLE_READS = 12
        const val HOLDING_SESSIONS = 4
    }
}
