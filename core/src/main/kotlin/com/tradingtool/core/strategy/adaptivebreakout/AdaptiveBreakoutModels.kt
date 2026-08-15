package com.tradingtool.core.strategy.adaptivebreakout

data class AdaptiveBreakoutConfig(
    val atrPeriod: Int = 14,
    val floorReboundAtrMultiple: Double = 1.0,
    val peakRejectionAtrMultiple: Double = 0.75,
    val ceilingWidthAtrMultiple: Double = 0.5,
    val maximumLocalCeilingDistanceAtrMultiple: Double = 3.0,
    val strongReboundAtrMultiple: Double = 2.0,
)

enum class AdaptiveBreakoutStatus {
    NO_CEILING,
    BELOW_CEILING,
    TESTING_CEILING,
    STRONG_REBOUND,
    FRESH_BREAKOUT,
    BREAKOUT_CONTINUATION,
}

enum class AdaptiveBreakoutDecision {
    BUILDING_STRUCTURE,
    FLOOR_CONFIRMED,
    CEILING_CONFIRMED,
    CEILING_CANDIDATE,
    BELOW_CEILING,
    CEILING_TEST,
    STRONG_REBOUND,
    FRESH_BREAKOUT,
    BREAKOUT_CONTINUATION,
}

data class AdaptiveBreakoutCeiling(
    val anchorDate: String,
    val confirmedDate: String,
    val anchorPrice: Double,
    val upperBoundary: Double,
    val atrAtAnchor: Double,
    val breakoutDate: String?,
)

data class AdaptiveBreakoutRawStep(
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val atr: Double,
    val candidateFloor: Double,
    val candidatePeak: Double,
    val ceilingAnchor: Double?,
    val ceilingUpperBoundary: Double?,
    val majorCeilingUpperBoundary: Double?,
    val decision: AdaptiveBreakoutDecision,
    val explanation: String,
)

data class AdaptiveBreakoutConfirmationEvidence(
    val date: String,
    val closePositionPct: Double?,
    val volumeVsTenDayAverage: Double?,
    val distanceFromFiftyTwoWeekHighPct: Double?,
)

data class AdaptiveBreakoutEvaluation(
    val status: AdaptiveBreakoutStatus,
    val latestDate: String,
    val latestOpen: Double,
    val latestHigh: Double,
    val latestLow: Double,
    val latestClose: Double,
    val latestVolume: Long,
    val latestAtr: Double,
    val ceiling: AdaptiveBreakoutCeiling?,
    val majorCeiling: AdaptiveBreakoutCeiling?,
    val ceilingAgeSessions: Int?,
    val closeVsCeilingPct: Double?,
    val closePositionPct: Double?,
    val volumeVsTenDayAverage: Double?,
    val fiftyTwoWeekHigh: Double?,
    val distanceFromFiftyTwoWeekHighPct: Double?,
    val breakoutEvidence: AdaptiveBreakoutConfirmationEvidence?,
    val rawSteps: List<AdaptiveBreakoutRawStep>,
)

data class AdaptiveBreakoutScanResponse(
    val watchlistKey: String,
    val requestedAsOfDate: String,
    val latestCandleDate: String?,
    val scannedStockCount: Int,
    val freshBreakoutCount: Int,
    val config: AdaptiveBreakoutConfig,
    val rows: List<AdaptiveBreakoutScanRow>,
)

data class AdaptiveBreakoutScanRow(
    val symbol: String,
    val companyName: String,
    val instrumentToken: Long,
    val status: AdaptiveBreakoutStatus,
    val latestDate: String,
    val latestOpen: Double,
    val latestHigh: Double,
    val latestLow: Double,
    val latestClose: Double,
    val latestVolume: Long,
    val latestAtr: Double,
    val ceiling: AdaptiveBreakoutCeiling?,
    val majorCeiling: AdaptiveBreakoutCeiling?,
    val ceilingAgeSessions: Int?,
    val closeVsCeilingPct: Double?,
    val closePositionPct: Double?,
    val volumeVsTenDayAverage: Double?,
    val fiftyTwoWeekHigh: Double?,
    val distanceFromFiftyTwoWeekHighPct: Double?,
    val breakoutEvidence: AdaptiveBreakoutConfirmationEvidence?,
    val rawSteps: List<AdaptiveBreakoutRawStep>,
)
