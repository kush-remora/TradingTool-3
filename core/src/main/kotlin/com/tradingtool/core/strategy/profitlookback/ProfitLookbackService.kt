package com.tradingtool.core.strategy.profitlookback

import com.tradingtool.core.kite.KiteConnectClient
import com.google.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.Date

class ProfitLookbackService @Inject constructor(
    private val kiteClient: KiteConnectClient,
) {
    private val log = LoggerFactory.getLogger(ProfitLookbackService::class.java)
    private val istZone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val lookbackBufferDays: Int = 20
    private val bulkConcurrencyLimit: Int = 6

    suspend fun analyze(request: ProfitLookbackRequest): ProfitLookbackResponse {
        val requestedSellDate = LocalDate.parse(request.sellDate)
        val targetPercents = request.targetPercents
        val candles = loadDailyCandles(
            kiteClient = kiteClient,
            symbol = request.symbol,
            instrumentToken = request.instrumentToken,
            requestedSellDate = requestedSellDate,
            lookbackDays = request.lookbackDays,
        )

        return analyzeWithCandles(
            symbol = request.symbol,
            instrumentToken = request.instrumentToken,
            requestedSellDate = requestedSellDate,
            lookbackDays = request.lookbackDays,
            targetPercents = targetPercents,
            candles = candles,
        )
    }

    suspend fun analyzeBulk(request: ProfitLookbackBulkRequest): ProfitLookbackBulkResponse {
        return analyzeBulkWithExecutor(
            request = request,
            analyzer = { singleRequest -> analyze(singleRequest) },
        )
    }

    internal suspend fun analyzeBulkWithExecutor(
        request: ProfitLookbackBulkRequest,
        analyzer: suspend (ProfitLookbackRequest) -> ProfitLookbackResponse,
    ): ProfitLookbackBulkResponse {
        val dedupeResultByKey = ConcurrentHashMap<ProfitLookbackBulkKey, ProfitLookbackBulkComputeResult>()
        val uniqueRows = request.rows.distinctBy { row ->
            ProfitLookbackBulkKey(
                symbol = row.symbol,
                instrumentToken = row.instrumentToken,
                sellDate = row.sellDate,
            )
        }
        val semaphore = Semaphore(bulkConcurrencyLimit)

        supervisorScope {
            uniqueRows.map { row ->
                async {
                    semaphore.withPermit {
                        val key = ProfitLookbackBulkKey(
                            symbol = row.symbol,
                            instrumentToken = row.instrumentToken,
                            sellDate = row.sellDate,
                        )
                        val result = runCatching {
                            analyzer(
                                ProfitLookbackRequest(
                                    symbol = row.symbol,
                                    instrumentToken = row.instrumentToken,
                                    sellDate = row.sellDate,
                                    lookbackDays = request.lookbackDays,
                                    targetPercents = request.targetPercents,
                                ),
                            )
                        }.fold(
                            onSuccess = { response ->
                                ProfitLookbackBulkComputeResult(ok = true, data = response, error = null)
                            },
                            onFailure = { error ->
                                ProfitLookbackBulkComputeResult(
                                    ok = false,
                                    data = null,
                                    error = error.message ?: "Failed to analyze row ${row.rowId}",
                                )
                            },
                        )
                        dedupeResultByKey[key] = result
                    }
                }
            }.awaitAll()
        }

        val rows = request.rows.map { row ->
            val key = ProfitLookbackBulkKey(
                symbol = row.symbol,
                instrumentToken = row.instrumentToken,
                sellDate = row.sellDate,
            )
            val result = dedupeResultByKey[key]
                ?: ProfitLookbackBulkComputeResult(
                    ok = false,
                    data = null,
                    error = "No analysis result found for row ${row.rowId}",
                )
            ProfitLookbackBulkRowResponse(
                rowId = row.rowId,
                ok = result.ok,
                data = result.data,
                error = result.error,
            )
        }

        return ProfitLookbackBulkResponse(rows = rows)
    }

    internal fun analyzeWithCandles(
        symbol: String,
        instrumentToken: Long,
        requestedSellDate: LocalDate,
        lookbackDays: Int,
        targetPercents: List<Double>,
        candles: List<ProfitLookbackDailyCandle>,
    ): ProfitLookbackResponse {
        val sortedCandles = candles.sortedBy { candle -> candle.date }
        val sellCandle = sortedCandles.lastOrNull { candle -> !candle.date.isAfter(requestedSellDate) }
            ?: throw IllegalArgumentException(
                "No daily candle found on or before sell date $requestedSellDate for $symbol",
            )

        val earliestBuyDate = sellCandle.date.minusDays(lookbackDays.toLong())
        val buyCandidates = sortedCandles.filter { candle ->
            candle.date.isBefore(sellCandle.date) && !candle.date.isBefore(earliestBuyDate)
        }

        val results = targetPercents.map { target ->
            val matchedBuyCandle = buyCandidates.lastOrNull { buyCandle ->
                calculateReturnPct(buyOpen = buyCandle.open, sellOpen = sellCandle.open) >= target
            }

            if (matchedBuyCandle == null) {
                ProfitLookbackTargetResult(
                    targetPercent = target,
                    status = "NOT_ACHIEVABLE",
                    suggestedBuyDate = null,
                    buyOpenPrice = null,
                    daysBefore = null,
                    returnPercent = null,
                    maxDrawdownPercent = null,
                    maxDrawdownDays = null,
                )
            } else {
                val drawdown = calculateMaxDrawdown(
                    candles = sortedCandles,
                    buyCandle = matchedBuyCandle,
                    sellCandle = sellCandle,
                )
                ProfitLookbackTargetResult(
                    targetPercent = target,
                    status = "ACHIEVED",
                    suggestedBuyDate = matchedBuyCandle.date.toString(),
                    buyOpenPrice = matchedBuyCandle.open.roundTo4(),
                    daysBefore = ChronoUnit.DAYS.between(matchedBuyCandle.date, sellCandle.date).toInt(),
                    returnPercent = calculateReturnPct(
                        buyOpen = matchedBuyCandle.open,
                        sellOpen = sellCandle.open,
                    ).roundTo4(),
                    maxDrawdownPercent = drawdown.maxDrawdownPercent.roundTo4(),
                    maxDrawdownDays = drawdown.maxDrawdownDays,
                )
            }
        }

        return ProfitLookbackResponse(
            symbol = symbol,
            instrumentToken = instrumentToken,
            requestedSellDate = requestedSellDate.toString(),
            resolvedSellDate = sellCandle.date.toString(),
            sellOpenPrice = sellCandle.open.roundTo4(),
            results = results,
        )
    }

    private suspend fun loadDailyCandles(
        kiteClient: KiteConnectClient,
        symbol: String,
        instrumentToken: Long,
        requestedSellDate: LocalDate,
        lookbackDays: Int,
    ): List<ProfitLookbackDailyCandle> {
        val fromDate = requestedSellDate.minusDays((lookbackDays + lookbackBufferDays).toLong())
        val toDateExclusive = requestedSellDate.plusDays(1)

        val history = withContext(Dispatchers.IO) {
            kiteClient.client().getHistoricalData(
                Date.from(fromDate.atStartOfDay(istZone).toInstant()),
                Date.from(toDateExclusive.atStartOfDay(istZone).toInstant()),
                instrumentToken.toString(),
                "day",
                false,
                false,
            )
        }

        val candles = history.dataArrayList.mapNotNull { bar ->
            parseKiteDayCandle(bar.timeStamp, bar.open, bar.low)
        }

        if (candles.isEmpty()) {
            log.warn(
                "No daily candles returned from Kite for symbol={} token={} from={} to={}",
                symbol,
                instrumentToken,
                fromDate,
                requestedSellDate,
            )
        }

        return candles
    }

    private fun parseKiteDayCandle(timestamp: String?, open: Double, low: Double): ProfitLookbackDailyCandle? {
        if (timestamp.isNullOrBlank()) {
            return null
        }
        if (open <= 0.0 || !open.isFinite() || low <= 0.0 || !low.isFinite()) {
            return null
        }

        val datePart = timestamp.take(10)
        return runCatching {
            ProfitLookbackDailyCandle(date = LocalDate.parse(datePart), open = open, low = low)
        }.getOrNull()
    }

    private fun calculateMaxDrawdown(
        candles: List<ProfitLookbackDailyCandle>,
        buyCandle: ProfitLookbackDailyCandle,
        sellCandle: ProfitLookbackDailyCandle,
    ): ProfitLookbackDrawdown {
        val windowCandles = candles.filter { candle ->
            !candle.date.isBefore(buyCandle.date) && !candle.date.isAfter(sellCandle.date)
        }
        val minLowCandle = windowCandles.minByOrNull { candle -> candle.low } ?: buyCandle
        val maxDrawdownPercent = ((minLowCandle.low - buyCandle.open) / buyCandle.open) * 100.0
        val maxDrawdownDays = ChronoUnit.DAYS.between(buyCandle.date, minLowCandle.date).toInt()
        return ProfitLookbackDrawdown(
            maxDrawdownPercent = maxDrawdownPercent,
            maxDrawdownDays = maxDrawdownDays,
        )
    }

    private fun calculateReturnPct(buyOpen: Double, sellOpen: Double): Double {
        if (buyOpen <= 0.0) {
            return Double.NEGATIVE_INFINITY
        }
        return ((sellOpen - buyOpen) / buyOpen) * 100.0
    }

    private fun Double.roundTo4(): Double = kotlin.math.round(this * 10_000.0) / 10_000.0
}

private data class ProfitLookbackBulkKey(
    val symbol: String,
    val instrumentToken: Long,
    val sellDate: String,
)

private data class ProfitLookbackBulkComputeResult(
    val ok: Boolean,
    val data: ProfitLookbackResponse?,
    val error: String?,
)

private data class ProfitLookbackDrawdown(
    val maxDrawdownPercent: Double,
    val maxDrawdownDays: Int,
)
