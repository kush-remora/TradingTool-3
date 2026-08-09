package com.tradingtool.core.strategy.volumeeventbacktest

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
class VolumeEventConfirmationBacktestService @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val candleCacheService: CandleCacheService,
    private val engine: VolumeEventConfirmationBacktestEngine,
) {
    suspend fun run(
        request: VolumeEventConfirmationBacktestRequest,
        today: LocalDate = LocalDate.now(),
    ): VolumeEventConfirmationBacktestReport {
        val requestedWatchlist = request.watchlistKey?.trim().orEmpty()
        require(requestedWatchlist.isNotBlank()) { "watchlistKey is required." }

        val resolvedWatchlist = indexConstituentHandler.read { dao ->
            dao.listUniqueIndices()
                .firstOrNull { summary -> summary.indexKey.equals(requestedWatchlist, ignoreCase = true) }
                ?.indexKey
        } ?: throw IllegalArgumentException("Unknown watchlist: $requestedWatchlist")

        val toDate = parseDate(request.toDate, today, "toDate")
        val fromDate = parseDate(request.fromDate, toDate.minusMonths(DEFAULT_TEST_MONTHS), "fromDate")
        require(!fromDate.isAfter(toDate)) { "fromDate must not be after toDate." }
        val entryMode = request.entryMode
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.isNotBlank() }
            ?: VolumeEventEntryModes.FIVE_DAY_FUTURE_RSI_CONFIRMATION
        require(entryMode in VolumeEventEntryModes.all) { "Unknown entryMode: $entryMode" }

        val members = indexConstituentHandler.read { dao -> dao.listActiveByIndex(resolvedWatchlist) }
            .filter { member -> member.instrumentToken > 0 && member.symbol.isNotBlank() }
            .distinctBy { member -> member.symbol.trim().uppercase() }
            .map(::toMember)
        val selectedSymbol = request.symbol?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        val selectedMembers = selectedSymbol?.let { symbol ->
            members.filter { member -> member.symbol == symbol }
                .takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("Symbol $symbol is not in watchlist $resolvedWatchlist.")
        } ?: members
        require(selectedMembers.isNotEmpty()) { "No stocks were found for watchlist $resolvedWatchlist." }

        val config = VolumeEventConfirmationBacktestConfig(entryMode = entryMode)
        val reports = coroutineScope {
            val semaphore = Semaphore(MAX_PARALLEL_CANDLE_READS)
            selectedMembers.map { member ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val candles = candleCacheService.getDailyCandles(
                            token = member.instrumentToken,
                            symbol = member.symbol,
                        from = fromDate.minusDays(CANDLE_WARMUP_CALENDAR_DAYS),
                            to = toDate,
                        )
                        engine.run(member, candles, fromDate, toDate, config)
                    }
                }
            }.awaitAll()
        }.sortedBy(VolumeEventConfirmationSymbolReport::symbol)

        val allObservations = reports.flatMap(VolumeEventConfirmationSymbolReport::observations)
        return VolumeEventConfirmationBacktestReport(
            watchlistKey = resolvedWatchlist,
            selectedSymbol = selectedSymbol,
            testedFromDate = reports.mapNotNull(VolumeEventConfirmationSymbolReport::testedFromDate).minOrNull(),
            testedToDate = reports.mapNotNull(VolumeEventConfirmationSymbolReport::testedToDate).maxOrNull(),
            config = config,
            summary = summarizeVolumeEventObservations(allObservations, entryMode),
            symbols = reports,
        )
    }

    private fun toMember(member: IndexConstituentUpsertRow): VolumeEventConfirmationMember = VolumeEventConfirmationMember(
        symbol = member.symbol.trim().uppercase(),
        companyName = member.companyName,
        instrumentToken = member.instrumentToken,
    )

    private fun parseDate(value: String?, fallback: LocalDate, fieldName: String): LocalDate = try {
        value?.trim()?.takeIf { it.isNotEmpty() }?.let(LocalDate::parse) ?: fallback
    } catch (_: Exception) {
        throw IllegalArgumentException("$fieldName must be a valid ISO date in YYYY-MM-DD format.")
    }

    private companion object {
        const val MAX_PARALLEL_CANDLE_READS = 12
        const val CANDLE_WARMUP_CALENDAR_DAYS = 400L
        const val DEFAULT_TEST_MONTHS: Long = 6
    }
}
