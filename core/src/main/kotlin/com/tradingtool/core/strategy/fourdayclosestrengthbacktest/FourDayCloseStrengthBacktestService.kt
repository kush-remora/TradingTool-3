package com.tradingtool.core.strategy.fourdayclosestrengthbacktest

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.indexconstituents.dao.IndexConstituentUpsertRow
import com.tradingtool.core.kite.InstrumentTokenResolverService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate

@Singleton
class FourDayCloseStrengthBacktestService @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val candleCacheService: CandleCacheService,
    private val instrumentTokenResolver: InstrumentTokenResolverService,
    private val engine: FourDayCloseStrengthBacktestEngine,
) {
    suspend fun run(request: FourDayCloseStrengthBacktestRequest, toDate: LocalDate = LocalDate.now()): FourDayCloseStrengthBacktestResponse {
        val normalizedSymbol = request.symbol?.trim()?.uppercase().orEmpty()
        val requestedWatchlist = request.watchlistKey?.trim().orEmpty()
        val hasStockRequest = normalizedSymbol.isNotEmpty() || request.instrumentToken != null
        val hasWatchlistRequest = requestedWatchlist.isNotEmpty()
        require(hasStockRequest != hasWatchlistRequest) { "Provide exactly one of symbol/instrumentToken or watchlistKey." }
        require(request.closePositionThresholdPct in 0.0..100.0) {
            "closePositionThresholdPct must be between 0 and 100."
        }

        return if (hasWatchlistRequest) {
            runWatchlist(requestedWatchlist, request.closePositionThresholdPct, toDate)
        } else {
            val token = request.instrumentToken
                ?: instrumentTokenResolver.resolve("NSE", normalizedSymbol)
                ?: throw IllegalArgumentException("instrumentToken is required for a stock backtest.")
            runStock(normalizedSymbol, token, null, request.closePositionThresholdPct, toDate)
        }
    }

    private suspend fun runStock(
        symbol: String,
        instrumentToken: Long,
        companyName: String?,
        thresholdPct: Double,
        toDate: LocalDate,
    ): FourDayCloseStrengthBacktestResponse {
        val testFrom = toDate.minusMonths(TEST_WINDOW_MONTHS)
        val candles = candleCacheService.getDailyCandles(
            token = instrumentToken,
            symbol = symbol,
            from = testFrom.minusDays(DETAIL_BUFFER_CALENDAR_DAYS),
            to = toDate,
        )
        require(candles.isNotEmpty()) { "No daily candle data is available for $symbol." }
        val effectiveTestFrom = maxOf(testFrom, candles.minOf { candle -> candle.candleDate })
        val report = engine.run(
            member = FourDayCloseStrengthMember(symbol, companyName, instrumentToken),
            candles = candles,
            testFrom = effectiveTestFrom,
            toDate = toDate,
            closePositionThresholdPct = thresholdPct,
        )
        return responseForStock(report, thresholdPct)
    }

    private suspend fun runWatchlist(
        requestedWatchlist: String,
        thresholdPct: Double,
        toDate: LocalDate,
    ): FourDayCloseStrengthBacktestResponse {
        val resolvedWatchlist = resolveWatchlist(requestedWatchlist)
        val members = indexConstituentHandler.read { dao -> dao.listActiveByIndex(resolvedWatchlist) }
            .filter { member -> member.instrumentToken > 0 && member.symbol.isNotBlank() }
            .distinctBy { member -> member.symbol.trim().uppercase() }
            .map(::toMember)
        require(members.isNotEmpty()) { "No stocks were found for watchlist $resolvedWatchlist." }

        val testFrom = toDate.minusMonths(TEST_WINDOW_MONTHS)
        val reports = coroutineScope {
            val semaphore = Semaphore(MAX_PARALLEL_CANDLE_READS)
            members.map { member ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val candles = candleCacheService.getDailyCandles(
                            token = member.instrumentToken,
                            symbol = member.symbol,
                            from = testFrom.minusDays(DETAIL_BUFFER_CALENDAR_DAYS),
                            to = toDate,
                        )
                        require(candles.isNotEmpty()) { "No daily candle data is available for ${member.symbol}." }
                        engine.run(
                            member = member,
                            candles = candles,
                            testFrom = maxOf(testFrom, candles.minOf { candle -> candle.candleDate }),
                            toDate = toDate,
                            closePositionThresholdPct = thresholdPct,
                        )
                    }
                }
            }.awaitAll().sortedBy(FourDayCloseStrengthSymbolReport::symbol)
        }
        val signals = reports.flatMap(FourDayCloseStrengthSymbolReport::signals)
            .sortedWith(compareByDescending<FourDayCloseStrengthSignal> { it.signalDate }.thenBy { it.symbol })
        return FourDayCloseStrengthBacktestResponse(
            symbol = null,
            watchlistKey = resolvedWatchlist,
            testedFromDate = testFrom.toString(),
            testedToDate = toDate.toString(),
            closePositionThresholdPct = thresholdPct,
            patternSessions = PATTERN_SESSIONS,
            detailSessions = DETAIL_SESSIONS,
            signalCount = signals.size,
            signals = signals,
            symbols = reports,
        )
    }

    private suspend fun resolveWatchlist(requestedWatchlist: String): String =
        indexConstituentHandler.read { dao ->
            dao.listUniqueIndices()
                .firstOrNull { summary -> summary.indexKey.equals(requestedWatchlist, ignoreCase = true) }
                ?.indexKey
        } ?: throw IllegalArgumentException("Unknown watchlist: $requestedWatchlist")

    private fun responseForStock(report: FourDayCloseStrengthSymbolReport, thresholdPct: Double): FourDayCloseStrengthBacktestResponse =
        FourDayCloseStrengthBacktestResponse(
            symbol = report.symbol,
            watchlistKey = null,
            testedFromDate = report.testedFromDate,
            testedToDate = report.testedToDate,
            closePositionThresholdPct = thresholdPct,
            patternSessions = PATTERN_SESSIONS,
            detailSessions = DETAIL_SESSIONS,
            signalCount = report.signalCount,
            signals = report.signals,
            symbols = listOf(report),
        )

    private fun toMember(member: IndexConstituentUpsertRow): FourDayCloseStrengthMember = FourDayCloseStrengthMember(
        symbol = member.symbol.trim().uppercase(),
        companyName = member.companyName,
        instrumentToken = member.instrumentToken,
    )

    private companion object {
        const val TEST_WINDOW_MONTHS = 60L
        const val DETAIL_BUFFER_CALENDAR_DAYS = 60L
        const val PATTERN_SESSIONS = 4
        const val DETAIL_SESSIONS = 20
        const val MAX_PARALLEL_CANDLE_READS = 12
    }
}
