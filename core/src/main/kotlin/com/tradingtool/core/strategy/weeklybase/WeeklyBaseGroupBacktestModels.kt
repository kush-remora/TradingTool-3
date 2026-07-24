package com.tradingtool.core.strategy.weeklybase

data class WeeklyBaseGroupBacktestRequest(
    val indexKeys: List<String>,
)

data class WeeklyBaseGroupBacktestTrade(
    val entryDate: String,
    val entryPrice: Double,
    val targetPrice: Double,
    val exitDate: String?,
    val outcome: String,
    val holdingTradingDays: Int?,
)

data class WeeklyBaseGroupBacktestRow(
    val indexKey: String,
    val symbol: String,
    val companyName: String,
    val validBaseCount: Int,
    val filledTradeCount: Int,
    val targetHitCount: Int,
    val openTradeCount: Int,
    val latestZoneFloor: Double?,
    val latestZoneCeiling: Double?,
    val latestSmaDistancePct: Double?,
    val trades: List<WeeklyBaseGroupBacktestTrade>,
)

data class WeeklyBaseGroupBacktestGroupSummary(
    val indexKey: String,
    val totalStocks: Int,
    val stocksWithValidBase: Int,
    val filledTradeCount: Int,
    val targetHitCount: Int,
    val openTradeCount: Int,
)

data class WeeklyBaseGroupBacktestReport(
    val testedFromDate: String,
    val testedToDate: String,
    val groups: List<WeeklyBaseGroupBacktestGroupSummary>,
    val rows: List<WeeklyBaseGroupBacktestRow>,
)
