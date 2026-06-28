package com.tradingtool.core.strategy.csvbacktest

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import com.fasterxml.jackson.annotation.JsonProperty

data class BacktestTradeReview(
    val id: UUID? = null,
    val symbol: String,
    val signalDate: LocalDate,
    val marketCap: String?,
    val sector: String?,
    val entryDate: LocalDate?,
    val entryPrice: Double?,
    val exitDate: LocalDate?,
    val exitPrice: Double?,
    val pnlPct: Double?,
    val daysHeld: Int?,
    val slHit: Boolean?,
    @JsonProperty("isPass") val pass: Boolean?,
    val reasonTags: String?,
    val notes: String?,
    val createdAt: OffsetDateTime? = null,
    val updatedAt: OffsetDateTime? = null
)

data class BacktestTradeReviewApiRequest(
    val symbol: String,
    val signalDate: String,
    val marketCap: String?,
    val sector: String?,
    val entryDate: String?,
    val entryPrice: Double?,
    val exitDate: String?,
    val exitPrice: Double?,
    val pnlPct: Double?,
    val daysHeld: Int?,
    val slHit: Boolean?,
    @JsonProperty("isPass") val pass: Boolean?,
    val reasonTags: String?,
    val notes: String?
)

data class BacktestTradeReviewApiResponse(
    val reviews: List<BacktestTradeReview>
)
