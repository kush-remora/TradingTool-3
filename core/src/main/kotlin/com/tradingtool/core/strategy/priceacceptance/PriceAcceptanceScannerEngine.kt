package com.tradingtool.core.strategy.priceacceptance

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate

internal data class PriceAcceptanceEvaluation(
    val anchorDate: LocalDate,
    val open: Double,
    val close: Double,
    val bodyLow: Double,
    val bodyHigh: Double,
    val bodyRangePct: Double,
    val priorSessionCount: Int,
    val closeHits20: Int,
    val closeHitRate20Pct: Double,
    val closeHits40: Int,
    val closeHitRate40Pct: Double,
    val closeHits60: Int,
    val closeHitRate60Pct: Double,
    val closeHits80: Int,
    val closeHitRate80Pct: Double,
    val closeHits100: Int,
    val closeHitRate100Pct: Double,
)

internal object PriceAcceptanceScannerEngine {
    private const val MAX_LOOKBACK_SESSIONS = 100

    fun evaluate(candles: List<DailyCandle>, asOfDate: LocalDate): PriceAcceptanceEvaluation? {
        val sortedCandles = candles
            .filter { candle -> !candle.candleDate.isAfter(asOfDate) }
            .distinctBy(DailyCandle::candleDate)
            .sortedBy(DailyCandle::candleDate)
        val anchorIndex = sortedCandles.lastIndex
        if (anchorIndex < 0) return null

        val anchor = sortedCandles[anchorIndex]
        if (anchor.open <= 0.0 || anchor.close <= 0.0) return null

        val priorCandles = sortedCandles
            .subList(0, anchorIndex)
            .takeLast(MAX_LOOKBACK_SESSIONS)
        if (priorCandles.isEmpty()) return null

        val bodyLow = minOf(anchor.open, anchor.close)
        val bodyHigh = maxOf(anchor.open, anchor.close)
        val bodyRangePct = ((bodyHigh - bodyLow) / bodyLow) * 100.0

        fun hitCount(lookback: Int): Int {
            return priorCandles.takeLast(lookback).count { candle ->
                candle.close in bodyLow..bodyHigh
            }
        }

        fun hitRatePct(lookback: Int, hits: Int): Double {
            val sampleSize = priorCandles.size.coerceAtMost(lookback)
            return hits.toDouble() / sampleSize.toDouble() * 100.0
        }

        val closeHits20 = hitCount(20)
        val closeHits40 = hitCount(40)
        val closeHits60 = hitCount(60)
        val closeHits80 = hitCount(80)
        val closeHits100 = hitCount(100)

        return PriceAcceptanceEvaluation(
            anchorDate = anchor.candleDate,
            open = anchor.open,
            close = anchor.close,
            bodyLow = bodyLow,
            bodyHigh = bodyHigh,
            bodyRangePct = bodyRangePct,
            priorSessionCount = priorCandles.size,
            closeHits20 = closeHits20,
            closeHitRate20Pct = hitRatePct(20, closeHits20),
            closeHits40 = closeHits40,
            closeHitRate40Pct = hitRatePct(40, closeHits40),
            closeHits60 = closeHits60,
            closeHitRate60Pct = hitRatePct(60, closeHits60),
            closeHits80 = closeHits80,
            closeHitRate80Pct = hitRatePct(80, closeHits80),
            closeHits100 = closeHits100,
            closeHitRate100Pct = hitRatePct(100, closeHits100),
        )
    }
}
