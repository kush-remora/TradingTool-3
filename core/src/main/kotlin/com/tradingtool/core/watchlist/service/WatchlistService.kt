package com.tradingtool.core.watchlist.service

import com.tradingtool.core.model.watchlist.CreateStockInput
import com.tradingtool.core.model.watchlist.CreateWatchlistInput
import com.tradingtool.core.model.watchlist.CreateWatchlistStockInput
import com.tradingtool.core.model.watchlist.StockRecord
import com.tradingtool.core.model.watchlist.StockUpdateField
import com.tradingtool.core.model.watchlist.TableAccessStatus
import com.tradingtool.core.model.watchlist.UpdateStockInput
import com.tradingtool.core.model.watchlist.UpdateWatchlistInput
import com.tradingtool.core.model.watchlist.UpdateWatchlistStockInput
import com.tradingtool.core.model.watchlist.WatchlistRecord
import com.tradingtool.core.model.watchlist.WatchlistStockRecord
import com.tradingtool.core.model.watchlist.WatchlistStockUpdateField
import com.tradingtool.core.model.watchlist.WatchlistUpdateField
import com.tradingtool.core.watchlist.dao.WatchlistDal
import com.tradingtool.core.watchlist.dao.WatchlistDalError
import com.tradingtool.core.watchlist.dao.WatchlistDalNotConfiguredError

open class WatchlistServiceError(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class WatchlistValidationError(
    message: String,
) : WatchlistServiceError(message)

class WatchlistServiceNotConfiguredError(
    message: String,
) : WatchlistServiceError(message)

class WatchlistService(
    private val dal: WatchlistDal,
) {
    fun checkTablesAccess(): List<TableAccessStatus> {
        return runDao(
            action = "check tables access",
            operation = { dal.checkTablesAccess() },
        )
    }

    fun createStock(inputData: CreateStockInput): StockRecord {
        val normalizedInput = CreateStockInput(
            nseSymbol = normalizeSymbol(inputData.nseSymbol),
            companyName = normalizeRequiredText(
                value = inputData.companyName,
                fieldName = "company_name",
            ),
            growwSymbol = normalizeOptionalText(inputData.growwSymbol),
            kiteSymbol = normalizeOptionalText(inputData.kiteSymbol),
            description = normalizeOptionalText(inputData.description),
            rating = validateRating(inputData.rating),
            tags = normalizeTags(inputData.tags),
        )
        return runDao(
            action = "create stock",
            operation = { dal.createStock(normalizedInput) },
        )
    }

    fun getStockById(stockId: Long): StockRecord? {
        val validStockId = validatePositiveId(stockId, fieldName = "stock_id")
        return runDao(
            action = "get stock by id",
            operation = { dal.getStockById(validStockId) },
        )
    }

    fun getStockByNseSymbol(nseSymbol: String): StockRecord? {
        val normalizedSymbol = normalizeSymbol(nseSymbol)
        return runDao(
            action = "get stock by symbol",
            operation = { dal.getStockByNseSymbol(normalizedSymbol) },
        )
    }

    fun listStocks(limit: Int = 200): List<StockRecord> {
        val validLimit = validateLimit(limit)
        return runDao(
            action = "list stocks",
            operation = { dal.listStocks(limit = validLimit) },
        )
    }

    fun updateStock(stockId: Long, inputData: UpdateStockInput): StockRecord? {
        val validStockId = validatePositiveId(stockId, fieldName = "stock_id")
        val normalizedInput = normalizeUpdateStockInput(inputData)
        return runDao(
            action = "update stock",
            operation = { dal.updateStock(validStockId, normalizedInput) },
        )
    }

    fun deleteStock(stockId: Long): Boolean {
        val validStockId = validatePositiveId(stockId, fieldName = "stock_id")
        return runDao(
            action = "delete stock",
            operation = { dal.deleteStock(validStockId) },
        )
    }

    fun createWatchlist(inputData: CreateWatchlistInput): WatchlistRecord {
        val normalizedInput = CreateWatchlistInput(
            name = normalizeRequiredText(inputData.name, fieldName = "name"),
            description = normalizeOptionalText(inputData.description),
        )
        return runDao(
            action = "create watchlist",
            operation = { dal.createWatchlist(normalizedInput) },
        )
    }

    fun getWatchlistById(watchlistId: Long): WatchlistRecord? {
        val validWatchlistId = validatePositiveId(
            value = watchlistId,
            fieldName = "watchlist_id",
        )
        return runDao(
            action = "get watchlist by id",
            operation = { dal.getWatchlistById(validWatchlistId) },
        )
    }

    fun getWatchlistByName(name: String): WatchlistRecord? {
        val normalizedName = normalizeRequiredText(name, fieldName = "name")
        return runDao(
            action = "get watchlist by name",
            operation = { dal.getWatchlistByName(normalizedName) },
        )
    }

    fun listWatchlists(limit: Int = 200): List<WatchlistRecord> {
        val validLimit = validateLimit(limit)
        return runDao(
            action = "list watchlists",
            operation = { dal.listWatchlists(limit = validLimit) },
        )
    }

    fun updateWatchlist(watchlistId: Long, inputData: UpdateWatchlistInput): WatchlistRecord? {
        val validWatchlistId = validatePositiveId(
            value = watchlistId,
            fieldName = "watchlist_id",
        )
        val normalizedInput = normalizeUpdateWatchlistInput(inputData)
        return runDao(
            action = "update watchlist",
            operation = { dal.updateWatchlist(validWatchlistId, normalizedInput) },
        )
    }

    fun deleteWatchlist(watchlistId: Long): Boolean {
        val validWatchlistId = validatePositiveId(
            value = watchlistId,
            fieldName = "watchlist_id",
        )
        return runDao(
            action = "delete watchlist",
            operation = { dal.deleteWatchlist(validWatchlistId) },
        )
    }

    fun createWatchlistStock(inputData: CreateWatchlistStockInput): WatchlistStockRecord {
        val validWatchlistId = validatePositiveId(
            value = inputData.watchlistId,
            fieldName = "watchlist_id",
        )
        val validStockId = validatePositiveId(
            value = inputData.stockId,
            fieldName = "stock_id",
        )

        if (getWatchlistById(validWatchlistId) == null) {
            throw WatchlistValidationError("Watchlist '$validWatchlistId' does not exist")
        }
        if (getStockById(validStockId) == null) {
            throw WatchlistValidationError("Stock '$validStockId' does not exist")
        }

        val normalizedInput = CreateWatchlistStockInput(
            watchlistId = validWatchlistId,
            stockId = validStockId,
            notes = normalizeOptionalText(inputData.notes),
        )
        return runDao(
            action = "create watchlist stock mapping",
            operation = { dal.createWatchlistStock(normalizedInput) },
        )
    }

    fun getWatchlistStock(watchlistId: Long, stockId: Long): WatchlistStockRecord? {
        val validWatchlistId = validatePositiveId(
            value = watchlistId,
            fieldName = "watchlist_id",
        )
        val validStockId = validatePositiveId(
            value = stockId,
            fieldName = "stock_id",
        )
        return runDao(
            action = "get watchlist stock mapping",
            operation = { dal.getWatchlistStock(validWatchlistId, validStockId) },
        )
    }

    fun listStocksForWatchlist(watchlistId: Long): List<WatchlistStockRecord> {
        val validWatchlistId = validatePositiveId(
            value = watchlistId,
            fieldName = "watchlist_id",
        )
        return runDao(
            action = "list stocks for watchlist",
            operation = { dal.listStocksForWatchlist(validWatchlistId) },
        )
    }

    fun updateWatchlistStock(
        watchlistId: Long,
        stockId: Long,
        inputData: UpdateWatchlistStockInput,
    ): WatchlistStockRecord? {
        val validWatchlistId = validatePositiveId(
            value = watchlistId,
            fieldName = "watchlist_id",
        )
        val validStockId = validatePositiveId(
            value = stockId,
            fieldName = "stock_id",
        )
        val normalizedInput = normalizeUpdateWatchlistStockInput(inputData)
        return runDao(
            action = "update watchlist stock mapping",
            operation = {
                dal.updateWatchlistStock(
                    watchlistId = validWatchlistId,
                    stockId = validStockId,
                    inputData = normalizedInput,
                )
            },
        )
    }

    fun deleteWatchlistStock(watchlistId: Long, stockId: Long): Boolean {
        val validWatchlistId = validatePositiveId(
            value = watchlistId,
            fieldName = "watchlist_id",
        )
        val validStockId = validatePositiveId(
            value = stockId,
            fieldName = "stock_id",
        )
        return runDao(
            action = "delete watchlist stock mapping",
            operation = { dal.deleteWatchlistStock(validWatchlistId, validStockId) },
        )
    }

    private fun normalizeUpdateStockInput(inputData: UpdateStockInput): UpdateStockInput {
        if (inputData.fieldsToUpdate.isEmpty()) {
            throw WatchlistValidationError("update stock called with no fields")
        }

        var companyName: String? = null
        var growwSymbol: String? = null
        var kiteSymbol: String? = null
        var description: String? = null
        var rating: Int? = null
        var tags: List<String>? = null

        if (StockUpdateField.COMPANY_NAME in inputData.fieldsToUpdate) {
            val rawCompanyName: String = inputData.companyName
                ?: throw WatchlistValidationError("company_name cannot be null")
            companyName = normalizeRequiredText(rawCompanyName, fieldName = "company_name")
        }

        if (StockUpdateField.GROWW_SYMBOL in inputData.fieldsToUpdate) {
            growwSymbol = normalizeOptionalText(inputData.growwSymbol)
        }

        if (StockUpdateField.KITE_SYMBOL in inputData.fieldsToUpdate) {
            kiteSymbol = normalizeOptionalText(inputData.kiteSymbol)
        }

        if (StockUpdateField.DESCRIPTION in inputData.fieldsToUpdate) {
            description = normalizeOptionalText(inputData.description)
        }

        if (StockUpdateField.RATING in inputData.fieldsToUpdate) {
            rating = validateRating(inputData.rating)
        }

        if (StockUpdateField.TAGS in inputData.fieldsToUpdate) {
            tags = if (inputData.tags == null) {
                emptyList()
            } else {
                normalizeTags(inputData.tags)
            }
        }

        return UpdateStockInput(
            fieldsToUpdate = inputData.fieldsToUpdate,
            companyName = companyName,
            growwSymbol = growwSymbol,
            kiteSymbol = kiteSymbol,
            description = description,
            rating = rating,
            tags = tags,
        )
    }

    private fun normalizeUpdateWatchlistInput(inputData: UpdateWatchlistInput): UpdateWatchlistInput {
        if (inputData.fieldsToUpdate.isEmpty()) {
            throw WatchlistValidationError("update watchlist called with no fields")
        }

        var name: String? = null
        var description: String? = null

        if (WatchlistUpdateField.NAME in inputData.fieldsToUpdate) {
            val rawName: String = inputData.name ?: throw WatchlistValidationError("name cannot be null")
            name = normalizeRequiredText(rawName, fieldName = "name")
        }
        if (WatchlistUpdateField.DESCRIPTION in inputData.fieldsToUpdate) {
            description = normalizeOptionalText(inputData.description)
        }

        return UpdateWatchlistInput(
            fieldsToUpdate = inputData.fieldsToUpdate,
            name = name,
            description = description,
        )
    }

    private fun normalizeUpdateWatchlistStockInput(
        inputData: UpdateWatchlistStockInput,
    ): UpdateWatchlistStockInput {
        if (inputData.fieldsToUpdate.isEmpty()) {
            throw WatchlistValidationError("update watchlist stock mapping called with no fields")
        }

        var notes: String? = null
        if (WatchlistStockUpdateField.NOTES in inputData.fieldsToUpdate) {
            notes = normalizeOptionalText(inputData.notes)
        }

        return UpdateWatchlistStockInput(
            fieldsToUpdate = inputData.fieldsToUpdate,
            notes = notes,
        )
    }

    private fun validatePositiveId(value: Long, fieldName: String): Long {
        if (value <= 0L) {
            throw WatchlistValidationError("$fieldName must be > 0")
        }
        return value
    }

    private fun validateLimit(limit: Int): Int {
        if (limit <= 0) {
            throw WatchlistValidationError("limit must be > 0")
        }
        if (limit > 1000) {
            throw WatchlistValidationError("limit must be <= 1000")
        }
        return limit
    }

    private fun normalizeRequiredText(value: String, fieldName: String): String {
        val normalized: String = value.trim()
        if (normalized.isEmpty()) {
            throw WatchlistValidationError("$fieldName cannot be empty")
        }
        return normalized
    }

    private fun normalizeOptionalText(value: String?): String? {
        if (value == null) {
            return null
        }
        val normalized: String = value.trim()
        if (normalized.isEmpty()) {
            return null
        }
        return normalized
    }

    private fun normalizeSymbol(symbol: String): String {
        val normalized: String = symbol.trim().uppercase()
        if (normalized.isEmpty()) {
            throw WatchlistValidationError("nse_symbol cannot be empty")
        }
        if (NSE_SYMBOL_PATTERN.matches(normalized).not()) {
            throw WatchlistValidationError(
                "nse_symbol must match ^[A-Z0-9][A-Z0-9._-]{0,31}$",
            )
        }
        return normalized
    }

    private fun validateRating(rating: Int?): Int? {
        if (rating == null) {
            return null
        }
        if (rating < 1 || rating > 5) {
            throw WatchlistValidationError("rating must be between 1 and 5")
        }
        return rating
    }

    private fun normalizeTags(tags: List<String>): List<String> {
        val normalizedTags: MutableList<String> = mutableListOf()
        tags.forEach { tag ->
            val normalized: String = tag.trim().lowercase()
            if (normalized.isEmpty()) {
                return@forEach
            }
            if (normalizedTags.contains(normalized).not()) {
                normalizedTags.add(normalized)
            }
        }
        return normalizedTags
    }

    private fun <ResultT> runDao(
        action: String,
        operation: () -> ResultT,
    ): ResultT {
        return try {
            operation()
        } catch (error: WatchlistDalNotConfiguredError) {
            throw WatchlistServiceNotConfiguredError(error.message ?: "Watchlist DB is not configured")
        } catch (error: WatchlistDalError) {
            throw WatchlistServiceError(
                message = "Watchlist service failed while '$action': ${error.message}",
                cause = error,
            )
        }
    }

    private companion object {
        val NSE_SYMBOL_PATTERN: Regex = Regex("^[A-Z0-9][A-Z0-9._-]{0,31}$")
    }
}
