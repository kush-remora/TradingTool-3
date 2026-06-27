package com.tradingtool.core.strategy.fiftytwomomentum

import com.google.inject.Inject
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.screener.CandleDataService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.StringReader

class FiftyTwoWeekMomentumRule5Service @Inject constructor(
    private val candleHandler: CandleJdbiHandler,
    private val candleDataService: CandleDataService,
    private val kiteClient: KiteConnectClient
) {
    private val log = LoggerFactory.getLogger(FiftyTwoWeekMomentumRule5Service::class.java)
    private val dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    suspend fun runRule5Analysis(csvContent: String): Rule5ApiResponse = withContext(Dispatchers.IO) {
        if (csvContent.isBlank()) return@withContext Rule5ApiResponse(emptyList())

        val parser = CSVParser(
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
        val symbolHeader = headerMap.keys.firstOrNull { it == "symbol" }
        val marketCapHeader = headerMap.keys.firstOrNull { it.contains("marketcap") }
        val sectorHeader = headerMap.keys.firstOrNull { it.contains("sector") }

        if (symbolHeader == null) {
            log.error("No 'symbol' column found in CSV. Headers: {}", headerMap.keys)
            return@withContext Rule5ApiResponse(emptyList())
        }

        val results = mutableListOf<Rule5SymbolResult>()
        for (row in parser) {
            try {
                val symbolIndex = headerMap[symbolHeader]!!
                val symbol = row.get(symbolIndex)?.trim()?.uppercase()
                
                if (symbol.isNullOrBlank()) {
                    continue
                }

                val dateStr = if (dateHeader != null) row.get(headerMap[dateHeader]!!)?.trim() else null
                val endDate = if (!dateStr.isNullOrBlank()) {
                    LocalDate.parse(dateStr, dateFormatter)
                } else {
                    LocalDate.now()
                }

                val marketCap = if (marketCapHeader != null) row.get(headerMap[marketCapHeader]!!)?.trim() else "Unknown"
                val sector = if (sectorHeader != null) row.get(headerMap[sectorHeader]!!)?.trim() else "Unknown"
                
                val res = processSymbol(symbol, endDate, marketCap ?: "Unknown", sector ?: "Unknown")
                if (res != null) {
                    results.add(res)
                }
            } catch (e: Exception) {
                log.error("Failed to parse or process row: {}", row.toMap(), e)
            }
        }
        
        Rule5ApiResponse(results = results)
    }

    private suspend fun processSymbol(
        symbol: String, 
        endDate: LocalDate, 
        marketCapName: String, 
        sector: String
    ): Rule5SymbolResult? {
        val fromDate = endDate.minusDays(400) // Ensure enough history for 200 SMA + 30 days
        
        // Ensure data is synced
        candleDataService.syncDailyRange(
            symbols = listOf(symbol),
            fromDate = fromDate,
            toDate = endDate,
            kiteClient = kiteClient
        )
        
        val candles = candleHandler.read { dao ->
            dao.getDailyCandlesBySymbol(symbol, fromDate, endDate)
        }.distinctBy { it.candleDate }.sortedBy { it.candleDate }
        
        if (candles.size < 230) {
            log.warn("Not enough data for symbol {} to calculate 200 SMA over 30 days (found {} candles)", symbol, candles.size)
            return null
        }
        
        // Find the index of the end date (or the closest preceding trading day)
        val endIndex = candles.indexOfLast { !it.candleDate.isAfter(endDate) }
        if (endIndex == -1 || endIndex < 229) {
            log.warn("Not enough history before endDate {} for symbol {}", endDate, symbol)
            return null
        }
        
        // Take the last 30 trading days ending at endIndex
        val windowStartIndex = maxOf(200 - 1, endIndex - 30 + 1)
        val windowCandles = candles.subList(windowStartIndex, endIndex + 1)
        
        val dailyDetails = windowCandles.mapIndexed { index, dailyCandle ->
            val currentIndexInFullList = windowStartIndex + index
            
            // Calculate 200 SMA ending on this day
            val smaCandles = candles.subList(currentIndexInFullList - 200 + 1, currentIndexInFullList + 1)
            val sma200 = smaCandles.map { it.close }.average()
            
            val pctDiff = ((dailyCandle.close - sma200) / sma200) * 100
            val absPctDiff = abs(pctDiff)
            
            Rule5DailyDetail(
                date = dailyCandle.candleDate.format(DateTimeFormatter.ISO_DATE),
                closePrice = dailyCandle.close,
                sma200 = sma200,
                in2Pct = absPctDiff <= 2.0,
                in3Pct = absPctDiff <= 3.0,
                in4Pct = absPctDiff <= 4.0
            )
        }
        
        val totalDays = dailyDetails.size
        val in2PctCount = dailyDetails.count { it.in2Pct }
        val in3PctCount = dailyDetails.count { it.in3Pct }
        val in4PctCount = dailyDetails.count { it.in4Pct }
        
        val latestDay = dailyDetails.last()
        
        val oneYearAgoIndex = maxOf(0, endIndex - 252)
        val oneYearCandles = candles.subList(oneYearAgoIndex, endIndex + 1)
        val fiftyTwoWeekHigh = oneYearCandles.maxOf { it.high }
        val fiftyTwoWeekLow = oneYearCandles.minOf { it.low }

        val distTo52wHighPct = ((latestDay.closePrice - fiftyTwoWeekHigh) / fiftyTwoWeekHigh) * 100
        val distTo52wLowPct = ((latestDay.closePrice - fiftyTwoWeekLow) / fiftyTwoWeekLow) * 100
        
        return Rule5SymbolResult(
            date = endDate.format(dateFormatter),
            symbol = symbol,
            marketCapName = marketCapName,
            sector = sector,
            closePrice = latestDay.closePrice,
            sma200 = latestDay.sma200,
            fiftyTwoWeekHigh = fiftyTwoWeekHigh,
            fiftyTwoWeekLow = fiftyTwoWeekLow,
            distTo52wHighPct = distTo52wHighPct,
            distTo52wLowPct = distTo52wLowPct,
            daysIn2Pct = in2PctCount,
            daysIn3Pct = in3PctCount,
            daysIn4Pct = in4PctCount,
            dailyBreakdown = dailyDetails.reversed() // newest first for UI
        )
    }
}
