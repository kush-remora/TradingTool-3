package com.tradingtool.core.stock.service

import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.model.stock.DayDetail
import com.tradingtool.core.model.stock.StockDetailResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBarSeriesBuilder
import org.ta4j.core.indicators.RSIIndicator
import org.ta4j.core.indicators.SMAIndicator
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.indicators.helpers.VolumeIndicator
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.slf4j.LoggerFactory
import java.util.Date

/**
 * Fetches 7 trading days of enriched OHLCV data for a stock from Kite.
 *
 * Fetches 45 calendar days to guarantee ≥30 trading days (accounts for weekends/holidays).
 * From those 30 bars: computes 20-day avg volume and RSI14 warmup, then returns the last 7 days
 * enriched with daily % change, RSI14, and volume vs 20-day average ratio.
 */
class StockDetailService(
    private val stockHandler: StockJdbiHandler,
) {
    private val log = LoggerFactory.getLogger(StockDetailService::class.java)
    private val ist = ZoneId.of("Asia/Kolkata")

    suspend fun getDetail(symbol: String, kiteClient: KiteConnectClient): StockDetailResponse? {
        val stock = stockHandler.read { it.getBySymbol(symbol, "NSE") } ?: return null

        val today = LocalDate.now(ist)
        // 45 calendar days ≈ 30 trading days (enough for RSI14 warmup + 20d avg volume + 7 visible days)
        val fromDate = Date.from(today.minusDays(45).atStartOfDay(ist).toInstant())
        val toDate = Date.from(today.atStartOfDay(ist).toInstant())

        val history = withContext(Dispatchers.IO) {
            kiteClient.client()
                .getHistoricalData(fromDate, toDate, stock.instrumentToken.toString(), "day", false, false)
        }

        val series = buildBarSeries(history.dataArrayList, symbol)
        if (series.barCount < 2) {
            log.warn("Not enough bars for {} ({} bars)", symbol, series.barCount)
            return StockDetailResponse(symbol = symbol, avgVolume20d = null, days = emptyList())
        }

        val closePrice = ClosePriceIndicator(series)
        val volumeIndicator = VolumeIndicator(series)

        val avgVol20d = if (series.barCount >= 20)
            SMAIndicator(volumeIndicator, 20).getValue(series.endIndex).doubleValue()
        else null

        // RSI needs 15+ bars to produce a meaningful value for index 14 (1 warmup + 14 periods)
        val rsiIndicator = if (series.barCount >= 15) RSIIndicator(closePrice, 14) else null

        // Need index >= 1 to compute daily change vs previous close
        val endIdx = series.endIndex
        val startIdx = maxOf(endIdx - 6, 1)

        val days = (startIdx..endIdx).map { i ->
            val bar = series.getBar(i)
            val prevClose = series.getBar(i - 1).closePrice.doubleValue()
            val close = bar.closePrice.doubleValue()
            val vol = bar.volume.doubleValue().toLong()

            DayDetail(
                date = bar.endTime.toLocalDate().toString(),
                open = bar.openPrice.doubleValue(),
                high = bar.highPrice.doubleValue(),
                low = bar.lowPrice.doubleValue(),
                close = close,
                volume = vol,
                dailyChangePct = if (prevClose > 0) (close - prevClose) / prevClose * 100.0 else null,
                rsi14 = rsiIndicator?.getValue(i)?.doubleValue(),
                volRatio = if (avgVol20d != null && avgVol20d > 0) vol / avgVol20d else null,
            )
        }

        return StockDetailResponse(symbol = symbol, avgVolume20d = avgVol20d, days = days)
    }

    // Same parsing logic as IndicatorService.buildBarSeries — Kite timestamps are always IST.
    private fun buildBarSeries(historicalData: List<*>, symbol: String): BarSeries {
        val series = BaseBarSeriesBuilder().withName(symbol).build()
        for (bar in historicalData) {
            if (bar == null) continue
            val hk = bar as com.zerodhatech.models.HistoricalData
            val localDt = LocalDateTime.parse(hk.timeStamp.substring(0, 19))
            val zdt: ZonedDateTime = localDt.atZone(ist)
            series.addBar(zdt, hk.open, hk.high, hk.low, hk.close, hk.volume)
        }
        return series
    }
}
