package com.tradingtool.core.strategy.wyckoff.phase1

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import java.time.LocalDate

data class WyckoffPhase1RunRequest(
    val universeKeys: List<String> = emptyList(),
    val symbols: List<String> = emptyList(),
    val asOfDate: String? = null,
)

data class WyckoffPhase1RunConfig(
    val universeKeys: List<String>,
    val symbols: List<String>,
    val asOfDate: LocalDate,
)

data class WyckoffPhase1Config(
    val enabled: Boolean = true,
    val signalLookbackDays: Int = 10,
    val trackA: WyckoffPhase1TrackAConfig = WyckoffPhase1TrackAConfig(),
    val contextFilter: WyckoffPhase1ContextFilter = WyckoffPhase1ContextFilter(),
)

data class WyckoffPhase1TrackAConfig(
    val deliveryThresholdByCap: Map<String, Double> = mapOf(
        "MID_CAP" to 55.0,
        "SMALL_CAP" to 70.0,
        "MICRO_CAP" to 85.0,
        "NANO_CAP" to 92.0,
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
    val signal_date: String,
    val days_ago: Int,
    val index_key: String,
    val delivery_pct: Double,
    val delivery_threshold_pct: Double,
    val delivery_pass: Int,
    val density_breach_count_15d: Int,
    val density_pass: Int,
    val delivery_volume_zscore_60d: Double?,
    val zscore_pass: Int,
    val lvq_dq_pass: Int,
    val lvq_hit_count_15d: Int,
    val spread_pct: Double?,
    val avg_spread_pct_20d: Double?,
    val absorption_pass: Int,
    val roc20_pct: Double?,
    val roc20_range_pass: Int,
    val sma200_distance_pct: Double?,
    val sma_window_used: Int,
    val dma200_range_pass: Int,
    val low_volume_high_delivery_info: Int,
    val volume_vs_50d_ratio: Double?,
    val passed_count: Int,
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
