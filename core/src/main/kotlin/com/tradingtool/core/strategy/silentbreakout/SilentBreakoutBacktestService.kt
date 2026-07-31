package com.tradingtool.core.strategy.silentbreakout

import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.candle.dao.CandleReadDao
import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.database.StockDeliveryJdbiHandler
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.screener.CandleDataService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.StringReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class SilentBreakoutBacktestService(
    private val candleCacheService: CandleCacheService,
    private val candleHandler: CandleJdbiHandler,
    private val candleDataService: CandleDataService,
    private val kiteClient: KiteConnectClient,
    private val instrumentCache: InstrumentCache,
    private val stockDeliveryHandler: StockDeliveryJdbiHandler,
) {
    suspend fun run(csvContent: String, targetPct: Double): SilentBreakoutBacktestResponse = withContext(Dispatchers.IO) {
        val signals = parseSignals(csvContent)
        if (signals.isEmpty()) {
            return@withContext SilentBreakoutBacktestResponse(emptyList(), emptySummary())
        }

        val earliestDate = signals.minOf(SilentBreakoutSignal::signalDate)
        val candlesBySymbol = signals.map(SilentBreakoutSignal::symbol).distinct().associateWith { symbol ->
            loadCandles(symbol, earliestDate.minusDays(HISTORY_CALENDAR_DAYS), LocalDate.now())
        }
        val instrumentTokens = candlesBySymbol.values.flatten().map(DailyCandle::instrumentToken).distinct()
        val deliveryPctByToken = if (instrumentTokens.isEmpty()) emptyMap() else stockDeliveryHandler.read { dao ->
            dao.findByInstrumentTokensBetweenDates(
                instrumentTokens,
                earliestDate.minusDays(14),
                LocalDate.now(),
            )
        }.groupBy { delivery -> delivery.instrumentToken }
            .mapValues { (_, deliveries) ->
                deliveries.mapNotNull { delivery ->
                    delivery.delivPer?.let { deliveryPct -> delivery.tradingDate to deliveryPct }
                }.toMap()
            }
        val rows = signals.map { signal ->
            val candles = candlesBySymbol[signal.symbol].orEmpty()
            SilentBreakoutBacktestAnalyzer.analyze(
                signal,
                candles,
                targetPct,
                candles.firstOrNull()?.instrumentToken?.let { token -> deliveryPctByToken[token] }.orEmpty(),
            )
        }.sortedWith(compareByDescending<SilentBreakoutBacktestRow> { row -> row.signalDate }.thenBy(SilentBreakoutBacktestRow::symbol))

        SilentBreakoutBacktestResponse(rows, summary(rows))
    }

    private fun parseSignals(csvContent: String): List<SilentBreakoutSignal> {
        CSVParser.parse(
            StringReader(csvContent),
            CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build(),
        ).use { parser ->
            val headerMap = parser.headerMap.mapKeys { (header) -> header.lowercase().replace(" ", "") }
            val symbolHeader = headerMap.keys.firstOrNull { header -> header.contains("symbol") }
            val dateHeader = headerMap.keys.firstOrNull { header -> header.contains("date") }
            require(symbolHeader != null && dateHeader != null) { "CSV must contain symbol and date columns." }

            return parser.mapNotNull { record ->
                val symbol = record.get(headerMap.getValue(symbolHeader)).trim().uppercase()
                val date = parseDate(record.get(headerMap.getValue(dateHeader)).trim())
                if (symbol.isBlank() || date == null) null else SilentBreakoutSignal(symbol, date)
            }.distinctBy { signal -> signal.symbol to signal.signalDate }
        }
    }

    private fun parseDate(value: String): LocalDate? = DATE_FORMATTERS.firstNotNullOfOrNull { formatter ->
        try {
            LocalDate.parse(value, formatter)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private suspend fun loadCandles(symbol: String, fromDate: LocalDate, toDate: LocalDate): List<DailyCandle> {
        val instrumentToken = instrumentCache.token("NSE", symbol)
        var candles = instrumentToken?.let { token ->
            candleCacheService.getDailyCandles(token, symbol, fromDate, toDate)
        } ?: candleHandler.read { dao: CandleReadDao -> dao.getDailyCandlesBySymbol(symbol, fromDate, toDate) }

        if (candles.isEmpty()) {
            candleDataService.syncDailyRange(listOf(symbol), fromDate, toDate, kiteClient)
            candleCacheService.invalidateDailyCandles(symbol)
            candles = instrumentCache.token("NSE", symbol)?.let { token ->
                candleCacheService.getDailyCandles(token, symbol, fromDate, toDate)
            } ?: candleHandler.read { dao: CandleReadDao -> dao.getDailyCandlesBySymbol(symbol, fromDate, toDate) }
        }
        return candles.sortedBy(DailyCandle::candleDate)
    }

    private fun summary(rows: List<SilentBreakoutBacktestRow>): SilentBreakoutBacktestSummary {
        val availableRows = rows.filter { row -> row.dataStatus != SilentBreakoutDataStatus.MISSING_SIGNAL_CANDLE }
        return SilentBreakoutBacktestSummary(
            signalCount = rows.size,
            availableCount = availableRows.size,
            lateStageRiskCount = availableRows.count { row -> row.lateStageRisk == true },
            averageForward20SessionReturnPct = availableRows.mapNotNull(SilentBreakoutBacktestRow::forward20SessionReturnPct).averageOrNull(),
            averageForward40SessionReturnPct = availableRows.mapNotNull(SilentBreakoutBacktestRow::forward40SessionReturnPct).averageOrNull(),
        )
    }

    private fun emptySummary(): SilentBreakoutBacktestSummary = SilentBreakoutBacktestSummary(0, 0, 0, null, null)

    private fun List<Double>.averageOrNull(): Double? = takeIf { values -> values.isNotEmpty() }?.average()

    private companion object {
        const val HISTORY_CALENDAR_DAYS = 800L
        val DATE_FORMATTERS = listOf(DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("dd-MM-yyyy"))
    }
}
