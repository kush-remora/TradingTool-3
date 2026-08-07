package com.tradingtool.core.strategy.netwebcycle

import java.time.LocalDate

enum class NetwebCyclePhase {
    WEEKLY_ROTATION,
    BULL_RUN,
    DRAWDOWN,
    NEW_BASE,
}

data class NetwebCycleRequest(
    val symbol: String = "NETWEB",
)

data class NetwebCycleRunConfig(
    val symbol: String,
    val toDate: LocalDate,
)

data class NetwebCycleConfig(
    val baseLookbackTradingDays: Int = 20,
    val minimumBaseHistoryTradingDays: Int = 15,
    val maximumBaseWidthPct: Double = 10.0,
    val maximumBaseDriftPct: Double = 8.0,
    val breakoutBufferPct: Double = 1.0,
    val strongBreakoutMovePct: Double = 5.0,
    val drawdownTriggerPct: Double = 8.0,
    val minimumNewBaseTradingDays: Int = 10,
    val newBaseLookbackTradingDays: Int = 10,
    val minimumHistoryTradingDays: Int = 25,
    val rotationMoveTargetPct: Double = 5.0,
)

data class NetwebCycleSnapshot(
    val date: String,
    val phase: NetwebCyclePhase,
    val currentPrice: Double,
    val baseLow: Double?,
    val baseHigh: Double?,
    val baseWidthPct: Double?,
    val positionInBasePct: Double?,
    val dailyChangePct: Double?,
    val fiveDayReturnPct: Double?,
    val twentyDayReturnPct: Double?,
    val volumeRatio20Day: Double?,
    val expansionPeak: Double?,
    val drawdownFromPeakPct: Double?,
    val phaseStartDate: String,
    val phaseAgeTradingDays: Int,
    val fivePercentMoveCount: Int,
    val breakoutAboveBase: Boolean,
    val confidencePct: Int,
    val action: String,
    val evidence: List<String>,
)

data class NetwebCycleSegment(
    val phase: NetwebCyclePhase,
    val startDate: String,
    val endDate: String,
    val tradingDays: Int,
    val startPrice: Double,
    val endPrice: Double,
    val returnPct: Double,
)

data class NetwebCycleReport(
    val symbol: String,
    val testedFromDate: String,
    val testedToDate: String,
    val current: NetwebCycleSnapshot,
    val segments: List<NetwebCycleSegment>,
    val dailySnapshots: List<NetwebCycleSnapshot>,
)
