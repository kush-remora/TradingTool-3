package com.tradingtool.core.volumeshocker.groww

data class GrowwVolumeShockerDashboardDatesResponse(
    val available_dates: List<String>,
    val default_date: String?,
)

data class GrowwVolumeShockerDashboardResponse(
    val trade_date: String,
    val rows: List<GrowwVolumeShockerDashboardRow>,
)

data class GrowwVolumeShockerDashboardRow(
    val source_rank: Int,
    val symbol: String,
    val company_name: String,
    val ltp: Double,
    val volume: Long,
    val delivery_volume: Long?,
    val delivery_pct: Double?,
    val max_delivery_volume_10d_before_event: Long?,
    val delivery_volume_vs_max_10d_before_event_ratio: Double?,
    val appearance_count_10d: Int,
    val streak_length_10d: Int,
    val sma200_price: Double?,
    val distance_from_sma200_pct: Double?,
    val pre_event_accumulation_hint: Boolean,
    val tag: String,
)

data class GrowwVolumeShockerDashboardDetailResponse(
    val symbol: String,
    val trade_date: String,
    val summary: GrowwVolumeShockerDashboardDetailSummary,
    val days: List<GrowwVolumeShockerDashboardDetailDay>,
)

data class GrowwVolumeShockerDashboardDetailSummary(
    val appearance_count_10d: Int,
    val streak_length_10d: Int,
    val max_delivery_volume_10d_before_event: Long?,
    val delivery_volume_vs_max_10d_before_event_ratio: Double?,
)

data class GrowwVolumeShockerDashboardDetailDay(
    val date: String,
    val open: Double,
    val close: Double,
    val volume: Long,
    val delivery_volume: Long?,
    val delivery_pct: Double?,
    val daily_change_pct: Double?,
    val is_event_day: Boolean,
)
