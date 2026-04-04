package com.tradingtool.core.strategy.remora

import com.tradingtool.core.config.IndicatorConfig
import com.tradingtool.core.database.RemoraJdbiHandler
import com.tradingtool.core.database.StockDeliveryJdbiHandler
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.delivery.source.NseDeliverySourceAdapter
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.model.remora.RemoraEnvelope
import com.tradingtool.core.model.remora.RemoraSignal
import com.tradingtool.core.telegram.TelegramSender
import com.tradingtool.core.model.telegram.TelegramSendTextRequest
import com.tradingtool.core.strategy.SignalScanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.ta4j.core.BaseBarSeriesBuilder
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date

/**
 * Computes and persists Remora (institutional volume) signals for all tracked stocks.
 *
 * Fetches 30 calendar days of daily OHLCV from Kite (enough for 20d avg baseline + 10 signal days).
 * Runs [RemoraSignalCalculator] per stock and inserts a row when a signal fires.
 * Sends a Telegram message for each new signal (skips if already stored today).
 */
class RemoraService(
    private val stockHandler: StockJdbiHandler,
    private val remoraHandler: RemoraJdbiHandler,
    private val deliveryHandler: StockDeliveryJdbiHandler,
    private val nseAdapter: NseDeliverySourceAdapter,
    private val telegramSender: TelegramSender,
    private val kiteClient: KiteConnectClient,
    private val config: IndicatorConfig = IndicatorConfig.DEFAULT,
) : SignalScanner {
    private val log = LoggerFactory.getLogger(RemoraService::class.java)
    private val ist = ZoneId.of("Asia/Kolkata")
    
    private val deliveryMutex = Mutex()
    private var lastFailureAt: Instant? = null
    private val failureCooldown = Duration.ofMinutes(15)
    private var staleReason: String? = null

    override val name = "Remora"

    override suspend fun scan(kiteClient: KiteConnectClient) = computeAll(kiteClient)

    suspend fun computeAll(kiteClient: KiteConnectClient) {
        val stocks = stockHandler.read { it.listAll() }
        log.info("Remora scan starting for ${stocks.size} stocks")

        ensureDeliveryFresh()

        var signalsFound = 0
        var successfulFetches = 0
        val dateRange = buildDateRange()

        for (stock in stocks) {
            delay(config.kiteRateLimitDelayMs)
            try {
                val (fromDate, toDate) = dateRange
                val history = withContext(Dispatchers.IO) {
                    kiteClient.client()
                        .getHistoricalData(fromDate, toDate, stock.instrumentToken.toString(), "day", false, false)
                }

                val series = BaseBarSeriesBuilder().withName(stock.symbol).build()
                for (bar in history.dataArrayList) {
                    if (bar == null) continue
                    val hk = bar as com.zerodhatech.models.HistoricalData
                    val localDt = LocalDateTime.parse(hk.timeStamp.substring(0, 19))
                    series.addBar(localDt.atZone(ist), hk.open, hk.high, hk.low, hk.close, hk.volume)
                }
                successfulFetches++

                // Fetch delivery history for ratio calculation (need 10 lookback + 20 baseline = 30 records min)
                val deliveryRows = deliveryHandler.read {
                    it.findRecentByStockId(stock.id.toInt(), LocalDate.now(ist).plusDays(1), 50)
                }
                val deliveryMap = prepareDeliveryMap(deliveryRows)

                val result = RemoraSignalCalculator.compute(series, deliveryMap) ?: continue

                val rowsInserted = remoraHandler.write {
                    it.upsert(
                        stockId = stock.id.toInt(),
                        symbol = stock.symbol,
                        companyName = stock.companyName,
                        exchange = stock.exchange,
                        signalType = result.signalType,
                        volumeRatio = result.volumeRatio,
                        priceChangePct = result.priceChangePct,
                        consecutiveDays = result.consecutiveDays,
                        signalDate = result.tradingDate,
                        deliveryPct = result.deliveryPct,
                        deliveryRatio = result.deliveryRatio
                    )
                }

                if (rowsInserted > 0) {
                    signalsFound++
                    log.info("Remora signal: ${stock.symbol} — ${result.signalType} (${result.volumeRatio}x vol, ${result.consecutiveDays}d, ${result.deliveryPct}% deliv)")
                    sendTelegram(stock.symbol, stock.exchange, result)
                } else {
                    log.debug("Remora signal for ${stock.symbol} already recorded for ${result.tradingDate} — skipping Telegram")
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Remora scan failed for ${stock.symbol}: ${e.message}")
            }
        }

        log.info("Remora scan complete: $signalsFound new signals out of ${stocks.size} stocks")
        if (stocks.isNotEmpty()) {
            check(successfulFetches > 0) {
                "Remora scan failed for all ${stocks.size} stocks."
            }
        }
    }

    private suspend fun ensureDeliveryFresh() {
        if (deliveryMutex.isLocked) {
            deliveryMutex.withLock { } // Wait for completion
            return
        }

        deliveryMutex.withLock {
            val latestInDb = deliveryHandler.read { it.getLatestTradingDate() }
            if (latestInDb != null && !isDeliveryStale(latestInDb)) {
                staleReason = null
                return@withLock
            }

            if (lastFailureAt != null && Duration.between(lastFailureAt, Instant.now()) < failureCooldown) {
                log.warn("NSE delivery fetch in cooldown. Serving stale data.")
                return@withLock
            }

            try {
                log.info("Refreshing delivery data from NSE...")
                val rows = nseAdapter.fetchLatestDeliveryData()
                if (rows.isNotEmpty()) {
                    persistDelivery(rows)
                    staleReason = null
                    lastFailureAt = null
                } else {
                    handleFetchFailure("No delivery data found in NSE report")
                }
            } catch (e: Exception) {
                handleFetchFailure("NSE fetch error: ${e.message}")
            }
        }
    }

    private fun isDeliveryStale(latestDate: LocalDate): Boolean {
        val now = ZonedDateTime.now(ist)
        // NSE delivery data for today is usually available after 6:30 PM IST
        val reportAvailableTime = now.toLocalDate().atTime(18, 30).atZone(ist)
        
        return if (now.isAfter(reportAvailableTime)) {
            latestDate.isBefore(now.toLocalDate())
        } else {
            // Before 6:30 PM, yesterday's data is the latest expected
            latestDate.isBefore(now.toLocalDate().minusDays(1))
        }
    }

    private fun handleFetchFailure(reason: String) {
        log.warn("Delivery refresh failed: $reason")
        lastFailureAt = Instant.now()
        staleReason = reason
    }

    private suspend fun persistDelivery(rows: List<NseDeliverySourceAdapter.NseDeliveryRow>) {
        val trackedStocks = stockHandler.read { it.listAll() }.filter { it.exchange == "NSE" }
        val symbolMap = trackedStocks.associateBy { it.symbol.uppercase() }

        val bestRows = rows
            .filter { symbolMap.containsKey(it.symbol.uppercase()) }
            .groupBy { it.symbol.uppercase() to it.tradingDate }
            .mapValues { (_, symbolRows) ->
                symbolRows.sortedWith(compareBy<NseDeliverySourceAdapter.NseDeliveryRow> {
                    when (it.series) {
                        "EQ" -> 0
                        "BE" -> 1
                        else -> 2
                    }
                }.thenByDescending { it.ttlTrdQnty }).first()
            }.values

        deliveryHandler.write { dao ->
            bestRows.forEach { row ->
                val stock = symbolMap[row.symbol.uppercase()]!!
                dao.upsert(
                    stockId = stock.id.toInt(),
                    symbol = stock.symbol,
                    exchange = stock.exchange,
                    tradingDate = row.tradingDate,
                    series = row.series,
                    ttlTrdQnty = row.ttlTrdQnty,
                    delivQty = row.delivQty,
                    delivPer = row.delivPer,
                    sourceFileName = row.sourceFileName,
                    sourceUrl = row.sourceUrl
                )
            }
        }
    }

    private fun prepareDeliveryMap(history: List<com.tradingtool.core.delivery.model.StockDeliveryDaily>): Map<LocalDate, RemoraSignalCalculator.DeliveryMetrics> {
        val sorted = history.sortedByDescending { it.tradingDate }
        return sorted.mapIndexedNotNull { index, today ->
            val prior = sorted.drop(index + 1).take(20)
            if (prior.size < 20) return@mapIndexedNotNull null
            
            val avg = prior.mapNotNull { it.delivPer }.average()
            if (avg <= 0) return@mapIndexedNotNull null
            
            val ratio = (today.delivPer ?: 0.0) / avg
            today.tradingDate to RemoraSignalCalculator.DeliveryMetrics(today.delivPer ?: 0.0, ratio)
        }.toMap()
    }

    suspend fun getSignals(type: String? = null, onDemand: Boolean = true): RemoraEnvelope {
        val signals = remoraHandler.read { dao ->
            if (type.isNullOrBlank()) dao.findAll() else dao.findByType(type.uppercase())
        }

        val latestDeliveryDate = deliveryHandler.read { it.getLatestTradingDate() }
        val isStale = latestDeliveryDate == null || isDeliveryStale(latestDeliveryDate)

        if (onDemand && isStale) {
            log.info("Remora signals or delivery stale. Triggering on-demand scan.")
            try {
                computeAll(kiteClient)
            } catch (e: Exception) {
                log.error("On-demand scan failed: ${e.message}")
            }
            
            val updatedSignals = remoraHandler.read { dao ->
                if (type.isNullOrBlank()) dao.findAll() else dao.findByType(type.uppercase())
            }
            val updatedDeliveryDate = deliveryHandler.read { it.getLatestTradingDate() }
            return RemoraEnvelope(
                signals = updatedSignals,
                asOfDate = updatedDeliveryDate?.toString(),
                isStale = updatedDeliveryDate == null || isDeliveryStale(updatedDeliveryDate),
                staleReason = staleReason
            )
        }

        return RemoraEnvelope(
            signals = signals,
            asOfDate = latestDeliveryDate?.toString(),
            isStale = isStale,
            staleReason = staleReason
        )
    }

    private suspend fun sendTelegram(symbol: String, exchange: String, result: RemoraSignalCalculator.Result) {
        if (!telegramSender.isConfigured()) return
        val emoji = if (result.signalType == "ACCUMULATION") "📈" else "📉"
        val changeSign = if (result.priceChangePct >= 0) "+" else ""
        val message = """
            $emoji Remora Signal: $symbol
            Type: ${result.signalType}
            Exchange: $exchange
            Volume: ${"%.2f".format(result.volumeRatio)}x avg (${result.consecutiveDays} consecutive days)
            Delivery: ${"%.2f".format(result.deliveryPct)}% (${"%.2f".format(result.deliveryRatio)}x avg)
            Price Change: $changeSign${"%.2f".format(result.priceChangePct)}% today
        """.trimIndent()

        runCatching {
            telegramSender.sendText(TelegramSendTextRequest(text = message))
        }.onFailure {
            log.warn("Failed to send Telegram for $symbol: ${it.message}")
        }
    }

    // 30 calendar days covers ~22 trading days — enough for 20d avg + 10 signal days.
    private fun buildDateRange(): Pair<Date, Date> {
        val today = LocalDate.now(ist)
        return Pair(
            Date.from(today.minusDays(30).atStartOfDay(ist).toInstant()),
            Date.from(today.atStartOfDay(ist).toInstant()),
        )
    }
}
