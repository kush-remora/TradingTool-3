package com.tradingtool.core.model.stock

import com.fasterxml.jackson.annotation.JsonProperty

// ==================== Core Entity ====================

data class StockTag(
    @JsonProperty("name")
    val name: String,
    @JsonProperty("color")
    val color: String,
)

data class Stock(
    val id: Long,
    val symbol: String,
    @get:JsonProperty("instrument_token")
    val instrumentToken: Long,
    @get:JsonProperty("company_name")
    val companyName: String,
    val exchange: String,
    val notes: String? = null,
    val priority: Int? = null,
    val tags: List<StockTag> = emptyList(),
    @get:JsonProperty("created_at")
    val createdAt: String,
    @get:JsonProperty("updated_at")
    val updatedAt: String,
)

// ==================== Input Models ====================

data class CreateStockInput(
    @JsonProperty("symbol")
    val symbol: String,
    @JsonProperty("instrument_token")
    val instrumentToken: Long,
    @JsonProperty("company_name")
    val companyName: String,
    @JsonProperty("exchange")
    val exchange: String,
    @JsonProperty("notes")
    val notes: String? = null,
    @JsonProperty("priority")
    val priority: Int? = null,
    @JsonProperty("tags")
    val tags: List<StockTag> = emptyList(),
)

data class UpdateStockPayload(
    @JsonProperty("notes")
    val notes: String? = null,
    @JsonProperty("priority")
    val priority: Int? = null,
    @JsonProperty("tags")
    val tags: List<StockTag>? = null,
)

// ==================== Health Check ====================

data class TableAccessStatus(
    @get:JsonProperty("table_name")
    val tableName: String,
    val accessible: Boolean,
    val error: String? = null,
)

// ==================== Instrument Search (used by InstrumentResource) ====================

data class InstrumentSearchResult(
    @get:JsonProperty("instrument_token")
    val instrumentToken: Long,
    @get:JsonProperty("trading_symbol")
    val tradingSymbol: String,
    @get:JsonProperty("company_name")
    val companyName: String,
    val exchange: String,
    @get:JsonProperty("instrument_type")
    val instrumentType: String,
)
