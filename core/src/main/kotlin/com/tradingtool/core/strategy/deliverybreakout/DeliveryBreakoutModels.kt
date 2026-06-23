package com.tradingtool.core.strategy.deliverybreakout

data class DeliveryBreakoutDashboardResponse(
    val meta: DeliveryBreakoutDashboardMeta,
    val rows: List<DeliveryBreakoutDashboardRow>,
)

data class DeliveryBreakoutDashboardMeta(
    val trade_date: String,
    val scanned_count: Int,
    val liquidity_eligible_count: Int,
    val shortlisted_count: Int,
    val confirmed_breakout_count: Int,
    val quiet_clue_count: Int,
)

data class DeliveryBreakoutDashboardRow(
    val symbol: String,
    val trade_date: String,
    val close: Double?,
    val close_pct_change: Double?,
    val volume: Long,
    val delivery_quantity: Long,
    val delivery_percentage: Double?,
    val prior_10d_max_volume: Long,
    val prior_10d_max_delivery_quantity: Long,
    val volume_ratio_vs_10d_max: Double,
    val delivery_ratio_vs_10d_max: Double,
    val has_quiet_clue: Boolean,
    val quiet_clue_day: String?,
    val is_confirmed_breakout_today: Boolean,
    val sma200: Double?,
    val distance_from_sma200_pct: Double?,
    val is_near_200_sma: Boolean?,
    val label: String,
)

internal data class DeliveryBreakoutStage1Candidate(
    val instrumentToken: Long,
    val symbol: String,
    val tradeDate: String,
    val volume: Long,
    val deliveryQuantity: Long,
    val deliveryPercentage: Double?,
    val prior10dMaxVolume: Long,
    val prior10dMaxDeliveryQuantity: Long,
    val volumeRatioVs10dMax: Double,
    val deliveryRatioVs10dMax: Double,
)
