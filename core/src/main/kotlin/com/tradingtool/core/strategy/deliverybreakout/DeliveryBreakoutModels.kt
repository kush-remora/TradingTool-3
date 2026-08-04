package com.tradingtool.core.strategy.deliverybreakout

data class DeliveryBreakoutDashboardResponse(
    val meta: DeliveryBreakoutDashboardMeta,
    val rows: List<DeliveryBreakoutDashboardRow>,
)

data class DeliveryBreakoutDashboardMeta(
    val watchlist_key: String,
    val trade_date: String,
    val window_start_date: String,
    val window_end_date: String,
    val scanned_count: Int,
    val data_available_count: Int,
    val event_count: Int,
    val both_count: Int,
    val delivery_only_count: Int,
    val volume_only_count: Int,
    val no_event_count: Int,
)

data class DeliveryBreakoutDashboardRow(
    val symbol: String,
    val instrument_token: Long,
    val event_date: String,
    val event_type: String,
    val close: Double?,
    val prev_close: Double?,
    val close_pct_change: Double?,
    val fifty_two_week_high: Double?,
    val fifty_two_week_low: Double?,
    val volume: Long?,
    val delivery_quantity: Long?,
    val delivery_percentage: Double?,
    val average_volume_10d: Double?,
    val average_delivery_quantity_10d: Double?,
    val volume_ratio: Double?,
    val delivery_ratio: Double?,
)

internal data class DeliveryBreakoutEvent(
    val instrumentToken: Long,
    val symbol: String,
    val eventDate: String,
    val eventType: String,
    val volume: Long?,
    val deliveryQuantity: Long?,
    val deliveryPercentage: Double?,
    val averageVolume10d: Double?,
    val averageDeliveryQuantity10d: Double?,
    val volumeRatio: Double?,
    val deliveryRatio: Double?,
)
