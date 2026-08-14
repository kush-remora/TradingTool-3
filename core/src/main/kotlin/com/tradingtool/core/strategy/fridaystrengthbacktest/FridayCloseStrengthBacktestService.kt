package com.tradingtool.core.strategy.fridaystrengthbacktest

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
import kotlin.math.round

@Singleton
class FridayCloseStrengthBacktestService @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val candleCacheService: CandleCacheService,
    private val engine: FridayCloseStrengthBacktestEngine,
) {
    suspend fun run(config: FridayCloseStrengthBacktestRunConfig): FridayCloseStrengthBacktestReport {
        require(config.watchlistKey.isNotBlank()) { "watchlistKey is required." }
        val members = resolveWatchlist(config.watchlistKey)
        require(members.isNotEmpty()) { "No stocks were found for this watchlist." }

        val testedFrom = config.toDate.minusMonths(TEST_WINDOW_MONTHS)
        val observations = coroutineScope {
            val semaphore = Semaphore(MAX_PARALLEL_CANDLE_READS)
            members.map { member ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val candles = candleCacheService.getDailyCandles(
                            token = member.instrumentToken,
                            symbol = member.symbol,
                            from = testedFrom.minusDays(DATA_BUFFER_DAYS),
                            to = config.toDate,
                        )
                        engine.run(member, candles, testedFrom, config.toDate)
                    }
                }
            }.awaitAll().flatten().sortedWith(compareByDescending<FridayCloseStrengthObservation> { it.signalDate }.thenBy { it.symbol })
        }

        return FridayCloseStrengthBacktestReport(
            watchlistKey = config.watchlistKey.trim(),
            testedFromDate = testedFrom.toString(),
            testedToDate = config.toDate.toString(),
            closePositionThresholdPct = CLOSE_POSITION_THRESHOLD_PCT,
            fridayMoveThresholdPct = FRIDAY_MOVE_THRESHOLD_PCT,
            summary = summarize(observations),
            observations = observations,
        )
    }

    private suspend fun resolveWatchlist(watchlistKey: String): List<FridayCloseStrengthMember> {
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

    private fun toMember(member: IndexConstituentUpsertRow): FridayCloseStrengthMember = FridayCloseStrengthMember(
        symbol = member.symbol.trim().uppercase(),
        companyName = member.companyName,
        instrumentToken = member.instrumentToken,
    )

    private fun summarize(observations: List<FridayCloseStrengthObservation>): FridayCloseStrengthBacktestSummary {
        val upsideValues = observations.map(FridayCloseStrengthObservation::maximumUpsidePct)
        val atLeast2PctCount = upsideValues.count { value -> value >= 2.0 }
        return FridayCloseStrengthBacktestSummary(
            signalCount = observations.size,
            maximumUpsideAtLeast2PctCount = atLeast2PctCount,
            maximumUpsideAtLeast5PctCount = upsideValues.count { value -> value >= 5.0 },
            maximumUpsideAtLeast2PctRatePct = atLeast2PctCount.toDouble().percentageOf(observations.size),
            averageMaximumUpsidePct = upsideValues.averageOrNull()?.let(::roundTo2),
            medianMaximumUpsidePct = upsideValues.medianOrNull()?.let(::roundTo2),
        )
    }

    private fun List<Double>.averageOrNull(): Double? = takeIf(List<Double>::isNotEmpty)?.average()

    private fun List<Double>.medianOrNull(): Double? {
        if (isEmpty()) return null
        val sortedValues = sorted()
        val middle = sortedValues.size / 2
        return if (sortedValues.size % 2 == 0) {
            (sortedValues[middle - 1] + sortedValues[middle]) / 2.0
        } else {
            sortedValues[middle]
        }
    }

    private fun Double.percentageOf(count: Int): Double? =
        takeIf { count > 0 }?.let { value -> round((value / count) * 100.0 * 100.0) / 100.0 }

    private fun roundTo2(value: Double): Double = round(value * 100.0) / 100.0

    private companion object {
        const val TEST_WINDOW_MONTHS = 6L
        const val DATA_BUFFER_DAYS = 14L
        const val MAX_PARALLEL_CANDLE_READS = 12
        const val CLOSE_POSITION_THRESHOLD_PCT = 70.0
        const val FRIDAY_MOVE_THRESHOLD_PCT = 2.0
    }
}
