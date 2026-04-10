package com.tradingtool.core.model.trade

import com.fasterxml.jackson.annotation.JsonProperty

data class TradeReadinessResponse(
    val symbols: List<TradeReadinessSymbol>,
)

data class TradeReadinessSymbol(
    val symbol: String,
    @get:JsonProperty("company_name")
    val companyName: String,
    @get:JsonProperty("rsi14")
    val rsi14: Double?,
    @get:JsonProperty("rsi15m")
    val rsi15m: Double?,
    @get:JsonProperty("alerts")
    val alerts: List<TradeReadinessAlert>,
)

data class TradeReadinessAlert(
    @get:JsonProperty("raw_text")
    val rawText: String,
    val action: String?,
    @get:JsonProperty("limit_price")
    val limitPrice: Double?,
    @get:JsonProperty("target_price")
    val targetPrice: Double?,
    @get:JsonProperty("received_at")
    val receivedAt: String,
)
