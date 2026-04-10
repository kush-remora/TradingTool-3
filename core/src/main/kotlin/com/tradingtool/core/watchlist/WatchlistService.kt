package com.tradingtool.core.watchlist

import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.kite.TickStore
import com.tradingtool.core.model.watchlist.ComputedIndicators
import com.tradingtool.core.model.watchlist.WatchlistRow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Merges three data sources into a flat [WatchlistRow] list for a given tag:
 *   1. Stock metadata (symbol, exchange) — from Postgres
 *   2. Computed indicators (SMA, RSI, MACD, …) — from Redis L1 / Postgres L2
 *   3. Live ticks (LTP, change%) — from in-memory [TickStore] fed by Kite WebSocket
 *
 * Live fields are optional: if ticker is disconnected or the tick store is cold,
 * [ltp] and [changePercent] will be null and the row still returns with indicators.
 */
class WatchlistService(
    private val stockHandler: StockJdbiHandler,
    private val indicatorService: IndicatorService,
    private val tickStore: TickStore,
) {
    suspend fun getRows(tag: String?): List<WatchlistRow> {
        // stocks and indicators both depend only on `tag` — fetch in parallel.
        val (stocks, indicatorsList) = coroutineScope {
            val stocksJob = async {
                if (tag.isNullOrBlank()) stockHandler.read { it.listAll() }
                else stockHandler.read { it.listByTagName(tag) }
            }
            val indicatorsJob = async {
                indicatorService.getIndicatorsForTag(tag?.trim()?.takeIf { it.isNotEmpty() })
            }
            stocksJob.await() to indicatorsJob.await()
        }
        if (stocks.isEmpty()) return emptyList()

        val indicators: Map<Long, ComputedIndicators> = indicatorsList.associateBy { it.instrumentToken }

        return stocks.map { stock ->
            val ind = indicators[stock.instrumentToken]
            val tick = tickStore.get(stock.instrumentToken)

            val effectiveLtp = tick?.ltp ?: ind?.lastClose
            val priceVs200maPct = if (effectiveLtp != null && ind?.sma200 != null && ind.sma200 != 0.0) {
                (effectiveLtp - ind.sma200) / ind.sma200 * 100.0
            } else null

            val liveVolumeVsAvg = if (tick != null && ind?.avgVol20d != null && ind.avgVol20d != 0.0) {
                tick.volume.toDouble() / ind.avgVol20d
            } else {
                null
            }
            val volumeVsAvg = liveVolumeVsAvg ?: ind?.volumeVsAvg20d

            val high60d = ind?.high60d
            val low60d = ind?.low60d
            val rangePosition60dPct = if (
                effectiveLtp != null &&
                high60d != null &&
                low60d != null &&
                high60d > low60d
            ) {
                ((effectiveLtp - low60d) / (high60d - low60d)) * 100.0
            } else {
                null
            }

            val trendState = deriveTrendState(
                ltp = effectiveLtp,
                sma50 = ind?.sma50,
                sma200 = ind?.sma200,
            )

            val atr14Pct = if (ind?.atr14 != null && effectiveLtp != null && effectiveLtp != 0.0) {
                (ind.atr14 / effectiveLtp) * 100.0
            } else {
                null
            }

            val gapTo3mLowPct = if (effectiveLtp != null && ind?.low3m != null && ind.low3m != 0.0) {
                ((effectiveLtp - ind.low3m) / ind.low3m) * 100.0
            } else {
                null
            }

            val gapTo3mHighPct = if (effectiveLtp != null && ind?.high3m != null && ind.high3m != 0.0) {
                ((ind.high3m - effectiveLtp) / ind.high3m) * 100.0
            } else {
                null
            }

            WatchlistRow(
                symbol = stock.symbol,
                instrumentToken = stock.instrumentToken,
                companyName = stock.companyName,
                exchange = stock.exchange,
                sector = null, // not in Stock model yet
                ltp = effectiveLtp,
                changePercent = tick?.changePercent,
                sma50 = ind?.sma50,
                sma200 = ind?.sma200,
                trendState = trendState,
                high60d = high60d,
                low60d = low60d,
                rangePosition60dPct = rangePosition60dPct,
                gapTo3mLowPct = gapTo3mLowPct,
                gapTo3mHighPct = gapTo3mHighPct,
                rsiAtHigh60d = ind?.rsiAtHigh60d,
                rsiAtLow60d = ind?.rsiAtLow60d,
                volumeAtHigh60d = ind?.volumeAtHigh60d,
                volumeAtLow60d = ind?.volumeAtLow60d,
                priceVs200maPct = priceVs200maPct,
                rsi14 = ind?.rsi14,
                atr14 = ind?.atr14,
                atr14Pct = atr14Pct,
                roc1w = ind?.roc1w,
                roc3m = ind?.roc3m,
                macdSignal = ind?.macdSignal,
                drawdownPct = ind?.drawdownPct,
                maxDd1y = ind?.maxDd1y,
                volumeVsAvg = volumeVsAvg,
            )
        }
    }

    private fun deriveTrendState(
        ltp: Double?,
        sma50: Double?,
        sma200: Double?,
    ): String? {
        if (ltp == null || sma50 == null || sma200 == null) return null

        val above50 = ltp >= sma50
        val above200 = ltp >= sma200

        return when {
            above50 && above200 -> "ABOVE_BOTH"
            above50 -> "ABOVE_50_ONLY"
            above200 -> "ABOVE_200_ONLY"
            else -> "BELOW_BOTH"
        }
    }
}
