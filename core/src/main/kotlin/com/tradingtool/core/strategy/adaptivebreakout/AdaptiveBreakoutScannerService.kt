package com.tradingtool.core.strategy.adaptivebreakout

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.database.StockDeliveryJdbiHandler
import com.tradingtool.core.indexconstituents.dao.IndexConstituentUpsertRow
import com.tradingtool.core.kite.InstrumentTokenResolverService
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
class AdaptiveBreakoutScannerService @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val candleCacheService: CandleCacheService,
    private val stockDeliveryHandler: StockDeliveryJdbiHandler,
    private val instrumentTokenResolver: InstrumentTokenResolverService,
) {
    suspend fun listWatchlists(): UniverseOptionsResponse {
        val options = indexConstituentHandler.read { dao -> dao.listUniqueIndices() }
            .map { summary -> UniverseOption(summary.indexKey, summary.indexKey, summary.count) }
            .sortedBy(UniverseOption::label)
        return UniverseOptionsResponse(options)
    }

    suspend fun scan(
        watchlistKey: String,
        requestedAsOfDate: LocalDate = LocalDate.now(),
    ): AdaptiveBreakoutScanResponse {
        val resolvedKey = resolveWatchlist(watchlistKey)
        val members = indexConstituentHandler.read { dao -> dao.listActiveByIndex(resolvedKey) }
            .distinctBy(IndexConstituentUpsertRow::symbol)
        val rows = coroutineScope {
            val semaphore = Semaphore(MAX_PARALLEL_CANDLE_READS)
            members.map { member ->
                async(Dispatchers.IO) {
                    semaphore.withPermit { scanMember(member, requestedAsOfDate) }
                }
            }.awaitAll().filterNotNull()
        }.sortedWith(compareBy<AdaptiveBreakoutScanRow> { it.status.sortOrder }.thenBy(AdaptiveBreakoutScanRow::symbol))

        return AdaptiveBreakoutScanResponse(
            watchlistKey = resolvedKey,
            requestedAsOfDate = requestedAsOfDate.toString(),
            latestCandleDate = rows.maxOfOrNull(AdaptiveBreakoutScanRow::latestDate),
            scannedStockCount = members.size,
            freshBreakoutCount = rows.count { row -> row.status == AdaptiveBreakoutStatus.FRESH_BREAKOUT },
            config = CONFIG,
            rows = rows,
        )
    }

    suspend fun reviewBreakoutDay(symbol: String, requestedDate: LocalDate): BreakoutDayQualityResponse {
        val normalizedSymbol = symbol.trim().uppercase()
        require(normalizedSymbol.isNotEmpty()) { "symbol is required." }
        require(!requestedDate.isAfter(LocalDate.now())) { "date cannot be in the future." }
        val token = instrumentTokenResolver.resolve("NSE", normalizedSymbol)
            ?: throw IllegalArgumentException("Unknown NSE symbol: $normalizedSymbol")
        val candles = candleCacheService.getDailyCandles(
            token = token,
            symbol = normalizedSymbol,
            from = requestedDate.minusYears(HISTORY_YEARS),
            to = requestedDate,
        ).filter { candle -> !candle.candleDate.isAfter(requestedDate) }
        require(candles.any { candle -> candle.candleDate == requestedDate }) {
            "No completed daily candle exists for $normalizedSymbol on $requestedDate."
        }
        val evaluation = AdaptiveBreakoutEngine.evaluate(candles, CONFIG)
            ?: throw IllegalArgumentException("Not enough candle history to review $normalizedSymbol on $requestedDate.")
        val deliveries = stockDeliveryHandler.read { dao ->
            dao.findByInstrumentTokenBetweenDates(
                instrumentToken = token,
                fromDate = requestedDate.minusMonths(6),
                toDate = requestedDate,
            )
        }
        return BreakoutDayQualityAnalyzer.analyze(
            symbol = normalizedSymbol,
            candles = candles,
            deliveries = deliveries,
            evaluation = evaluation,
        )
    }

    suspend fun runBacktest(request: AdaptiveBreakoutBacktestRequest): AdaptiveBreakoutBacktestResponse {
        val normalizedSymbol = request.symbol.trim().uppercase()
        require(normalizedSymbol.isNotEmpty()) { "symbol is required." }
        require(request.instrumentToken > 0) { "instrumentToken must be positive." }
        require(request.months in 1..24) { "months must be between 1 and 24." }
        require(request.targetPct > 0.0 && request.targetPct <= 100.0) {
            "targetPct must be greater than 0 and no more than 100."
        }
        require(request.stopLossPct > 0.0 && request.stopLossPct <= 100.0) {
            "stopLossPct must be greater than 0 and no more than 100."
        }

        val candles = candleCacheService.getDailyCandles(
            token = request.instrumentToken,
            symbol = normalizedSymbol,
            from = LocalDate.now().minusYears(HISTORY_YEARS),
            to = LocalDate.now(),
        ).sortedBy { candle -> candle.candleDate }
        require(candles.isNotEmpty()) { "No daily candle data is available for $normalizedSymbol." }

        val availableToDate = candles.last().candleDate
        val requestedFromDate = availableToDate.minusMonths(request.months)
        val testedFromDate = maxOf(requestedFromDate, candles.first().candleDate)
        return AdaptiveBreakoutBacktestEngine.run(
            candles = candles,
            testFromDate = testedFromDate,
            testToDate = availableToDate,
            targetPct = request.targetPct,
            stopLossPct = request.stopLossPct,
        ).copy(symbol = normalizedSymbol)
    }

    private suspend fun resolveWatchlist(watchlistKey: String): String {
        val normalizedKey = watchlistKey.trim()
        require(normalizedKey.isNotEmpty()) { "watchlist is required." }
        return indexConstituentHandler.read { dao ->
            dao.listUniqueIndices().firstOrNull { summary ->
                summary.indexKey.equals(normalizedKey, ignoreCase = true)
            }?.indexKey
        } ?: throw IllegalArgumentException("Unknown watchlist: $watchlistKey")
    }

    private suspend fun scanMember(
        member: IndexConstituentUpsertRow,
        requestedAsOfDate: LocalDate,
    ): AdaptiveBreakoutScanRow? {
        val candles = candleCacheService.getDailyCandles(
            token = member.instrumentToken,
            symbol = member.symbol,
            from = requestedAsOfDate.minusYears(HISTORY_YEARS),
            to = requestedAsOfDate,
        )
        val evaluation = AdaptiveBreakoutEngine.evaluate(candles, CONFIG) ?: return null
        return AdaptiveBreakoutScanRow(
            symbol = member.symbol,
            companyName = member.companyName,
            instrumentToken = member.instrumentToken,
            status = evaluation.status,
            latestDate = evaluation.latestDate,
            latestOpen = evaluation.latestOpen,
            latestHigh = evaluation.latestHigh,
            latestLow = evaluation.latestLow,
            latestClose = evaluation.latestClose,
            latestVolume = evaluation.latestVolume,
            latestAtr = evaluation.latestAtr,
            ceiling = evaluation.ceiling,
            majorCeiling = evaluation.majorCeiling,
            ceilingAgeSessions = evaluation.ceilingAgeSessions,
            closeVsCeilingPct = evaluation.closeVsCeilingPct,
            closePositionPct = evaluation.closePositionPct,
            volumeVsTenDayAverage = evaluation.volumeVsTenDayAverage,
            fiftyTwoWeekHigh = evaluation.fiftyTwoWeekHigh,
            distanceFromFiftyTwoWeekHighPct = evaluation.distanceFromFiftyTwoWeekHighPct,
            breakoutEvidence = evaluation.breakoutEvidence,
            rawSteps = evaluation.rawSteps.takeLast(RAW_HISTORY_SESSIONS),
        )
    }

    private val AdaptiveBreakoutStatus.sortOrder: Int
        get() = when (this) {
            AdaptiveBreakoutStatus.FRESH_BREAKOUT -> 0
            AdaptiveBreakoutStatus.EARLY_BREAKOUT -> 1
            AdaptiveBreakoutStatus.TESTING_CEILING -> 2
            AdaptiveBreakoutStatus.STRONG_REBOUND -> 3
            AdaptiveBreakoutStatus.BELOW_CEILING -> 4
            AdaptiveBreakoutStatus.NO_CEILING -> 5
            AdaptiveBreakoutStatus.BREAKOUT_CONTINUATION -> 6
        }

    private companion object {
        val CONFIG = AdaptiveBreakoutConfig()
        const val HISTORY_YEARS = 5L
        const val RAW_HISTORY_SESSIONS = 260
        const val MAX_PARALLEL_CANDLE_READS = 12
    }
}
