package com.tradingtool.core.strategy.wyckoff.observation

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
class WyckoffRawObservationService @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val candleCacheService: CandleCacheService,
) {
    private val engine = WyckoffRawObservationEngine()

    suspend fun listWatchlists(): UniverseOptionsResponse {
        val options = indexConstituentHandler.read { dao -> dao.listUniqueIndices() }
            .map { summary -> UniverseOption(summary.indexKey, summary.indexKey, summary.count) }
            .sortedBy(UniverseOption::label)
        return UniverseOptionsResponse(options)
    }

    suspend fun observe(
        watchlist: String?,
        symbol: String?,
        asOfDate: LocalDate,
        lookbackCalendarDays: Int,
    ): WyckoffRawObservationResponse {
        val requestedWatchlist = watchlist?.trim().orEmpty()
        val requestedSymbol = symbol?.trim()?.uppercase().orEmpty()
        require(requestedWatchlist.isNotEmpty() xor requestedSymbol.isNotEmpty()) {
            "Provide exactly one of watchlist or symbol."
        }

        val members = if (requestedWatchlist.isNotEmpty()) {
            resolveWatchlistMembers(requestedWatchlist)
        } else {
            resolveSymbolMember(requestedSymbol)?.let(::listOf)
                ?: throw IllegalArgumentException("Unknown NSE symbol: $requestedSymbol")
        }
        val distinctMembers = members.distinctBy { member -> member.symbol.uppercase() }
        val normalizedLookbackDays = lookbackCalendarDays.coerceIn(MIN_LOOKBACK_DAYS, MAX_LOOKBACK_DAYS)
        val candleFromDate = asOfDate.minusDays((normalizedLookbackDays + CANDLE_WARMUP_DAYS).toLong())
        val stocks = coroutineScope {
            val semaphore = Semaphore(MAX_PARALLEL_SYMBOLS)
            distinctMembers.map { member ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val candles = candleCacheService.getDailyCandles(
                            token = member.instrumentToken,
                            symbol = member.symbol,
                            from = candleFromDate,
                            to = asOfDate,
                        )
                        engine.buildStock(
                            symbol = member.symbol,
                            companyName = member.companyName,
                            watchlists = listOf(member.indexKey),
                            candles = candles,
                            asOfDate = asOfDate,
                            lookbackCalendarDays = normalizedLookbackDays,
                        )
                    }
                }
            }.awaitAll().filterNotNull().sortedBy { stock -> stock.symbol }
        }

        return WyckoffRawObservationResponse(
            asOfDate = asOfDate.toString(),
            lookbackCalendarDays = normalizedLookbackDays,
            watchlist = requestedWatchlist.takeIf { value -> value.isNotEmpty() },
            stocks = stocks,
        )
    }

    private suspend fun resolveWatchlistMembers(watchlist: String): List<IndexConstituentUpsertRow> {
        val resolvedWatchlist = indexConstituentHandler.read { dao ->
            dao.listUniqueIndices()
                .firstOrNull { summary -> summary.indexKey.equals(watchlist, ignoreCase = true) }
                ?.indexKey
        } ?: throw IllegalArgumentException("Unknown watchlist: $watchlist")

        return indexConstituentHandler.read { dao -> dao.listActiveByIndex(resolvedWatchlist) }
    }

    private suspend fun resolveSymbolMember(symbol: String): IndexConstituentUpsertRow? {
        return indexConstituentHandler.read { dao ->
            dao.listAllActive().firstOrNull { member -> member.symbol.equals(symbol, ignoreCase = true) }
        }
    }

    companion object {
        private const val CANDLE_WARMUP_DAYS = 45
        private const val MAX_PARALLEL_SYMBOLS = 16
        private const val MIN_LOOKBACK_DAYS = 60
        private const val MAX_LOOKBACK_DAYS = 365
    }
}
