package com.tradingtool.core.model.stock

import com.fasterxml.jackson.annotation.JsonProperty
import com.tradingtool.core.strategy.momentum.MomentumEvidence

data class DayDetail(
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    @get:JsonProperty("daily_change_pct") val dailyChangePct: Double?,
    val rsi14: Double?,
    @get:JsonProperty("vol_ratio") val volRatio: Double?,
    @get:JsonProperty("volume_signal") val volumeSignal: String? = null,
    @get:JsonProperty("volume_average_50") val volumeAverage50: Double? = null,
    @get:JsonProperty("relative_volume_50") val relativeVolume50: Double? = null,
    @get:JsonProperty("pocket_pivot") val pocketPivot: Boolean = false,
    @get:JsonProperty("bull_snort") val bullSnort: Boolean = false,
)

data class DeliveryDayDetail(
    val date: String,
    @get:JsonProperty("delivery_percentage") val deliveryPercentage: Double?,
    @get:JsonProperty("delivered_quantity") val deliveredQuantity: Long?,
    @get:JsonProperty("traded_quantity") val tradedQuantity: Long?,
)

data class PivotLevels(
    val pivot: Double,
    val r1: Double,
    val r2: Double,
    val r3: Double,
    val s1: Double,
    val s2: Double,
    val s3: Double,
)

data class StockFundamentals(
    val currentPrice: Double,
    val fiftyTwoWeekLow: Double?,
    val fiftyTwoWeekHigh: Double?,
    val sma200: Double?,
    val sma100: Double?,
)

data class Rsi14Range(
    val current: Double?,
    @get:JsonProperty("min_60d") val min60d: Double?,
    @get:JsonProperty("max_60d") val max60d: Double?,
    @get:JsonProperty("direction_3d") val direction3d: String?,   // "UP" | "DOWN" | "FLAT"
)

data class Roc9(
    val current: Double?,
    @get:JsonProperty("direction_3d") val direction3d: String?,   // "UP" | "DOWN" | "FLAT"
)

data class FreshBreakoutDates(
    @get:JsonProperty("breakout_20d") val breakout20d: String?,
    @get:JsonProperty("breakout_50d") val breakout50d: String?,
    @get:JsonProperty("breakout_52d") val breakout52d: String?,
    @get:JsonProperty("breakout_100d") val breakout100d: String?,
    @get:JsonProperty("breakout_20d_level") val breakout20dLevel: Double? = null,
    @get:JsonProperty("breakout_50d_level") val breakout50dLevel: Double? = null,
    @get:JsonProperty("breakout_52d_level") val breakout52dLevel: Double? = null,
    @get:JsonProperty("breakout_100d_level") val breakout100dLevel: Double? = null,
)

data class StockDetailResponse(
    val symbol: String,
    val exchange: String,
    @get:JsonProperty("avg_volume_20d") val avgVolume20d: Double?,
    @get:JsonProperty("pivot_levels") val pivotLevels: PivotLevels?,
    val fundamentals: StockFundamentals,
    val days: List<DayDetail>,
    @get:JsonProperty("delivery_days") val deliveryDays: List<DeliveryDayDetail>,
    @get:JsonProperty("momentum_evidence") val momentumEvidence: MomentumEvidence? = null,
    @get:JsonProperty("rsi14_range") val rsi14Range: Rsi14Range? = null,
    @get:JsonProperty("roc9") val roc9: Roc9? = null,
    @get:JsonProperty("breakout_dates") val breakoutDates: FreshBreakoutDates? = null,
)
