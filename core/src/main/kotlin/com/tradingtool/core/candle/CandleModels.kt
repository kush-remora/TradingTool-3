package com.tradingtool.core.candle

import java.time.LocalDate
import java.time.LocalDateTime

data class DailyCandle(
    val instrumentToken: Long,
    val symbol: String,
    val candleDate: LocalDate,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
)

data class IntradayCandle(
    val instrumentToken: Long,
    val symbol: String,
    val interval: String,
    val candleTimestamp: LocalDateTime, // always IST, no timezone
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
)
