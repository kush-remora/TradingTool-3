package com.tradingtool.core.strategy.fiftytwohigh

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.indexconstituents.dao.IndexConstituentUpsertRow
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.screener.CandleDataService
import com.tradingtool.core.strategy.deliverythreshold.normalizeIndexKeyInCore
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Singleton
class FiftyTwoWeekHighBacktestService @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val candleCacheService: CandleCacheService,
    private val candleDataService: CandleDataService,
    private val kiteClient: KiteConnectClient,
) {
    suspend fun runBacktest(config: FiftyTwoWeekHighBacktestRunConfig): FiftyTwoWeekHighBacktestResponse {
        val toDate = config.toDate
        val fromDate = toDate.minusDays(config.backtestDays)
        val historyFrom = toDate.minusDays(config.historyDays)

        val universe = resolveUniverse(config)
        val rows = mutableListOf<FiftyTwoWeekHighBacktestRow>()

        for (symbol in universe) {
            val candles = loadCandles(symbol.symbol, symbol.instrumentToken, historyFrom, toDate)
            if (candles.size <= LOOKBACK_DAYS) continue
            rows += runSymbolBacktest(symbol, candles, fromDate, toDate, config)
        }

        val sortedRows = rows.sortedWith(compareBy<FiftyTwoWeekHighBacktestRow> { it.symbol }.thenBy { it.enterTrade })
        val closed = sortedRows.count { it.status == "CLOSED" }
        val open = sortedRows.count { it.status == "OPEN" }

        return FiftyTwoWeekHighBacktestResponse(
            config = FiftyTwoWeekHighBacktestConfigSnapshot(
                indexKeys = config.indexKeys,
                symbols = config.symbols,
                profitPct = config.profitPct,
                historyDays = config.historyDays,
                backtestDays = config.backtestDays,
                cooldownDays = config.cooldownDays,
                fromDate = fromDate.toString(),
                toDate = toDate.toString(),
            ),
            summary = FiftyTwoWeekHighBacktestSummary(
                totalTrades = sortedRows.size,
                closedTrades = closed,
                openTrades = open,
            ),
            rows = sortedRows,
        )
    }

    suspend fun listUniverseOptions(): List<Pair<String, Int>> {
        return indexConstituentHandler.read { dao -> dao.listUniqueIndices() }
            .map { summary -> summary.indexKey to summary.count }
    }

    private suspend fun loadCandles(symbol: String, token: Long, fromDate: LocalDate, toDate: LocalDate): List<DailyCandle> {
        var candles = candleCacheService.getDailyCandles(token = token, symbol = symbol, from = fromDate, to = toDate)
            .sortedBy { it.candleDate }
        val latest = candles.lastOrNull()?.candleDate
        val daysGapFromLatest = latest?.let { ChronoUnit.DAYS.between(it, toDate) } ?: Long.MAX_VALUE
        val shouldBackfill = candles.isEmpty() || (latest != null && latest.isBefore(toDate) && daysGapFromLatest > MAX_ALLOWED_LATEST_GAP_DAYS)

        if (shouldBackfill) {
            runCatching {
                candleDataService.syncDailyRange(
                    symbols = listOf(symbol),
                    fromDate = fromDate,
                    toDate = toDate,
                    kiteClient = kiteClient,
                )
            }
            candles = candleCacheService.getDailyCandles(token = token, symbol = symbol, from = fromDate, to = toDate)
                .sortedBy { it.candleDate }
        }

        return candles
    }

    private fun runSymbolBacktest(
        symbol: FiftyTwoWeekHighResolvedSymbol,
        candles: List<DailyCandle>,
        fromDate: LocalDate,
        toDate: LocalDate,
        config: FiftyTwoWeekHighBacktestRunConfig,
    ): List<FiftyTwoWeekHighBacktestRow> {
        val rows = mutableListOf<FiftyTwoWeekHighBacktestRow>()
        val bucket = classifyBucket(symbol.memberships)
        var openPositionUntil: LocalDate? = null
        var lastEntryDate: LocalDate? = null
        val targetFactor = 1.0 + (config.profitPct / 100.0)

        for (i in LOOKBACK_DAYS until candles.lastIndex) {
            val signalCandle = candles[i]
            if (signalCandle.candleDate < fromDate || signalCandle.candleDate > toDate) continue

            val blockedByOpenPosition = openPositionUntil?.let { !it.isBefore(signalCandle.candleDate) } ?: false
            if (blockedByOpenPosition) continue
            val blockedByCooldown = lastEntryDate?.let { signalCandle.candleDate.isBefore(it.plusDays(config.cooldownDays)) } ?: false
            if (blockedByCooldown) continue

            val priorHigh = candles.subList(i - LOOKBACK_DAYS, i).maxOf { it.high }
            val isBreakout = signalCandle.high > priorHigh
            if (!isBreakout) continue

            val entryCandle = candles[i + 1]
            val entryDate = entryCandle.candleDate
            val entryPrice = entryCandle.open
            val targetPrice = entryPrice * targetFactor

            var exitDate: LocalDate? = null
            for (j in (i + 1)..candles.lastIndex) {
                val day = candles[j]
                if (day.high >= targetPrice) {
                    exitDate = day.candleDate
                    break
                }
            }

            val holdingDays = if (exitDate != null) {
                java.time.temporal.ChronoUnit.DAYS.between(entryDate, exitDate).toInt()
            } else {
                java.time.temporal.ChronoUnit.DAYS.between(entryDate, toDate).toInt()
            }

            rows += FiftyTwoWeekHighBacktestRow(
                symbol = symbol.symbol,
                indexBucket = bucket,
                enterTrade = entryDate.toString(),
                exitTrade = exitDate?.toString(),
                holdingDays = holdingDays,
                status = if (exitDate == null) "OPEN" else "CLOSED",
            )

            lastEntryDate = entryDate
            if (exitDate == null) {
                openPositionUntil = toDate
            } else {
                openPositionUntil = exitDate
            }
        }

        return rows
    }

    private suspend fun resolveUniverse(config: FiftyTwoWeekHighBacktestRunConfig): List<FiftyTwoWeekHighResolvedSymbol> {
        val availableIndexKeys = indexConstituentHandler.read { dao -> dao.listUniqueIndices() }.map { it.indexKey }
        val indexMembers = config.indexKeys
            .flatMap { indexKey ->
                val normalized = normalizeIndexKeyInCore(indexKey)
                val matches = availableIndexKeys.filter { normalizeIndexKeyInCore(it) == normalized }
                if (matches.isEmpty()) {
                    indexConstituentHandler.read { dao -> dao.listActiveByIndex(indexKey) }
                } else {
                    matches.flatMap { resolved -> indexConstituentHandler.read { dao -> dao.listActiveByIndex(resolved) } }
                }
            }

        val symbolFilter = config.symbols.map { it.uppercase() }.toSet()
        return indexMembers
            .groupBy { it.symbol.trim().uppercase() }
            .filterKeys { symbol -> symbolFilter.isEmpty() || symbolFilter.contains(symbol) }
            .mapNotNull { (symbol, members) ->
                val token = members.firstOrNull()?.instrumentToken ?: return@mapNotNull null
                FiftyTwoWeekHighResolvedSymbol(
                    symbol = symbol,
                    instrumentToken = token,
                    memberships = members.map(IndexConstituentUpsertRow::indexKey).distinct(),
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
            else -> "OTHER"
        }
    }

    companion object {
        private const val LOOKBACK_DAYS: Int = 504
        private const val MAX_ALLOWED_LATEST_GAP_DAYS: Long = 3
    }
}
