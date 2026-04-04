package com.tradingtool.core.kite

/**
 * Live update for a single stock symbol.
 *
 * Combines real-time [TickSnapshot] data with computed context indicators
 * like 20-day average volume to calculate "Volume Heat".
 */
data class LiveMarketUpdate(
    val symbol: String,
    val instrumentToken: Long,
    val ltp: Double,
    val changePercent: Double,
    val high: Double,
    val low: Double,
    val volume: Long,
    val avgVol20d: Double?,
    val volumeHeat: Double?,
    val updatedAt: Long = System.currentTimeMillis()
)
