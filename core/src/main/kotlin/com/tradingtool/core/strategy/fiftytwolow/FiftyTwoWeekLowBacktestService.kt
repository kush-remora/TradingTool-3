package com.tradingtool.core.strategy.fiftytwolow

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.indexconstituents.dao.IndexConstituentUpsertRow
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.screener.CandleDataService
import com.tradingtool.core.strategy.wyckoff.deliverythreshold.normalizeIndexKeyInCore
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Singleton
class FiftyTwoWeekLowBacktestService @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val candleCacheService: CandleCacheService,
    private val candleDataService: CandleDataService,
    private val kiteClient: KiteConnectClient,
) {
    suspend fun runBacktest(config: FiftyTwoWeekLowBacktestRunConfig): FiftyTwoWeekLowBacktestResponse {
        val toDate = config.toDate
        val fromDate = toDate.minusDays(config.backtestDays)
        val historyFrom = toDate.minusDays(config.historyDays)

        val universe = resolveUniverse(config)
        val rows = mutableListOf<FiftyTwoWeekLowBacktestRow>()

        for (symbol in universe) {
            val candles = loadCandles(symbol.symbol, symbol.instrumentToken, historyFrom, toDate)
            if (candles.size <= LOOKBACK_TRADING_DAYS) continue
            rows += runSymbolBacktest(symbol, candles, fromDate, toDate, config)
        }

        val sortedRows = rows.sortedWith(compareBy<FiftyTwoWeekLowBacktestRow> { it.symbol }.thenBy { it.enterTrade })
        val closedRows = sortedRows.filter { it.status == "CLOSED" }
        val closedCount = closedRows.size
        val openCount = sortedRows.size - closedCount
        val avgDaysHeld = if (closedCount > 0) closedRows.map { it.holdingDays }.average() else null

        return FiftyTwoWeekLowBacktestResponse(
            config = FiftyTwoWeekLowBacktestConfigSnapshot(
                indexKeys = config.indexKeys,
                symbols = config.symbols,
                profitPct = config.profitPct,
                historyDays = config.historyDays,
                backtestDays = config.backtestDays,
                fromDate = fromDate.toString(),
                toDate = toDate.toString(),
            ),
            summary = FiftyTwoWeekLowBacktestSummary(
                totalTrades = sortedRows.size,
                closedTrades = closedCount,
                openTrades = openCount,
                avgDaysHeldClosed = avgDaysHeld,
            ),
            rows = sortedRows,
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
        symbol: FiftyTwoWeekLowResolvedSymbol,
        candles: List<DailyCandle>,
        fromDate: LocalDate,
        toDate: LocalDate,
        config: FiftyTwoWeekLowBacktestRunConfig,
    ): List<FiftyTwoWeekLowBacktestRow> {
        val rows = mutableListOf<FiftyTwoWeekLowBacktestRow>()
        var openPositionUntil: LocalDate? = null
        val targetFactor = 1.0 + (config.profitPct / 100.0)

        for (i in LOOKBACK_TRADING_DAYS until candles.lastIndex) {
            val signalCandle = candles[i]
            if (signalCandle.candleDate < fromDate || signalCandle.candleDate > toDate) continue

            val blockedByOpenPosition = openPositionUntil?.let { !it.isBefore(signalCandle.candleDate) } ?: false
            if (blockedByOpenPosition) continue

            val priorLow = candles.subList(i - LOOKBACK_TRADING_DAYS, i).minOf { it.low }
            val isNewLow = signalCandle.low < priorLow
            if (!isNewLow) continue

            val entryCandle = signalCandle
            val entryDate = entryCandle.candleDate
            val entryPrice = entryCandle.close
            val targetPrice = entryPrice * targetFactor

            var exitDate: LocalDate? = null
            var exitPrice: Double? = null
            for (j in (i + 1)..candles.lastIndex) {
                val day = candles[j]
                if (day.high >= targetPrice) {
                    exitDate = day.candleDate
                    exitPrice = targetPrice // or day.high, but targetPrice represents filling the limit order
                    break
                }
            }

            val holdingDays = if (exitDate != null) {
                ChronoUnit.DAYS.between(entryDate, exitDate).toInt()
            } else {
                ChronoUnit.DAYS.between(entryDate, toDate).toInt()
            }

            var ltp: Double? = null
            var currentProfitPct: Double? = null
            if (exitDate == null && candles.isNotEmpty()) {
                ltp = candles.last().close
                currentProfitPct = ((ltp - entryPrice) / entryPrice) * 100.0
            }

            rows += FiftyTwoWeekLowBacktestRow(
                symbol = symbol.symbol,
                indexBucket = symbol.memberships.joinToString(", "),
                enterTrade = entryDate.toString(),
                exitTrade = exitDate?.toString(),
                buyPrice = entryPrice,
                sellPrice = exitPrice,
                holdingDays = holdingDays,
                profitPct = config.profitPct,
                status = if (exitDate == null) "OPEN" else "CLOSED",
                ltp = ltp,
                currentProfitPct = currentProfitPct,
            )

            if (exitDate == null) {
                openPositionUntil = toDate
            } else {
                openPositionUntil = exitDate
            }
        }

        return rows
    }

    private suspend fun resolveUniverse(config: FiftyTwoWeekLowBacktestRunConfig): List<FiftyTwoWeekLowResolvedSymbol> {
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
                FiftyTwoWeekLowResolvedSymbol(
                    symbol = symbol,
                    instrumentToken = token,
                    memberships = members.map(IndexConstituentUpsertRow::indexKey).distinct(),
                )
            }
            .sortedBy { it.symbol }
    }

    companion object {
        private const val LOOKBACK_TRADING_DAYS: Int = 252
        private const val MAX_ALLOWED_LATEST_GAP_DAYS: Long = 3
    }
}
