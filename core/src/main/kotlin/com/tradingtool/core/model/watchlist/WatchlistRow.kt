package com.tradingtool.core.model.watchlist

import kotlinx.serialization.Serializable

@Serializable
data class WatchlistRow(
    val symbol: String,
    val instrumentToken: Long,
    val companyName: String,
    val exchange: String,
    val sector: String? = null,

    // Live injected fields
    val ltp: Double? = null,
    val changePercent: Double? = null,

    // Computed indicators
    val sma50: Double? = null,
    val sma200: Double? = null,
    val trendState: String? = null,
    val high60d: Double? = null,
    val low60d: Double? = null,
    val rangePosition60dPct: Double? = null,
    val gapTo3mLowPct: Double? = null,
    val gapTo3mHighPct: Double? = null,
    val rsiAtHigh60d: Double? = null,
    val rsiAtLow60d: Double? = null,
    val volumeAtHigh60d: Double? = null,
    val volumeAtLow60d: Double? = null,
    val priceVs200maPct: Double? = null,
    val rsi14: Double? = null,
    val atr14: Double? = null,
    val atr14Pct: Double? = null,
    val roc1w: Double? = null,
    val roc3m: Double? = null,
    val macdSignal: String? = null,
    val drawdownPct: Double? = null,
    val maxDd1y: Double? = null,
    val volumeVsAvg: Double? = null
)
