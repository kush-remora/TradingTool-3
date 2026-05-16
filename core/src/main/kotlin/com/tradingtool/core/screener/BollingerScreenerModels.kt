package com.tradingtool.core.screener

import kotlinx.serialization.Serializable

@Serializable
data class BollingerScanResult(
    val symbol: String,
    val companyName: String,
    val instrumentToken: Long,
    val ltp: Double,
    val bbUpper: Double,
    val bbLower: Double,
    val bbMiddle: Double,
    val percentB: Double,
    val bandwidth: Double,
    val isSqueeze: Boolean,
    val rsi14: Double?,
    val signal: String, // "OVERSOLD", "OVERBOUGHT", "SQUEEZE", "NORMAL"
    val setupScore: Int,
    val reasoning: String
)

@Serializable
data class BollingerScanResponse(
    val runAt: String,
    val universe: String,
    val results: List<BollingerScanResult>
)
