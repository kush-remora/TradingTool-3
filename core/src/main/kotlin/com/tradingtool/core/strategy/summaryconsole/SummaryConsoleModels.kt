package com.tradingtool.core.strategy.summaryconsole

data class SummaryConsoleResponse(
    val requestedAsOfDate: String,
    val lookbackSessions: Int,
    val watchlists: List<String>,
    val scannedCount: Int,
    val eventCount: Int,
    val uniqueStockCount: Int,
    val rows: List<SummaryConsoleRow>,
)

data class SummaryConsoleRow(
    val symbol: String,
    val companyName: String,
    val instrumentToken: Long,
    val watchlists: List<String>,
    val asOfDate: String,
    val close: Double,
    val previousClose: Double?,
    val dailyMovePct: Double?,
    val largeMove: Boolean,
    val sma200: Double?,
    val sma200Crossed: Boolean,
    val volume: Long,
    val averageVolume5: Double?,
    val volumeRatio: Double?,
    val volumeAnomaly: Boolean,
    val deliveryPercentage: Double?,
    val breakout20Level: Double?,
    val breakout20LevelCrossed: Boolean,
    val breakout20CloseConfirmed: Boolean,
    val breakout40Level: Double?,
    val breakout40LevelCrossed: Boolean,
    val breakout40CloseConfirmed: Boolean,
    val breakout60Level: Double?,
    val breakout60LevelCrossed: Boolean,
    val breakout60CloseConfirmed: Boolean,
)
