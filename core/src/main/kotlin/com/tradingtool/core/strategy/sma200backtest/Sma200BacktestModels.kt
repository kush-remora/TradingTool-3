package com.tradingtool.core.strategy.sma200backtest

data class Sma200BacktestRequest(
    val symbol: String,
    val instrumentToken: Long,
    val entrySmaPeriod: Int = 200,
)

data class Sma200BacktestTrade(
    val entryDate: String,
    val entryPrice: Double,
    val entryClose: Double,
    val sma100: Double,
    val pctToSma100: Double,
    val sma200: Double,
    val pctToSma200: Double,
    val distanceToSma200AbsPct: Double,
    val rsi14: Double,
    val drawdownFromHigh20Pct: Double,
    val drawdownFromHigh60Pct: Double,
    val consecutiveRedDays: Int,
    val move3dPct: Double,
    val return10dPct: Double?,
    val return20dPct: Double?,
    val return40dPct: Double?,
    val return10dDate: String?,
    val return20dDate: String?,
    val return40dDate: String?,
)

data class Sma200BacktestSummary(
    val smaTouchCount: Int,
    val tradeCount: Int,
    val ignoredTouchCount: Int,
    val completed10dCount: Int,
    val completed20dCount: Int,
    val completed40dCount: Int,
)

data class Sma200BacktestResponse(
    val symbol: String,
    val entrySmaPeriod: Int,
    val testedFromDate: String,
    val testedToDate: String,
    val summary: Sma200BacktestSummary,
    val trades: List<Sma200BacktestTrade>,
)
