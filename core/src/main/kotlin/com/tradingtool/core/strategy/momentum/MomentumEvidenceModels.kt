package com.tradingtool.core.strategy.momentum

import com.fasterxml.jackson.annotation.JsonProperty

enum class MomentumDataStatus {
    AVAILABLE,
    INSUFFICIENT_HISTORY,
    NO_CANDLES,
}

data class MomentumWeeklyReturn(
    @get:JsonProperty("week_start") val weekStart: String,
    @get:JsonProperty("week_end") val weekEnd: String,
    @get:JsonProperty("return_pct") val returnPct: Double,
)

data class MomentumParticipationEvent(
    @get:JsonProperty("event_date") val eventDate: String,
    val close: Double,
    val volume: Long,
    @get:JsonProperty("volume_ratio") val volumeRatio: Double,
    @get:JsonProperty("daily_return_pct") val dailyReturnPct: Double?,
    @get:JsonProperty("price_since_event_pct") val priceSinceEventPct: Double,
    @get:JsonProperty("delivery_percentage") val deliveryPercentage: Double?,
)

data class MomentumEvidence(
    @get:JsonProperty("as_of_date") val asOfDate: String,
    @get:JsonProperty("current_close") val currentClose: Double?,
    @get:JsonProperty("sma200") val sma200: Double?,
    @get:JsonProperty("above_sma200") val aboveSma200: Boolean?,
    @get:JsonProperty("distance_from_sma200_pct") val distanceFromSma200Pct: Double?,
    @get:JsonProperty("fifty_two_week_high") val fiftyTwoWeekHigh: Double?,
    @get:JsonProperty("distance_from_fifty_two_week_high_pct") val distanceFromFiftyTwoWeekHighPct: Double?,
    @get:JsonProperty("weekly_returns") val weeklyReturns: List<MomentumWeeklyReturn>,
    @get:JsonProperty("participation_events") val participationEvents: List<MomentumParticipationEvent>,
    @get:JsonProperty("participation_threshold") val participationThreshold: Double,
    @get:JsonProperty("participation_lookback_days") val participationLookbackDays: Int,
    @get:JsonProperty("data_status") val dataStatus: MomentumDataStatus,
)
