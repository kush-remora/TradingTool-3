package com.tradingtool.core.strategy.emacontinuation

data class EmaContinuationStudyRequest(
    val watchlistKey: String? = null,
    val symbol: String? = null,
    val instrumentToken: Long? = null,
)

data class EmaContinuationWatchlistStudyRequest(
    val watchlistKey: String? = null,
)

enum class EmaContinuationSignalType {
    EMA_CROSS,
    BASE_BREAKOUT,
}

data class EmaContinuationDailyCandle(
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val closeNearHighPct: Double?,
    val closeAboveEma20Atr: Double?,
    val volumeVs20SessionAverageRatio: Double?,
    val volumeConfirmed: Boolean,
    val ema10: Double?,
    val ema20: Double?,
    val sma200: Double?,
    val emaProximityPct: Double?,
    val emaCross: Boolean = false,
    val baseBreakout: Boolean = false,
)

data class EmaContinuationSignal(
    val type: EmaContinuationSignalType,
    val date: String,
    val setupDate: String,
    val baseStartDate: String? = null,
    val baseEndDate: String? = null,
    val baseHigh: Double? = null,
    val baseLow: Double? = null,
    val baseWidthPct: Double? = null,
    val close: Double,
    val ema10: Double,
    val ema20: Double,
    val sma200: Double,
    val closeNearHighPct: Double?,
    val closeAboveEma20Atr: Double?,
    val volumeVs20SessionAverageRatio: Double?,
    val volumeConfirmed: Boolean,
    val entryDate: String? = null,
    val entryPrice: Double? = null,
    val daysTo5PctTarget: Int? = null,
    val daysTo10PctTarget: Int? = null,
    val daysTo20PctTarget: Int? = null,
)

data class EmaContinuationTargetPerformance(
    val targetPct: Double,
    val observations: Int,
    val hitCount: Int,
    val hitRatePct: Double?,
    val averageDaysToHit: Double?,
)

data class EmaContinuationStudySummary(
    val emaCrossCount: Int,
    val baseBreakoutCount: Int,
    val latestSignalDate: String?,
    val latestSignalType: EmaContinuationSignalType?,
)

data class EmaContinuationStudyResponse(
    val symbol: String,
    val watchlistKey: String,
    val testedFromDate: String,
    val testedToDate: String,
    val warmupSessions: Int,
    val minimumBaseSessions: Int,
    val maximumBaseSessions: Int,
    val maximumBaseWidthPct: Double,
    val atrPeriodSessions: Int,
    val volumeLookbackSessions: Int,
    val minimumVolumeRatio: Double,
    val entryRule: String,
    val baseRule: String,
    val summary: EmaContinuationStudySummary,
    val candles: List<EmaContinuationDailyCandle>,
    val signals: List<EmaContinuationSignal>,
    val targetPerformance: List<EmaContinuationTargetPerformance>,
)

enum class EmaContinuationWatchlistStudyStatus {
    ANALYZED,
    ERROR,
}

data class EmaContinuationWatchlistRow(
    val symbol: String,
    val companyName: String,
    val instrumentToken: Long,
    val status: EmaContinuationWatchlistStudyStatus,
    val errorMessage: String?,
    val emaCrossCount: Int,
    val baseBreakoutCount: Int,
    val totalEntryCount: Int,
    val latestSignalDate: String?,
    val latestSignalType: EmaContinuationSignalType?,
)

data class EmaContinuationWatchlistStudyResponse(
    val watchlistKey: String,
    val testedFromDate: String,
    val testedToDate: String,
    val stockCount: Int,
    val analyzedCount: Int,
    val rows: List<EmaContinuationWatchlistRow>,
)

data class EmaContinuationCurrentResponse(
    val symbol: String,
    val asOfDate: String,
    val latestCompletedCandleDate: String,
    val price: Double,
    val open: Double?,
    val high: Double?,
    val low: Double?,
    val volume: Long?,
    val changePct: Double?,
    val ema10: Double,
    val ema20: Double,
    val sma200: Double,
    val closeNearHighPct: Double?,
    val closeAboveEma20Atr: Double?,
    val distanceToEma10Pct: Double,
    val distanceToEma20Pct: Double,
    val distanceToSma200Pct: Double,
    val state: String,
    val isLiveQuote: Boolean,
    val updatedAt: String?,
)
