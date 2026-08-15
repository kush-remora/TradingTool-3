package com.tradingtool.core.strategy.baseretestbacktest

import java.time.LocalDate

data class BaseRetestBacktestRequest(
    val watchlistKey: String,
    val symbol: String? = null,
    val targetPct: Double = 5.0,
    val stopLossPct: Double = 5.0,
)

data class BaseRetestBacktestRunConfig(
    val watchlistKey: String,
    val symbol: String?,
    val targetPct: Double,
    val stopLossPct: Double,
    val toDate: LocalDate,
)

data class BaseRetestObservation(
    val symbol: String,
    val companyName: String?,
    val instrumentToken: Long,
    val firstLowDate: String,
    val firstLow: Double,
    val firstReboundDate: String,
    val firstReboundHigh: Double,
    val firstReboundMovePct: Double,
    val secondLowDate: String,
    val secondLow: Double,
    val lowDifferencePct: Double,
    val confirmationDate: String,
    val confirmationHigh: Double,
    val confirmationMovePct: Double,
    val basePrice: Double,
    val limitPrice: Double,
    val invalidationClosePrice: Double,
    val orderActiveDate: String,
    val orderEndDate: String,
    val invalidationDate: String?,
    val fillDate: String?,
    val fillPrice: Double?,
    val targetPrice: Double?,
    val stopLossPrice: Double?,
    val exitDate: String?,
    val exitPrice: Double?,
    val outcome: String,
    val pnlPct: Double?,
    val holdingSessions: Int?,
)

data class BaseRetestBacktestSummary(
    val setupCount: Int,
    val filledTradeCount: Int,
    val noFillCount: Int,
    val baseInvalidatedCount: Int,
    val targetHitCount: Int,
    val stopLossCount: Int,
    val endOfDataExitCount: Int,
    val profitableTradeCount: Int,
    val lossTradeCount: Int,
    val winRatePct: Double?,
    val averagePnlPct: Double?,
    val medianPnlPct: Double?,
    val worstPnlPct: Double?,
    val totalPnlPct: Double?,
    val totalHoldingSessions: Int,
)

data class BaseRetestBacktestReport(
    val watchlistKey: String,
    val selectedSymbol: String?,
    val testedFromDate: String,
    val testedToDate: String,
    val lowTolerancePct: Double,
    val reboundPct: Double,
    val limitOffsetPct: Double,
    val invalidationPct: Double,
    val targetPct: Double,
    val stopLossPct: Double,
    val summary: BaseRetestBacktestSummary,
    val observations: List<BaseRetestObservation>,
)

object BaseRetestOutcomes {
    const val NO_FILL = "NO_FILL"
    const val BASE_INVALIDATED = "BASE_INVALIDATED"
    const val TARGET_HIT = "TARGET_HIT"
    const val STOP_LOSS = "STOP_LOSS"
    const val END_OF_DATA_EXIT = "END_OF_DATA_EXIT"
}

internal data class BaseRetestMember(
    val symbol: String,
    val companyName: String?,
    val instrumentToken: Long,
)
