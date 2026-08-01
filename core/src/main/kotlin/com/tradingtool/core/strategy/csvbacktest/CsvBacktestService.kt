package com.tradingtool.core.strategy.csvbacktest

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.database.StockDeliveryJdbiHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.slf4j.LoggerFactory
import java.io.StringReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

internal data class CsvBacktestSignal(
    val symbol: String,
    val date: LocalDate,
    val marketCapName: String,
    val sector: String,
)

internal fun deduplicateCsvBacktestSignals(signals: List<CsvBacktestSignal>): List<CsvBacktestSignal> =
    signals.distinctBy { signal -> signal.symbol to signal.date }

class CsvBacktestService(
    private val candleCacheService: CandleCacheService,
    private val stockDeliveryHandler: StockDeliveryJdbiHandler,
) {
    private val log = LoggerFactory.getLogger(CsvBacktestService::class.java)

    suspend fun runBacktest(
        csvContent: String,
        type: String,
        targetPct: Double,
        stopLossPct: Double,
        initialStopLossSessions: Int,
        trailingStopLossPct: Double,
        entryStrategy: String,
        retestWindowDays: Int,
        retestTolerancePct: Double,
        applyV2Validation: Boolean,
        breakoutLookbackSessions: Int,
        maxCloseToCloseGainPct: Double,
    ): CsvBacktestResponse = withContext(Dispatchers.IO) {
        
        // Parse CSV
        val parser = CSVParser.parse(
            StringReader(csvContent),
            CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build()
        )

        val headerMap = parser.headerMap.mapKeys { it.key.lowercase().replace(" ", "") }
        val dateHeader = headerMap.keys.firstOrNull { it.contains("date") }
        val symbolHeader = headerMap.keys.firstOrNull { it.contains("symbol") }
        val marketCapHeader = headerMap.keys.firstOrNull { it.contains("marketcap") }
        val sectorHeader = headerMap.keys.firstOrNull { it.contains("sector") }

        if (symbolHeader == null || dateHeader == null) {
            log.error("CSV must contain 'symbol' and 'date' columns.")
            return@withContext CsvBacktestResponse(emptyList(), emptyList(), inputSignalCount = 0, validatedSignalCount = 0)
        }

        val signals = mutableListOf<CsvBacktestSignal>()
        val dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

        for (row in parser) {
            try {
                val symbol = row.get(headerMap[symbolHeader]!!)?.trim()?.uppercase()
                val dateStr = row.get(headerMap[dateHeader]!!)?.trim()
                val marketCap = if (marketCapHeader != null) row.get(headerMap[marketCapHeader]!!)?.trim() else "Unknown"
                val sector = if (sectorHeader != null) row.get(headerMap[sectorHeader]!!)?.trim() else "Unknown"

                if (!symbol.isNullOrBlank() && !dateStr.isNullOrBlank()) {
                    val date = try {
                        LocalDate.parse(dateStr, dateFormatter)
                    } catch (e: DateTimeParseException) {
                        LocalDate.now()
                    }
                    signals.add(CsvBacktestSignal(symbol, date, marketCap ?: "Unknown", sector ?: "Unknown"))
                }
            } catch (e: Exception) {
                log.warn("Failed to parse row: {}", row, e)
            }
        }

        val uniqueSignals = deduplicateCsvBacktestSignals(signals)
        val duplicateSignalCount = signals.size - uniqueSignals.size
        if (duplicateSignalCount > 0) {
            log.info("Ignored {} duplicate CSV backtest signals.", duplicateSignalCount)
        }

        if (uniqueSignals.isEmpty()) {
            return@withContext CsvBacktestResponse(emptyList(), emptyList(), inputSignalCount = 0, validatedSignalCount = 0)
        }

        val uniqueSymbols = uniqueSignals.map { it.symbol }.distinct()
        val historyCalendarDays = maxOf(
            MIN_BREAKOUT_HISTORY_CALENDAR_DAYS,
            breakoutLookbackSessions.toLong() * BREAKOUT_HISTORY_CALENDAR_DAYS_PER_SESSION,
        )
        val minDate = uniqueSignals.minOf { it.date }.minusDays(historyCalendarDays)
        val today = LocalDate.now()
        val resolvedEntryStrategy = CsvBacktestEntryStrategy.from(entryStrategy)

        val candlesBySymbol = mutableMapOf<String, List<DailyCandle>>()
        for (symbol in uniqueSymbols) {
            candlesBySymbol[symbol] = loadCandles(symbol, minDate, today)
        }
        val instrumentTokens = candlesBySymbol.values
            .flatMap { candles -> candles.map(DailyCandle::instrumentToken) }
            .distinct()
        val deliveryPctByToken = if (instrumentTokens.isEmpty()) {
            emptyMap()
        } else {
            stockDeliveryHandler.read { dao ->
                dao.findByInstrumentTokensBetweenDates(instrumentTokens, minDate, today)
            }.groupBy { delivery -> delivery.instrumentToken }
                .mapValues { (_, deliveries) ->
                    deliveries.associate { delivery -> delivery.tradingDate to delivery.delivPer }
                }
        }

        val trades = mutableListOf<CsvBacktestTradeResult>()
        var validatedSignalCount = 0

        for (signal in uniqueSignals) {
            val signalStartDate = signal.date.minusDays(historyCalendarDays)
            val candles = candlesBySymbol[signal.symbol].orEmpty()
                .filter { candle -> !candle.candleDate.isBefore(signalStartDate) }
                .distinctBy(DailyCandle::candleDate)
                .sortedBy(DailyCandle::candleDate)

            val freshBreakoutLevel = CsvBacktestV2Validator.freshBreakoutLevel(
                candles = candles,
                signalDate = signal.date,
                breakoutLookbackSessions = breakoutLookbackSessions,
            ) ?: continue
            val breakoutSpan = CsvBacktestBreakoutSpanCalculator.calculate(
                candles = candles,
                signalDate = signal.date,
            )
            val validation = if (applyV2Validation) {
                CsvBacktestV2Validator.validate(
                    candles = candles,
                    signalDate = signal.date,
                    maxCloseToCloseGainPct = maxCloseToCloseGainPct,
                    breakoutLookbackSessions = breakoutLookbackSessions,
                )
            } else {
                null
            }
            if (applyV2Validation && validation == null) continue
            validatedSignalCount++

            val breakoutDayMovePct = candles
                .firstOrNull { candle -> candle.candleDate == signal.date }
                ?.takeIf { candle -> candle.open > 0.0 }
                ?.let { candle -> ((candle.close - candle.open) / candle.open) * 100.0 }
            val deliveryMetrics = calculateCsvBacktestDeliveryMetrics(
                signalDate = signal.date,
                priorTradingDates = candles
                    .filter { candle -> candle.candleDate.isBefore(signal.date) }
                    .map(DailyCandle::candleDate),
                deliveryPctByDate = candles.firstOrNull()
                    ?.instrumentToken
                    ?.let { token -> deliveryPctByToken[token] }
                    .orEmpty(),
            )
            val priorFiveDaysDelivery = deliveryMetrics.priorFiveDaysDelivery.map { delivery ->
                CsvBacktestPriorDeliveryDay(
                    date = delivery.date.format(dateFormatter),
                    deliveryPct = delivery.deliveryPct,
                )
            }

            val entry = CsvBacktestEntryEvaluator.findEntry(
                candles = candles,
                signalDate = signal.date,
                strategy = resolvedEntryStrategy,
                retestWindowDays = retestWindowDays,
                retestTolerancePct = retestTolerancePct,
                maxCloseToCloseGainPct = maxCloseToCloseGainPct,
            )
            
            if (entry == null) {
                trades.add(
                    CsvBacktestTradeResult(
                        symbol = signal.symbol,
                        instrumentToken = null,
                        marketCapName = signal.marketCapName,
                        sector = signal.sector,
                        signalDate = signal.date.format(dateFormatter),
                        entryStrategy = resolvedEntryStrategy.name,
                        breakoutLevel = freshBreakoutLevel,
                        breakoutSpanSessions = breakoutSpan?.sessions,
                        breakoutSpanIsLowerBound = breakoutSpan?.isLowerBound ?: false,
                        breakoutDayMovePct = breakoutDayMovePct,
                        breakoutDayDeliveryPct = deliveryMetrics.breakoutDayDeliveryPct,
                        priorFiveDaysMaxDeliveryPct = deliveryMetrics.priorFiveDaysMaxDeliveryPct,
                        priorFiveDaysDelivery = priorFiveDaysDelivery,
                        entryDate = null,
                        entryPrice = null,
                        firstFiveDaysLowestPrice = null,
                        firstFiveDaysDropAmount = null,
                        firstFiveDaysDropPct = null,
                        firstThreeDaysRedCandleCount = null,
                        v2MaxPreBreakoutVolumeRatio = validation?.maxPreBreakoutVolumeRatio,
                        v2FailedResistanceAttempts = validation?.failedResistanceAttempts,
                        v2RecentRunBasePrice = validation?.recentRunBasePrice,
                        v2MoveFromRecentBasePct = validation?.moveFromRecentBasePct,
                        exitDate = null,
                        exitPrice = null,
                        profitLossPct = null,
                        daysHeld = 0,
                        targetHit = false,
                        slHit = false,
                        isOpen = false
                    )
                )
                continue
            }

            val entryCandle = entry.candle
            val entryPrice = entry.price
            val initialStopLossPrice = entryPrice * (1.0 - stopLossPct / 100.0)
            val targetPrice = entryPrice * (1.0 + targetPct / 100.0)
            val tradeCandles = candles.filter { it.candleDate >= entryCandle.candleDate }
            val exit = if (type == "TRAILING") {
                CsvBacktestExitEvaluator.findTrailingExit(
                    candles = tradeCandles,
                    initialStopLossPrice = initialStopLossPrice,
                    targetPrice = targetPrice,
                    initialStopLossSessions = initialStopLossSessions,
                    trailingStopLossPct = trailingStopLossPct,
                )
            } else {
                CsvBacktestExitEvaluator.findFixedExit(
                    candles = tradeCandles,
                    stopLossPrice = initialStopLossPrice,
                    targetPrice = targetPrice,
                )
            }

            val exitDate = exit?.candle?.candleDate
            val exitPrice = exit?.price
            val targetHit = exit?.targetHit ?: false
            val slHit = exit?.slHit ?: false
            val postEntryCandles = candles.filter { it.candleDate.isAfter(entryCandle.candleDate) }
            val earlyDip = CsvBacktestEarlyDipCalculator.calculate(entryPrice, tradeCandles)
            val firstThreeDaysRedCandleCount = CsvBacktestCandleColorCalculator.countRedCandles(
                candles = candles,
                entryDate = entryCandle.candleDate,
            )

            val profitLossPct = if (exitPrice != null) {
                ((exitPrice - entryPrice) / entryPrice) * 100.0
            } else {
                // If open, calculate unrealized PnL based on last available close
                val lastClose = postEntryCandles.lastOrNull()?.close ?: entryCandle.close
                ((lastClose - entryPrice) / entryPrice) * 100.0
            }
            
            val daysHeld = if (exitDate != null) {
                ChronoUnit.DAYS.between(entryCandle.candleDate, exitDate).toInt()
            } else {
                val lastDate = postEntryCandles.lastOrNull()?.candleDate ?: entryCandle.candleDate
                ChronoUnit.DAYS.between(entryCandle.candleDate, lastDate).toInt()
            }

            trades.add(
                CsvBacktestTradeResult(
                    symbol = signal.symbol,
                    instrumentToken = entryCandle.instrumentToken,
                    marketCapName = signal.marketCapName,
                    sector = signal.sector,
                    signalDate = signal.date.format(dateFormatter),
                    entryStrategy = resolvedEntryStrategy.name,
                    breakoutLevel = entry.breakoutLevel ?: freshBreakoutLevel,
                    breakoutSpanSessions = breakoutSpan?.sessions,
                    breakoutSpanIsLowerBound = breakoutSpan?.isLowerBound ?: false,
                    breakoutDayMovePct = breakoutDayMovePct,
                    breakoutDayDeliveryPct = deliveryMetrics.breakoutDayDeliveryPct,
                    priorFiveDaysMaxDeliveryPct = deliveryMetrics.priorFiveDaysMaxDeliveryPct,
                    priorFiveDaysDelivery = priorFiveDaysDelivery,
                    entryDate = entryCandle.candleDate.format(dateFormatter),
                    entryPrice = entryPrice,
                    firstFiveDaysLowestPrice = earlyDip?.lowestPrice,
                    firstFiveDaysDropAmount = earlyDip?.dropAmount,
                    firstFiveDaysDropPct = earlyDip?.dropPct,
                    firstThreeDaysRedCandleCount = firstThreeDaysRedCandleCount,
                    v2MaxPreBreakoutVolumeRatio = validation?.maxPreBreakoutVolumeRatio,
                    v2FailedResistanceAttempts = validation?.failedResistanceAttempts,
                    v2RecentRunBasePrice = validation?.recentRunBasePrice,
                    v2MoveFromRecentBasePct = validation?.moveFromRecentBasePct,
                    exitDate = exitDate?.format(dateFormatter),
                    exitPrice = exitPrice,
                    profitLossPct = profitLossPct,
                    daysHeld = daysHeld,
                    targetHit = targetHit,
                    slHit = slHit,
                    isOpen = exitDate == null
                )
            )
        }

        // Generate monthly summary
        val summaryMap = mutableMapOf<String, CsvBacktestSummary>()
        
        trades.filter { it.entryDate != null }.forEach { trade ->
            val entryDateParsed = LocalDate.parse(trade.entryDate!!, dateFormatter)
            val monthKey = "${entryDateParsed.year}-${entryDateParsed.monthValue.toString().padStart(2, '0')}"
            
            val existing = summaryMap[monthKey]
            val isTargetHit = trade.targetHit
            val isStopLossHit = trade.slHit
            val isUnresolved = !isTargetHit && !isStopLossHit
            
            if (existing == null) {
                summaryMap[monthKey] = CsvBacktestSummary(
                    month = monthKey,
                    totalTrades = 1,
                    targetHitTrades = if (isTargetHit) 1 else 0,
                    stopLossHitTrades = if (isStopLossHit) 1 else 0,
                    unresolvedTrades = if (isUnresolved) 1 else 0,
                    avgHoldingPeriod = trade.daysHeld.toDouble(),
                    avgProfitPct = trade.profitLossPct ?: 0.0,
                    avgFirstFiveDaysDropPct = trade.firstFiveDaysDropPct ?: 0.0,
                )
            } else {
                val newTotal = existing.totalTrades + 1
                summaryMap[monthKey] = existing.copy(
                    totalTrades = newTotal,
                    targetHitTrades = existing.targetHitTrades + if (isTargetHit) 1 else 0,
                    stopLossHitTrades = existing.stopLossHitTrades + if (isStopLossHit) 1 else 0,
                    unresolvedTrades = existing.unresolvedTrades + if (isUnresolved) 1 else 0,
                    avgHoldingPeriod = ((existing.avgHoldingPeriod * existing.totalTrades) + trade.daysHeld) / newTotal,
                    avgProfitPct = ((existing.avgProfitPct * existing.totalTrades) + (trade.profitLossPct ?: 0.0)) / newTotal,
                    avgFirstFiveDaysDropPct = (
                        (existing.avgFirstFiveDaysDropPct * existing.totalTrades) +
                            (trade.firstFiveDaysDropPct ?: 0.0)
                        ) / newTotal,
                )
            }
        }

        val summaries = summaryMap.values.sortedByDescending { it.month }

        return@withContext CsvBacktestResponse(
            trades = trades,
            summaries = summaries,
            inputSignalCount = uniqueSignals.size,
            validatedSignalCount = validatedSignalCount,
        )
    }

    private suspend fun loadCandles(symbol: String, fromDate: LocalDate, toDate: LocalDate): List<DailyCandle> {
        return candleCacheService.getDailyCandles(symbol, fromDate, toDate)
            .sortedBy(DailyCandle::candleDate)
    }

    private companion object {
        const val MIN_BREAKOUT_HISTORY_CALENDAR_DAYS = 800L
        const val BREAKOUT_HISTORY_CALENDAR_DAYS_PER_SESSION = 3L
    }
}
