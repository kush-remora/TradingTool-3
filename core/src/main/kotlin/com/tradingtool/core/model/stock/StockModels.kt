package com.tradingtool.core.model.stock

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ==================== Core Entity ====================

@Serializable
data class StockTag(
    val name: String,
    val color: String,
)

@Serializable
data class Stock(
    val id: Long,
    val symbol: String,
    @SerialName("instrument_token")
    @get:JsonProperty("instrument_token")
    val instrumentToken: Long,
    @SerialName("company_name")
    @get:JsonProperty("company_name")
    val companyName: String,
    val exchange: String,
    val notes: String? = null,
    val priority: Int? = null,
    val tags: List<StockTag> = emptyList(),
    @SerialName("created_at")
    @get:JsonProperty("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    @get:JsonProperty("updated_at")
    val updatedAt: String,
)

// ==================== Input Models ====================

@Serializable
data class CreateStockInput(
    val symbol: String,
    @SerialName("instrument_token")
    @JsonProperty("instrument_token")
    val instrumentToken: Long,
    @SerialName("company_name")
    @JsonProperty("company_name")
    val companyName: String,
    val exchange: String,
    val notes: String? = null,
    val priority: Int? = null,
    val tags: List<StockTag> = emptyList(),
)

@Serializable
data class UpdateStockPayload(
    val notes: String? = null,
    val priority: Int? = null,
    val tags: List<StockTag>? = null,
)

// ==================== Health Check ====================

@Serializable
data class TableAccessStatus(
    @SerialName("table_name")
    @get:JsonProperty("table_name")
    val tableName: String,
    val accessible: Boolean,
    val error: String? = null,
)

// ==================== Instrument Search (used by InstrumentResource) ====================

@Serializable
data class InstrumentSearchResult(
    @SerialName("instrument_token")
    @get:JsonProperty("instrument_token")
    val instrumentToken: Long,
    @SerialName("trading_symbol")
    @get:JsonProperty("trading_symbol")
    val tradingSymbol: String,
    @SerialName("company_name")
    @get:JsonProperty("company_name")
    val companyName: String,
    val exchange: String,
    @SerialName("instrument_type")
    @get:JsonProperty("instrument_type")
    val instrumentType: String,
)
