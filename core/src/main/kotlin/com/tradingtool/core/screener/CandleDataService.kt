package com.tradingtool.core.screener

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.candle.IntradayCandle
import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.KiteConnectClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

/**
 * Fetches raw OHLCV candles from Kite Connect and upserts them into the database.
 *
 * Fetches 10 weeks of:
 *   - daily candles  → for SMA, momentum, Thursday peak detection
 *   - 15-min candles → for Monday morning dip detection and future intraday strategies
 *
 * Rate-limited to ~2 req/s (350ms delay between Kite calls) to stay under the 3 req/s limit.
 */
class CandleDataService(
    private val stockHandler: StockJdbiHandler,
    private val candleHandler: CandleJdbiHandler,
    private val instrumentCache: InstrumentCache,
) {
    private val log = LoggerFactory.getLogger(CandleDataService::class.java)
    private val ist = ZoneId.of("Asia/Kolkata")

    /**
     * Syncs candle data for all given [symbols] from Kite.
     * Returns the count of symbols successfully synced.
     */
    suspend fun sync(symbols: List<String>, kiteClient: KiteConnectClient): Int {
        ensureInstrumentCacheLoaded(kiteClient)

        val today = LocalDate.now(ist)
        val fromDaily = today.minusDays(500) // Approx 2 years of trading days for SMA200 + RSI bounds
        val fromIntraday = today.minusDays(70) // 10-week lookback for intraday patterns
        val toDate = today.toJavaDate()

        var synced = 0
        for (symbol in symbols) {
            val stock = stockHandler.read { it.getBySymbol(symbol, "NSE") }
            val token = stock?.instrumentToken ?: instrumentCache.token("NSE", symbol)
            if (token == null || token <= 0) {
                log.warn("Symbol {} has no instrument token in stocks table or instrument cache — skipping sync", symbol)
                continue
            }

            val tokenStr = token.toString()
            log.info("Syncing candles for {} (token={})", symbol, tokenStr)

            try {
                // Fetch + store daily candles (Longer lookback)
                val dailyRaw = withContext(Dispatchers.IO) {
                    kiteClient.client().getHistoricalData(fromDaily.toJavaDate(), toDate, tokenStr, "day", false, false)
                }
                val dailyCandles = dailyRaw.dataArrayList.mapNotNull { bar ->
                    parseDailyCandle(bar, token, symbol)
                }
                if (dailyCandles.isNotEmpty()) {
                    candleHandler.write { it.upsertDailyCandles(dailyCandles) }
                    log.info("  → {} daily candles upserted for {}", dailyCandles.size, symbol)
                }
                delay(350)

                // Fetch + store 15-minute intraday candles (Shorter lookback)
                val intradayRaw = withContext(Dispatchers.IO) {
                    kiteClient.client().getHistoricalData(fromIntraday.toJavaDate(), toDate, tokenStr, "15minute", false, false)
                }
                val intradayCandles = intradayRaw.dataArrayList.mapNotNull { bar ->
                    parseIntradayCandle(bar, token, symbol, "15minute")
                }
                if (intradayCandles.isNotEmpty()) {
                    candleHandler.write { it.upsertIntradayCandles(intradayCandles) }
                    log.info("  → {} intraday candles upserted for {}", intradayCandles.size, symbol)
                }
                delay(350)

                synced++
            } catch (e: Exception) {
                log.error("Failed to sync candles for {}: {}", symbol, e.message)
                // Continue with remaining symbols — partial sync is better than full failure
            }
        }

        log.info("Candle sync complete: {}/{} symbols synced", synced, symbols.size)
        return synced
    }

    private fun parseDailyCandle(bar: Any?, token: Long, symbol: String): DailyCandle? {
        if (bar == null) return null
        return try {
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
                volume = hk.volume.toLong(),
            )
        } catch (e: Exception) {
            log.warn("Skipping unparseable daily candle: {}", e.message)
            null
        }
    }

    private fun parseIntradayCandle(bar: Any?, token: Long, symbol: String, interval: String): IntradayCandle? {
        if (bar == null) return null
        return try {
            val hk = bar as com.zerodhatech.models.HistoricalData
            val timestamp = LocalDateTime.parse(hk.timeStamp.substring(0, 19))
            IntradayCandle(
                instrumentToken = token,
                symbol = symbol,
                interval = interval,
                candleTimestamp = timestamp,
                open = hk.open,
                high = hk.high,
                low = hk.low,
                close = hk.close,
                volume = hk.volume.toLong(),
            )
        } catch (e: Exception) {
            log.warn("Skipping unparseable intraday candle: {}", e.message)
            null
        }
    }

    private fun LocalDate.toJavaDate(): Date =
        Date.from(atStartOfDay(ist).toInstant())

    private suspend fun ensureInstrumentCacheLoaded(kiteClient: KiteConnectClient) {
        if (!instrumentCache.isEmpty()) {
            return
        }
        val instruments = withContext(Dispatchers.IO) {
            kiteClient.client().getInstruments("NSE")
        }
        instrumentCache.refresh(instruments)
    }
}
