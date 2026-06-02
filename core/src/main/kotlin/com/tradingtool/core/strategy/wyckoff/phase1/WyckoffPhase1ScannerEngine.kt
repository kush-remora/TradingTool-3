package com.tradingtool.core.strategy.wyckoff.phase1

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import com.tradingtool.core.technical.roundTo2
import org.slf4j.LoggerFactory
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBarSeriesBuilder
import org.ta4j.core.indicators.CachedIndicator
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.indicators.helpers.VolumeIndicator
import org.ta4j.core.indicators.helpers.LowestValueIndicator
import org.ta4j.core.indicators.SMAIndicator
import org.ta4j.core.indicators.ROCIndicator
import org.ta4j.core.indicators.RSIIndicator
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator
import org.ta4j.core.num.Num
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.max

class WyckoffPhase1ScannerEngine {

    companion object {
        private const val ROC_LOOKBACK_DAYS = 20
        private const val SMA_TARGET_WINDOW_DAYS = 200
        private const val RSI_LOOKBACK_DAYS = 14
        private val log = LoggerFactory.getLogger(WyckoffPhase1ScannerEngine::class.java)
    }

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
                applyStrictBaseFilter = runConfig.applyStrictBaseFilter,
            )
        }.sortedWith(
            compareByDescending<WyckoffPhase1Row> { row -> row.signal_date }
                .thenByDescending { row -> row.tier_80_count_15d }
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
        applyStrictBaseFilter: Boolean,
    ): WyckoffPhase1Row? {
        if (context.symbol == "BEL" || context.symbol == "HINDUNILVR") {
            log.info("DEBUG ${context.symbol} evaluateSymbol: asOfDate=$asOfDate, total_raw_candles=${context.candles.size}")
        }

        val deliveryByDate = context.deliveries.associateBy { it.tradingDate }
        val alignedData = context.candles
            .filter { it.candleDate <= asOfDate }
            .sortedBy { it.candleDate }
            .map { candle ->
                AlignedMarketDay(candle, deliveryByDate[candle.candleDate])
            }

        if (context.symbol == "BEL" || context.symbol == "HINDUNILVR") {
            log.info("DEBUG ${context.symbol} alignedData size=${alignedData.size}, latest_aligned_date=${alignedData.lastOrNull()?.candle?.candleDate}")
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
        val deliveryVolumeInd = DeliveryVolumeIndicator(series, alignedData)
        
        val sma50 = SMAIndicator(closePrice, 50)
        val sma200 = SMAIndicator(closePrice, SMA_TARGET_WINDOW_DAYS)
        val roc20 = ROCIndicator(closePrice, ROC_LOOKBACK_DAYS)
        val rsiInd = RSIIndicator(closePrice, RSI_LOOKBACK_DAYS)
        val lowest52w = LowestValueIndicator(closePrice, 252)
        
        val volSma60 = SMAIndicator(deliveryVolumeInd, config.trackA.deliveryVolumeZScore.baselineDays)
        val volStdDev60 = StandardDeviationIndicator(deliveryVolumeInd, config.trackA.deliveryVolumeZScore.baselineDays)
        
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
            val signalDeliveryVolume = deliveryVolumeInd.getValue(i).doubleValue()
            val signalDeliveryPct = day.delivery?.delivPer

            val isDeliveryPass = signalDeliveryPct != null && signalDeliveryPct >= context.deliveryThresholdPct
            
            val zscore = if (i >= config.trackA.deliveryVolumeZScore.baselineDays && !volStdDev60.getValue(i).isZero) {
                (signalDeliveryVolume - volSma60.getValue(i).doubleValue()) / volStdDev60.getValue(i).doubleValue()
            } else null
            
            val isZscorePass = config.trackA.deliveryVolumeZScore.enabled && zscore != null && zscore >= config.trackA.deliveryVolumeZScore.minZScore

            // Must pass either delivery threshold OR zscore threshold to be a signal date
            if (!isDeliveryPass && !isZscorePass) {
                if ((context.symbol == "BEL" || context.symbol == "HINDUNILVR") && signalDate.toString() >= "2026-05-27") {
                    log.info("DEBUG ${context.symbol} failed isDeliveryPass and isZscorePass on $signalDate. delivPct=$signalDeliveryPct threshold=${context.deliveryThresholdPct}")
                }
                continue
            }

            val tiers = computeTierCounts(alignedData, i, 15)
            val lvqHitCount15d = computeLvqHitCount(alignedData, volumeInd, minVolLvq, i, config.trackA.lvqDq, context.deliveryThresholdPct)
            
            val spreadPct = spreadPctInd.getValue(i).doubleValue()
            val avgSpreadPct20d = avgSpread20d.getValue(i-1)?.doubleValue()
            val roc20Pct = if (i >= ROC_LOOKBACK_DAYS) roc20.getValue(i).doubleValue() else null
            val rsiPct = if (i >= RSI_LOOKBACK_DAYS) rsiInd.getValue(i).doubleValue() else null

            val sma50Val = sma50.getValue(i).doubleValue()
            val dma50DistancePct = if (sma50Val > 0.0) ((day.candle.close / sma50Val) - 1.0) * 100.0 else null

            val sma200Val = sma200.getValue(i).doubleValue()
            val dma200DistancePct = if (sma200Val > 0.0) ((day.candle.close / sma200Val) - 1.0) * 100.0 else null

            if (applyStrictBaseFilter) {
                val strict = config.strictFilter
                
                if (context.symbol == "BEL" || context.symbol == "HINDUNILVR") {
                    log.info("DEBUG ${context.symbol} $signalDate: dma200DistancePct=$dma200DistancePct, roc20Pct=$roc20Pct, spreadPct=$spreadPct, avgSpreadPct20d=$avgSpreadPct20d, t55=${tiers.t55}, isDeliveryPass=$isDeliveryPass")
                }

                if (strict.dma200Proximity.enabled) {
                    if (dma200DistancePct == null) continue
                    if (dma200DistancePct < strict.dma200Proximity.minDistancePct || dma200DistancePct > strict.dma200Proximity.maxDistancePct) {
                        if (context.symbol == "BEL" || context.symbol == "HINDUNILVR") log.info("DEBUG ${context.symbol} $signalDate failed dma200Proximity")
                        continue
                    }
                }
                
                if (strict.roc20Proximity.enabled) {
                    if (roc20Pct == null) continue
                    if (roc20Pct < strict.roc20Proximity.minPct || roc20Pct > strict.roc20Proximity.maxPct) {
                        if (context.symbol == "BEL" || context.symbol == "HINDUNILVR") log.info("DEBUG ${context.symbol} $signalDate failed roc20Proximity")
                        continue
                    }
                }
                
                if (strict.movingAverageCompression.enabled && dma200DistancePct != null && dma200DistancePct >= 0.0) {
                    if (dma50DistancePct == null || dma200DistancePct == null) continue
                    if (kotlin.math.abs(dma50DistancePct - dma200DistancePct) > strict.movingAverageCompression.maxDma50To200DistancePct) {
                        if (context.symbol == "BEL" || context.symbol == "HINDUNILVR") log.info("DEBUG ${context.symbol} $signalDate failed movingAverageCompression")
                        continue
                    }
                }
                
                if (strict.volatilityContraction.enabled && strict.volatilityContraction.requireSpreadLessThan20dAverage) {
                    if (avgSpreadPct20d == null) continue

                    val isBelowDma200 = dma200DistancePct != null && dma200DistancePct < 0.0

                    var isBypassed = false
                    if (isBelowDma200 && strict.volatilityContraction.bypassIfBelowDma200AndDeliveryHigh) {
                        val pct = alignedData[i].delivery?.delivPer
                        if (pct != null && pct >= strict.volatilityContraction.bypassDeliveryThreshold) {
                            isBypassed = true
                        }
                    }

                    if (!isBypassed) {
                        val maxAllowedSpread = if (isBelowDma200 && strict.volatilityContraction.relaxIfBelowDma200) {
                            avgSpreadPct20d * strict.volatilityContraction.relaxedMultiplierIfBelowDma200
                        } else {
                            avgSpreadPct20d
                        }

                        if (spreadPct >= maxAllowedSpread) {
                            if (context.symbol == "BEL" || context.symbol == "HINDUNILVR") log.info("DEBUG ${context.symbol} $signalDate failed volatilityContraction: spread=$spreadPct max=$maxAllowedSpread")
                            continue
                        }
                    }
                }
                
                if (strict.accumulationDensity.enabled) {
                    if (tiers.t55 < strict.accumulationDensity.minTier55Count) {
                        if (context.symbol == "BEL" || context.symbol == "HINDUNILVR") log.info("DEBUG ${context.symbol} $signalDate failed accumulationDensity: t55=${tiers.t55} min=${strict.accumulationDensity.minTier55Count}")
                        continue
                    }
                }
            }

            val low52wVal = lowest52w.getValue(i).doubleValue()
            val distanceFrom52wLowPct = if (low52wVal > 0.0) ((day.candle.close / low52wVal) - 1.0) * 100.0 else null

            val volSma50Val = volSma50.getValue(i).doubleValue()
            val volumeVs50dRatio = if (volSma50Val > 0.0) signalVolume / volSma50Val else null

            val accumulationRunLengthDays = computeAccumulationRunLength(alignedData, i, context.deliveryThresholdPct)
            
            return WyckoffPhase1Row(
                symbol = context.symbol,
                company_name = context.companyName,
                signal_date = signalDate.toString(),
                days_ago = lastIndex - i,
                index_key = context.indexKey,
                tier_80_count_15d = tiers.t80,
                tier_70_count_15d = tiers.t70,
                tier_65_count_15d = tiers.t65,
                tier_60_count_15d = tiers.t60,
                tier_55_count_15d = tiers.t55,
                delivery_volume_zscore_60d = zscore?.roundTo2(),
                lvq_hit_count_15d = lvqHitCount15d,
                spread_pct = spreadPct.roundTo2(),
                avg_spread_pct_20d = avgSpreadPct20d?.roundTo2(),
                rsi_14 = rsiPct?.roundTo2(),
                roc20_pct = roc20Pct?.roundTo2(),
                sma50_distance_pct = dma50DistancePct?.roundTo2(),
                sma200_distance_pct = dma200DistancePct?.roundTo2(),
                distance_from_52w_low_pct = distanceFrom52wLowPct?.roundTo2(),
                volume_vs_50d_ratio = volumeVs50dRatio?.roundTo2(),
                accumulation_run_length_days = accumulationRunLengthDays,
            )
        }
        return null
    }

    data class TierCounts(val t55: Int, val t60: Int, val t65: Int, val t70: Int, val t80: Int)
    private fun computeTierCounts(alignedData: List<AlignedMarketDay>, signalIndex: Int, lookbackDays: Int): TierCounts {
        if (lookbackDays <= 0) return TierCounts(0, 0, 0, 0, 0)
        val startIndex = max(0, signalIndex - lookbackDays + 1)
        var t55 = 0; var t60 = 0; var t65 = 0; var t70 = 0; var t80 = 0
        for (i in startIndex..signalIndex) {
            val pct = alignedData[i].delivery?.delivPer ?: continue
            if (pct >= 80.0) t80++
            if (pct >= 70.0) t70++
            if (pct >= 65.0) t65++
            if (pct >= 60.0) t60++
            if (pct >= 55.0) t55++
        }
        return TierCounts(t55, t60, t65, t70, t80)
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

    private class DeliveryVolumeIndicator(
        private val series: BarSeries,
        private val alignedData: List<AlignedMarketDay>
    ) : CachedIndicator<Num>(series) {
        override fun calculate(index: Int): Num {
            val delivQty = alignedData.getOrNull(index)?.delivery?.delivQty?.toDouble() ?: 0.0
            return series.numOf(delivQty)
        }
        override fun getUnstableBars(): Int = 0
    }
}
