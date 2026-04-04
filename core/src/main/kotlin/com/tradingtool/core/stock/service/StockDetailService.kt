package com.tradingtool.core.stock.service

import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.model.stock.DayDetail
import com.tradingtool.core.model.stock.StockDetailResponse
import com.tradingtool.core.technical.calculateRsi
import com.tradingtool.core.technical.getNullableDouble
import com.tradingtool.core.technical.toTa4jSeriesFromKite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.ta4j.core.indicators.SMAIndicator
import org.ta4j.core.indicators.helpers.VolumeIndicator
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * Fetches N trading days of enriched OHLCV data for a stock from Kite.
 *
 * Expands the calendar lookback based on requested [days] to account for weekends/holidays.
 * Also includes warmup history to keep RSI aligned with standard ta4j behavior.
 */
class StockDetailService(
    private val stockHandler: StockJdbiHandler,
) {
    private val log = LoggerFactory.getLogger(StockDetailService::class.java)
    private val ist = ZoneId.of("Asia/Kolkata")
    private val indicatorWarmupYears: Long = 5

    suspend fun getDetail(symbol: String, kiteClient: KiteConnectClient, days: Int = 7): StockDetailResponse? {
        val stock = stockHandler.read { it.getBySymbol(symbol, "NSE") } ?: return null

        val today = LocalDate.now(ist)
        val fromDate = Date.from(today.minusYears(indicatorWarmupYears).atStartOfDay(ist).toInstant())
        val toDate = Date.from(today.atStartOfDay(ist).toInstant())

        val history = withContext(Dispatchers.IO) {
            kiteClient.client()
                .getHistoricalData(fromDate, toDate, stock.instrumentToken.toString(), "day", false, false)
        }

        val series = history.dataArrayList.toTa4jSeriesFromKite(symbol)
        if (series.barCount < 2) {
            log.warn("Not enough bars for {} ({} bars)", symbol, series.barCount)
            return StockDetailResponse(symbol = symbol, exchange = stock.exchange, avgVolume20d = null, days = emptyList())
        }

        val lastIndex = series.endIndex
        val volumeIndicator = VolumeIndicator(series)
        val sma20Indicator = if (series.barCount >= 20) SMAIndicator(volumeIndicator, 20) else null
        val rsiIndicator = if (series.barCount >= 14) series.calculateRsi(14) else null

        // Need index >= 1 to compute daily change vs previous close.
        val endIdx = series.endIndex
        val startIdx = maxOf(endIdx - (days - 1), 1)

        val enrichedDays = (startIdx..endIdx).map { index ->
            val bar = series.getBar(index)
            val prevClose = series.getBar(index - 1).closePrice.doubleValue()
            val close = bar.closePrice.doubleValue()
            val volume = bar.volume.doubleValue().toLong()
            val dailyAvgVol = sma20Indicator?.getNullableDouble(index)

            DayDetail(
                date = bar.endTime.toLocalDate().toString(),
                open = bar.openPrice.doubleValue(),
                high = bar.highPrice.doubleValue(),
                low = bar.lowPrice.doubleValue(),
                close = close,
                volume = volume,
                dailyChangePct = if (prevClose > 0.0) (close - prevClose) / prevClose * 100.0 else null,
                rsi14 = rsiIndicator?.getNullableDouble(index),
                volRatio = if (dailyAvgVol != null && dailyAvgVol > 0.0) volume.toDouble() / dailyAvgVol else null,
            )
        }

        return StockDetailResponse(
            symbol = symbol,
            exchange = stock.exchange,
            avgVolume20d = sma20Indicator?.getNullableDouble(endIdx),
            days = enrichedDays,
        )
    }
}
