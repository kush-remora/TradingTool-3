package com.tradingtool.core.model.remora

import com.fasterxml.jackson.annotation.JsonProperty

data class RemoraSignal(
    val id: Int,
    @JsonProperty("stock_id") val stockId: Int,
    val symbol: String,
    @JsonProperty("company_name") val companyName: String,
    val exchange: String,
    @JsonProperty("signal_type") val signalType: String,       // "ACCUMULATION" or "DISTRIBUTION"
    @JsonProperty("volume_ratio") val volumeRatio: Double,      // e.g. 2.1 means 2.1x the 20-day avg
    @JsonProperty("price_change_pct") val priceChangePct: Double,   // daily price change % on the signal day
    @JsonProperty("consecutive_days") val consecutiveDays: Int,
    @JsonProperty("signal_date") val signalDate: String,       // ISO date: "2026-03-25"
    @JsonProperty("computed_at") val computedAt: String,
    @JsonProperty("delivery_pct") val deliveryPct: Double,      // percentage of volume delivered
    @JsonProperty("delivery_ratio") val deliveryRatio: Double,    // today_deliv_per / avg_20d_deliv_per
)

data class RemoraEnvelope(
    val signals: List<RemoraSignal>,
    @JsonProperty("as_of_date") val asOfDate: String?,
    @JsonProperty("is_stale") val isStale: Boolean,
    @JsonProperty("stale_reason") val staleReason: String? = null
)
