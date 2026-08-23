package com.tradingtool.core.strategy.fourdayclosestrengthbacktest

data class FourDayCloseStrengthBacktestRequest(
    val symbol: String? = null,
    val instrumentToken: Long? = null,
    val watchlistKey: String? = null,
    val closePositionThresholdPct: Double = 80.0,
)

data class FourDayCloseStrengthCandle(
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val dailyChangePct: Double?,
    val greenDay: Boolean,
    val openToClosePct: Double?,
    val closeLocationPct: Double?,
)

data class FourDayCloseStrengthSignal(
    val symbol: String,
    val companyName: String?,
    val instrumentToken: Long,
    val signalDate: String,
    val patternStartDate: String,
    val patternEndDate: String,
    val patternClosePositionPct: List<Double>,
    val lastTwentyTradingDays: List<FourDayCloseStrengthCandle>,
)

data class FourDayCloseStrengthSymbolReport(
    val symbol: String,
    val companyName: String?,
    val instrumentToken: Long,
    val testedFromDate: String,
    val testedToDate: String,
    val signalCount: Int,
    val signals: List<FourDayCloseStrengthSignal>,
)

data class FourDayCloseStrengthBacktestResponse(
    val symbol: String?,
    val watchlistKey: String?,
    val testedFromDate: String,
    val testedToDate: String,
    val closePositionThresholdPct: Double,
    val patternSessions: Int,
    val detailSessions: Int,
    val signalCount: Int,
    val signals: List<FourDayCloseStrengthSignal>,
    val symbols: List<FourDayCloseStrengthSymbolReport>,
)

internal data class FourDayCloseStrengthMember(
    val symbol: String,
    val companyName: String?,
    val instrumentToken: Long,
)
