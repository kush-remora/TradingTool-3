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

enum class MomentumRocState {
    INSUFFICIENT_HISTORY,
    RISING_FROM_NEGATIVE,
    RISING_POSITIVE,
    FALLING,
    FLAT,
}

data class MomentumWeeklyRoc(
    @get:JsonProperty("lookback_weeks") val lookbackWeeks: Int,
    @get:JsonProperty("current_roc_pct") val currentRocPct: Double?,
    @get:JsonProperty("previous_roc_pct") val previousRocPct: Double?,
    @get:JsonProperty("change_pct_points") val changePctPoints: Double?,
    val state: MomentumRocState,
)

data class MomentumParticipationEvent(
    @get:JsonProperty("event_date") val eventDate: String,
    val close: Double,
    @get:JsonProperty("rsi14") val rsi14: Double?,
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
    @get:JsonProperty("thirty_day_low") val thirtyDayLow: Double?,
    @get:JsonProperty("distance_from_thirty_day_low_pct") val distanceFromThirtyDayLowPct: Double?,
    @get:JsonProperty("weekly_returns") val weeklyReturns: List<MomentumWeeklyReturn>,
    @get:JsonProperty("weekly_roc") val weeklyRoc: MomentumWeeklyRoc?,
    @get:JsonProperty("participation_events") val participationEvents: List<MomentumParticipationEvent>,
    @get:JsonProperty("participation_threshold") val participationThreshold: Double,
    @get:JsonProperty("participation_lookback_days") val participationLookbackDays: Int,
    @get:JsonProperty("data_status") val dataStatus: MomentumDataStatus,
)
