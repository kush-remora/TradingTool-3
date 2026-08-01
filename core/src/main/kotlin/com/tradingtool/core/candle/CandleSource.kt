package com.tradingtool.core.candle

import java.time.LocalDate
import java.time.LocalDateTime

interface CandleSource {
    suspend fun getDailyCandles(
        token: Long?,
        symbol: String,
        fromDate: LocalDate,
        toDate: LocalDate,
    ): List<DailyCandle>

    suspend fun getIntradayCandles(
        token: Long?,
        symbol: String,
        interval: String,
        from: LocalDateTime,
        to: LocalDateTime,
    ): List<IntradayCandle>
}
