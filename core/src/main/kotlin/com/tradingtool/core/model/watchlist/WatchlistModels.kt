package com.tradingtool.core.model.watchlist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

// ==================== Core Entity Models ====================

@Serializable
data class Stock(
    val id: Long,
    val symbol: String,
    @SerialName("instrument_token")
    val instrumentToken: Long,
    @SerialName("company_name")
    val companyName: String,
    val exchange: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
)

@Serializable
data class Watchlist(
    val id: Long,
    val name: String,
    val description: String? = null,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
)

@Serializable
data class Tag(
    val id: Long,
    val name: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
)

@Serializable
data class WatchlistStock(
    val id: Long,
    @SerialName("watchlist_id")
    val watchlistId: Long,
    @SerialName("stock_id")
    val stockId: Long,
    @SerialName("created_at")
    val createdAt: String,
)

@Serializable
data class StockTag(
    val id: Long,
    @SerialName("stock_id")
    val stockId: Long,
    @SerialName("tag_id")
    val tagId: Long,
    @SerialName("created_at")
    val createdAt: String,
)

@Serializable
data class WatchlistTag(
    val id: Long,
    @SerialName("watchlist_id")
    val watchlistId: Long,
    @SerialName("tag_id")
    val tagId: Long,
    @SerialName("created_at")
    val createdAt: String,
)

// ==================== Input Models for Create Operations ====================

@Serializable
data class CreateStockInput(
    val symbol: String,
    @SerialName("instrument_token")
    val instrumentToken: Long,
    @SerialName("company_name")
    val companyName: String,
    val exchange: String,
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
    val watchlistId: Long,
    @SerialName("stock_id")
    val stockId: Long,
)

@Serializable
data class CreateStockTagInput(
    @SerialName("stock_id")
    val stockId: Long,
    @SerialName("tag_id")
    val tagId: Long,
)

@Serializable
data class CreateWatchlistTagInput(
    @SerialName("watchlist_id")
    val watchlistId: Long,
    @SerialName("tag_id")
    val tagId: Long,
)

// ==================== Update Field Enums ====================

enum class StockUpdateField {
    COMPANY_NAME,
    EXCHANGE,
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
)

@Serializable
data class UpdateStockPayload(
    @SerialName("company_name")
    val companyName: String? = null,
    val exchange: String? = null,
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

// ==================== Health Check ====================

@Serializable
data class TableAccessStatus(
    @SerialName("table_name")
    val tableName: String,
    val accessible: Boolean,
    @SerialName("sample_row_count")
    val sampleRowCount: Int? = null,
    val error: String? = null,
)
