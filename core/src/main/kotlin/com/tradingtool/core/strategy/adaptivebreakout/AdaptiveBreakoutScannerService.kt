package com.tradingtool.core.strategy.adaptivebreakout

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.DailyCandle
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

    suspend fun scanSymbol(
        symbol: String,
        requestedAsOfDate: LocalDate = LocalDate.now(),
    ): AdaptiveBreakoutScanResponse {
        val normalizedSymbol = symbol.trim().uppercase()
        require(normalizedSymbol.isNotEmpty()) { "symbol is required." }
        val token = instrumentTokenResolver.resolve("NSE", normalizedSymbol)
            ?: throw IllegalArgumentException("Unknown NSE symbol: $normalizedSymbol")
        val companyName = indexConstituentHandler.read { dao ->
            dao.listAllActive().firstOrNull { member -> member.symbol.equals(normalizedSymbol, ignoreCase = true) }
                ?.companyName
        } ?: normalizedSymbol
        val row = scanStock(normalizedSymbol, companyName, token, requestedAsOfDate)
            ?: throw IllegalArgumentException("Not enough candle history to scan $normalizedSymbol.")

        return AdaptiveBreakoutScanResponse(
            watchlistKey = "STOCK:$normalizedSymbol",
            requestedAsOfDate = requestedAsOfDate.toString(),
            latestCandleDate = row.latestDate,
            scannedStockCount = 1,
            freshBreakoutCount = if (row.status == AdaptiveBreakoutStatus.FRESH_BREAKOUT) 1 else 0,
            config = CONFIG,
            rows = listOf(row),
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
        val normalizedSymbol = request.symbol?.trim()?.uppercase().orEmpty()
        val requestedWatchlist = request.watchlistKey?.trim().orEmpty()
        val hasStockRequest = normalizedSymbol.isNotEmpty() || request.instrumentToken != null
        val hasWatchlistRequest = requestedWatchlist.isNotEmpty()
        require(hasStockRequest != hasWatchlistRequest) { "Provide exactly one of symbol/instrumentToken or watchlistKey." }
        require(request.months in 1..24) { "months must be between 1 and 24." }
        require(request.targetPct > 0.0 && request.targetPct <= 100.0) {
            "targetPct must be greater than 0 and no more than 100."
        }
        require(request.stopLossPct > 0.0 && request.stopLossPct <= 100.0) {
            "stopLossPct must be greater than 0 and no more than 100."
        }
        require(request.maxEntryGapPct >= 0.0) {
            "maxEntryGapPct must not be negative."
        }
        require(request.transactionCostPct >= 0.0) {
            "transactionCostPct must not be negative."
        }
        require(request.slippagePct >= 0.0) {
            "slippagePct must not be negative."
        }

        if (hasWatchlistRequest) {
            return runWatchlistBacktest(
                watchlistKey = requestedWatchlist,
                months = request.months,
                targetPct = request.targetPct,
                stopLossPct = request.stopLossPct,
                entryTrigger = request.entryTrigger,
                qualityFilter = request.qualityFilter,
                maxEntryGapPct = request.maxEntryGapPct,
                transactionCostPct = request.transactionCostPct,
                slippagePct = request.slippagePct,
            )
        }

        require(normalizedSymbol.isNotEmpty()) { "symbol is required." }
        val instrumentToken = request.instrumentToken
            ?: throw IllegalArgumentException("instrumentToken is required for a stock backtest.")
        require(instrumentToken > 0) { "instrumentToken must be positive." }
        return runStockBacktest(
            symbol = normalizedSymbol,
            instrumentToken = instrumentToken,
            months = request.months,
            targetPct = request.targetPct,
            stopLossPct = request.stopLossPct,
            entryTrigger = request.entryTrigger,
            qualityFilter = request.qualityFilter,
            maxEntryGapPct = request.maxEntryGapPct,
            transactionCostPct = request.transactionCostPct,
            slippagePct = request.slippagePct,
        )
    }

    private suspend fun runStockBacktest(
        symbol: String,
        instrumentToken: Long,
        months: Long,
        targetPct: Double,
        stopLossPct: Double,
        entryTrigger: AdaptiveBreakoutBacktestEntryTrigger,
        qualityFilter: AdaptiveBreakoutQualityFilter,
        maxEntryGapPct: Double,
        transactionCostPct: Double,
        slippagePct: Double,
        companyName: String? = null,
        marketCandles: List<DailyCandle>? = null,
    ): AdaptiveBreakoutBacktestResponse {
        val candles = candleCacheService.getDailyCandles(
            token = instrumentToken,
            symbol = symbol,
            from = LocalDate.now().minusYears(HISTORY_YEARS),
            to = LocalDate.now(),
        ).sortedBy { candle -> candle.candleDate }
        require(candles.isNotEmpty()) { "No daily candle data is available for $symbol." }

        val availableToDate = candles.last().candleDate
        val requestedFromDate = availableToDate.minusMonths(months)
        val testedFromDate = maxOf(requestedFromDate, candles.first().candleDate)
        val orderedMarketCandles = marketCandles ?: candleCacheService.getDailyCandles(
            token = MARKET_INSTRUMENT_TOKEN,
            symbol = MARKET_SYMBOL,
            from = candles.first().candleDate,
            to = availableToDate,
        )

        val deliveries = stockDeliveryHandler.read { dao ->
            dao.findByInstrumentTokenBetweenDates(
                instrumentToken = instrumentToken,
                fromDate = testedFromDate.minusMonths(6),
                toDate = availableToDate,
            )
        }

        val response = AdaptiveBreakoutBacktestEngine.run(
            candles = candles,
            deliveries = deliveries,
            testFromDate = testedFromDate,
            testToDate = availableToDate,
            targetPct = targetPct,
            stopLossPct = stopLossPct,
            entryTrigger = entryTrigger,
            qualityFilter = qualityFilter,
            maxEntryGapPct = maxEntryGapPct,
            transactionCostPct = transactionCostPct,
            slippagePct = slippagePct,
            marketCandles = orderedMarketCandles,
        ).copy(symbol = symbol)
        return response.copy(
            symbols = listOf(
                AdaptiveBreakoutBacktestSymbolReport(
                    symbol = symbol,
                    companyName = companyName,
                    testedFromDate = response.testedFromDate,
                    testedToDate = response.testedToDate,
                    summary = response.summary,
                    trades = response.trades,
                ),
            ),
        )
    }

    private suspend fun runWatchlistBacktest(
        watchlistKey: String,
        months: Long,
        targetPct: Double,
        stopLossPct: Double,
        entryTrigger: AdaptiveBreakoutBacktestEntryTrigger,
        qualityFilter: AdaptiveBreakoutQualityFilter,
        maxEntryGapPct: Double,
        transactionCostPct: Double,
        slippagePct: Double,
    ): AdaptiveBreakoutBacktestResponse {
        val resolvedKey = resolveWatchlist(watchlistKey)
        val members = indexConstituentHandler.read { dao -> dao.listActiveByIndex(resolvedKey) }
            .filter { member -> member.instrumentToken > 0 && member.symbol.isNotBlank() }
            .distinctBy { member -> member.symbol.trim().uppercase() }
        require(members.isNotEmpty()) { "No stocks were found for this watchlist." }
        val marketCandles = candleCacheService.getDailyCandles(
            token = MARKET_INSTRUMENT_TOKEN,
            symbol = MARKET_SYMBOL,
            from = LocalDate.now().minusYears(HISTORY_YEARS),
            to = LocalDate.now(),
        )

        val responses = coroutineScope {
            val semaphore = Semaphore(MAX_PARALLEL_CANDLE_READS)
            members.map { member ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        runStockBacktest(
                            symbol = member.symbol.trim().uppercase(),
                            instrumentToken = member.instrumentToken,
                            months = months,
                            targetPct = targetPct,
                            stopLossPct = stopLossPct,
                            entryTrigger = entryTrigger,
                            qualityFilter = qualityFilter,
                            maxEntryGapPct = maxEntryGapPct,
                            transactionCostPct = transactionCostPct,
                            slippagePct = slippagePct,
                            companyName = member.companyName,
                            marketCandles = marketCandles,
                        )
                    }
                }
            }.awaitAll().sortedBy { response -> response.symbol }
        }
        val trades = responses.flatMap { response -> response.trades }
        val summary = summarizeAdaptiveBreakoutTrades(
            signalCount = responses.sumOf { response -> response.summary.signalCount },
            trades = trades,
        )
        return AdaptiveBreakoutBacktestResponse(
            symbol = null,
            watchlistKey = resolvedKey,
            entryTrigger = entryTrigger,
            qualityFilter = qualityFilter,
            testedFromDate = responses.minOf { response -> response.testedFromDate },
            testedToDate = responses.maxOf { response -> response.testedToDate },
            targetPct = targetPct,
            stopLossPct = stopLossPct,
            transactionCostPct = transactionCostPct,
            slippagePct = slippagePct,
            entryRule = "${entryTrigger.displayName()} with ${qualityFilter.displayName()} quality filter. " +
                describeEntryGapGuard(maxEntryGapPct) + " Returns subtract ${transactionCostPct}% round-trip costs and ${slippagePct}% slippage.",
            ambiguousCandleRule = "If one daily candle touches both target and stop, stop is assumed first because daily OHLC has no intraday order.",
            summary = summary,
            trades = trades.sortedWith(compareBy<AdaptiveBreakoutBacktestTrade> { it.breakoutDate }.thenBy { it.symbol }),
            symbols = responses.flatMap { response -> response.symbols },
        )
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
    ): AdaptiveBreakoutScanRow? = scanStock(
        symbol = member.symbol,
        companyName = member.companyName,
        instrumentToken = member.instrumentToken,
        requestedAsOfDate = requestedAsOfDate,
    )

    private suspend fun scanStock(
        symbol: String,
        companyName: String,
        instrumentToken: Long,
        requestedAsOfDate: LocalDate,
    ): AdaptiveBreakoutScanRow? {
        val candles = candleCacheService.getDailyCandles(
            token = instrumentToken,
            symbol = symbol,
            from = requestedAsOfDate.minusYears(HISTORY_YEARS),
            to = requestedAsOfDate,
        )
        val evaluation = AdaptiveBreakoutEngine.evaluate(candles, CONFIG) ?: return null
        return AdaptiveBreakoutScanRow(
            symbol = symbol,
            companyName = companyName,
            instrumentToken = instrumentToken,
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
        const val MARKET_INSTRUMENT_TOKEN = 256265L
        const val MARKET_SYMBOL = "NIFTY 50"
    }
}
