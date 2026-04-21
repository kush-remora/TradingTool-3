package com.tradingtool.core.earnings

import java.time.LocalDate

data class EarningsResultEvent(
    val stockSymbol: String,
    val resultDate: LocalDate,
)

data class EarningsResultRow(
    val id: Long,
    val stockSymbol: String,
    val resultDate: LocalDate,
    val behaviorPayloadJson: String,
)

data class EarningsRefreshRequest(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val pastDays: Int = 30,
    val chunkDays: Int = 5,
)

data class EarningsRefreshResult(
    val from: LocalDate,
    val to: LocalDate,
    val pastFrom: LocalDate,
    val pastTo: LocalDate,
    val chunkDays: Int,
    val chunkCount: Int,
    val fetchedEvents: Int,
    val upsertedRows: Int,
    val pastRowsEvaluated: Int,
    val behaviorRowsUpdated: Int,
    val preResultUpdates: Int,
    val resultDayUpdates: Int,
    val nextDayUpdates: Int,
    val plus5dUpdates: Int,
)

data class DateChunk(
    val from: LocalDate,
    val to: LocalDate,
)

interface EarningsCorporateEventSource {
    suspend fun fetchResultEvents(from: LocalDate, to: LocalDate): List<EarningsResultEvent>
}

interface EarningsResultGateway {
    suspend fun upsert(stockSymbol: String, resultDate: LocalDate): Int
    suspend fun findByResultDateRange(from: LocalDate, to: LocalDate): List<EarningsResultRow>
    suspend fun updateBehaviorPayload(id: Long, behaviorPayloadJson: String): Int
}

interface EarningsCandleGateway {
    suspend fun findDailyCandlesBySymbol(symbol: String, from: LocalDate, to: LocalDate): List<com.tradingtool.core.candle.DailyCandle>
}
