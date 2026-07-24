package com.tradingtool.core.strategy.weeklybase

import java.time.LocalDate

data class WeeklyBaseDefinitionRequest(
    val symbol: String = "NETWEB",
)

data class WeeklyBaseDefinitionRunConfig(
    val symbol: String,
    val toDate: LocalDate,
)

data class WeeklyBaseDefinitionConfig(
    val smaWindowTradingDays: Int = 200,
    val minimumSmaDistancePct: Double = -15.0,
    val maximumSmaDistancePct: Double = 15.0,
    val maximumZoneWidthPct: Double = 2.0,
)

data class WeeklyBaseDefinitionRow(
    val evaluationDate: String,
    val firstWeekStartDate: String,
    val firstWeekLow: Double,
    val secondWeekStartDate: String,
    val secondWeekLow: Double,
    val thirdWeekStartDate: String,
    val thirdWeekLow: Double,
    val zoneFloor: Double,
    val zoneCeiling: Double,
    val zoneWidthPct: Double,
    val sma200: Double,
    val distanceFromSma200Pct: Double,
    val isWithinSma200Range: Boolean,
    val isValid: Boolean,
    val validityReason: String,
)

data class WeeklyBaseDefinitionReport(
    val symbol: String,
    val testedFromDate: String,
    val testedToDate: String,
    val validBaseCount: Int,
    val rows: List<WeeklyBaseDefinitionRow>,
)
