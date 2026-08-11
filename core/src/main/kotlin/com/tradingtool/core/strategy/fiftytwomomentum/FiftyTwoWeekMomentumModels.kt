package com.tradingtool.core.strategy.fiftytwomomentum

data class Rule5BreakoutDay(
    val date: String,
    val high: Double,
    val close: Double,
    val referenceHigh: Double,
    val referenceHighDaysAgo: Int,
    val closeVsReferenceHighPct: Double,
)

data class Rule5SymbolResult(
    val symbol: String,
    val companyName: String,
    val instrumentToken: Long,
    val watchlists: List<String>,
    val latestBreakoutDate: String,
    val latestHigh: Double,
    val latestClose: Double,
    val latestReferenceHigh: Double,
    val latestReferenceHighDaysAgo: Int,
    val latestCloseVsReferenceHighPct: Double,
    val freshBreakoutDays: List<Rule5BreakoutDay>,
)

data class Rule5ApiResponse(
    val requestedAsOfDate: String,
    val lookbackSessions: Int,
    val breakoutPeriodSessions: Int,
    val nearHighTolerancePct: Double,
    val watchlists: List<String>,
    val scannedCount: Int,
    val breakoutStockCount: Int,
    val results: List<Rule5SymbolResult>,
)

data class Rule5BacktestSignal(
    val symbol: String,
    val companyName: String,
    val signalDate: String,
    val breakoutHigh: Double,
    val breakoutClose: Double,
    val referenceHigh: Double,
    val referenceHighDaysAgo: Int,
    val closeVsReferenceHighPct: Double,
    val outcome: String,
    val entryPrice: Double?,
    val targetPrice: Double?,
    val tradeStatus: String?,
)

data class Rule5BacktestTrade(
    val symbol: String,
    val companyName: String,
    val instrumentToken: Long,
    val entryDate: String,
    val entryPrice: Double,
    val targetPrice: Double,
    val exitDate: String?,
    val exitPrice: Double?,
    val latestPrice: Double,
    val changeFromEntryPct: Double,
    val status: String,
    val holdingTradingDays: Int,
)

data class Rule5BacktestResponse(
    val requestedAsOfDate: String,
    val periodStartDate: String,
    val breakoutPeriodSessions: Int,
    val nearHighTolerancePct: Double,
    val targetPct: Double,
    val scannedCount: Int,
    val signalCount: Int,
    val enteredTradeCount: Int,
    val targetHitCount: Int,
    val openTradeCount: Int,
    val signals: List<Rule5BacktestSignal>,
    val trades: List<Rule5BacktestTrade>,
)
