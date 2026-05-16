package com.tradingtool.core.screener

data class BaseSwingResult(
    val symbol: String,
    val companyName: String,
    val instrumentToken: Long,
    val currentPrice: Double,
    val price30dAgo: Double?,
    val baseDriftPct: Double?,
    val high30d: Double,
    val low30d: Double,
    val internalVolPct: Double,
    val distFrom52wHighPct: Double?,
    val high52w: Double?,
    val weeklyPulses: List<WeeklyPulse>,
    val setupScore: Int,
    val reasoning: String,
)

data class WeeklyPulse(
    val label: String,
    val startDate: String,
    val endDate: String,
    val swingPct: Double,
)

data class BaseSwingListResponse(
    val runAt: String,
    val lookbackDays: Int,
    val results: List<BaseSwingResult>,
)
