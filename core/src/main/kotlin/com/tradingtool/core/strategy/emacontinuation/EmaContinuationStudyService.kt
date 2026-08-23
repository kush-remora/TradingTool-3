package com.tradingtool.core.strategy.emacontinuation

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.indexconstituents.dao.IndexConstituentUpsertRow
import com.tradingtool.core.kite.InstrumentTokenResolverService
import com.tradingtool.core.kite.LiveMarketService
import com.tradingtool.core.model.screener.UniverseOption
import com.tradingtool.core.model.screener.UniverseOptionsResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate
import java.util.Locale

@Singleton
class EmaContinuationStudyService @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val candleCacheService: CandleCacheService,
    private val instrumentTokenResolver: InstrumentTokenResolverService,
    private val liveMarketService: LiveMarketService,
) {
    suspend fun listWatchlists(): UniverseOptionsResponse {
        val options = indexConstituentHandler.read { dao -> dao.listUniqueIndices() }
            .map { summary -> UniverseOption(summary.indexKey, summary.indexKey, summary.count) }
            .sortedBy(UniverseOption::label)
        return UniverseOptionsResponse(options)
    }

    suspend fun study(
        request: EmaContinuationStudyRequest,
        testFromDate: LocalDate = STUDY_START_DATE,
        asOfDate: LocalDate = LocalDate.now(),
    ): EmaContinuationStudyResponse {
        val member = resolveMember(request)
        val candles = loadCandles(member, asOfDate)
        return EmaContinuationStudyEngine.run(
            candles = candles,
            testFromDate = testFromDate,
            testToDate = asOfDate,
            watchlistKey = member.watchlistKey,
        )
    }

    suspend fun studyWatchlist(
        request: EmaContinuationWatchlistStudyRequest,
        testFromDate: LocalDate = STUDY_START_DATE,
        asOfDate: LocalDate = LocalDate.now(),
    ): EmaContinuationWatchlistStudyResponse {
        val requestedWatchlist = request.watchlistKey?.trim().orEmpty()
        require(requestedWatchlist.isNotBlank()) { "watchlistKey is required." }
        val resolvedWatchlist = resolveWatchlist(requestedWatchlist)
        val members = indexConstituentHandler.read { dao -> dao.listActiveByIndex(resolvedWatchlist) }
            .filter { member -> member.instrumentToken > 0L && member.symbol.isNotBlank() }
            .distinctBy { member -> member.symbol.trim().uppercase(Locale.ROOT) }
            .sortedBy { member -> member.symbol.uppercase(Locale.ROOT) }
        require(members.isNotEmpty()) { "No stocks were found for watchlist $resolvedWatchlist." }

        val rows = coroutineScope {
            val semaphore = Semaphore(MAX_PARALLEL_CANDLE_READS)
            members.map { member ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        analyzeWatchlistMember(member, resolvedWatchlist, testFromDate, asOfDate)
                    }
                }
            }.awaitAll()
        }.sortedBy(EmaContinuationWatchlistRow::symbol)

        return EmaContinuationWatchlistStudyResponse(
            watchlistKey = resolvedWatchlist,
            testedFromDate = testFromDate.toString(),
            testedToDate = asOfDate.toString(),
            stockCount = rows.size,
            analyzedCount = rows.count { row -> row.status == EmaContinuationWatchlistStudyStatus.ANALYZED },
            rows = rows,
        )
    }

    suspend fun current(
        symbol: String,
        asOfDate: LocalDate = LocalDate.now(),
    ): EmaContinuationCurrentResponse {
        val normalizedSymbol = symbol.trim().uppercase(Locale.ROOT)
        require(normalizedSymbol.isNotBlank()) { "symbol is required." }
        val token = instrumentTokenResolver.resolve("NSE", normalizedSymbol)
            ?: throw IllegalArgumentException("Unknown NSE symbol: $normalizedSymbol")
        val candles = candleCacheService.getDailyCandles(
            token = token,
            symbol = normalizedSymbol,
            from = STUDY_START_DATE.minusDays(HISTORY_CALENDAR_DAYS),
            to = asOfDate,
        ).filter { candle -> !candle.candleDate.isAfter(asOfDate) }
        require(candles.isNotEmpty()) { "No daily candle data is available for $normalizedSymbol." }

        val study = EmaContinuationStudyEngine.run(
            candles = candles,
            testFromDate = STUDY_START_DATE,
            testToDate = asOfDate,
            watchlistKey = "STOCK:$normalizedSymbol",
        )
        val latest = study.candles.last()
        val quote = liveMarketService.getQuotes(listOf("NSE:$normalizedSymbol"))["NSE:$normalizedSymbol"]
        val livePrice = quote?.lastPrice?.takeIf { price -> price > 0.0 }
        val price = livePrice ?: latest.close
        val open = quote?.ohlc?.open ?: latest.open
        val high = quote?.ohlc?.high ?: latest.high
        val low = quote?.ohlc?.low ?: latest.low
        val state = currentState(price, latest)

        return EmaContinuationCurrentResponse(
            symbol = normalizedSymbol,
            asOfDate = asOfDate.toString(),
            latestCompletedCandleDate = latest.date,
            price = price,
            open = open,
            high = high,
            low = low,
            volume = quote?.volumeTradedToday?.toLong() ?: latest.volume,
            changePct = quote?.ohlc?.close?.takeIf { close -> close > 0.0 }?.let { close ->
                ((price / close) - 1.0) * 100.0
            },
            ema10 = requireNotNull(latest.ema10),
            ema20 = requireNotNull(latest.ema20),
            sma200 = requireNotNull(latest.sma200),
            closeNearHighPct = latest.closeNearHighPct,
            closeAboveEma20Atr = latest.closeAboveEma20Atr,
            distanceToEma10Pct = percentageDistance(price, requireNotNull(latest.ema10)),
            distanceToEma20Pct = percentageDistance(price, requireNotNull(latest.ema20)),
            distanceToSma200Pct = percentageDistance(price, requireNotNull(latest.sma200)),
            state = state,
            isLiveQuote = livePrice != null,
            updatedAt = quote?.timestamp?.toString(),
        )
    }

    private suspend fun resolveMember(request: EmaContinuationStudyRequest): EmaContinuationMember {
        val requestedWatchlist = request.watchlistKey?.trim().orEmpty()
        val normalizedSymbol = request.symbol?.trim()?.uppercase(Locale.ROOT).orEmpty()
        require(requestedWatchlist.isNotBlank()) { "watchlistKey is required." }
        require(normalizedSymbol.isNotBlank()) { "symbol is required." }
        require(request.instrumentToken != null && request.instrumentToken > 0) {
            "instrumentToken must be positive."
        }

        val resolvedWatchlist = indexConstituentHandler.read { dao ->
            dao.listUniqueIndices()
                .firstOrNull { summary -> summary.indexKey.equals(requestedWatchlist, ignoreCase = true) }
                ?.indexKey
        } ?: throw IllegalArgumentException("Unknown watchlist: $requestedWatchlist")
        val member = indexConstituentHandler.read { dao ->
            dao.listActiveByIndex(resolvedWatchlist)
                .firstOrNull { candidate -> candidate.symbol.equals(normalizedSymbol, ignoreCase = true) }
        } ?: throw IllegalArgumentException("$normalizedSymbol is not a member of $resolvedWatchlist.")
        require(member.instrumentToken > 0) { "No valid instrument token is available for $normalizedSymbol." }

        return EmaContinuationMember(
            symbol = member.symbol.trim().uppercase(Locale.ROOT),
            instrumentToken = member.instrumentToken,
            watchlistKey = resolvedWatchlist,
        )
    }

    private suspend fun resolveWatchlist(requestedWatchlist: String): String =
        indexConstituentHandler.read { dao ->
            dao.listUniqueIndices()
                .firstOrNull { summary -> summary.indexKey.equals(requestedWatchlist, ignoreCase = true) }
                ?.indexKey
        } ?: throw IllegalArgumentException("Unknown watchlist: $requestedWatchlist")

    private suspend fun analyzeWatchlistMember(
        member: IndexConstituentUpsertRow,
        watchlistKey: String,
        testFromDate: LocalDate,
        asOfDate: LocalDate,
    ): EmaContinuationWatchlistRow {
        val symbol = member.symbol.trim().uppercase(Locale.ROOT)
        return try {
            val candles = loadCandles(
                EmaContinuationMember(symbol, member.instrumentToken, watchlistKey),
                asOfDate,
            )
            val study = EmaContinuationStudyEngine.run(
                candles = candles,
                testFromDate = testFromDate,
                testToDate = asOfDate,
                watchlistKey = watchlistKey,
            )
            EmaContinuationWatchlistRow(
                symbol = symbol,
                companyName = member.companyName,
                instrumentToken = member.instrumentToken,
                status = EmaContinuationWatchlistStudyStatus.ANALYZED,
                errorMessage = null,
                emaCrossCount = study.summary.emaCrossCount,
                baseBreakoutCount = study.summary.baseBreakoutCount,
                totalEntryCount = study.signals.size,
                latestSignalDate = study.summary.latestSignalDate,
                latestSignalType = study.summary.latestSignalType,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            EmaContinuationWatchlistRow(
                symbol = symbol,
                companyName = member.companyName,
                instrumentToken = member.instrumentToken,
                status = EmaContinuationWatchlistStudyStatus.ERROR,
                errorMessage = error.message ?: "Study failed.",
                emaCrossCount = 0,
                baseBreakoutCount = 0,
                totalEntryCount = 0,
                latestSignalDate = null,
                latestSignalType = null,
            )
        }
    }

    private suspend fun loadCandles(member: EmaContinuationMember, asOfDate: LocalDate): List<DailyCandle> =
        candleCacheService.getDailyCandles(
            token = member.instrumentToken,
            symbol = member.symbol,
            from = STUDY_START_DATE.minusDays(HISTORY_CALENDAR_DAYS),
            to = asOfDate,
        ).filter { candle -> !candle.candleDate.isAfter(asOfDate) }
            .sortedBy(DailyCandle::candleDate)

    private fun currentState(
        price: Double,
        latest: EmaContinuationDailyCandle,
    ): String = when {
        latest.sma200 != null && price < latest.sma200 -> "BELOW_SMA200"
        latest.ema10 != null && latest.ema20 != null && price > latest.ema10 && price > latest.ema20 -> "ABOVE_BOTH_EMAS"
        latest.ema20 != null && price > latest.ema20 -> "BETWEEN_EMAS"
        else -> "BELOW_EMAS"
    }

    private fun percentageDistance(value: Double, reference: Double): Double = ((value / reference) - 1.0) * 100.0

    private data class EmaContinuationMember(
        val symbol: String,
        val instrumentToken: Long,
        val watchlistKey: String,
    )

    private companion object {
        val STUDY_START_DATE: LocalDate = LocalDate.of(2026, 1, 1)
        const val HISTORY_CALENDAR_DAYS: Long = 420
        const val MAX_PARALLEL_CANDLE_READS: Int = 8
    }
}
