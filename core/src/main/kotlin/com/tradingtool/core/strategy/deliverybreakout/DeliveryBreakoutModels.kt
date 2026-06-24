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
)

data class DeliveryBreakoutDashboardRow(
    val symbol: String,
    val trade_date: String,
    val close: Double?,
    val close_pct_change: Double?,
    val volume: Long,
    val delivery_quantity: Long,
    val delivery_percentage: Double?,
    val prev_volume: Long,
    val prev_delivery_quantity: Long,
    val volume_ratio: Double,
    val delivery_ratio: Double,
)

internal data class DeliveryBreakoutStage1Candidate(
    val instrumentToken: Long,
    val symbol: String,
    val tradeDate: String,
    val volume: Long,
    val deliveryQuantity: Long,
    val deliveryPercentage: Double?,
    val prevVolume: Long,
    val prevDeliveryQuantity: Long,
    val volumeRatio: Double,
    val deliveryRatio: Double,
)
