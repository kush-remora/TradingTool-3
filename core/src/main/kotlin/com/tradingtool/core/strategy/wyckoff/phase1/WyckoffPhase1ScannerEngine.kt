package com.tradingtool.core.strategy.wyckoff.phase1

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import com.tradingtool.core.technical.roundTo2
import java.time.LocalDate

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

    private fun evaluateSymbol(
        config: WyckoffPhase1Config,
        asOfDate: LocalDate,
        context: WyckoffPhase1SymbolContext,
        signalLookbackDays: Int,
    ): WyckoffPhase1Row? {
        val candles = context.candles.sortedBy { candle -> candle.candleDate }
        if (candles.isEmpty()) {
            return null
        }

        val deliveryByDate = context.deliveries
            .filter { delivery -> delivery.tradingDate <= asOfDate }
            .associateBy { delivery -> delivery.tradingDate }

        val lastTradingDates = candles
            .asSequence()
            .map { candle -> candle.candleDate }
            .filter { date -> date <= asOfDate }
            .toList()
            .asReversed()
            .take(signalLookbackDays)

        val dayQualifiersByDate = mutableMapOf<LocalDate, DayQualifiers>()
        fun qualifiersFor(date: LocalDate): DayQualifiers? {
            return dayQualifiersByDate.getOrPut(date) {
                val signalCandleIndex = candles.indexOfFirst { candle -> candle.candleDate == date }
                if (signalCandleIndex < 0) {
                    return@getOrPut DayQualifiers.empty()
                }
                val signalCandle = candles[signalCandleIndex]
                val signalDelivery = deliveryByDate[date]
                val signalDeliveryPct = signalDelivery?.delivPer
                val signalVolume = signalDelivery?.ttlTrdQnty?.toDouble() ?: signalCandle.volume.toDouble()

                val deliveryPass = if (signalDeliveryPct != null && signalDeliveryPct >= context.deliveryThresholdPct) 1 else 0
                val densityCount = computeDensityBreachCount(
                    deliveries = context.deliveries,
                    signalDate = date,
                    lookbackDays = config.trackA.rollingDensity.lookbackDays,
                    threshold = context.deliveryThresholdPct,
                )
                val densityPass = if (
                    config.trackA.rollingDensity.enabled &&
                    densityCount >= config.trackA.rollingDensity.minThresholdBreaches
                ) {
                    1
                } else {
                    0
                }

                val zscore = computeVolumeZScore(
                    candles = candles,
                    signalIndex = signalCandleIndex,
                    signalVolume = signalVolume,
                    baselineDays = config.trackA.deliveryVolumeZScore.baselineDays,
                )
                val zscorePass = if (
                    config.trackA.deliveryVolumeZScore.enabled &&
                    zscore != null &&
                    zscore >= config.trackA.deliveryVolumeZScore.minZScore
                ) {
                    1
                } else {
                    0
                }

                val lvqDayPass = computeLvqNearRollingMinPass(
                    candles = candles,
                    signalIndex = signalCandleIndex,
                    signalVolume = signalVolume,
                    rollingMinDays = config.trackA.lvqDq.rollingMinDays,
                    nearMinPctOfRollingMin = config.trackA.lvqDq.nearMinPctOfRollingMin,
                )
                val lvqDqPass = if (
                    config.trackA.lvqDq.enabled &&
                    lvqDayPass &&
                    (!config.trackA.lvqDq.requireDeliveryPass || deliveryPass == 1)
                ) {
                    1
                } else {
                    0
                }

                val lvqHitCount15d = computeLvqHitCount(
                    candles = candles,
                    deliveryByDate = deliveryByDate,
                    signalDate = date,
                    threshold = context.deliveryThresholdPct,
                    lvqConfig = config.trackA.lvqDq,
                )

                val spreadPct = computeSpreadPct(signalCandle)
                val avgSpreadPct20d = computeAverageSpreadPctBeforeIndex(
                    candles = candles,
                    signalIndex = signalCandleIndex,
                    lookbackDays = config.trackA.absorptionCheck.spreadLookbackDays,
                )
                val absorptionPass = if (
                    config.trackA.absorptionCheck.enabled &&
                    spreadPct != null &&
                    avgSpreadPct20d != null &&
                    spreadPct < avgSpreadPct20d
                ) {
                    1
                } else {
                    0
                }

                val roc20Pct = computeRocPct(candles = candles, signalIndex = signalCandleIndex, lookbackDays = ROC_LOOKBACK_DAYS)
                val roc20RangePass = if (
                    config.contextFilter.roc20Range.enabled &&
                    roc20Pct != null &&
                    roc20Pct >= config.contextFilter.roc20Range.minDistancePct &&
                    roc20Pct <= config.contextFilter.roc20Range.maxDistancePct
                ) {
                    1
                } else {
                    0
                }

                val smaWindowUsed = (signalCandleIndex + 1).coerceAtMost(SMA_TARGET_WINDOW_DAYS)
                val smaAtSignal = computeSmaAtIndex(
                    candles = candles,
                    signalIndex = signalCandleIndex,
                    windowSize = smaWindowUsed,
                )
                val dmaDistancePct = computeDistancePct(signalCandle.close, smaAtSignal)
                val dma200RangePass = if (
                    config.contextFilter.dma200Proximity.enabled &&
                    dmaDistancePct != null &&
                    dmaDistancePct >= config.contextFilter.dma200Proximity.minDistancePct &&
                    dmaDistancePct <= config.contextFilter.dma200Proximity.maxDistancePct
                ) {
                    1
                } else {
                    0
                }

                val volumeVs50dRatio = computeVolumeVsBaselineRatio(
                    candles = candles,
                    signalIndex = signalCandleIndex,
                    signalVolume = signalVolume,
                    baselineDays = config.trackA.lowVolumeHighDeliveryInfo.volumeBaselineDays,
                )
                val lowVolumeHighDeliveryInfo = if (
                    config.trackA.lowVolumeHighDeliveryInfo.enabled &&
                    deliveryPass == 1 &&
                    volumeVs50dRatio != null &&
                    volumeVs50dRatio <= config.trackA.lowVolumeHighDeliveryInfo.maxVolumeVsBaselineRatio
                ) {
                    1
                } else {
                    0
                }

                val passedCount = listOf(
                    deliveryPass,
                    densityPass,
                    zscorePass,
                    lvqDqPass,
                    absorptionPass,
                    roc20RangePass,
                    dma200RangePass,
                ).sum()

                DayQualifiers(
                    deliveryPct = signalDeliveryPct,
                    deliveryPass = deliveryPass,
                    densityCount = densityCount,
                    densityPass = densityPass,
                    zscore = zscore,
                    zscorePass = zscorePass,
                    lvqDqPass = lvqDqPass,
                    lvqHitCount15d = lvqHitCount15d,
                    spreadPct = spreadPct,
                    avgSpreadPct20d = avgSpreadPct20d,
                    absorptionPass = absorptionPass,
                    roc20Pct = roc20Pct,
                    roc20RangePass = roc20RangePass,
                    smaWindowUsed = smaWindowUsed,
                    dmaDistancePct = dmaDistancePct,
                    dma200RangePass = dma200RangePass,
                    lowVolumeHighDeliveryInfo = lowVolumeHighDeliveryInfo,
                    volumeVs50dRatio = volumeVs50dRatio,
                    passedCount = passedCount,
                )
            }.takeUnless { qualifiers -> qualifiers.isEmpty }
        }

        val signalDate = lastTradingDates.firstOrNull { tradingDate ->
            val qualifiers = qualifiersFor(tradingDate) ?: return@firstOrNull false
            qualifiers.deliveryPass == 1 || qualifiers.zscorePass == 1
        } ?: return null

        val signalCandleIndex = candles.indexOfFirst { candle -> candle.candleDate == signalDate }
        if (signalCandleIndex < 0) {
            return null
        }
        val qualifiers = qualifiersFor(signalDate) ?: return null
        val signalDeliveryPct = qualifiers.deliveryPct ?: return null

        val accumulationRunLengthDays = computeAccumulationRunLengthDays(
            deliveries = context.deliveries,
            signalDate = signalDate,
            threshold = context.deliveryThresholdPct,
        )

        val daysAgo = lastTradingDates.indexOf(signalDate).takeIf { index -> index >= 0 } ?: 0

        return WyckoffPhase1Row(
            symbol = context.symbol,
            signal_date = signalDate.toString(),
            days_ago = daysAgo,
            index_key = context.indexKey,
            delivery_pct = signalDeliveryPct.roundTo2(),
            delivery_threshold_pct = context.deliveryThresholdPct.roundTo2(),
            delivery_pass = qualifiers.deliveryPass,
            density_breach_count_15d = qualifiers.densityCount,
            density_pass = qualifiers.densityPass,
            delivery_volume_zscore_60d = qualifiers.zscore?.roundTo2(),
            zscore_pass = qualifiers.zscorePass,
            lvq_dq_pass = qualifiers.lvqDqPass,
            lvq_hit_count_15d = qualifiers.lvqHitCount15d,
            spread_pct = qualifiers.spreadPct?.roundTo2(),
            avg_spread_pct_20d = qualifiers.avgSpreadPct20d?.roundTo2(),
            absorption_pass = qualifiers.absorptionPass,
            roc20_pct = qualifiers.roc20Pct?.roundTo2(),
            roc20_range_pass = qualifiers.roc20RangePass,
            sma200_distance_pct = qualifiers.dmaDistancePct?.roundTo2(),
            sma_window_used = qualifiers.smaWindowUsed,
            dma200_range_pass = qualifiers.dma200RangePass,
            low_volume_high_delivery_info = qualifiers.lowVolumeHighDeliveryInfo,
            volume_vs_50d_ratio = qualifiers.volumeVs50dRatio?.roundTo2(),
            passed_count = qualifiers.passedCount,
            accumulation_run_length_days = accumulationRunLengthDays,
        )
    }

    private fun computeDensityBreachCount(
        deliveries: List<StockDeliveryDaily>,
        signalDate: LocalDate,
        lookbackDays: Int,
        threshold: Double,
    ): Int {
        if (lookbackDays <= 0) {
            return 0
        }
        return deliveries
            .asSequence()
            .filter { delivery -> delivery.tradingDate <= signalDate }
            .sortedByDescending { delivery -> delivery.tradingDate }
            .take(lookbackDays)
            .count { delivery -> (delivery.delivPer ?: Double.NEGATIVE_INFINITY) >= threshold }
    }

    private fun computeVolumeZScore(
        candles: List<DailyCandle>,
        signalIndex: Int,
        signalVolume: Double,
        baselineDays: Int,
    ): Double? {
        if (baselineDays <= 1 || signalIndex <= 0) {
            return null
        }
        val startIndex = (signalIndex - baselineDays).coerceAtLeast(0)
        val baseline = candles.subList(startIndex, signalIndex).map { candle -> candle.volume.toDouble() }
        if (baseline.size < 2) {
            return null
        }
        val mean = baseline.average()
        val variance = baseline.map { value -> (value - mean) * (value - mean) }.average()
        val std = kotlin.math.sqrt(variance)
        if (!std.isFinite() || std <= 0.0) {
            return null
        }
        return (signalVolume - mean) / std
    }

    private fun computeLvqNearRollingMinPass(
        candles: List<DailyCandle>,
        signalIndex: Int,
        signalVolume: Double,
        rollingMinDays: Int,
        nearMinPctOfRollingMin: Double,
    ): Boolean {
        if (rollingMinDays <= 0 || nearMinPctOfRollingMin <= 0.0 || nearMinPctOfRollingMin > 100.0) {
            return false
        }
        val startIndex = (signalIndex - rollingMinDays + 1).coerceAtLeast(0)
        val window = candles.subList(startIndex, signalIndex + 1)
        if (window.isEmpty()) {
            return false
        }
        val rollingMin = window.minOfOrNull { candle -> candle.volume.toDouble() } ?: return false
        if (rollingMin <= 0.0 || !rollingMin.isFinite()) {
            return false
        }
        val nearFactor = nearMinPctOfRollingMin / 100.0
        val maxAllowedVolume = rollingMin / nearFactor
        return signalVolume <= maxAllowedVolume
    }

    private fun computeLvqHitCount(
        candles: List<DailyCandle>,
        deliveryByDate: Map<LocalDate, StockDeliveryDaily>,
        signalDate: LocalDate,
        threshold: Double,
        lvqConfig: WyckoffPhase1LvqDqConfig,
    ): Int {
        if (!lvqConfig.enabled || lvqConfig.lookbackDays <= 0) {
            return 0
        }
        val tradingDates = candles
            .asSequence()
            .map { candle -> candle.candleDate }
            .filter { date -> date <= signalDate }
            .toList()
            .asReversed()
            .take(lvqConfig.lookbackDays)

        return tradingDates.count { date ->
            val signalIndex = candles.indexOfFirst { candle -> candle.candleDate == date }
            if (signalIndex < 0) {
                return@count false
            }
            val candle = candles[signalIndex]
            val delivery = deliveryByDate[date]
            val deliveryPct = delivery?.delivPer
            val deliveryPass = deliveryPct != null && deliveryPct >= threshold
            if (lvqConfig.requireDeliveryPass && !deliveryPass) {
                return@count false
            }
            val volume = delivery?.ttlTrdQnty?.toDouble() ?: candle.volume.toDouble()
            computeLvqNearRollingMinPass(
                candles = candles,
                signalIndex = signalIndex,
                signalVolume = volume,
                rollingMinDays = lvqConfig.rollingMinDays,
                nearMinPctOfRollingMin = lvqConfig.nearMinPctOfRollingMin,
            )
        }
    }

    private fun computeSpreadPct(candle: DailyCandle): Double? {
        if (candle.close <= 0.0 || !candle.close.isFinite()) {
            return null
        }
        val spread = candle.high - candle.low
        return (spread / candle.close) * 100.0
    }

    private fun computeAverageSpreadPctBeforeIndex(
        candles: List<DailyCandle>,
        signalIndex: Int,
        lookbackDays: Int,
    ): Double? {
        if (lookbackDays <= 0 || signalIndex <= 0) {
            return null
        }
        val startIndex = (signalIndex - lookbackDays).coerceAtLeast(0)
        val baseline = candles.subList(startIndex, signalIndex)
            .mapNotNull { candle -> computeSpreadPct(candle) }
        if (baseline.isEmpty()) {
            return null
        }
        return baseline.average()
    }

    private fun computeRocPct(
        candles: List<DailyCandle>,
        signalIndex: Int,
        lookbackDays: Int,
    ): Double? {
        if (signalIndex < lookbackDays) {
            return null
        }
        val currentClose = candles[signalIndex].close
        val oldClose = candles[signalIndex - lookbackDays].close
        if (oldClose <= 0.0 || !oldClose.isFinite() || !currentClose.isFinite()) {
            return null
        }
        return ((currentClose - oldClose) / oldClose) * 100.0
    }

    private fun computeSmaAtIndex(
        candles: List<DailyCandle>,
        signalIndex: Int,
        windowSize: Int,
    ): Double? {
        if (windowSize <= 0 || signalIndex < 0 || signalIndex >= candles.size) {
            return null
        }
        val startIndex = (signalIndex - windowSize + 1).coerceAtLeast(0)
        val window = candles.subList(startIndex, signalIndex + 1)
        if (window.isEmpty()) {
            return null
        }
        return window.map { candle -> candle.close }.average()
    }

    private fun computeDistancePct(price: Double, reference: Double?): Double? {
        if (reference == null || reference <= 0.0 || !price.isFinite() || !reference.isFinite()) {
            return null
        }
        return ((price / reference) - 1.0) * 100.0
    }

    private fun computeVolumeVsBaselineRatio(
        candles: List<DailyCandle>,
        signalIndex: Int,
        signalVolume: Double,
        baselineDays: Int,
    ): Double? {
        if (baselineDays <= 0 || signalIndex <= 0) {
            return null
        }
        val startIndex = (signalIndex - baselineDays).coerceAtLeast(0)
        val baseline = candles.subList(startIndex, signalIndex).map { candle -> candle.volume.toDouble() }
        if (baseline.isEmpty()) {
            return null
        }
        val avg = baseline.average()
        if (avg <= 0.0 || !avg.isFinite()) {
            return null
        }
        return signalVolume / avg
    }

    private fun computeAccumulationRunLengthDays(
        deliveries: List<StockDeliveryDaily>,
        signalDate: LocalDate,
        threshold: Double,
    ): Int {
        var count = 0
        val history = deliveries
            .asSequence()
            .filter { delivery -> delivery.tradingDate <= signalDate }
            .sortedByDescending { delivery -> delivery.tradingDate }
            .toList()

        for (delivery in history) {
            val deliveryPct = delivery.delivPer ?: break
            if (deliveryPct < threshold) {
                break
            }
            count += 1
        }
        return count
    }

    companion object {
        private const val ROC_LOOKBACK_DAYS = 20
        private const val SMA_TARGET_WINDOW_DAYS = 200
    }

    private data class DayQualifiers(
        val deliveryPct: Double?,
        val deliveryPass: Int,
        val densityCount: Int,
        val densityPass: Int,
        val zscore: Double?,
        val zscorePass: Int,
        val lvqDqPass: Int,
        val lvqHitCount15d: Int,
        val spreadPct: Double?,
        val avgSpreadPct20d: Double?,
        val absorptionPass: Int,
        val roc20Pct: Double?,
        val roc20RangePass: Int,
        val smaWindowUsed: Int,
        val dmaDistancePct: Double?,
        val dma200RangePass: Int,
        val lowVolumeHighDeliveryInfo: Int,
        val volumeVs50dRatio: Double?,
        val passedCount: Int,
        val isEmpty: Boolean = false,
    ) {
        companion object {
            fun empty(): DayQualifiers {
                return DayQualifiers(
                    deliveryPct = null,
                    deliveryPass = 0,
                    densityCount = 0,
                    densityPass = 0,
                    zscore = null,
                    zscorePass = 0,
                    lvqDqPass = 0,
                    lvqHitCount15d = 0,
                    spreadPct = null,
                    avgSpreadPct20d = null,
                    absorptionPass = 0,
                    roc20Pct = null,
                    roc20RangePass = 0,
                    smaWindowUsed = 0,
                    dmaDistancePct = null,
                    dma200RangePass = 0,
                    lowVolumeHighDeliveryInfo = 0,
                    volumeVs50dRatio = null,
                    passedCount = 0,
                    isEmpty = true,
                )
            }
        }
    }
}
