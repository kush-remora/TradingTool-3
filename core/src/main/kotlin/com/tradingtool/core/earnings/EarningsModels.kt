package com.tradingtool.core.earnings

import java.time.LocalDate

data class EarningsResultEvent(
    val stockSymbol: String,
    val resultDate: LocalDate,
    val instrumentToken: Long? = null,
)

data class EarningsResultRow(
    val id: Long,
    val stockSymbol: String,
    val instrumentToken: Long?,
    val resultDate: LocalDate,
    val behaviorPayloadJson: String,
)

data class EarningsMissingTokenRow(
    val id: Long,
    val stockSymbol: String,
    val resultDate: LocalDate,
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
    val uniqueEvents: Int,
    val upsertedRows: Int,
    val unresolvedTokenCount: Int,
    val unresolvedSymbolsSample: List<String>,
    val nullInstrumentTokenRowsAfterRefresh: Int,
    val instrumentTokenNotNullEnforced: Boolean,
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
    suspend fun upsert(stockSymbol: String, instrumentToken: Long, resultDate: LocalDate): Int
    suspend fun findByResultDateRange(from: LocalDate, to: LocalDate): List<EarningsResultRow>
    suspend fun backfillInstrumentTokenFromStocks(): Int
    suspend fun findRowsMissingInstrumentToken(limit: Int): List<EarningsMissingTokenRow>
    suspend fun updateInstrumentToken(id: Long, instrumentToken: Long): Int
    suspend fun countRowsMissingInstrumentToken(): Int
    suspend fun enforceInstrumentTokenNotNull(): Boolean
    suspend fun updateBehaviorPayload(id: Long, behaviorPayloadJson: String): Int
}

interface EarningsCandleGateway {
    suspend fun findDailyCandlesBySymbol(symbol: String, from: LocalDate, to: LocalDate): List<com.tradingtool.core.candle.DailyCandle>
}

interface EarningsStockTokenLookup {
    suspend fun findInstrumentTokenBySymbol(symbol: String): Long?
}
