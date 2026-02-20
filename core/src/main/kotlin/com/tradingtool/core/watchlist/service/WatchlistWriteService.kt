package com.tradingtool.core.watchlist.service

import com.tradingtool.core.database.WatchlistJdbiHandler
import com.tradingtool.core.model.watchlist.CreateStockInput
import com.tradingtool.core.model.watchlist.CreateStockTagInput
import com.tradingtool.core.model.watchlist.CreateTagInput
import com.tradingtool.core.model.watchlist.CreateWatchlistInput
import com.tradingtool.core.model.watchlist.CreateWatchlistStockInput
import com.tradingtool.core.model.watchlist.CreateWatchlistTagInput
import com.tradingtool.core.model.watchlist.Stock
import com.tradingtool.core.model.watchlist.StockTag
import com.tradingtool.core.model.watchlist.StockUpdateField
import com.tradingtool.core.model.watchlist.Tag
import com.tradingtool.core.model.watchlist.TagUpdateField
import com.tradingtool.core.model.watchlist.UpdateStockInput
import com.tradingtool.core.model.watchlist.UpdateTagInput
import com.tradingtool.core.model.watchlist.UpdateWatchlistInput
import com.tradingtool.core.model.watchlist.Watchlist
import com.tradingtool.core.model.watchlist.WatchlistStock
import com.tradingtool.core.model.watchlist.WatchlistTag
import com.tradingtool.core.model.watchlist.WatchlistUpdateField
import com.tradingtool.core.watchlist.dao.WatchlistReadDao
import com.tradingtool.core.watchlist.dao.WatchlistWriteDao

/**
 * Write service for watchlist domain operations.
 *
 * Every public method executes inside a DB transaction and uses
 * [WatchlistJdbiHandler] for safe IO dispatcher handling.
 */
class WatchlistWriteService(
    private val db: WatchlistJdbiHandler,
) {
    suspend fun createStock(input: CreateStockInput): Stock = runInTransaction { _, writeDao ->
        writeDao.createStock(
            symbol = input.symbol,
            instrumentToken = input.instrumentToken,
            companyName = input.companyName,
            exchange = input.exchange,
        )
    }

    suspend fun updateStock(stockId: Long, input: UpdateStockInput): Stock? = runInTransaction { _, writeDao ->
        writeDao.updateStock(
            stockId = stockId,
            setCompanyName = input.fieldsToUpdate.contains(StockUpdateField.COMPANY_NAME),
            companyName = input.companyName,
            setExchange = input.fieldsToUpdate.contains(StockUpdateField.EXCHANGE),
            exchange = input.exchange,
        )
    }

    suspend fun deleteStock(stockId: Long): Int = runInTransaction { _, writeDao ->
        writeDao.deleteStock(stockId)
    }

    suspend fun createWatchlist(input: CreateWatchlistInput): Watchlist = runInTransaction { _, writeDao ->
        writeDao.createWatchlist(
            name = input.name,
            description = input.description,
        )
    }

    suspend fun updateWatchlist(watchlistId: Long, input: UpdateWatchlistInput): Watchlist? = runInTransaction { _, writeDao ->
        writeDao.updateWatchlist(
            watchlistId = watchlistId,
            setName = input.fieldsToUpdate.contains(WatchlistUpdateField.NAME),
            name = input.name,
            setDescription = input.fieldsToUpdate.contains(WatchlistUpdateField.DESCRIPTION),
            description = input.description,
        )
    }

    suspend fun deleteWatchlist(watchlistId: Long): Int = runInTransaction { _, writeDao ->
        writeDao.deleteWatchlist(watchlistId)
    }

    suspend fun createTag(input: CreateTagInput): Tag = runInTransaction { _, writeDao ->
        writeDao.createTag(name = input.name)
    }

    suspend fun getOrCreateTag(name: String): Tag = runInTransaction { _, writeDao ->
        writeDao.getOrCreateTag(name)
    }

    suspend fun updateTag(tagId: Long, input: UpdateTagInput): Tag? = runInTransaction { _, writeDao ->
        writeDao.updateTag(
            tagId = tagId,
            setName = input.fieldsToUpdate.contains(TagUpdateField.NAME),
            name = input.name,
        )
    }

    suspend fun deleteTag(tagId: Long): Int = runInTransaction { _, writeDao ->
        writeDao.deleteTag(tagId)
    }

    suspend fun createStockTag(input: CreateStockTagInput): StockTag = runInTransaction { _, writeDao ->
        writeDao.createStockTag(stockId = input.stockId, tagId = input.tagId)
    }

    suspend fun getOrCreateStockTag(stockId: Long, tagId: Long): StockTag = runInTransaction { readDao, writeDao ->
        writeDao.getOrCreateStockTag(stockId = stockId, tagId = tagId)
            ?: readDao.getStockTag(stockId = stockId, tagId = tagId)
            ?: throw IllegalStateException("Failed to get or create stock tag for stockId=$stockId and tagId=$tagId")
    }

    suspend fun deleteStockTag(stockId: Long, tagId: Long): Int = runInTransaction { _, writeDao ->
        writeDao.deleteStockTag(stockId = stockId, tagId = tagId)
    }

    suspend fun deleteAllStockTags(stockId: Long): Int = runInTransaction { _, writeDao ->
        writeDao.deleteAllStockTags(stockId)
    }

    suspend fun createWatchlistTag(input: CreateWatchlistTagInput): WatchlistTag = runInTransaction { _, writeDao ->
        writeDao.createWatchlistTag(
            watchlistId = input.watchlistId,
            tagId = input.tagId,
        )
    }

    suspend fun getOrCreateWatchlistTag(watchlistId: Long, tagId: Long): WatchlistTag = runInTransaction { readDao, writeDao ->
        writeDao.getOrCreateWatchlistTag(watchlistId = watchlistId, tagId = tagId)
            ?: readDao.getWatchlistTag(watchlistId = watchlistId, tagId = tagId)
            ?: throw IllegalStateException(
                "Failed to get or create watchlist tag for watchlistId=$watchlistId and tagId=$tagId"
            )
    }

    suspend fun deleteWatchlistTag(watchlistId: Long, tagId: Long): Int = runInTransaction { _, writeDao ->
        writeDao.deleteWatchlistTag(watchlistId = watchlistId, tagId = tagId)
    }

    suspend fun deleteAllWatchlistTags(watchlistId: Long): Int = runInTransaction { _, writeDao ->
        writeDao.deleteAllWatchlistTags(watchlistId)
    }

    suspend fun createWatchlistStock(input: CreateWatchlistStockInput): WatchlistStock = runInTransaction { _, writeDao ->
        writeDao.createWatchlistStock(
            watchlistId = input.watchlistId,
            stockId = input.stockId,
        )
    }

    suspend fun getOrCreateWatchlistStock(watchlistId: Long, stockId: Long): WatchlistStock =
        runInTransaction { readDao, writeDao ->
            writeDao.getOrCreateWatchlistStock(watchlistId = watchlistId, stockId = stockId)
                ?: readDao.getWatchlistStock(watchlistId = watchlistId, stockId = stockId)
                ?: throw IllegalStateException(
                    "Failed to get or create watchlist stock for watchlistId=$watchlistId and stockId=$stockId"
                )
        }

    suspend fun deleteWatchlistStock(watchlistId: Long, stockId: Long): Int = runInTransaction { _, writeDao ->
        writeDao.deleteWatchlistStock(watchlistId = watchlistId, stockId = stockId)
    }

    suspend fun deleteAllWatchlistStocks(watchlistId: Long): Int = runInTransaction { _, writeDao ->
        writeDao.deleteAllWatchlistStocks(watchlistId)
    }

    private suspend fun <T> runInTransaction(operation: (WatchlistReadDao, WatchlistWriteDao) -> T): T {
        return db.transaction(operation)
    }
}
