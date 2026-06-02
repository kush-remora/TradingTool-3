package com.tradingtool.core.strategy.wyckoff.phase1

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import java.time.LocalDate

data class WyckoffPhase1RunRequest(
    val universeKeys: List<String> = emptyList(),
    val symbols: List<String> = emptyList(),
    val asOfDate: String? = null,
    val applyStrictBaseFilter: Boolean = false,
)

data class WyckoffPhase1RunConfig(
    val universeKeys: List<String>,
    val symbols: List<String>,
    val asOfDate: LocalDate,
    val applyStrictBaseFilter: Boolean,
)

data class WyckoffPhase1Config(
    val enabled: Boolean = true,
    val signalLookbackDays: Int = 10,
    val trackA: WyckoffPhase1TrackAConfig = WyckoffPhase1TrackAConfig(),
    val contextFilter: WyckoffPhase1ContextFilter = WyckoffPhase1ContextFilter(),
    val runtime: WyckoffPhase1RuntimeConfig = WyckoffPhase1RuntimeConfig(),
    val strictFilter: WyckoffPhase1StrictFilterConfig = WyckoffPhase1StrictFilterConfig(),
)

data class WyckoffPhase1RuntimeConfig(
    val enableCandleBackfill: Boolean = false,
    val maxParallelSymbols: Int = 16,
)

data class WyckoffPhase1TrackAConfig(
    val deliveryThresholdByCap: Map<String, Double> = mapOf(
        "MID_CAP" to 55.0,
        "SMALL_CAP" to 55.0,
        "MICRO_CAP" to 55.0,
        "NANO_CAP" to 55.0,
    ),
    val rollingDensity: WyckoffPhase1RollingDensityConfig = WyckoffPhase1RollingDensityConfig(),
    val deliveryVolumeZScore: WyckoffPhase1ZScoreConfig = WyckoffPhase1ZScoreConfig(),
    val lvqDq: WyckoffPhase1LvqDqConfig = WyckoffPhase1LvqDqConfig(),
    val absorptionCheck: WyckoffPhase1AbsorptionConfig = WyckoffPhase1AbsorptionConfig(),
    val lowVolumeHighDeliveryInfo: WyckoffPhase1LowVolumeHighDeliveryInfo = WyckoffPhase1LowVolumeHighDeliveryInfo(),
)

data class WyckoffPhase1RollingDensityConfig(
    val enabled: Boolean = true,
    val lookbackDays: Int = 15,
    val minThresholdBreaches: Int = 4,
)

data class WyckoffPhase1ZScoreConfig(
    val enabled: Boolean = true,
    val baselineDays: Int = 60,
    val minZScore: Double = 2.0,
)

data class WyckoffPhase1LvqDqConfig(
    val enabled: Boolean = true,
    val rollingMinDays: Int = 63,
    val nearMinPctOfRollingMin: Double = 95.0,
    val lookbackDays: Int = 15,
    val requireDeliveryPass: Boolean = true,
)

data class WyckoffPhase1AbsorptionConfig(
    val enabled: Boolean = true,
    val spreadLookbackDays: Int = 20,
)

data class WyckoffPhase1LowVolumeHighDeliveryInfo(
    val enabled: Boolean = true,
    val mode: String = "INFO_ONLY",
    val volumeBaselineDays: Int = 50,
    val maxVolumeVsBaselineRatio: Double = 0.8,
)

data class WyckoffPhase1ContextFilter(
    val roc20Range: WyckoffPhase1RangeConfig = WyckoffPhase1RangeConfig(),
    val dma200Proximity: WyckoffPhase1RangeConfig = WyckoffPhase1RangeConfig(),
)

data class WyckoffPhase1RangeConfig(
    val enabled: Boolean = true,
    val minDistancePct: Double = -5.0,
    val maxDistancePct: Double = 5.0,
)

data class WyckoffPhase1StrictFilterConfig(
    val dma200Proximity: WyckoffPhase1RangeConfig = WyckoffPhase1RangeConfig(minDistancePct = -100.0, maxDistancePct = 2.0),
    val roc20Proximity: WyckoffPhase1RocRangeConfig = WyckoffPhase1RocRangeConfig(),
    val movingAverageCompression: WyckoffPhase1MovingAverageCompressionConfig = WyckoffPhase1MovingAverageCompressionConfig(),
    val volatilityContraction: WyckoffPhase1VolatilityContractionConfig = WyckoffPhase1VolatilityContractionConfig(),
    val accumulationDensity: WyckoffPhase1AccumulationDensityConfig = WyckoffPhase1AccumulationDensityConfig(),
)

data class WyckoffPhase1RocRangeConfig(
    val enabled: Boolean = true,
    val minPct: Double = -100.0,
    val maxPct: Double = 2.0,
)

data class WyckoffPhase1MovingAverageCompressionConfig(
    val enabled: Boolean = true,
    val maxDma50To200DistancePct: Double = 3.0,
)

data class WyckoffPhase1VolatilityContractionConfig(
    val enabled: Boolean = true,
    val requireSpreadLessThan20dAverage: Boolean = true,
    val relaxIfBelowDma200: Boolean = false,
    val relaxedMultiplierIfBelowDma200: Double = 1.5,
    val bypassIfBelowDma200AndDeliveryHigh: Boolean = false,
    val bypassDeliveryThreshold: Double = 70.0,
)

data class WyckoffPhase1AccumulationDensityConfig(
    val enabled: Boolean = true,
    val minTier55Count: Int = 3,
)

data class WyckoffPhase1TableColumnsConfig(
    val enabled: Boolean = true,
    val defaultSort: List<WyckoffPhase1SortColumn> = listOf(WyckoffPhase1SortColumn(key = "signal_date", direction = "desc")),
    val columns: List<WyckoffPhase1ColumnConfig> = emptyList(),
)

data class WyckoffPhase1SortColumn(
    val key: String,
    val direction: String,
)

data class WyckoffPhase1ColumnConfig(
    val key: String,
    val enabled: Boolean,
)

data class WyckoffPhase1RunResponse(
    val rows: List<WyckoffPhase1Row>,
    val meta: WyckoffPhase1RunMeta,
)

data class WyckoffPhase1RunMeta(
    val as_of_date: String,
    val evaluated_trading_dates: List<String>,
    val universe_count: Int,
    val matched_count: Int,
)

data class WyckoffPhase1Row(
    val symbol: String,
    val company_name: String,
    val signal_date: String,
    val days_ago: Int,
    val index_key: String,
    val tier_80_count_15d: Int,
    val tier_70_count_15d: Int,
    val tier_65_count_15d: Int,
    val tier_60_count_15d: Int,
    val tier_55_count_15d: Int,
    val delivery_volume_zscore_60d: Double?,
    val lvq_hit_count_15d: Int,
    val spread_pct: Double?,
    val avg_spread_pct_20d: Double?,
    val rsi_14: Double?,
    val roc20_pct: Double?,
    val sma50_distance_pct: Double?,
    val sma200_distance_pct: Double?,
    val distance_from_52w_low_pct: Double?,
    val volume_vs_50d_ratio: Double?,
    val accumulation_run_length_days: Int,
)

data class WyckoffPhase1SymbolContext(
    val symbol: String,
    val instrumentToken: Long,
    val companyName: String,
    val indexKey: String,
    val deliveryThresholdPct: Double,
    val candles: List<DailyCandle>,
    val deliveries: List<StockDeliveryDaily>,
)

data class WyckoffPhase1ResolvedSymbol(
    val symbol: String,
    val instrumentToken: Long,
    val companyName: String,
    val indexKey: String,
    val deliveryThresholdPct: Double,
)
