package com.tradingtool.core.strategy.adaptivebreakout

import com.tradingtool.core.candle.DailyCandle

/**
 * Calculates point-in-time market and stock trend context for a breakout.
 * This is diagnostic context only; the backtest does not filter on it yet.
 */
internal object BreakoutTrendAlignmentAnalyzer {

    fun analyze(
        stockCandles: List<DailyCandle>,
        marketCandles: List<DailyCandle>,
    ): BreakoutTrendAlignmentResult {
        val stock = stockCandles.sortedBy(DailyCandle::candleDate)
        val market = marketCandles
            .filter { candle -> !candle.candleDate.isAfter(stock.lastOrNull()?.candleDate ?: return unavailable()) }
            .sortedBy(DailyCandle::candleDate)
        val stockMetrics = trendMetrics(stock)
        val marketMetrics = trendMetrics(market)
        if (stockMetrics == null || marketMetrics == null) return unavailable()

        val stockRelativeStrength3mPct = relativeStrengthPct(stockMetrics.return3mPct, marketMetrics.return3mPct)
        val stockRelativeStrength6mPct = relativeStrengthPct(stockMetrics.return6mPct, marketMetrics.return6mPct)
        val marketAligned = marketMetrics.close > marketMetrics.sma200 && marketMetrics.sma200SlopePct > 0.0
        val stockAligned = stockMetrics.close > stockMetrics.sma200 &&
            stockMetrics.sma200SlopePct > 0.0 &&
            stockRelativeStrength3mPct > 0.0 &&
            stockRelativeStrength6mPct > 0.0
        val aligned = marketAligned && stockAligned

        return BreakoutTrendAlignmentResult(
            available = true,
            marketAligned = marketAligned,
            stockAligned = stockAligned,
            aligned = aligned,
            marketClose = marketMetrics.close,
            marketSma200 = marketMetrics.sma200,
            marketSma200SlopePct = marketMetrics.sma200SlopePct,
            stockClose = stockMetrics.close,
            stockSma200 = stockMetrics.sma200,
            stockSma200SlopePct = stockMetrics.sma200SlopePct,
            stockRelativeStrength3mPct = stockRelativeStrength3mPct,
            stockRelativeStrength6mPct = stockRelativeStrength6mPct,
            explanation = if (aligned) {
                "Market trend and stock trend are aligned; the stock is above a rising 200-day average and outperforming NIFTY over 3 and 6 months."
            } else {
                "Market and stock trend conditions are not fully aligned."
            },
        )
    }

    private fun trendMetrics(candles: List<DailyCandle>): TrendMetrics? {
        if (candles.size < MINIMUM_SESSIONS) return null
        val sma200 = candles.takeLast(SMA_PERIOD).map(DailyCandle::close).average()
        val priorSma200 = candles.dropLast(SLOPE_LOOKBACK_SESSIONS)
            .takeLast(SMA_PERIOD)
            .map(DailyCandle::close)
            .average()
        val return3mPct = returnPct(candles, THREE_MONTH_SESSIONS) ?: return null
        val return6mPct = returnPct(candles, SIX_MONTH_SESSIONS) ?: return null
        return TrendMetrics(
            close = candles.last().close,
            sma200 = sma200,
            sma200SlopePct = percentChange(priorSma200, sma200),
            return3mPct = return3mPct,
            return6mPct = return6mPct,
        )
    }

    private fun returnPct(candles: List<DailyCandle>, sessions: Int): Double? {
        val earlier = candles.getOrNull(candles.lastIndex - sessions)?.close ?: return null
        val latest = candles.last().close
        return percentChange(earlier, latest)
    }

    private fun relativeStrengthPct(stockReturnPct: Double, marketReturnPct: Double): Double =
        stockReturnPct - marketReturnPct

    private fun percentChange(start: Double, end: Double): Double =
        if (start == 0.0) 0.0 else (end / start - 1.0) * 100.0

    private fun unavailable(): BreakoutTrendAlignmentResult = BreakoutTrendAlignmentResult(
        available = false,
        marketAligned = null,
        stockAligned = null,
        aligned = null,
        marketClose = null,
        marketSma200 = null,
        marketSma200SlopePct = null,
        stockClose = null,
        stockSma200 = null,
        stockSma200SlopePct = null,
        stockRelativeStrength3mPct = null,
        stockRelativeStrength6mPct = null,
        explanation = "Trend-alignment history is unavailable.",
    )

    private data class TrendMetrics(
        val close: Double,
        val sma200: Double,
        val sma200SlopePct: Double,
        val return3mPct: Double,
        val return6mPct: Double,
    )

    private const val SMA_PERIOD = 200
    private const val SLOPE_LOOKBACK_SESSIONS = 20
    private const val THREE_MONTH_SESSIONS = 63
    private const val SIX_MONTH_SESSIONS = 126
    private const val MINIMUM_SESSIONS = SMA_PERIOD + SLOPE_LOOKBACK_SESSIONS + SIX_MONTH_SESSIONS
}
