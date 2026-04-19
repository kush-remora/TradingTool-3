package com.tradingtool.core.analysis.swing

import java.time.LocalDate

enum class SwingType {
    PEAK, TROUGH
}

data class SwingCandle(
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
)

data class SwingPoint(
    val date: String,
    val dayOfWeek: String,
    val price: Double,
    val type: SwingType,
    val changePct: Double,
    val barsSinceLast: Int
)

data class SwingAnalysisResponse(
    val symbol: String,
    val reversalPct: Double,
    val points: List<SwingPoint>,
    val candles: List<SwingCandle>,
    val stats: SwingStats
)

data class SwingStats(
    val averageUpswingPct: Double,
    val averageDownswingPct: Double,
    val averageSwingDurationBars: Double
)
