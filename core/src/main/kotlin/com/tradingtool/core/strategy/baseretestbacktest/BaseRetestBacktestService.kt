package com.tradingtool.core.strategy.baseretestbacktest

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

@Singleton
class BaseRetestBacktestService @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val candleCacheService: CandleCacheService,
    private val engine: BaseRetestBacktestEngine,
) {
    suspend fun run(config: BaseRetestBacktestRunConfig): BaseRetestBacktestReport {
        require(config.watchlistKey.isNotBlank()) { "watchlistKey is required." }
        require(config.targetPct in MIN_PERCENT..MAX_PERCENT) { "targetPct must be between 0.1 and 100.0." }
        require(config.stopLossPct in MIN_PERCENT..<MAX_PERCENT) { "stopLossPct must be between 0.1 and less than 100.0." }
        val members = resolveWatchlist(config.watchlistKey, config.symbol)
        require(members.isNotEmpty()) { "No stocks were found for this watchlist and stock selection." }
        val testFrom = config.toDate.minusMonths(TEST_WINDOW_MONTHS)

        val observations = coroutineScope {
            val semaphore = Semaphore(MAX_PARALLEL_CANDLE_READS)
            members.map { member ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val candles = candleCacheService.getDailyCandles(
                            token = member.instrumentToken,
                            symbol = member.symbol,
                            from = testFrom,
                            to = config.toDate,
                        )
                        engine.run(member, candles, testFrom, config.toDate, config.targetPct, config.stopLossPct)
                    }
                }
            }.awaitAll().flatten()
        }.sortedWith(compareByDescending<BaseRetestObservation> { it.confirmationDate }.thenBy { it.symbol })

        return BaseRetestBacktestReport(
            watchlistKey = config.watchlistKey.trim(),
            selectedSymbol = config.symbol?.trim()?.uppercase(),
            testedFromDate = testFrom.toString(),
            testedToDate = config.toDate.toString(),
            lowTolerancePct = LOW_TOLERANCE_PCT,
            reboundPct = REBOUND_PCT,
            limitOffsetPct = LIMIT_OFFSET_PCT,
            invalidationPct = INVALIDATION_PCT,
            targetPct = config.targetPct,
            stopLossPct = config.stopLossPct,
            summary = summarize(observations),
            observations = observations,
        )
    }

    private suspend fun resolveWatchlist(watchlistKey: String, symbol: String?): List<BaseRetestMember> {
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

    private fun toMember(member: IndexConstituentUpsertRow): BaseRetestMember = BaseRetestMember(
        symbol = member.symbol.trim().uppercase(),
        companyName = member.companyName,
        instrumentToken = member.instrumentToken,
    )

    private fun summarize(observations: List<BaseRetestObservation>): BaseRetestBacktestSummary {
        val pnlValues = observations.mapNotNull(BaseRetestObservation::pnlPct)
        val profitableCount = pnlValues.count { value -> value > 0.0 }
        return BaseRetestBacktestSummary(
            setupCount = observations.size,
            filledTradeCount = pnlValues.size,
            noFillCount = observations.count { observation -> observation.outcome == BaseRetestOutcomes.NO_FILL },
            baseInvalidatedCount = observations.count { observation -> observation.outcome == BaseRetestOutcomes.BASE_INVALIDATED },
            targetHitCount = observations.count { observation -> observation.outcome == BaseRetestOutcomes.TARGET_HIT },
            stopLossCount = observations.count { observation -> observation.outcome == BaseRetestOutcomes.STOP_LOSS },
            endOfDataExitCount = observations.count { observation -> observation.outcome == BaseRetestOutcomes.END_OF_DATA_EXIT },
            profitableTradeCount = profitableCount,
            lossTradeCount = pnlValues.count { value -> value < 0.0 },
            winRatePct = if (pnlValues.isEmpty()) null else roundTo2(profitableCount * 100.0 / pnlValues.size),
            averagePnlPct = pnlValues.takeIf(List<Double>::isNotEmpty)?.average()?.let(::roundTo2),
            medianPnlPct = median(pnlValues),
            worstPnlPct = pnlValues.minOrNull()?.let(::roundTo2),
            totalPnlPct = pnlValues.takeIf(List<Double>::isNotEmpty)?.sum()?.let(::roundTo2),
            totalHoldingSessions = observations.mapNotNull(BaseRetestObservation::holdingSessions).sum(),
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
        const val MAX_PARALLEL_CANDLE_READS = 12
        const val MIN_PERCENT = 0.1
        const val MAX_PERCENT = 100.0
        const val LOW_TOLERANCE_PCT = 1.0
        const val REBOUND_PCT = 5.0
        const val LIMIT_OFFSET_PCT = 1.0
        const val INVALIDATION_PCT = 1.0
    }
}
