package com.tradingtool.core.strategy.csvbacktest

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.screener.CandleDataService
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

class CsvBacktestService(
    private val candleHandler: CandleJdbiHandler,
    private val candleDataService: CandleDataService,
    private val kiteClient: KiteConnectClient
) {
    private val log = LoggerFactory.getLogger(CsvBacktestService::class.java)

    suspend fun runBacktest(
        csvContent: String,
        type: String,
        targetPct: Double?,
        stopLossPct: Double
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
            return@withContext CsvBacktestResponse(emptyList(), emptyList())
        }

        data class CsvSignal(
            val symbol: String,
            val date: LocalDate,
            val marketCapName: String,
            val sector: String
        )

        val signals = mutableListOf<CsvSignal>()
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
                    signals.add(CsvSignal(symbol, date, marketCap ?: "Unknown", sector ?: "Unknown"))
                }
            } catch (e: Exception) {
                log.warn("Failed to parse row: {}", row, e)
            }
        }

        if (signals.isEmpty()) {
            return@withContext CsvBacktestResponse(emptyList(), emptyList())
        }

        val uniqueSymbols = signals.map { it.symbol }.distinct()
        val minDate = signals.minOf { it.date }
        val today = LocalDate.now()

        // Sync candles for all symbols from the earliest signal date to today
        candleDataService.syncDailyRange(
            symbols = uniqueSymbols,
            fromDate = minDate,
            toDate = today,
            kiteClient = kiteClient
        )

        val trades = mutableListOf<CsvBacktestTradeResult>()

        for (signal in signals) {
            val candles = candleHandler.read { dao ->
                dao.getDailyCandlesBySymbol(signal.symbol, signal.date, today)
            }.distinctBy { it.candleDate }.sortedBy { it.candleDate }

            // Find entry candle (first trading day AFTER signal date)
            val entryCandle = candles.firstOrNull { it.candleDate.isAfter(signal.date) }
            
            if (entryCandle == null) {
                trades.add(
                    CsvBacktestTradeResult(
                        symbol = signal.symbol,
                        instrumentToken = null,
                        marketCapName = signal.marketCapName,
                        sector = signal.sector,
                        signalDate = signal.date.format(dateFormatter),
                        entryDate = null,
                        entryPrice = null,
                        exitDate = null,
                        exitPrice = null,
                        profitLossPct = null,
                        daysHeld = 0,
                        slHit = false,
                        isOpen = false
                    )
                )
                continue
            }

            val entryPrice = entryCandle.open
            var highestClose = entryCandle.close
            
            // Initial SL
            var currentSlPrice = entryPrice * (1.0 - stopLossPct / 100.0)
            val fixedTargetPrice = if (targetPct != null) entryPrice * (1.0 + targetPct / 100.0) else null

            var exitDate: LocalDate? = null
            var exitPrice: Double? = null
            var slHit = false

            // Iterate over remaining candles to find exit
            val postEntryCandles = candles.filter { it.candleDate.isAfter(entryCandle.candleDate) }
            
            for (daily in postEntryCandles) {
                // Update Trailing SL if applicable
                if (type == "TRAILING") {
                    if (daily.close > highestClose) {
                        highestClose = daily.close
                        val newSl = highestClose * (1.0 - stopLossPct / 100.0)
                        if (newSl > currentSlPrice) {
                            currentSlPrice = newSl
                        }
                    }
                }

                // Check Gap Down SL Hit
                if (daily.open <= currentSlPrice) {
                    exitDate = daily.candleDate
                    exitPrice = daily.open
                    slHit = true
                    break
                }

                // Check Gap Up Target Hit (Fixed Mode Only)
                if (type == "FIXED" && fixedTargetPrice != null && daily.open >= fixedTargetPrice) {
                    exitDate = daily.candleDate
                    exitPrice = daily.open
                    slHit = false
                    break
                }

                // Check Intraday SL Hit
                if (daily.low <= currentSlPrice) {
                    exitDate = daily.candleDate
                    exitPrice = currentSlPrice
                    slHit = true
                    break
                }

                // Check Intraday Target Hit (Fixed Mode Only)
                if (type == "FIXED" && fixedTargetPrice != null && daily.high >= fixedTargetPrice) {
                    exitDate = daily.candleDate
                    exitPrice = fixedTargetPrice
                    slHit = false
                    break
                }
            }

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
                    entryDate = entryCandle.candleDate.format(dateFormatter),
                    entryPrice = entryPrice,
                    exitDate = exitDate?.format(dateFormatter),
                    exitPrice = exitPrice,
                    profitLossPct = profitLossPct,
                    daysHeld = daysHeld,
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
            val isWin = (trade.profitLossPct ?: 0.0) > 0
            val isLoss = (trade.profitLossPct ?: 0.0) <= 0
            
            if (existing == null) {
                summaryMap[monthKey] = CsvBacktestSummary(
                    month = monthKey,
                    totalTrades = 1,
                    winTrades = if (isWin) 1 else 0,
                    lossTrades = if (isLoss) 1 else 0,
                    avgHoldingPeriod = trade.daysHeld.toDouble(),
                    avgProfitPct = trade.profitLossPct ?: 0.0
                )
            } else {
                val newTotal = existing.totalTrades + 1
                summaryMap[monthKey] = existing.copy(
                    totalTrades = newTotal,
                    winTrades = existing.winTrades + if (isWin) 1 else 0,
                    lossTrades = existing.lossTrades + if (isLoss) 1 else 0,
                    avgHoldingPeriod = ((existing.avgHoldingPeriod * existing.totalTrades) + trade.daysHeld) / newTotal,
                    avgProfitPct = ((existing.avgProfitPct * existing.totalTrades) + (trade.profitLossPct ?: 0.0)) / newTotal
                )
            }
        }

        val summaries = summaryMap.values.sortedByDescending { it.month }

        return@withContext CsvBacktestResponse(trades, summaries)
    }
}
