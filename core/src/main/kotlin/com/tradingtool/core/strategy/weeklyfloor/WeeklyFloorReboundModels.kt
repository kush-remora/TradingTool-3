package com.tradingtool.core.strategy.weeklyfloor

import java.time.LocalDate

data class WeeklyFloorReboundRequest(
    val symbol: String = "NETWEB",
    val supportFloor: Double? = null,
    val supportCeiling: Double? = null,
    val activeFrom: String? = null,
)

data class WeeklyFloorReboundRunConfig(
    val symbol: String,
    val toDate: LocalDate,
    val supportFloor: Double,
    val supportCeiling: Double,
    val activeFrom: LocalDate,
)

data class WeeklyFloorReboundRow(
    val zoneId: Int,
    val zoneCreatedDate: String,
    val zoneFloor: Double,
    val zoneCeiling: Double,
    val outcome: String,
    val testDate: String?,
    val testLow: Double?,
    val entryDate: String?,
    val entryPrice: Double?,
    val stopPrice: Double?,
    val targetPrice: Double?,
    val exitDate: String?,
    val exitPrice: Double?,
    val holdingTradingDays: Int?,
    val returnPct: Double?,
    val gapStop: Boolean,
    val exitWasAmbiguous: Boolean,
)

data class WeeklyFloorReboundSummary(
    val zonesCreated: Int,
    val filledTrades: Int,
    val targetHitCount: Int,
    val stopLossCount: Int,
    val fridayExitCount: Int,
)

data class WeeklyFloorReboundDailyRow(
    val date: String,
    val low: Double,
    val high: Double,
    val baseFloor: Double?,
    val baseCeiling: Double?,
    val baseWidthPct: Double?,
    val reboundTrigger: Double?,
    val targetPrice: Double?,
    val decision: String,
)

data class WeeklyFloorReboundReport(
    val symbol: String,
    val testedFromDate: String,
    val testedToDate: String,
    val summary: WeeklyFloorReboundSummary,
    val trades: List<WeeklyFloorReboundRow>,
    val dailyData: List<WeeklyFloorReboundDailyRow>,
)
