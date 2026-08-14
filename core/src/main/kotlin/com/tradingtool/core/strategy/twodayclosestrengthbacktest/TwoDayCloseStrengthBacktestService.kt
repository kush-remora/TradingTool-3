package com.tradingtool.core.strategy.twodayclosestrengthbacktest

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
class TwoDayCloseStrengthBacktestService @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val candleCacheService: CandleCacheService,
    private val engine: TwoDayCloseStrengthBacktestEngine,
) {
    suspend fun run(config: TwoDayCloseStrengthBacktestRunConfig): TwoDayCloseStrengthBacktestReport {
        require(config.watchlistKey.isNotBlank()) { "watchlistKey is required." }
        val members = resolveWatchlist(config.watchlistKey)
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
            .sortedWith(compareByDescending<TwoDayCloseStrengthObservation> { it.patternEndDate }.thenBy { it.symbol })
        val testFrom = config.toDate.minusMonths(TEST_WINDOW_MONTHS)

        return TwoDayCloseStrengthBacktestReport(
            watchlistKey = config.watchlistKey.trim(),
            testedFromDate = testFrom.toString(),
            testedToDate = config.toDate.toString(),
            closePositionThresholdPct = CLOSE_POSITION_THRESHOLD_PCT,
            targetPct = TARGET_PCT,
            summary = summarize(observations),
            observations = observations,
        )
    }

    private suspend fun runMember(
        member: TwoDayCloseStrengthMember,
        config: TwoDayCloseStrengthBacktestRunConfig,
    ): List<TwoDayCloseStrengthObservation> {
        val testFrom = config.toDate.minusMonths(TEST_WINDOW_MONTHS)
        val candles = candleCacheService.getDailyCandles(
            token = member.instrumentToken,
            symbol = member.symbol,
            from = testFrom.minusDays(DATA_BUFFER_DAYS),
            to = config.toDate,
        )
        return engine.run(member, candles, testFrom, config.toDate)
    }

    private suspend fun resolveWatchlist(watchlistKey: String): List<TwoDayCloseStrengthMember> {
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

    private fun toMember(member: IndexConstituentUpsertRow): TwoDayCloseStrengthMember = TwoDayCloseStrengthMember(
        symbol = member.symbol.trim().uppercase(),
        companyName = member.companyName,
        instrumentToken = member.instrumentToken,
    )

    private fun summarize(observations: List<TwoDayCloseStrengthObservation>): TwoDayCloseStrengthBacktestSummary {
        val returns = observations.map(TwoDayCloseStrengthObservation::realizedReturnPct)
        return TwoDayCloseStrengthBacktestSummary(
            signalCount = observations.size,
            targetHitCount = observations.count { observation -> observation.exitReason == TwoDayCloseStrengthExitReasons.TARGET_HIT },
            thursdayCloseExitCount = observations.count { observation -> observation.exitReason == TwoDayCloseStrengthExitReasons.THURSDAY_CLOSE_EXIT },
            profitableExitCount = returns.count { value -> value > 0.0 },
            lossExitCount = returns.count { value -> value < 0.0 },
            averageRealizedReturnPct = returns.takeIf(List<Double>::isNotEmpty)?.average()?.let(::roundTo2),
            medianRealizedReturnPct = median(returns),
            worstRealizedReturnPct = returns.minOrNull()?.let(::roundTo2),
        )
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return roundTo2(if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle])
    }

    private fun roundTo2(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0

    private companion object {
        const val TEST_WINDOW_MONTHS = 6L
        const val DATA_BUFFER_DAYS = 21L
        const val MAX_PARALLEL_CANDLE_READS = 12
        const val CLOSE_POSITION_THRESHOLD_PCT = 80.0
        const val TARGET_PCT = 5.0
    }
}
