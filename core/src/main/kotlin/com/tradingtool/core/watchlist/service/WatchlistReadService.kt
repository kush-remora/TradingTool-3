package com.tradingtool.core.watchlist.service

import com.tradingtool.core.constants.DatabaseConstants.Tables
import com.tradingtool.core.database.WatchlistJdbiHandler
import com.tradingtool.core.model.watchlist.Stock
import com.tradingtool.core.model.watchlist.StockTag
import com.tradingtool.core.model.watchlist.TableAccessStatus
import com.tradingtool.core.model.watchlist.Tag
import com.tradingtool.core.model.watchlist.Watchlist
import com.tradingtool.core.model.watchlist.WatchlistStock
import com.tradingtool.core.model.watchlist.WatchlistTag
import com.tradingtool.core.watchlist.dao.WatchlistReadDao

/**
 * Read-only service for watchlist domain operations.
 *
 * All DAO calls go through [WatchlistJdbiHandler], which already routes blocking
 * database work to Dispatchers.IO.
 */
class WatchlistReadService(
    private val db: WatchlistJdbiHandler,
) {
    suspend fun getStockById(stockId: Long): Stock? = runRead { dao ->
        dao.getStockById(stockId)
    }

    suspend fun getStockBySymbol(symbol: String, exchange: String): Stock? = runRead { dao ->
        dao.getStockBySymbol(symbol, exchange)
    }

    suspend fun getStockByInstrumentToken(instrumentToken: Long): Stock? = runRead { dao ->
        dao.getStockByInstrumentToken(instrumentToken)
    }

    suspend fun listStocks(limit: Int): List<Stock> = runRead { dao ->
        dao.listStocks(limit)
    }

    suspend fun getStocksByTagName(tagName: String): List<Stock> = runRead { dao ->
        dao.getStocksByTagName(tagName)
    }

    suspend fun getWatchlistById(watchlistId: Long): Watchlist? = runRead { dao ->
        dao.getWatchlistById(watchlistId)
    }

    suspend fun getWatchlistByName(name: String): Watchlist? = runRead { dao ->
        dao.getWatchlistByName(name)
    }

    suspend fun listWatchlists(limit: Int): List<Watchlist> = runRead { dao ->
        dao.listWatchlists(limit)
    }

    suspend fun getTagById(tagId: Long): Tag? = runRead { dao ->
        dao.getTagById(tagId)
    }

    suspend fun getTagByName(name: String): Tag? = runRead { dao ->
        dao.getTagByName(name)
    }

    suspend fun listTags(limit: Int): List<Tag> = runRead { dao ->
        dao.listTags(limit)
    }

    suspend fun getStockTag(stockId: Long, tagId: Long): StockTag? = runRead { dao ->
        dao.getStockTag(stockId, tagId)
    }

    suspend fun getTagsForStock(stockId: Long): List<Tag> = runRead { dao ->
        dao.getTagsForStock(stockId)
    }

    suspend fun getStockTagsForStock(stockId: Long): List<StockTag> = runRead { dao ->
        dao.getStockTagsForStock(stockId)
    }

    suspend fun getWatchlistTag(watchlistId: Long, tagId: Long): WatchlistTag? = runRead { dao ->
        dao.getWatchlistTag(watchlistId, tagId)
    }

    suspend fun getTagsForWatchlist(watchlistId: Long): List<Tag> = runRead { dao ->
        dao.getTagsForWatchlist(watchlistId)
    }

    suspend fun getWatchlistTagsForWatchlist(watchlistId: Long): List<WatchlistTag> = runRead { dao ->
        dao.getWatchlistTagsForWatchlist(watchlistId)
    }

    suspend fun getWatchlistStock(watchlistId: Long, stockId: Long): WatchlistStock? = runRead { dao ->
        dao.getWatchlistStock(watchlistId, stockId)
    }

    suspend fun getStocksInWatchlist(watchlistId: Long): List<Stock> = runRead { dao ->
        dao.getStocksInWatchlist(watchlistId)
    }

    suspend fun getWatchlistStocksForWatchlist(watchlistId: Long): List<WatchlistStock> = runRead { dao ->
        dao.getWatchlistStocksForWatchlist(watchlistId)
    }

    suspend fun checkConnection(): Boolean {
        return db.checkConnection()
    }

    suspend fun checkTablesAccess(): List<TableAccessStatus> {
        return TABLES_TO_CHECK.map { tableName ->
            val accessible: Boolean = db.checkTableAccess(tableName)
            TableAccessStatus(
                tableName = tableName,
                accessible = accessible,
                error = if (accessible) null else "Table is not accessible",
            )
        }
    }

    private suspend fun <T> runRead(operation: (WatchlistReadDao) -> T): T {
        return db.read(operation)
    }

    private companion object {
        val TABLES_TO_CHECK: List<String> = listOf(
            Tables.STOCKS,
            Tables.WATCHLISTS,
            Tables.TAGS,
            Tables.STOCK_TAGS,
            Tables.WATCHLIST_STOCKS,
            Tables.WATCHLIST_TAGS,
        )
    }
}
