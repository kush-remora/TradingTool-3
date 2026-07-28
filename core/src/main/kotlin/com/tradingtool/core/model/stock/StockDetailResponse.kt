package com.tradingtool.core.model.stock

import com.fasterxml.jackson.annotation.JsonProperty

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
)

data class StockDetailResponse(
    val symbol: String,
    val exchange: String,
    @get:JsonProperty("avg_volume_20d") val avgVolume20d: Double?,
    @get:JsonProperty("pivot_levels") val pivotLevels: PivotLevels?,
    val fundamentals: StockFundamentals,
    val days: List<DayDetail>,
    @get:JsonProperty("delivery_days") val deliveryDays: List<DeliveryDayDetail>,
)
