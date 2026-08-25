package com.tradingtool.core.strategy.adaptivebreakout

data class AdaptiveBreakoutConfig(
    val atrPeriod: Int = 14,
    val floorReboundAtrMultiple: Double = 1.0,
    val peakRejectionAtrMultiple: Double = 1.0,
    val compactPeakRejectionAtrMultiple: Double = 0.5,
    val compactCeilingConfirmationSessions: Int = 2,
    val earlyBreakoutBufferAtrMultiple: Double = 0.1,
    val ceilingWidthAtrMultiple: Double = 0.5,
    val maximumLocalCeilingDistanceAtrMultiple: Double = 3.0,
    /** Expire an active ceiling after this many sessions without a new structure generation. */
    val maximumCeilingAgeSessions: Int = 252,
    /** Stop moving the strict failed-attempt cap after this many ratchets. */
    val maximumFailedAttemptRatchets: Int = 3,
    val strongReboundAtrMultiple: Double = 2.0,
)

enum class AdaptiveBreakoutStatus {
    NO_CEILING,
    BELOW_CEILING,
    TESTING_CEILING,
    STRONG_REBOUND,
    EARLY_BREAKOUT,
    FRESH_BREAKOUT,
    BREAKOUT_CONTINUATION,
}

enum class AdaptiveBreakoutDecision {
    BUILDING_STRUCTURE,
    FLOOR_CONFIRMED,
    COMPACT_CEILING_CANDIDATE,
    CEILING_CONFIRMED,
    AMBIGUOUS_OUTSIDE_DAY,
    BELOW_CEILING,
    CEILING_TEST,
    FAILED_BREAKOUT,
    CEILING_RECLAIM,
    STRONG_REBOUND,
    EARLY_BREAKOUT,
    FRESH_BREAKOUT,
    BREAKOUT_CONTINUATION,
}

enum class AdaptiveBreakoutCeilingType {
    STRONG_REJECTION,
    COMPACT_RANGE,
    POST_BREAKOUT_SWING,
}

data class AdaptiveBreakoutCeiling(
    val anchorDate: String,
    val confirmedDate: String,
    val anchorPrice: Double,
    val baseUpperBoundary: Double,
    val upperBoundary: Double,
    val failedAttemptHigh: Double?,
    val lastFailedAttemptDate: String?,
    val failedAttemptCount: Int,
    val atrAtAnchor: Double,
    val type: AdaptiveBreakoutCeilingType,
    val testCount: Int,
    val lastTestDate: String?,
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
    val candidateFloorDate: String,
    val candidateFloorAtr: Double,
    val candidatePeak: Double,
    val candidatePeakAtr: Double,
    val ceilingAnchor: Double?,
    val ceilingBaseUpperBoundary: Double?,
    val ceilingUpperBoundary: Double?,
    val ceilingFailedAttemptHigh: Double?,
    val majorCeilingUpperBoundary: Double?,
    val ceilingTestCount: Int?,
    val ceilingType: AdaptiveBreakoutCeilingType?,
    val breakoutBoundary: Double?,
    val compactCeilingCandidate: Double?,
    val compactCeilingConfirmationCount: Int?,
    val decision: AdaptiveBreakoutDecision,
    val explanation: String,
)

enum class BreakoutQualityVerdict {
    PASS,
    WAIT,
    REJECT,
    UNAVAILABLE,
}

enum class BreakoutQualityDecision {
    PASS,
    WAIT,
    REJECT,
    CONTEXT_ONLY,
}

data class BreakoutQualityRuleResult(
    val key: String,
    val label: String,
    val rule: String,
    val actual: String,
    val verdict: BreakoutQualityVerdict,
    val explanation: String,
)

data class BreakoutChartContext(
    val overallDecision: BreakoutQualityDecision,
    val decisionSummary: String,
    val sma50: Double?,
    val sma200: Double?,
    val sma50ChangePctFiveSessions: Double?,
    val sma200ChangePctTwentySessions: Double?,
    val priorFiftyTwoWeekHigh: Double?,
    val nextObstaclePrice: Double?,
    val nextObstacleLabel: String?,
    val roomToObstaclePct: Double?,
    val roomToObstacleAtr: Double?,
    val rules: List<BreakoutQualityRuleResult>,
)

/**
 * Delivery footprint for the reviewed day, compared against the stock's own history.
 * Context only — never scored (delivery % shrinks mechanically when breakout-day volume
 * expands, so a fixed % bar punishes exactly the sessions we care about).
 */
data class BreakoutDeliveryContext(
    val deliveryPercentage: Double?,
    val deliveredQuantity: Long?,
    /** Mean delivery % over the 20 sessions before the reviewed day (excludes the day itself). */
    val averageDeliveryPercentage: Double?,
    /** Mean delivered quantity over the 20 sessions before the reviewed day. */
    val averageDeliveredQuantity: Double?,
    /** Today's delivered quantity ÷ 20-session average. The real institutional footprint. */
    val deliveredQuantityMultiple: Double?,
)

data class BreakoutRecentVolumeDay(
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val volumeVsQuietAveragePct: Double,
    val lowToHighPct: Double?,
    val openToClosePct: Double?,
)

data class BreakoutTrendAlignmentResult(
    val available: Boolean,
    val marketAligned: Boolean?,
    val stockAligned: Boolean?,
    val aligned: Boolean?,
    val marketClose: Double?,
    val marketSma200: Double?,
    val marketSma200SlopePct: Double?,
    val stockClose: Double?,
    val stockSma200: Double?,
    val stockSma200SlopePct: Double?,
    val stockRelativeStrength3mPct: Double?,
    val stockRelativeStrength6mPct: Double?,
    val explanation: String,
)

enum class BreakoutVolumeFootprintDecision {
    PASS,
    WAIT,
    REJECT,
    UNAVAILABLE,
}

data class BreakoutVolumeFootprintResult(
    val decision: BreakoutVolumeFootprintDecision,
    val footprintDate: String?,
    val footprintVolumeRatio: Double?,
    val footprintPriceRankPct: Double?,
    val footprintLowHeld: Boolean?,
    val distributionWarningDate: String?,
    val distributionWarningVolumeRatio: Double?,
    val breakoutClosePositionPct: Double?,
    val explanation: String,
)

data class BreakoutDayQualityResponse(
    val symbol: String,
    val date: String,
    val structureStatus: AdaptiveBreakoutStatus,
    val structureDecision: AdaptiveBreakoutDecision,
    val structureExplanation: String,
    val overallDecision: BreakoutQualityDecision,
    val decisionSummary: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    /** Average of the five lowest volumes in the prior 20 sessions. */
    val twentyDayQuietAverageVolume: Double?,
    /** Breakout-day volume as a percentage of the average volume of the prior 10 sessions. */
    val volumeVsTenDayAveragePct: Double?,
    /** Breakout-day volume as a percentage of the average of the five lowest volumes in the prior 20 sessions. */
    val volumeVsTwentyDayQuietAveragePct: Double?,
    /** Breakout-day volume change versus the highest volume in the prior 20 sessions. */
    val volumeVsPriorTwentyDayHighChangePct: Double?,
    /** Prior 20-session days whose volume is at least five times the quiet baseline. */
    val recentAbnormalVolumeDays: List<BreakoutRecentVolumeDay>,
    val atr: Double,
    val floor: Double,
    val peak: Double,
    val breakoutLine: Double?,
    val majorCeiling: Double?,
    val sma50: Double?,
    val sma200: Double?,
    val deliveryContext: BreakoutDeliveryContext?,
    val volumeFootprint: BreakoutVolumeFootprintResult,
    val rules: List<BreakoutQualityRuleResult>,
    val chartContext: BreakoutChartContext,
)

data class AdaptiveBreakoutFollowThroughDay(
    val date: String,
    val close: Double,
    val volume: Long,
    val volumeVsTenDayAverage: Double?,
    val closePositionPct: Double?,
)

data class AdaptiveBreakoutConfirmationEvidence(
    val date: String,
    val closePositionPct: Double?,
    val volumeVsTenDayAverage: Double?,
    val distanceFromFiftyTwoWeekHighPct: Double?,
    val floorDate: String,
    val floorPrice: Double,
    val floorToBreakoutPct: Double,
    val floorToBreakoutAtr: Double?,
    val rangeLocked: Boolean,
    val breakoutDay: AdaptiveBreakoutFollowThroughDay? = null,
    val highestVolumeAfterBreakout: AdaptiveBreakoutFollowThroughDay? = null,
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
