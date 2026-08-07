package com.tradingtool.core.strategy.weeklyreview

import com.fasterxml.jackson.annotation.JsonProperty
import com.tradingtool.core.strategy.momentum.MomentumEvidence

data class WeeklyPriceWatchlistScannerResponse(
    val watchlistKey: String,
    val rows: List<WeeklyPriceWatchlistRow>,
)

data class WeeklyPriceWatchlistRow(
    val symbol: String,
    val companyName: String,
    val instrumentToken: Long,
    val days: List<WeeklyPriceWatchlistDay>,
    @get:JsonProperty("momentum_evidence") val momentumEvidence: MomentumEvidence? = null,
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
