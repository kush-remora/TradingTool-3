package com.tradingtool.core.database

import com.tradingtool.core.watchlist.dao.WatchlistReadDao
import com.tradingtool.core.watchlist.dao.WatchlistWriteDao

/**
 * Pre-configured JDBI handler for watchlist DAOs.
 *
 * Usage in service:
 *   class WatchlistService(private val db: WatchlistJdbiHandler) {
 *
 *       suspend fun getStockById(stockId: Long): Stock? {
 *           return db.read { dao -> dao.getStockById(stockId) }
 *       }
 *
 *       suspend fun createStock(symbol: String, instrumentToken: Long, companyName: String, exchange: String): Stock {
 *           return db.write { dao -> dao.createStock(symbol, instrumentToken, companyName, exchange) }
 *       }
 *
 *       suspend fun addStockWithTags(symbol: String, instrumentToken: Long, companyName: String, exchange: String, tagNames: List<String>): Stock {
 *           return db.transaction { read, write ->
 *               val stock = write.createStock(symbol, instrumentToken, companyName, exchange)
 *               tagNames.forEach { tagName ->
 *                   val tag = write.getOrCreateTag(tagName)
 *                   write.getOrCreateStockTag(stock.id, tag.id)
 *               }
 *               stock
 *           }
 *       }
 *   }
 */
typealias WatchlistJdbiHandler = JdbiHandler<WatchlistReadDao, WatchlistWriteDao>

/**
 * Factory function to create a WatchlistJdbiHandler.
 */
fun createWatchlistJdbiHandler(config: DatabaseConfig): WatchlistJdbiHandler {
    return JdbiHandler(
        config = config,
        readDaoClass = WatchlistReadDao::class.java,
        writeDaoClass = WatchlistWriteDao::class.java
    )
}
