package com.tradingtool.core.strategy.fridaystrengthbacktest

import java.time.LocalDate

data class FridayCloseStrengthBacktestRequest(
    val watchlistKey: String,
)

data class FridayCloseStrengthBacktestRunConfig(
    val watchlistKey: String,
    val toDate: LocalDate,
)

data class FridayCloseStrengthObservation(
    val symbol: String,
    val companyName: String?,
    val instrumentToken: Long,
    val signalDate: String,
    val thursdayClose: Double,
    val fridayHigh: Double,
    val fridayLow: Double,
    val fridayClose: Double,
    val fridayClosePositionPct: Double,
    val fridayMovePct: Double,
    val entryDate: String,
    val entryPrice: Double,
    val followingWeekHighDate: String,
    val followingWeekHigh: Double,
    val maximumUpsidePct: Double,
)

data class FridayCloseStrengthBacktestSummary(
    val signalCount: Int,
    val maximumUpsideAtLeast2PctCount: Int,
    val maximumUpsideAtLeast5PctCount: Int,
    val maximumUpsideAtLeast2PctRatePct: Double?,
    val averageMaximumUpsidePct: Double?,
    val medianMaximumUpsidePct: Double?,
)

data class FridayCloseStrengthBacktestReport(
    val watchlistKey: String,
    val testedFromDate: String,
    val testedToDate: String,
    val closePositionThresholdPct: Double,
    val fridayMoveThresholdPct: Double,
    val summary: FridayCloseStrengthBacktestSummary,
    val observations: List<FridayCloseStrengthObservation>,
)

internal data class FridayCloseStrengthMember(
    val symbol: String,
    val companyName: String?,
    val instrumentToken: Long,
)
