package com.tradingtool.resources

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.inject.Inject
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.database.EarningsResultJdbiHandler
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.stock.service.StockService
import com.tradingtool.resources.common.badRequest
import com.tradingtool.resources.common.endpoint
import com.tradingtool.resources.common.ok
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.concurrent.CompletableFuture

data class CorporateEventRequest(
    val symbol: String,
    val primaryDate: String, // ISO date YYYY-MM-DD
)

data class CorporateEventExportResponse(
    val symbol: String,
    val primaryDate: String,
    val candles: List<DailyCandle>,
)

data class EarningsDashboardRow(
    val symbol: String,
    val instrumentToken: Long,
    val resultDate: String,
    val daysToResult: Int,
    val isGrowwWatchlist: Boolean,
    val pre15dReturnPct: Double?,
    val pre10dReturnPct: Double?,
    val pre15dMaxDrawdownPct: Double?,
    val eventDayOcPct: Double?,
    val eventDayOhPct: Double?,
    val nextDayOcPct: Double?,
    val nextDayOhPct: Double?,
    val latestClose: Double?,
    val latestVolume: Long?,
    val candleCoverage20d: Int,
)

data class EarningsDashboardResponse(
    val asOfDate: String,
    val daysAhead: Int,
    val growwOnly: Boolean,
    val rows: List<EarningsDashboardRow>,
)

data class EarningsDashboardRawCandleBlock(
    val symbol: String,
    val instrumentToken: Long,
    val candles: List<DailyCandle>,
)

data class EarningsDashboardExportFilters(
    val daysAhead: Int,
    val growwOnly: Boolean,
)

data class EarningsDashboardExportDocument(
    @JsonProperty("generated_at")
    val generatedAt: String,
    val filters: EarningsDashboardExportFilters,
    @JsonProperty("calculated_rows")
    val calculatedRows: List<EarningsDashboardRow>,
    @JsonProperty("raw_daily_candles_20d")
    val rawDailyCandles20d: List<EarningsDashboardRawCandleBlock>,
)

private data class DashboardComputedRow(
    val source: com.tradingtool.core.earnings.EarningsResultRow,
    val row: EarningsDashboardRow,
    val candles20d: List<DailyCandle>,
)

@Path("/api/corporate-events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class CorporateEventResource @Inject constructor(
    private val kiteClient: KiteConnectClient,
    private val instrumentCache: InstrumentCache,
    private val stockService: StockService,
    private val candleHandler: CandleJdbiHandler,
    private val earningsHandler: EarningsResultJdbiHandler,
    private val objectMapper: ObjectMapper,
    private val resourceScope: ResourceScope,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ioScope = resourceScope.ioScope
    private val ist = ZoneId.of("Asia/Kolkata")

    @GET
    @Path("/dashboard")
    fun dashboard(
        @QueryParam("daysAhead") daysAheadRaw: Int?,
        @QueryParam("growwOnly") growwOnlyRaw: Boolean?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val daysAhead = normalizeDaysAhead(daysAheadRaw)
            ?: return@endpoint badRequest("daysAhead must be between 1 and 60.")
        val growwOnly = growwOnlyRaw ?: true
        val rows = buildDashboardRows(daysAhead = daysAhead, growwOnly = growwOnly, includeCandles = false)
            .map { it.row }
        ok(
            EarningsDashboardResponse(
                asOfDate = LocalDate.now(ist).toString(),
                daysAhead = daysAhead,
                growwOnly = growwOnly,
                rows = rows,
            ),
        )
    }

    @GET
    @Path("/dashboard/export")
    fun exportDashboard(
        @QueryParam("daysAhead") daysAheadRaw: Int?,
        @QueryParam("growwOnly") growwOnlyRaw: Boolean?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val daysAhead = normalizeDaysAhead(daysAheadRaw)
            ?: return@endpoint badRequest("daysAhead must be between 1 and 60.")
        val growwOnly = growwOnlyRaw ?: true
        val rows = buildDashboardRows(daysAhead = daysAhead, growwOnly = growwOnly, includeCandles = true)

        persistDashboardSnapshots(rows)

        ok(
            EarningsDashboardExportDocument(
                generatedAt = OffsetDateTime.now(ist).toString(),
                filters = EarningsDashboardExportFilters(daysAhead = daysAhead, growwOnly = growwOnly),
                calculatedRows = rows.map { it.row },
                rawDailyCandles20d = rows.map { computed ->
                    EarningsDashboardRawCandleBlock(
                        symbol = computed.row.symbol,
                        instrumentToken = computed.row.instrumentToken,
                        candles = computed.candles20d,
                    )
                },
            ),
        )
    }

    @POST
    @Path("/export")
    fun export(requests: List<CorporateEventRequest>): CompletableFuture<Response> = ioScope.endpoint {
        if (requests.isEmpty()) return@endpoint badRequest("Request list cannot be empty")

        val results = coroutineScope {
            requests.map { req ->
                async {
                    processRequest(req)
                }
            }.awaitAll()
        }

        ok(results)
    }

    private suspend fun processRequest(req: CorporateEventRequest): CorporateEventExportResponse {
        val symbol = req.symbol.trim().uppercase()
        val primaryDate = try {
            LocalDate.parse(req.primaryDate)
        } catch (e: Exception) {
            log.warn("Invalid date format for symbol $symbol: ${req.primaryDate}")
            return CorporateEventExportResponse(symbol, req.primaryDate, emptyList())
        }

        // Fetch enough data to cover +/- 5 trading days. 15 calendar days is usually plenty.
        val fromDate = primaryDate.minusDays(20)
        val toDate = primaryDate.plusDays(20)

        val token = instrumentCache.token("NSE", symbol)
            ?: stockService.listAll().find { it.symbol.uppercase() == symbol }?.instrumentToken

        if (token == null) {
            log.warn("Token not found for symbol $symbol")
            return CorporateEventExportResponse(symbol, req.primaryDate, emptyList())
        }

        // Try to get from database first
        var candles = candleHandler.read { it.getDailyCandles(token, fromDate, toDate) }

        // Check if we have enough coverage around the primaryDate.
        // If the date is in the past, we should have candles before and after.
        // If the date is in the future, we should have candles up to today.
        val hasEnoughData = when {
            candles.isEmpty() -> false
            else -> {
                val sorted = candles.sortedBy { it.candleDate }
                val eventIndex = sorted.indexOfFirst { it.candleDate >= primaryDate }
                val today = LocalDate.now(ist)
                
                if (primaryDate.isBefore(today)) {
                    // Past event: need some candles after primaryDate too
                    eventIndex != -1 && (sorted.size - eventIndex) >= 1
                } else {
                    // Future/Today event: just need candles up to the latest trading day
                    val lastCandleDate = sorted.last().candleDate
                    lastCandleDate.isAfter(today.minusDays(4)) // Recently synced
                }
            }
        }

        // If not enough data, fetch from Kite
        if (!hasEnoughData || candles.size < 10) {
            log.info("Insufficent data in DB for $symbol around $primaryDate (found ${candles.size} candles). Fetching from Kite.")
            try {
                val kiteData = withContext(Dispatchers.IO) {
                    kiteClient.client().getHistoricalData(
                        Date.from(fromDate.atStartOfDay(ist).toInstant()),
                        Date.from(toDate.atStartOfDay(ist).toInstant()),
                        token.toString(),
                        "day",
                        false,
                        false
                    )
                }
                
                val fetchedCandles = kiteData.dataArrayList.mapNotNull { bar ->
                    if (bar == null) return@mapNotNull null
                    try {
                        val hk = bar as com.zerodhatech.models.HistoricalData
                        val date = LocalDateTime.parse(hk.timeStamp.substring(0, 19)).toLocalDate()
                        DailyCandle(
                            instrumentToken = token,
                            symbol = symbol,
                            candleDate = date,
                            open = hk.open,
                            high = hk.high,
                            low = hk.low,
                            close = hk.close,
                            volume = hk.volume,
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                
                if (fetchedCandles.isNotEmpty()) {
                    candleHandler.write { it.upsertDailyCandles(fetchedCandles) }
                    candles = candleHandler.read { it.getDailyCandles(token, fromDate, toDate) }
                }
            } catch (e: Exception) {
                log.error("Failed to fetch historical data for $symbol: ${e.message}")
            }
        }

        // Filter exactly 5 before and 5 after
        val sortedCandles = candles.sortedBy { it.candleDate }
        val resultIndex = sortedCandles.indexOfFirst { it.candleDate >= primaryDate }
        
        val filteredCandles = if (resultIndex == -1) {
            // All candles are before primaryDate
            sortedCandles.takeLast(11)
        } else {
            val start = (resultIndex - 5).coerceAtLeast(0)
            val end = (resultIndex + 6).coerceAtMost(sortedCandles.size)
            sortedCandles.subList(start, end)
        }

        return CorporateEventExportResponse(symbol, req.primaryDate, filteredCandles)
    }

    private suspend fun buildDashboardRows(
        daysAhead: Int,
        growwOnly: Boolean,
        includeCandles: Boolean,
    ): List<DashboardComputedRow> {
        val today = LocalDate.now(ist)
        val endDate = today.plusDays(daysAhead.toLong())
        val earningsRows = earningsHandler.read { dao ->
            dao.findByResultDateRange(today, endDate)
        }
        if (earningsRows.isEmpty()) {
            return emptyList()
        }

        val stocksBySymbol = stockService.listAll().associateBy { stock -> stock.symbol.uppercase() }
        val results = mutableListOf<DashboardComputedRow>()

        for (earningsRow in earningsRows) {
            val instrumentToken = earningsRow.instrumentToken ?: continue
            val stock = stocksBySymbol[earningsRow.stockSymbol.uppercase()]
            val isGrowwWatchlist = stock?.tags?.any { tag -> tag.name.equals("GROWW", ignoreCase = true) } ?: false
            if (growwOnly && !isGrowwWatchlist) {
                continue
            }

            val candles20d = candleHandler.read { dao ->
                dao.getRecentDailyCandles(instrumentToken, 20)
            }.sortedBy { candle -> candle.candleDate }

            val eventWindow = candleHandler.read { dao ->
                dao.getDailyCandles(instrumentToken, earningsRow.resultDate.minusDays(2), earningsRow.resultDate.plusDays(6))
            }.sortedBy { candle -> candle.candleDate }

            val eventCandle = eventWindow.firstOrNull { candle -> candle.candleDate == earningsRow.resultDate }
            val nextCandle = eventWindow.firstOrNull { candle -> candle.candleDate.isAfter(earningsRow.resultDate) }
            val latestCandle = candles20d.lastOrNull()

            results += DashboardComputedRow(
                source = earningsRow,
                row = EarningsDashboardRow(
                    symbol = earningsRow.stockSymbol,
                    instrumentToken = instrumentToken,
                    resultDate = earningsRow.resultDate.toString(),
                    daysToResult = ChronoUnit.DAYS.between(today, earningsRow.resultDate).toInt(),
                    isGrowwWatchlist = isGrowwWatchlist,
                    pre15dReturnPct = computeLookbackReturn(candles20d, 15),
                    pre10dReturnPct = computeLookbackReturn(candles20d, 10),
                    pre15dMaxDrawdownPct = computeLookbackMaxDrawdown(candles20d, 15),
                    eventDayOcPct = eventCandle?.let { candle -> percentChange(candle.close, candle.open) },
                    eventDayOhPct = eventCandle?.let { candle -> percentChange(candle.high, candle.open) },
                    nextDayOcPct = nextCandle?.let { candle -> percentChange(candle.close, candle.open) },
                    nextDayOhPct = nextCandle?.let { candle -> percentChange(candle.high, candle.open) },
                    latestClose = latestCandle?.close,
                    latestVolume = latestCandle?.volume,
                    candleCoverage20d = candles20d.size,
                ),
                candles20d = if (includeCandles) candles20d else emptyList(),
            )
        }

        return results.sortedWith(
            compareBy<DashboardComputedRow> { row -> row.row.resultDate }
                .thenBy { row -> row.row.symbol },
        )
    }

    private suspend fun persistDashboardSnapshots(rows: List<DashboardComputedRow>) {
        if (rows.isEmpty()) {
            return
        }
        val generatedAt = OffsetDateTime.now(ist).toString()
        for (computed in rows) {
            val payload = parsePayload(computed.source.behaviorPayloadJson).toMutableMap()
            payload["dashboard_snapshot"] = mapOf(
                "generated_at" to generatedAt,
                "row" to objectMapper.convertValue(computed.row, MAP_TYPE),
            )
            val payloadJson = objectMapper.writeValueAsString(payload)
            earningsHandler.write { dao ->
                dao.updateBehaviorPayload(computed.source.id, payloadJson)
            }
        }
    }

    private fun parsePayload(rawJson: String): Map<String, Any> {
        if (rawJson.isBlank()) {
            return emptyMap()
        }
        return runCatching { objectMapper.readValue(rawJson, MAP_TYPE) }
            .getOrElse { emptyMap() }
    }

    private fun normalizeDaysAhead(daysAheadRaw: Int?): Int? {
        val daysAhead = daysAheadRaw ?: DEFAULT_DAYS_AHEAD
        return daysAhead.takeIf { value -> value in 1..60 }
    }

    private fun computeLookbackReturn(candles: List<DailyCandle>, lookbackDays: Int): Double? {
        if (candles.size <= lookbackDays) {
            return null
        }
        val latest = candles.last().close
        val reference = candles[candles.size - 1 - lookbackDays].close
        return percentChange(latest, reference)
    }

    private fun computeLookbackMaxDrawdown(candles: List<DailyCandle>, lookbackDays: Int): Double? {
        if (candles.size <= lookbackDays) {
            return null
        }
        val slice = candles.takeLast(lookbackDays + 1)
        var peak = slice.first().close
        var maxDrawdown = 0.0
        for (candle in slice) {
            peak = maxOf(peak, candle.close)
            if (peak == 0.0) {
                continue
            }
            val drawdown = ((candle.close - peak) / peak) * 100.0
            maxDrawdown = minOf(maxDrawdown, drawdown)
        }
        return maxDrawdown
    }

    private fun percentChange(target: Double, base: Double): Double? {
        if (base == 0.0) {
            return null
        }
        return ((target - base) / base) * 100.0
    }

    private companion object {
        private const val DEFAULT_DAYS_AHEAD: Int = 15
        private val MAP_TYPE = object : TypeReference<Map<String, Any>>() {}
    }
}
