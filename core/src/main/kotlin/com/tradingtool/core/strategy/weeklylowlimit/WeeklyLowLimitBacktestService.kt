package com.tradingtool.core.strategy.weeklylowlimit

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.indexconstituents.dao.IndexConstituentUpsertRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

@Singleton
class WeeklyLowLimitBacktestService @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val candleCacheService: CandleCacheService,
    private val engine: WeeklyLowLimitBacktestEngine,
) {
    suspend fun run(config: WeeklyLowLimitBacktestRunConfig): WeeklyLowLimitBacktestReport {
        val mode = config.mode.trim().uppercase()
        require(mode == STOCK_MODE || mode == WATCHLIST_MODE) { "mode must be STOCK or WATCHLIST." }
        require(config.entryRule in WeeklyLowLimitBacktestEntryRules.all) { "Unsupported weekly low limit entry rule: ${config.entryRule}" }
        val members = if (mode == STOCK_MODE) resolveStock(config) else resolveWatchlist(config.watchlistKey)
        require(members.isNotEmpty()) { "No stocks were found for this selection." }

        val results = coroutineScope {
            val semaphore = Semaphore(MAX_PARALLEL_CANDLE_READS)
            members.map { member ->
                async(Dispatchers.IO) {
                    semaphore.withPermit { runMember(member, config.toDate, config.entryRule) }
                }
            }.awaitAll()
        }.sortedBy(WeeklyLowLimitBacktestSymbolReport::symbol)
        val allTrades = results.flatMap(WeeklyLowLimitBacktestSymbolReport::trades)
        val selection = if (mode == STOCK_MODE) results.single().symbol else config.watchlistKey.orEmpty().trim()
        return WeeklyLowLimitBacktestReport(
            mode = mode,
            entryRule = config.entryRule,
            selection = selection,
            testedFromDate = results.minOf(WeeklyLowLimitBacktestSymbolReport::testedFromDate),
            testedToDate = results.maxOf(WeeklyLowLimitBacktestSymbolReport::testedToDate),
            summary = summarizeWeeklyLowLimitTrades(allTrades),
            symbols = results,
        )
    }

    suspend fun loadDailyValidation(
        request: WeeklyLowLimitDailyValidationRequest,
    ): WeeklyLowLimitDailyValidationResponse {
        val symbol = request.symbol.trim().uppercase()
        require(symbol.isNotBlank()) { "symbol is required." }
        require(request.instrumentToken > 0) { "instrumentToken must be positive." }
        val previousWeekLowDate = parseDate(request.previousWeekLowDate, "previousWeekLowDate")
        val entryWeekStartDate = parseDate(request.entryWeekStartDate, "entryWeekStartDate")
        val entryDate = request.entryDate?.let { date -> parseDate(date, "entryDate") }
        val candles = candleCacheService.getDailyCandles(
            token = request.instrumentToken,
            symbol = symbol,
            from = previousWeekLowDate.minusDays(7),
            to = (entryDate ?: entryWeekStartDate).plusDays(20),
        ).sortedBy(DailyCandle::candleDate)
        require(candles.isNotEmpty()) { "No daily candle data is available for $symbol." }

        val startIndex = candles.indexOfFirst { candle -> candle.candleDate >= previousWeekLowDate }
        require(startIndex >= 0) { "No daily candle data is available from $previousWeekLowDate for $symbol." }
        val entryWeekEndIndex = candles.indexOfLast { candle ->
            candle.candleDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) == entryWeekStartDate
        }
        require(entryWeekEndIndex >= startIndex) { "No daily candle data is available for entry week $entryWeekStartDate." }
        val holdingAnchorIndex = entryDate?.let { date -> candles.indexOfFirst { candle -> candle.candleDate == date } }
            ?.takeIf { index -> index >= 0 }
            ?: entryWeekEndIndex
        val endIndex = maxOf(entryWeekEndIndex, minOf(holdingAnchorIndex + MAX_VALIDATION_FORWARD_DAYS, candles.lastIndex))
        val rows = (startIndex..endIndex).map { index ->
            val candle = candles[index]
            val previousClose = candles.getOrNull(index - 1)?.close
            WeeklyLowLimitDailyValidationRow(
                date = candle.candleDate.toString(),
                open = candle.open,
                high = candle.high,
                low = candle.low,
                close = candle.close,
                dailyChangePct = previousClose
                    ?.takeIf { close -> close > 0.0 }
                    ?.let { close -> roundTo2(((candle.close - close) / close) * 100.0) },
            )
        }
        return WeeklyLowLimitDailyValidationResponse(
            symbol = symbol,
            previousWeekLowDate = previousWeekLowDate.toString(),
            entryWeekStartDate = entryWeekStartDate.toString(),
            entryDate = entryDate?.toString(),
            rows = rows,
        )
    }

    private suspend fun runMember(
        member: WeeklyLowLimitMember,
        toDate: LocalDate,
        entryRule: String,
    ): WeeklyLowLimitBacktestSymbolReport {
        val fromDate = toDate.minusMonths(TEST_WINDOW_MONTHS).minusDays(14)
        val candles = candleCacheService.getDailyCandles(
            token = member.instrumentToken,
            symbol = member.symbol,
            from = fromDate,
            to = toDate,
        )
        return engine.run(
            symbol = member.symbol,
            companyName = member.companyName,
            candles = candles,
            testFrom = toDate.minusMonths(TEST_WINDOW_MONTHS),
            toDate = toDate,
            entryRule = entryRule,
        )
    }

    private suspend fun resolveStock(config: WeeklyLowLimitBacktestRunConfig): List<WeeklyLowLimitMember> {
        val symbol = config.symbol?.trim()?.uppercase().orEmpty()
        require(symbol.isNotBlank()) { "symbol is required for STOCK mode." }
        val instrumentToken = config.instrumentToken ?: 0L
        require(instrumentToken > 0) { "instrumentToken is required for STOCK mode." }
        return listOf(WeeklyLowLimitMember(symbol, null, instrumentToken))
    }

    private suspend fun resolveWatchlist(watchlistKey: String?): List<WeeklyLowLimitMember> {
        val requestedKey = watchlistKey?.trim().orEmpty()
        require(requestedKey.isNotBlank()) { "watchlistKey is required for WATCHLIST mode." }
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

    private fun toMember(member: IndexConstituentUpsertRow): WeeklyLowLimitMember = WeeklyLowLimitMember(
        symbol = member.symbol.trim().uppercase(),
        companyName = member.companyName,
        instrumentToken = member.instrumentToken,
    )

    private fun parseDate(value: String, fieldName: String): LocalDate = runCatching { LocalDate.parse(value) }
        .getOrElse { throw IllegalArgumentException("$fieldName must use yyyy-MM-dd format.") }

    private fun roundTo2(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0

    private companion object {
        const val STOCK_MODE = "STOCK"
        const val WATCHLIST_MODE = "WATCHLIST"
        const val TEST_WINDOW_MONTHS = 6L
        const val MAX_PARALLEL_CANDLE_READS = 12
        const val MAX_VALIDATION_FORWARD_DAYS = 5
    }
}
