package com.tradingtool.core.strategy.rsioversold

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.indexconstituents.dao.IndexConstituentUpsertRow
import com.tradingtool.core.model.screener.UniverseOption
import com.tradingtool.core.model.screener.UniverseOptionsResponse
import com.tradingtool.core.strategy.wyckoff.deliverythreshold.normalizeIndexKeyInCore
import com.tradingtool.core.technical.calculateRsiValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate

@Singleton
class RsiOversoldScannerService @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val candleCacheService: CandleCacheService,
    private val engine: RsiOversoldScannerEngine,
) {
    suspend fun listWatchlists(): UniverseOptionsResponse {
        val options = indexConstituentHandler.read { dao -> dao.listUniqueIndices() }
            .map { summary -> UniverseOption(summary.indexKey, summary.indexKey, summary.count) }
            .sortedBy(UniverseOption::label)
        return UniverseOptionsResponse(options)
    }

    suspend fun scan(request: RsiOversoldScanRequest, asOfDate: LocalDate = LocalDate.now()): RsiOversoldScanResponse {
        val selection = resolveSelection(request.indexKeys)
        val evaluations = coroutineScope {
            val semaphore = Semaphore(MAX_PARALLEL_CANDLE_READS)
            selection.members.map { member ->
                async(Dispatchers.IO) {
                    semaphore.withPermit { evaluate(member, asOfDate) }
                }
            }.awaitAll()
        }

        val rows = evaluations.mapNotNull(RsiOversoldEvaluation::row).sortedWith(
            compareBy<RsiOversoldRow> { row -> row.signalRsi }.thenBy { row -> row.symbol },
        )
        val symbols = selection.members.map(RsiOversoldMember::symbol)

        return RsiOversoldScanResponse(
            selectedIndexKeys = selection.selectedIndexKeys,
            config = RsiOversoldScanConfig(
                rsiPeriod = RSI_PERIOD,
                baselineSessions = BASELINE_SESSIONS,
                signalWindowSessions = SIGNAL_WINDOW_SESSIONS,
                signalOffset = SIGNAL_OFFSET,
                asOfDate = asOfDate.toString(),
            ),
            scannedStockCount = symbols.size,
            resultCount = rows.size,
            insufficientDataSymbols = evaluations
                .filter { evaluation -> evaluation.status == RsiOversoldEvaluationStatus.INSUFFICIENT_DATA }
                .map(RsiOversoldEvaluation::symbol)
                .sorted(),
            noSignalSymbols = evaluations
                .filter { evaluation -> evaluation.status == RsiOversoldEvaluationStatus.NO_SIGNAL }
                .map(RsiOversoldEvaluation::symbol)
                .sorted(),
            rows = rows,
        )
    }

    private suspend fun evaluate(member: RsiOversoldMember, asOfDate: LocalDate): RsiOversoldEvaluation {
        val candles = candleCacheService.getDailyCandles(
            token = member.instrumentToken,
            symbol = member.symbol,
            from = asOfDate.minusDays(HISTORY_CALENDAR_DAYS),
            to = asOfDate,
        ).sortedBy(DailyCandle::candleDate)
        val availableCandleCount = candles.count { candle -> !candle.candleDate.isAfter(asOfDate) }
        if (availableCandleCount < REQUIRED_SESSIONS) {
            return RsiOversoldEvaluation(member.symbol, null, RsiOversoldEvaluationStatus.INSUFFICIENT_DATA)
        }

        val rsiValues = try {
            candles.calculateRsiValues(period = RSI_PERIOD, fallback = RSI_FALLBACK)
        } catch (_: IllegalArgumentException) {
            return RsiOversoldEvaluation(member.symbol, null, RsiOversoldEvaluationStatus.INSUFFICIENT_DATA)
        }
        val row = try {
            engine.evaluate(
                symbol = member.symbol,
                companyName = member.companyName,
                watchlistKeys = member.watchlistKeys,
                candles = candles,
                rsiValues = rsiValues,
                asOfDate = asOfDate,
            )
        } catch (_: IllegalArgumentException) {
            return RsiOversoldEvaluation(member.symbol, null, RsiOversoldEvaluationStatus.INSUFFICIENT_DATA)
        }
        return RsiOversoldEvaluation(
            symbol = member.symbol,
            row = row,
            status = if (row == null) RsiOversoldEvaluationStatus.NO_SIGNAL else RsiOversoldEvaluationStatus.SIGNAL,
        )
    }

    private suspend fun resolveSelection(requestedKeys: List<String>): RsiOversoldSelection {
        val requested = requestedKeys.map(String::trim).filter(String::isNotEmpty).distinct()
        require(requested.isNotEmpty()) { "Select at least one watchlist." }

        val available = indexConstituentHandler.read { dao -> dao.listUniqueIndices() }
        val matchingKeys = available
            .groupBy { summary -> normalizeIndexKeyInCore(summary.indexKey) }
        val selectedKeys = requested.flatMap { requestedKey ->
            matchingKeys[normalizeIndexKeyInCore(requestedKey)]?.map { summary -> summary.indexKey }
                ?: throw IllegalArgumentException("Unknown watchlist: $requestedKey")
        }.distinct()

        val members = selectedKeys
            .flatMap { key -> indexConstituentHandler.read { dao -> dao.listActiveByIndex(key) } }
            .filter { member -> member.instrumentToken > 0 && member.symbol.isNotBlank() }
            .groupBy { member -> member.symbol.trim().uppercase() }
            .map { (symbol, memberships) ->
                val first = memberships.first()
                RsiOversoldMember(
                    symbol = symbol,
                    companyName = first.companyName,
                    instrumentToken = first.instrumentToken,
                    watchlistKeys = memberships.map(IndexConstituentUpsertRow::indexKey).distinct(),
                )
            }
            .sortedBy(RsiOversoldMember::symbol)

        return RsiOversoldSelection(selectedKeys.sorted(), members)
    }

    private companion object {
        const val RSI_PERIOD: Int = 14
        const val RSI_FALLBACK: Double = 50.0
        const val BASELINE_SESSIONS: Int = 200
        const val SIGNAL_WINDOW_SESSIONS: Int = 20
        const val SIGNAL_OFFSET: Double = 1.0
        const val REQUIRED_SESSIONS: Int = BASELINE_SESSIONS + SIGNAL_WINDOW_SESSIONS
        const val HISTORY_CALENDAR_DAYS: Long = 420
        const val MAX_PARALLEL_CANDLE_READS: Int = 12
    }
}

private data class RsiOversoldMember(
    val symbol: String,
    val companyName: String?,
    val instrumentToken: Long,
    val watchlistKeys: List<String>,
)

private data class RsiOversoldSelection(
    val selectedIndexKeys: List<String>,
    val members: List<RsiOversoldMember>,
)

private data class RsiOversoldEvaluation(
    val symbol: String,
    val row: RsiOversoldRow?,
    val status: RsiOversoldEvaluationStatus,
)

private enum class RsiOversoldEvaluationStatus {
    SIGNAL,
    NO_SIGNAL,
    INSUFFICIENT_DATA,
}
