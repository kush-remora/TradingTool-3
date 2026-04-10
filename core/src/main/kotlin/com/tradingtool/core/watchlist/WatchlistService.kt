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

            val ltp = tick?.ltp
            val priceVs200maPct = if (ltp != null && ind?.sma200 != null && ind.sma200 != 0.0) {
                (ltp - ind.sma200) / ind.sma200 * 100.0
            } else null

            val volumeVsAvg = if (tick != null && ind?.avgVol20d != null && ind.avgVol20d != 0.0) {
                tick.volume.toDouble() / ind.avgVol20d
            } else null

            WatchlistRow(
                symbol = stock.symbol,
                instrumentToken = stock.instrumentToken,
                companyName = stock.companyName,
                exchange = stock.exchange,
                sector = null, // not in Stock model yet
                ltp = ltp,
                changePercent = tick?.changePercent,
                sma50 = ind?.sma50,
                sma200 = ind?.sma200,
                priceVs200maPct = priceVs200maPct,
                rsi14 = ind?.rsi14,
                roc1w = ind?.roc1w,
                roc3m = ind?.roc3m,
                macdSignal = ind?.macdSignal,
                drawdownPct = ind?.drawdownPct,
                maxDd1y = ind?.maxDd1y,
                volumeVsAvg = volumeVsAvg,
            )
        }
    }
}
