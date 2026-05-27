package com.tradingtool.core.strategy.wyckoff.phase1

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import com.tradingtool.core.technical.roundTo2
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBarSeriesBuilder
import org.ta4j.core.indicators.CachedIndicator
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.indicators.helpers.VolumeIndicator
import org.ta4j.core.indicators.helpers.LowestValueIndicator
import org.ta4j.core.indicators.SMAIndicator
import org.ta4j.core.indicators.ROCIndicator
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator
import org.ta4j.core.num.Num
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.max

class WyckoffPhase1ScannerEngine {

    fun evaluate(
        config: WyckoffPhase1Config,
        runConfig: WyckoffPhase1RunConfig,
        contexts: List<WyckoffPhase1SymbolContext>,
    ): WyckoffPhase1RunResponse {
        val signalLookbackDays = config.signalLookbackDays.coerceAtLeast(1)
        val rows = contexts.mapNotNull { context ->
            evaluateSymbol(
                config = config,
                asOfDate = runConfig.asOfDate,
                context = context,
                signalLookbackDays = signalLookbackDays,
            )
        }.sortedWith(
            compareByDescending<WyckoffPhase1Row> { row -> row.signal_date }
                .thenByDescending { row -> row.passed_count }
                .thenBy { row -> row.symbol },
        )

        val evaluatedTradingDates = contexts
            .flatMap { context -> context.candles.map { candle -> candle.candleDate } }
            .filter { date -> date <= runConfig.asOfDate }
            .distinct()
            .sortedDescending()
            .take(signalLookbackDays)
            .map { date -> date.toString() }

        return WyckoffPhase1RunResponse(
            rows = rows,
            meta = WyckoffPhase1RunMeta(
                as_of_date = runConfig.asOfDate.toString(),
                evaluated_trading_dates = evaluatedTradingDates,
                universe_count = contexts.size,
                matched_count = rows.size,
            ),
        )
    }

    private data class AlignedMarketDay(
        val candle: DailyCandle,
        val delivery: StockDeliveryDaily?
    )

    private fun evaluateSymbol(
        config: WyckoffPhase1Config,
        asOfDate: LocalDate,
        context: WyckoffPhase1SymbolContext,
        signalLookbackDays: Int,
    ): WyckoffPhase1Row? {
        val deliveryByDate = context.deliveries.associateBy { it.tradingDate }
        val alignedData = context.candles
            .filter { it.candleDate <= asOfDate }
            .sortedBy { it.candleDate }
            .map { candle ->
                AlignedMarketDay(candle, deliveryByDate[candle.candleDate])
            }

        if (alignedData.isEmpty()) return null

        val series = BaseBarSeriesBuilder().withName(context.symbol).build()
        val zone = ZoneId.of("Asia/Kolkata")
        
        alignedData.forEach { day ->
            val zdt = ZonedDateTime.of(day.candle.candleDate.atTime(15, 30), zone)
            val volume = day.delivery?.ttlTrdQnty?.toDouble() ?: day.candle.volume.toDouble()
            series.addBar(zdt, day.candle.open, day.candle.high, day.candle.low, day.candle.close, volume)
        }

        val closePrice = ClosePriceIndicator(series)
        val volumeInd = VolumeIndicator(series)
        
        val sma200 = SMAIndicator(closePrice, SMA_TARGET_WINDOW_DAYS)
        val roc20 = ROCIndicator(closePrice, ROC_LOOKBACK_DAYS)
        
        val volSma60 = SMAIndicator(volumeInd, config.trackA.deliveryVolumeZScore.baselineDays)
        val volStdDev60 = StandardDeviationIndicator(volumeInd, config.trackA.deliveryVolumeZScore.baselineDays)
        
        val spreadPctInd = SpreadPctIndicator(series)
        val avgSpread20d = SMAIndicator(spreadPctInd, config.trackA.absorptionCheck.spreadLookbackDays)

        val minVolLvq = LowestValueIndicator(volumeInd, config.trackA.lvqDq.rollingMinDays)
        val volSma50 = SMAIndicator(volumeInd, config.trackA.lowVolumeHighDeliveryInfo.volumeBaselineDays)

        val lastIndex = series.endIndex
        val startIndex = max(0, lastIndex - signalLookbackDays + 1)
        
        for (i in lastIndex downTo startIndex) {
            val day = alignedData[i]
            val signalDate = day.candle.candleDate
            val signalVolume = volumeInd.getValue(i).doubleValue()
            val signalDeliveryPct = day.delivery?.delivPer

            val isDeliveryPass = signalDeliveryPct != null && signalDeliveryPct >= context.deliveryThresholdPct
            
            val zscore = if (i >= config.trackA.deliveryVolumeZScore.baselineDays && !volStdDev60.getValue(i).isZero) {
                (signalVolume - volSma60.getValue(i).doubleValue()) / volStdDev60.getValue(i).doubleValue()
            } else null
            
            val isZscorePass = config.trackA.deliveryVolumeZScore.enabled && zscore != null && zscore >= config.trackA.deliveryVolumeZScore.minZScore

            // Must pass either delivery threshold OR zscore threshold to be a signal date
            if (!isDeliveryPass && !isZscorePass) continue

            // Evaluate the rest of the qualifiers for this valid signal date
            val densityCount = computeDensityCount(alignedData, i, config.trackA.rollingDensity.lookbackDays, context.deliveryThresholdPct)
            val isDensityPass = config.trackA.rollingDensity.enabled && densityCount >= config.trackA.rollingDensity.minThresholdBreaches

            val minVol = minVolLvq.getValue(i).doubleValue()
            val isLvqNearMin = if (minVol > 0.0) {
                val nearFactor = config.trackA.lvqDq.nearMinPctOfRollingMin / 100.0
                signalVolume <= (minVol / nearFactor)
            } else false
            val isLvqDqPass = config.trackA.lvqDq.enabled && isLvqNearMin && (!config.trackA.lvqDq.requireDeliveryPass || isDeliveryPass)
            
            val lvqHitCount15d = computeLvqHitCount(alignedData, volumeInd, minVolLvq, i, config.trackA.lvqDq, context.deliveryThresholdPct)

            val spreadPct = spreadPctInd.getValue(i).doubleValue()
            val avgSpreadPct20d = avgSpread20d.getValue(i-1)?.doubleValue() // avg spread *before* index. The previous implementation checked avg before index! Wait, the original was `subList(startIndex, signalIndex).average()`. Let's use i-1.
            val isAbsorptionPass = config.trackA.absorptionCheck.enabled && avgSpreadPct20d != null && spreadPct < avgSpreadPct20d

            val roc20Pct = if (i >= ROC_LOOKBACK_DAYS) roc20.getValue(i).doubleValue() else null
            val isRoc20Pass = config.contextFilter.roc20Range.enabled && roc20Pct != null && 
                roc20Pct >= config.contextFilter.roc20Range.minDistancePct && 
                roc20Pct <= config.contextFilter.roc20Range.maxDistancePct

            val sma200Val = sma200.getValue(i).doubleValue()
            val dmaDistancePct = if (sma200Val > 0.0) ((day.candle.close / sma200Val) - 1.0) * 100.0 else null
            val isDma200Pass = config.contextFilter.dma200Proximity.enabled && dmaDistancePct != null &&
                dmaDistancePct >= config.contextFilter.dma200Proximity.minDistancePct &&
                dmaDistancePct <= config.contextFilter.dma200Proximity.maxDistancePct

            val volSma50Val = volSma50.getValue(i).doubleValue()
            val volumeVs50dRatio = if (volSma50Val > 0.0) signalVolume / volSma50Val else null
            val isLowVolumeHighDeliveryInfo = config.trackA.lowVolumeHighDeliveryInfo.enabled && isDeliveryPass && 
                volumeVs50dRatio != null && volumeVs50dRatio <= config.trackA.lowVolumeHighDeliveryInfo.maxVolumeVsBaselineRatio

            val passedCount = listOf(isDeliveryPass, isDensityPass, isZscorePass, isLvqDqPass, isAbsorptionPass, isRoc20Pass, isDma200Pass).count { it }
            
            val accumulationRunLengthDays = computeAccumulationRunLength(alignedData, i, context.deliveryThresholdPct)
            
            return WyckoffPhase1Row(
                symbol = context.symbol,
                signal_date = signalDate.toString(),
                days_ago = lastIndex - i,
                index_key = context.indexKey,
                delivery_pct = signalDeliveryPct?.roundTo2() ?: 0.0,
                delivery_threshold_pct = context.deliveryThresholdPct.roundTo2(),
                delivery_pass = if (isDeliveryPass) 1 else 0,
                density_breach_count_15d = densityCount,
                density_pass = if (isDensityPass) 1 else 0,
                delivery_volume_zscore_60d = zscore?.roundTo2(),
                zscore_pass = if (isZscorePass) 1 else 0,
                lvq_dq_pass = if (isLvqDqPass) 1 else 0,
                lvq_hit_count_15d = lvqHitCount15d,
                spread_pct = spreadPct.roundTo2(),
                avg_spread_pct_20d = avgSpreadPct20d?.roundTo2(),
                absorption_pass = if (isAbsorptionPass) 1 else 0,
                roc20_pct = roc20Pct?.roundTo2(),
                roc20_range_pass = if (isRoc20Pass) 1 else 0,
                sma200_distance_pct = dmaDistancePct?.roundTo2(),
                sma_window_used = minOf(i + 1, SMA_TARGET_WINDOW_DAYS),
                dma200_range_pass = if (isDma200Pass) 1 else 0,
                low_volume_high_delivery_info = if (isLowVolumeHighDeliveryInfo) 1 else 0,
                volume_vs_50d_ratio = volumeVs50dRatio?.roundTo2(),
                passed_count = passedCount,
                accumulation_run_length_days = accumulationRunLengthDays,
            )
        }
        return null
    }

    private fun computeDensityCount(alignedData: List<AlignedMarketDay>, signalIndex: Int, lookbackDays: Int, threshold: Double): Int {
        if (lookbackDays <= 0) return 0
        val startIndex = max(0, signalIndex - lookbackDays + 1)
        var count = 0
        for (i in startIndex..signalIndex) {
            val pct = alignedData[i].delivery?.delivPer ?: continue
            if (pct >= threshold) count++
        }
        return count
    }

    private fun computeLvqHitCount(
        alignedData: List<AlignedMarketDay>, 
        volumeInd: VolumeIndicator, 
        minVolLvq: LowestValueIndicator, 
        signalIndex: Int, 
        lvqConfig: WyckoffPhase1LvqDqConfig, 
        threshold: Double
    ): Int {
        if (!lvqConfig.enabled || lvqConfig.lookbackDays <= 0) return 0
        val startIndex = max(0, signalIndex - lvqConfig.lookbackDays + 1)
        var count = 0
        for (i in startIndex..signalIndex) {
            val isDeliveryPass = (alignedData[i].delivery?.delivPer ?: 0.0) >= threshold
            if (lvqConfig.requireDeliveryPass && !isDeliveryPass) continue
            
            val minVol = minVolLvq.getValue(i).doubleValue()
            if (minVol <= 0.0) continue
            
            val nearFactor = lvqConfig.nearMinPctOfRollingMin / 100.0
            val vol = volumeInd.getValue(i).doubleValue()
            if (vol <= minVol / nearFactor) count++
        }
        return count
    }

    private fun computeAccumulationRunLength(alignedData: List<AlignedMarketDay>, signalIndex: Int, threshold: Double): Int {
        var count = 0
        for (i in signalIndex downTo 0) {
            val pct = alignedData[i].delivery?.delivPer ?: break
            if (pct < threshold) break
            count++
        }
        return count
    }

    private class SpreadPctIndicator(private val series: BarSeries) : CachedIndicator<Num>(series) {
        override fun calculate(index: Int): Num {
            val bar = series.getBar(index)
            val close = bar.closePrice
            if (close.isZero) return series.numOf(0)
            val spread = bar.highPrice.minus(bar.lowPrice)
            return spread.dividedBy(close).multipliedBy(series.numOf(100))
        }
        override fun getUnstableBars(): Int = 0
    }

    companion object {
        private const val ROC_LOOKBACK_DAYS = 20
        private const val SMA_TARGET_WINDOW_DAYS = 200
    }
}
