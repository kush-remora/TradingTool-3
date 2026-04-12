package com.tradingtool.core.fundamentals.screener

data class ScreenerFundamentalsSnapshot(
    val symbol: String,
    val companyName: String,
    val marketCapCr: Double?,
    val stockPe: Double?,
    val rocePercent: Double?,
    val roePercent: Double?,
    val promoterHoldingPercent: Double?,
    val broadIndustry: String?,
    val industry: String?,
    val debtToEquity: Double?,
    val pledgedPercent: Double?,
)

data class ScreenerFundamentalsValidationRow(
    val symbol: String,
    val reachable: Boolean,
    val parsed: Boolean,
    val snapshot: ScreenerFundamentalsSnapshot?,
    val error: String?,
)

data class ScreenerFundamentalsValidationReport(
    val requestedSymbols: List<String>,
    val testedCount: Int,
    val reachableCount: Int,
    val parsedCount: Int,
    val marketCapCount: Int,
    val stockPeCount: Int,
    val roceCount: Int,
    val roeCount: Int,
    val promoterHoldingCount: Int,
    val broadIndustryCount: Int,
    val industryCount: Int,
    val debtToEquityCount: Int,
    val pledgedPercentCount: Int,
    val rows: List<ScreenerFundamentalsValidationRow>,
)
