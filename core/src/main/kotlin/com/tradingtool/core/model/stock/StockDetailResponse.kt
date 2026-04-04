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

data class StockDetailResponse(
    val symbol: String,
    val exchange: String,
    @get:JsonProperty("avg_volume_20d") val avgVolume20d: Double?,
    val days: List<DayDetail>,
)