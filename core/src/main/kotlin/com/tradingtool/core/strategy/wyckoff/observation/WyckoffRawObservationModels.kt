package com.tradingtool.core.strategy.wyckoff.observation

data class WyckoffRawObservationResponse(
    val asOfDate: String,
    val lookbackCalendarDays: Int,
    val watchlist: String?,
    val stocks: List<WyckoffRawObservationStock>,
)

data class WyckoffRawObservationStock(
    val symbol: String,
    val companyName: String,
    val watchlists: List<String>,
    val startDate: String,
    val endDate: String,
    val firstClose: Double?,
    val latestClose: Double?,
    val periodReturnPct: Double?,
    val periodHigh: Double?,
    val periodLow: Double?,
    val averageAtrPct: Double?,
    val lowestAtrPct: Double?,
    val averageVolume: Double?,
    val periods: List<WyckoffRawObservationPeriod>,
    val observations: List<WyckoffRawObservationRow>,
)

enum class WyckoffRawObservationPhase {
    PRIOR_DECLINE,
    CANDIDATE_ACCUMULATION,
    ORDERLY_ADVANCE,
    POSSIBLE_PHASE_D,
    UNCLASSIFIED,
}

data class WyckoffRawObservationPeriod(
    val phase: WyckoffRawObservationPhase,
    val startDate: String,
    val endDate: String,
    val observationCount: Int,
    val explanation: String,
)

data class WyckoffRawObservationRow(
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val closeChangePct: Double?,
    val rangePct: Double?,
    val trueRange: Double?,
    val atr14: Double?,
    val atrPct: Double?,
    val movementVsAtr: Double?,
    val volumeVs20dAverage: Double?,
    val priceChange20dPct: Double?,
    val twentyDayRangePct: Double?,
    val priorTwentyDayHigh: Double?,
    val phase: WyckoffRawObservationPhase,
    val annotation: String?,
)
