package com.tradingtool.core.strategy.volume

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.technical.roundTo2

object VolumeAnalyzer {
    private const val CRORE_DIVISOR: Double = 10_000_000.0
    private const val BASELINE_WINDOW: Int = 20
    private const val RECENT_3D_WINDOW: Int = 3
    private const val RECENT_5D_WINDOW: Int = 5
    private const val RECENT_10D_WINDOW: Int = 10
    private const val SOFT_ELEVATED_VOLUME_RATIO: Double = 1.5
    private const val MIN_HISTORY_BARS: Int = BASELINE_WINDOW + RECENT_10D_WINDOW

    fun analyze(candles: List<DailyCandle>): VolumeAnalysisResult? {
        val sortedCandles = candles.sortedBy { candle -> candle.candleDate }
        if (sortedCandles.size < MIN_HISTORY_BARS) {
            return null
        }

        val latest = sortedCandles.last()
        val previous = sortedCandles[sortedCandles.lastIndex - 1]
        val prior20 = sortedCandles.dropLast(1).takeLast(BASELINE_WINDOW)
        if (prior20.size < BASELINE_WINDOW) {
            return null
        }

        val avgVolume20d = prior20.map { candle -> candle.volume.toDouble() }.average()
        if (!avgVolume20d.isFinite() || avgVolume20d <= 0.0) {
            return null
        }

        val avgTradedValueCr20d = prior20
            .map { candle -> (candle.close * candle.volume) / CRORE_DIVISOR }
            .average()
            .roundTo2()

        val todayVolumeRatio = (latest.volume / avgVolume20d).roundTo2()

        val recent3 = sortedCandles.takeLast(RECENT_3D_WINDOW)
        val prior20BeforeRecent3 = sortedCandles.dropLast(RECENT_3D_WINDOW).takeLast(BASELINE_WINDOW)
        val recent3dAvgVolumeRatio = if (recent3.size == RECENT_3D_WINDOW && prior20BeforeRecent3.size == BASELINE_WINDOW) {
            val recent3Avg = recent3.map { candle -> candle.volume.toDouble() }.average()
            val baseline = prior20BeforeRecent3.map { candle -> candle.volume.toDouble() }.average()
            if (baseline > 0.0) (recent3Avg / baseline).roundTo2() else 0.0
        } else {
            0.0
        }

        val recent5 = sortedCandles.takeLast(RECENT_5D_WINDOW)
        val prior20BeforeRecent5 = sortedCandles.dropLast(RECENT_5D_WINDOW).takeLast(BASELINE_WINDOW)
        val volumeRatios5d = if (recent5.size == RECENT_5D_WINDOW && prior20BeforeRecent5.size == BASELINE_WINDOW) {
            val baseline = prior20BeforeRecent5.map { candle -> candle.volume.toDouble() }.average()
            if (baseline > 0.0) {
                recent5.map { candle -> candle.volume / baseline }
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }

        val recent5dMaxVolumeRatio = (volumeRatios5d.maxOrNull() ?: 0.0).roundTo2()
        val spikePersistenceDays5d = volumeRatios5d.count { ratio -> ratio >= 2.0 }

        val recent10 = sortedCandles.takeLast(RECENT_10D_WINDOW)
        val prior20BeforeRecent10 = sortedCandles.dropLast(RECENT_10D_WINDOW).takeLast(BASELINE_WINDOW)
        val recent10dAvgVolumeRatio: Double
        val elevatedVolumeDays10d: Int
        if (recent10.size == RECENT_10D_WINDOW && prior20BeforeRecent10.size == BASELINE_WINDOW) {
            val baseline = prior20BeforeRecent10.map { candle -> candle.volume.toDouble() }.average()
            if (baseline > 0.0) {
                val recent10Ratios = recent10.map { candle -> candle.volume / baseline }
                recent10dAvgVolumeRatio = recent10Ratios.average().roundTo2()
                elevatedVolumeDays10d = recent10Ratios.count { ratio -> ratio >= SOFT_ELEVATED_VOLUME_RATIO }
            } else {
                recent10dAvgVolumeRatio = 0.0
                elevatedVolumeDays10d = 0
            }
        } else {
            recent10dAvgVolumeRatio = 0.0
            elevatedVolumeDays10d = 0
        }

        val todayPriceChangePct = if (previous.close > 0.0) {
            (((latest.close - previous.close) / previous.close) * 100.0).roundTo2()
        } else {
            0.0
        }

        val close3BarsAgo = sortedCandles.getOrNull(sortedCandles.lastIndex - RECENT_3D_WINDOW)?.close
        val priceReturn3dPct = if (close3BarsAgo != null && close3BarsAgo > 0.0) {
            (((latest.close - close3BarsAgo) / close3BarsAgo) * 100.0).roundTo2()
        } else {
            0.0
        }

        val breakoutBaseline = sortedCandles.dropLast(1).takeLast(BASELINE_WINDOW)
        val breakoutAbove20dHigh = breakoutBaseline.isNotEmpty() && latest.close > breakoutBaseline.maxOf { candle -> candle.high }

        return VolumeAnalysisResult(
            asOfDate = latest.candleDate,
            close = latest.close.roundTo2(),
            avgVolume20d = avgVolume20d.roundTo2(),
            avgTradedValueCr20d = avgTradedValueCr20d,
            todayVolumeRatio = todayVolumeRatio,
            recent3dAvgVolumeRatio = recent3dAvgVolumeRatio,
            recent5dMaxVolumeRatio = recent5dMaxVolumeRatio,
            spikePersistenceDays5d = spikePersistenceDays5d,
            recent10dAvgVolumeRatio = recent10dAvgVolumeRatio,
            elevatedVolumeDays10d = elevatedVolumeDays10d,
            todayPriceChangePct = todayPriceChangePct,
            priceReturn3dPct = priceReturn3dPct,
            breakoutAbove20dHigh = breakoutAbove20dHigh,
        )
    }
}
