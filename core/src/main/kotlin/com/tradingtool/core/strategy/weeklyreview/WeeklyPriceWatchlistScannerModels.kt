package com.tradingtool.core.strategy.weeklyreview

data class WeeklyPriceWatchlistScannerResponse(
    val watchlistKey: String,
    val rows: List<WeeklyPriceWatchlistRow>,
)

data class WeeklyPriceWatchlistRow(
    val symbol: String,
    val companyName: String,
    val days: List<WeeklyPriceWatchlistDay>,
)

data class WeeklyPriceWatchlistDay(
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val deliveryPercentage: Double?,
)
