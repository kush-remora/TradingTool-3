package com.tradingtool.core.screener

import kotlinx.serialization.Serializable

@Serializable
data class BollingerSqueezeScanResult(
    val symbol: String,
    val companyName: String,
    val instrumentToken: Long,
    val ltp: Double,
    val above200Sma: Boolean,
    val filter1Passed: Boolean,
    val filter1OriginDate: String?,
    val filter1LatestDate: String?,
    val filter2Passed: Boolean,
    val filter2OriginDate: String?,
    val filter2LatestDate: String?,
    val filter2Type: String?,
    val alertStatus: String,
    val currentRsi: Double?,
    val triggerRsi: Double?,
    val maxRsi52w: Double?,
    val maxDrawdownPct: Double,
    val bbUpper: Double,
    val bbMiddle: Double,
    val bbLower: Double
)

@Serializable
data class BollingerSqueezeScanResponse(
    val runAt: String,
    val universe: String,
    val results: List<BollingerSqueezeScanResult>
)

@Serializable
data class SqueezePositionInput(
    val symbol: String,
    val buyDate: String,
    val buyPrice: Double
)

@Serializable
data class SqueezeTrackResult(
    val symbol: String,
    val companyName: String,
    val buyDate: String,
    val buyPrice: Double,
    val ltp: Double,
    val profitPct: Double,
    val currentPhase: String,
    val requiredSl: Double,
    val todayRsi: Double?,
    val maxRsi1y: Double?,
    val maxDrawdownPct: Double,
    val bbUpper: Double,
    val bbMiddle: Double,
    val bbLower: Double
)

@Serializable
data class SqueezeTrackResponse(
    val results: List<SqueezeTrackResult>
)
