package com.tradingtool.core.strategy.fiftytwohigh

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.indexconstituents.dao.IndexConstituentUpsertRow
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.screener.CandleDataService
import com.tradingtool.core.strategy.deliverythreshold.normalizeIndexKeyInCore
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Singleton
class FiftyTwoWeekHighLiveService @Inject constructor(
    private val stockHandler: StockJdbiHandler,
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val candleCacheService: CandleCacheService,
    private val candleDataService: CandleDataService,
    private val kiteClient: KiteConnectClient,
) {
    suspend fun listUniverseOptions(): List<FiftyTwoWeekHighLiveUniverseOption> {
        val watchlistCount = stockHandler.read { dao ->
            dao.listAll().count { row -> row.exchange.equals("NSE", ignoreCase = true) }
        }
        val indexOptions = indexConstituentHandler.read { dao -> dao.listUniqueIndices() }
            .map { summary -> FiftyTwoWeekHighLiveUniverseOption(value = "INDEX:${summary.indexKey}", count = summary.count) }

        return listOf(FiftyTwoWeekHighLiveUniverseOption(value = WATCHLIST_UNIVERSE_KEY, count = watchlistCount)) + indexOptions
    }

    suspend fun runLive(config: FiftyTwoWeekHighLiveRunConfig): FiftyTwoWeekHighLiveResponse {
        val resolvedUniverse = resolveUniverse(config)

        val nearRows = mutableListOf<FiftyTwoWeekHighLiveRow>()
        val hitRows = mutableListOf<FiftyTwoWeekHighLiveRow>()
        val hitTodayRows = mutableListOf<FiftyTwoWeekHighLiveRow>()

        for (symbol in resolvedUniverse) {
            val toDate = LocalDate.now()
            val fromDate = toDate.minusDays(HISTORY_DAYS)
            val candles = loadCandles(symbol.symbol, symbol.instrumentToken, fromDate, toDate)
            val analysis = analyzeSymbol(symbol, candles) ?: continue

            if (analysis.isHitToday) {
                hitTodayRows += analysis.row
            }
            if (analysis.isHitInRecentWindow) {
                hitRows += analysis.row
            }
            if (analysis.isNearBreakout && !analysis.isHitInRecentWindow && !analysis.row.cooldownActive) {
                nearRows += analysis.row
            }
        }

        val rowComparator = compareByDescending<FiftyTwoWeekHighLiveRow> { it.lastHitDate ?: "" }
            .thenByDescending { it.latestDate }
            .thenBy { it.symbol }

        return FiftyTwoWeekHighLiveResponse(
            config = FiftyTwoWeekHighLiveConfigSnapshot(
                nearThresholdPct = NEAR_THRESHOLD_PCT,
                breakoutLookbackDays = LOOKBACK_DAYS,
                hitLookbackTradingDays = HIT_LOOKBACK_TRADING_DAYS,
                hitTodayTradingDays = HIT_TODAY_TRADING_DAYS,
                cooldownTradingDays = COOLDOWN_TRADING_DAYS,
            ),
            summary = FiftyTwoWeekHighLiveSummary(
                nearBreakout = nearRows.size,
                hitInLast2Weeks = hitRows.size,
                hitToday = hitTodayRows.size,
            ),
            nearBreakout = nearRows.sortedWith(rowComparator),
            hitInLast2Weeks = hitRows.sortedWith(rowComparator),
            hitToday = hitTodayRows.sortedWith(rowComparator),
        )
    }

    private suspend fun loadCandles(symbol: String, token: Long, fromDate: LocalDate, toDate: LocalDate): List<DailyCandle> {
        var candles = candleCacheService.getDailyCandles(token = token, symbol = symbol, from = fromDate, to = toDate)
            .sortedBy { it.candleDate }
        val latest = candles.lastOrNull()?.candleDate
        val daysGapFromLatest = latest?.let { ChronoUnit.DAYS.between(it, toDate) } ?: Long.MAX_VALUE
        val shouldBackfill = candles.isEmpty() || (latest != null && latest.isBefore(toDate) && daysGapFromLatest > MAX_ALLOWED_LATEST_GAP_DAYS)

        if (shouldBackfill) {
            runCatching {
                candleDataService.syncDailyRange(symbols = listOf(symbol), fromDate = fromDate, toDate = toDate, kiteClient = kiteClient)
            }
            candles = candleCacheService.getDailyCandles(token = token, symbol = symbol, from = fromDate, to = toDate)
                .sortedBy { it.candleDate }
        }

        return candles
    }

    private fun analyzeSymbol(symbol: FiftyTwoWeekHighResolvedSymbol, candles: List<DailyCandle>): SymbolAnalysis? {
        if (candles.size <= LOOKBACK_DAYS) return null

        var latestBreakoutIndex: Int? = null
        var nextEligibleIndex = LOOKBACK_DAYS

        for (i in LOOKBACK_DAYS until candles.size) {
            if (i < nextEligibleIndex) continue
            val signalCandle = candles[i]
            val priorHigh = candles.subList(i - LOOKBACK_DAYS, i).maxOf { it.high }
            if (signalCandle.high >= priorHigh) {
                latestBreakoutIndex = i
                nextEligibleIndex = i + COOLDOWN_TRADING_DAYS
            }
        }

        val latestIndex = candles.lastIndex
        val latestCandle = candles[latestIndex]
        val latestBreakoutLevel = candles.subList(latestIndex - LOOKBACK_DAYS, latestIndex).maxOf { it.high }
        val gapToBreakoutPct = if (latestBreakoutLevel <= 0.0) 0.0 else ((latestBreakoutLevel - latestCandle.high) / latestBreakoutLevel) * 100.0

        val recentHitStart = (candles.size - HIT_LOOKBACK_TRADING_DAYS).coerceAtLeast(LOOKBACK_DAYS)
        val todayHitStart = (candles.size - HIT_TODAY_TRADING_DAYS).coerceAtLeast(LOOKBACK_DAYS)

        val isHitInRecentWindow = latestBreakoutIndex != null && latestBreakoutIndex >= recentHitStart
        val isHitToday = latestBreakoutIndex != null && latestBreakoutIndex >= todayHitStart
        val cooldownActive = latestBreakoutIndex != null && latestIndex < latestBreakoutIndex + COOLDOWN_TRADING_DAYS
        val isNearBreakout = gapToBreakoutPct in 0.0..NEAR_THRESHOLD_PCT

        val row = FiftyTwoWeekHighLiveRow(
            symbol = symbol.symbol,
            indexBucket = classifyBucket(symbol.memberships),
            latestDate = latestCandle.candleDate.toString(),
            breakoutLevel = latestBreakoutLevel,
            latestHigh = latestCandle.high,
            latestClose = latestCandle.close,
            gapToBreakoutPct = gapToBreakoutPct,
            lastHitDate = latestBreakoutIndex?.let { index -> candles[index].candleDate.toString() },
            cooldownActive = cooldownActive,
        )

        return SymbolAnalysis(
            row = row,
            isNearBreakout = isNearBreakout,
            isHitInRecentWindow = isHitInRecentWindow,
            isHitToday = isHitToday,
        )
    }

    private suspend fun resolveUniverse(config: FiftyTwoWeekHighLiveRunConfig): List<FiftyTwoWeekHighResolvedSymbol> {
        val includeWatchlist = config.universeKeys.any { it.equals(WATCHLIST_UNIVERSE_KEY, ignoreCase = true) }

        val watchlistMembers = if (includeWatchlist) {
            stockHandler.read { dao -> dao.listAll() }
                .filter { stock -> stock.exchange.equals("NSE", ignoreCase = true) }
                .map { stock ->
                    FiftyTwoWeekHighResolvedSymbol(
                        symbol = stock.symbol.trim().uppercase(),
                        instrumentToken = stock.instrumentToken,
                        memberships = listOf(WATCHLIST_UNIVERSE_KEY),
                    )
                }
        } else {
            emptyList()
        }

        val requestedIndices = config.universeKeys
            .filter { key -> key.startsWith("INDEX:", ignoreCase = true) }
            .map { key -> key.substringAfter(':').trim() }
            .filter { key -> key.isNotEmpty() }
            .distinct()

        val availableIndexKeys = indexConstituentHandler.read { dao -> dao.listUniqueIndices() }.map { it.indexKey }

        val indexMembers = requestedIndices.flatMap { rawIndexKey ->
            val normalized = normalizeIndexKeyInCore(rawIndexKey)
            val matches = availableIndexKeys.filter { normalizeIndexKeyInCore(it) == normalized }
            val resolvedKeys = if (matches.isEmpty()) listOf(rawIndexKey) else matches
            resolvedKeys.flatMap { resolvedKey ->
                indexConstituentHandler.read { dao -> dao.listActiveByIndex(resolvedKey) }
            }
        }.map { row ->
            FiftyTwoWeekHighResolvedSymbol(
                symbol = row.symbol.trim().uppercase(),
                instrumentToken = row.instrumentToken,
                memberships = listOf(row.indexKey),
            )
        }

        val symbolFilter = config.symbols.map { it.uppercase() }.toSet()

        return (watchlistMembers + indexMembers)
            .groupBy { row -> row.symbol }
            .filterKeys { symbol -> symbolFilter.isEmpty() || symbolFilter.contains(symbol) }
            .mapNotNull { (symbol, rows) ->
                val token = rows.firstOrNull()?.instrumentToken ?: return@mapNotNull null
                FiftyTwoWeekHighResolvedSymbol(
                    symbol = symbol,
                    instrumentToken = token,
                    memberships = rows.flatMap { it.memberships }.distinct(),
                )
            }
            .sortedBy { it.symbol }
    }

    private fun classifyBucket(memberships: List<String>): String {
        val normalized = memberships.map { normalizeIndexKeyInCore(it) }
        return when {
            normalized.any { it.contains("LARGE") } -> "LARGE"
            normalized.any { it.contains("MID") } -> "MID"
            normalized.any { it.contains("SMALL") } -> "SMALL"
            normalized.any { it.contains("WATCHLIST") } -> "WATCHLIST"
            else -> "OTHER"
        }
    }

    private data class SymbolAnalysis(
        val row: FiftyTwoWeekHighLiveRow,
        val isNearBreakout: Boolean,
        val isHitInRecentWindow: Boolean,
        val isHitToday: Boolean,
    )

    companion object {
        const val WATCHLIST_UNIVERSE_KEY: String = "WATCHLIST"
        private const val LOOKBACK_DAYS: Int = 504
        private const val HISTORY_DAYS: Long = 1300
        private const val HIT_LOOKBACK_TRADING_DAYS: Int = 10
        private const val HIT_TODAY_TRADING_DAYS: Int = 2
        private const val COOLDOWN_TRADING_DAYS: Int = 180
        private const val NEAR_THRESHOLD_PCT: Double = 5.0
        private const val MAX_ALLOWED_LATEST_GAP_DAYS: Long = 3
    }
}
