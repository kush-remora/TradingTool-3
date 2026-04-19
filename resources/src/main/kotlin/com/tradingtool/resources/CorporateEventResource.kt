package com.tradingtool.resources

import com.google.inject.Inject
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.stock.service.StockService
import com.tradingtool.resources.common.badRequest
import com.tradingtool.resources.common.endpoint
import com.tradingtool.resources.common.ok
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
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
import java.time.ZoneId
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

@Path("/api/corporate-events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class CorporateEventResource @Inject constructor(
    private val kiteClient: KiteConnectClient,
    private val instrumentCache: InstrumentCache,
    private val stockService: StockService,
    private val candleHandler: CandleJdbiHandler,
    private val resourceScope: ResourceScope,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ioScope = resourceScope.ioScope
    private val ist = ZoneId.of("Asia/Kolkata")

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
}
