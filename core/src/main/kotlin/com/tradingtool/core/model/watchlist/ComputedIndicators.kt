package com.tradingtool.core.model.watchlist

import kotlinx.serialization.Serializable

@Serializable
data class ComputedIndicators(
    val instrumentToken: Long = 0,
    val lastClose: Double? = null,
    val sma50: Double? = null,
    val sma200: Double? = null,
    val high60d: Double? = null,
    val low60d: Double? = null,
    val high3m: Double? = null,
    val low3m: Double? = null,
    val rsiAtHigh60d: Double? = null,
    val rsiAtLow60d: Double? = null,
    val volumeAtHigh60d: Double? = null,
    val volumeAtLow60d: Double? = null,
    val rsi14: Double? = null,
    val atr14: Double? = null,
    val roc1w: Double? = null,
    val roc3m: Double? = null,
    val macdSignal: String? = null, // e.g., "BULLISH", "BEARISH", "FLAT"
    val drawdownPct: Double? = null,
    val maxDd1y: Double? = null,
    val avgVol20d: Double? = null,
    val volumeVsAvg20d: Double? = null,
    val computedAt: Long
)
