package com.tradingtool.core.technical

data class TechnicalContext(
    val symbol: String,
    val atr14: Double,
    val rsi14: Double,
    val lowestRsi50d: Double,
    val highestRsi50d: Double,
    val lowestRsi100d: Double,
    val highestRsi100d: Double,
    val lowestRsi200d: Double,
    val highestRsi200d: Double,
    val sma200: Double,
    val ltp: Double,
    val recentSessions: List<SessionCandle>
)

data class SessionCandle(
    val date: String,
    val dayOfWeek: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val range: Double,
    val lowToHighPct: Double
)
