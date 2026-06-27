package com.tradingtool.core.strategy.fiftytwomomentum

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDate

@JsonIgnoreProperties(ignoreUnknown = true)
data class Rule5ApiRequest(
    val csvContent: String
)

data class Rule5SymbolResult(
    val date: String,
    val symbol: String,
    val marketCapName: String,
    val sector: String,
    val closePrice: Double,
    val sma200: Double,
    val fiftyTwoWeekHigh: Double,
    val fiftyTwoWeekLow: Double,
    val distTo52wHighPct: Double,
    val distTo52wLowPct: Double,
    val daysIn2Pct: Int,
    val daysIn3Pct: Int,
    val daysIn4Pct: Int,
    val dailyBreakdown: List<Rule5DailyDetail>
)

data class Rule5DailyDetail(
    val date: String,
    val closePrice: Double,
    val sma200: Double,
    val in2Pct: Boolean,
    val in3Pct: Boolean,
    val in4Pct: Boolean
)

data class Rule5ApiResponse(
    val results: List<Rule5SymbolResult>
)
