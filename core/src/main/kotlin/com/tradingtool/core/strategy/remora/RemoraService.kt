package com.tradingtool.core.strategy.remora

import com.tradingtool.core.config.IndicatorConfig
import com.tradingtool.core.database.RemoraJdbiHandler
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.model.remora.RemoraSignal
import com.tradingtool.core.telegram.TelegramSender
import com.tradingtool.core.model.telegram.TelegramSendTextRequest
import com.tradingtool.core.strategy.SignalScanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.ta4j.core.BaseBarSeriesBuilder
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
    private val telegramSender: TelegramSender,
    private val config: IndicatorConfig = IndicatorConfig.DEFAULT,
) : SignalScanner {
    private val log = LoggerFactory.getLogger(RemoraService::class.java)
    private val ist = ZoneId.of("Asia/Kolkata")

    override val name = "Remora"

    override suspend fun scan(kiteClient: KiteConnectClient) = computeAll(kiteClient)

    suspend fun computeAll(kiteClient: KiteConnectClient) {
        val stocks = stockHandler.read { it.listAll() }
        log.info("Remora scan starting for ${stocks.size} stocks")

        var signalsFound = 0
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

                val result = RemoraSignalCalculator.compute(series) ?: continue

                val rowsInserted = remoraHandler.write {
                    it.insertIfAbsent(
                        stockId = stock.id.toInt(),
                        symbol = stock.symbol,
                        companyName = stock.companyName,
                        exchange = stock.exchange,
                        signalType = result.signalType,
                        volumeRatio = result.volumeRatio,
                        priceChangePct = result.priceChangePct,
                        consecutiveDays = result.consecutiveDays,
                    )
                }

                if (rowsInserted > 0) {
                    signalsFound++
                    log.info("Remora signal: ${stock.symbol} — ${result.signalType} (${result.volumeRatio}x vol, ${result.consecutiveDays}d)")
                    sendTelegram(stock.symbol, stock.exchange, result)
                } else {
                    log.debug("Remora signal for ${stock.symbol} already recorded today — skipping Telegram")
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Remora scan failed for ${stock.symbol}: ${e.message}")
            }
        }

        log.info("Remora scan complete: $signalsFound new signals out of ${stocks.size} stocks")
    }

    suspend fun getSignals(type: String? = null): List<RemoraSignal> {
        return remoraHandler.read { dao ->
            if (type.isNullOrBlank()) dao.findAll() else dao.findByType(type.uppercase())
        }
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
