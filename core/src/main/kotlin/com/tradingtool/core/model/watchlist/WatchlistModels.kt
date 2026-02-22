package com.tradingtool.core.model.watchlist

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

// ==================== Core Entity Models ====================

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
    val description: String? = null,
    val priority: Int? = null,
    @SerialName("created_at")
    @get:JsonProperty("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    @get:JsonProperty("updated_at")
    val updatedAt: String,
)

@Serializable
data class Watchlist(
    val id: Long,
    val name: String,
    val description: String? = null,
    @SerialName("created_at")
    @get:JsonProperty("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    @get:JsonProperty("updated_at")
    val updatedAt: String,
)

@Serializable
data class Tag(
    val id: Long,
    val name: String,
    @SerialName("created_at")
    @get:JsonProperty("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    @get:JsonProperty("updated_at")
    val updatedAt: String,
)

@Serializable
data class WatchlistStock(
    val id: Long,
    @SerialName("watchlist_id")
    @get:JsonProperty("watchlist_id")
    val watchlistId: Long,
    @SerialName("stock_id")
    @get:JsonProperty("stock_id")
    val stockId: Long,
    @SerialName("created_at")
    @get:JsonProperty("created_at")
    val createdAt: String,
)

@Serializable
data class StockTag(
    val id: Long,
    @SerialName("stock_id")
    @get:JsonProperty("stock_id")
    val stockId: Long,
    @SerialName("tag_id")
    @get:JsonProperty("tag_id")
    val tagId: Long,
    @SerialName("created_at")
    @get:JsonProperty("created_at")
    val createdAt: String,
)

@Serializable
data class WatchlistTag(
    val id: Long,
    @SerialName("watchlist_id")
    @get:JsonProperty("watchlist_id")
    val watchlistId: Long,
    @SerialName("tag_id")
    @get:JsonProperty("tag_id")
    val tagId: Long,
    @SerialName("created_at")
    @get:JsonProperty("created_at")
    val createdAt: String,
)

// ==================== Input Models for Create Operations ====================

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
    val description: String? = null,
    val priority: Int? = null,
)

@Serializable
data class CreateWatchlistInput(
    val name: String,
    val description: String? = null,
)

@Serializable
data class CreateTagInput(
    val name: String,
)

@Serializable
data class CreateWatchlistStockInput(
    @SerialName("watchlist_id")
    @JsonProperty("watchlist_id")
    val watchlistId: Long,
    @SerialName("stock_id")
    @JsonProperty("stock_id")
    val stockId: Long,
)

@Serializable
data class CreateStockTagInput(
    @SerialName("stock_id")
    @JsonProperty("stock_id")
    val stockId: Long,
    @SerialName("tag_id")
    @JsonProperty("tag_id")
    val tagId: Long,
)

@Serializable
data class CreateWatchlistTagInput(
    @SerialName("watchlist_id")
    @JsonProperty("watchlist_id")
    val watchlistId: Long,
    @SerialName("tag_id")
    @JsonProperty("tag_id")
    val tagId: Long,
)

// ==================== Update Field Enums ====================

enum class StockUpdateField {
    COMPANY_NAME,
    EXCHANGE,
    DESCRIPTION,
    PRIORITY,
}

enum class WatchlistUpdateField {
    NAME,
    DESCRIPTION,
}

enum class TagUpdateField {
    NAME,
}

// ==================== Update Input Models ====================

data class UpdateStockInput(
    val fieldsToUpdate: Set<StockUpdateField>,
    val companyName: String? = null,
    val exchange: String? = null,
    val description: String? = null,
    val priority: Int? = null,
)

@Serializable
data class UpdateStockPayload(
    @SerialName("company_name")
    @JsonProperty("company_name")
    val companyName: String? = null,
    val exchange: String? = null,
    val description: String? = null,
    val priority: Int? = null,
)

data class UpdateWatchlistInput(
    val fieldsToUpdate: Set<WatchlistUpdateField>,
    val name: String? = null,
    val description: String? = null,
)

@Serializable
data class UpdateWatchlistPayload(
    val name: String? = null,
    val description: String? = null,
)

data class UpdateTagInput(
    val fieldsToUpdate: Set<TagUpdateField>,
    val name: String? = null,
)

@Serializable
data class UpdateTagPayload(
    val name: String? = null,
)

// ==================== Response Models with Enriched Data ====================

@Serializable
data class StockWithTags(
    val stock: Stock,
    val tags: List<Tag>,
)

@Serializable
data class WatchlistWithTags(
    val watchlist: Watchlist,
    val tags: List<Tag>,
)

@Serializable
data class WatchlistWithStocks(
    val watchlist: Watchlist,
    val stocks: List<StockWithTags>,
)

// ==================== Stock Notes ====================

@Serializable
data class StockNote(
    val id: Long,
    @SerialName("stock_id")
    @get:JsonProperty("stock_id")
    val stockId: Long,
    val content: String,
    @SerialName("created_at")
    @get:JsonProperty("created_at")
    val createdAt: String,
)

@Serializable
data class CreateStockNoteInput(
    val content: String,
)

// ==================== User Layout ====================

@Serializable
data class UserLayout(
    val id: Int,
    @SerialName("layout_data")
    @get:JsonProperty("layout_data")
    val layoutData: String, // raw JSON string, parsed by frontend
    @SerialName("updated_at")
    @get:JsonProperty("updated_at")
    val updatedAt: String,
)

@Serializable
data class UpdateLayoutPayload(
    @SerialName("layout_data")
    @JsonProperty("layout_data")
    val layoutData: String,
)

// ==================== Instrument Search ====================

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

// ==================== Health Check ====================

@Serializable
data class TableAccessStatus(
    @SerialName("table_name")
    @get:JsonProperty("table_name")
    val tableName: String,
    val accessible: Boolean,
    @SerialName("sample_row_count")
    @get:JsonProperty("sample_row_count")
    val sampleRowCount: Int? = null,
    val error: String? = null,
)
