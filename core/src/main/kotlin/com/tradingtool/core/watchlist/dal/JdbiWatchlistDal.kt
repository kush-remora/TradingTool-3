package com.tradingtool.core.watchlist.dal

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
import com.tradingtool.core.watchlist.dao.WatchlistDao
import com.tradingtool.core.watchlist.dao.WatchlistDaoError
import com.tradingtool.core.watchlist.dao.WatchlistDaoNotConfiguredError
import com.tradingtool.core.watchlist.dal.sql.StockSqlObject
import com.tradingtool.core.watchlist.dal.sql.WatchlistSqlObject
import com.tradingtool.core.watchlist.dal.sql.WatchlistStockSqlObject
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.array.SqlArrayTypes
import org.jdbi.v3.postgres.PostgresPlugin
import org.jdbi.v3.sqlobject.SqlObjectPlugin

data class WatchlistDatabaseConfig(
    val jdbcUrl: String,
    val user: String,
    val password: String,
)

class JdbiWatchlistDal(
    config: WatchlistDatabaseConfig,
) : WatchlistDao {
    private val jdbi: Jdbi? = createJdbi(config)

    override fun checkTablesAccess(tableNames: List<String>): List<TableAccessStatus> {
        return tableNames.map { tableName ->
            try {
                val safeTableName: String = sanitizeIdentifier(tableName)
                val sampleRowCount: Int = withDaoOperation(
                    action = "check table access for '$tableName'",
                ) { activeJdbi ->
                    activeJdbi.open().use { handle ->
                        val sql = """
                            SELECT COUNT(*) AS sample_count
                            FROM (SELECT 1 FROM "$safeTableName" LIMIT 1) AS sample
                        """.trimIndent()
                        handle.createQuery(sql)
                            .mapTo(Int::class.javaObjectType)
                            .one()
                    }
                }

                TableAccessStatus(
                    tableName = tableName,
                    accessible = true,
                    sampleRowCount = sampleRowCount,
                )
            } catch (error: WatchlistDaoError) {
                TableAccessStatus(
                    tableName = tableName,
                    accessible = false,
                    error = error.message,
                )
            }
        }
    }

    override fun createStock(inputData: CreateStockInput): StockRecord {
        return withStockDao(action = "create stock") { stockSqlObject ->
            stockSqlObject.createStock(
                nseSymbol = inputData.nseSymbol,
                companyName = inputData.companyName,
                growwSymbol = inputData.growwSymbol,
                kiteSymbol = inputData.kiteSymbol,
                description = inputData.description,
                rating = inputData.rating,
                tags = inputData.tags.toTypedArray(),
            )
        }
    }

    override fun getStockById(stockId: Long): StockRecord? {
        return withStockDao(action = "get stock by id '$stockId'") { stockSqlObject ->
            stockSqlObject.getStockById(stockId = stockId)
        }
    }

    override fun getStockByNseSymbol(nseSymbol: String): StockRecord? {
        return withStockDao(action = "get stock by symbol '$nseSymbol'") { stockSqlObject ->
            stockSqlObject.getStockByNseSymbol(nseSymbol = nseSymbol)
        }
    }

    override fun listStocks(limit: Int): List<StockRecord> {
        return withStockDao(action = "list stocks") { stockSqlObject ->
            stockSqlObject.listStocks(limit = limit)
        }
    }

    override fun updateStock(stockId: Long, inputData: UpdateStockInput): StockRecord? {
        if (inputData.fieldsToUpdate.isEmpty()) {
            throw WatchlistDaoError("update stock called with no fields to update")
        }

        val setCompanyName: Boolean = StockUpdateField.COMPANY_NAME in inputData.fieldsToUpdate
        val setGrowwSymbol: Boolean = StockUpdateField.GROWW_SYMBOL in inputData.fieldsToUpdate
        val setKiteSymbol: Boolean = StockUpdateField.KITE_SYMBOL in inputData.fieldsToUpdate
        val setDescription: Boolean = StockUpdateField.DESCRIPTION in inputData.fieldsToUpdate
        val setRating: Boolean = StockUpdateField.RATING in inputData.fieldsToUpdate
        val setTags: Boolean = StockUpdateField.TAGS in inputData.fieldsToUpdate

        if (setCompanyName && inputData.companyName == null) {
            throw WatchlistDaoError("company_name cannot be null")
        }

        return withStockDao(action = "update stock '$stockId'") { stockSqlObject ->
            stockSqlObject.updateStock(
                stockId = stockId,
                setCompanyName = setCompanyName,
                companyName = inputData.companyName,
                setGrowwSymbol = setGrowwSymbol,
                growwSymbol = inputData.growwSymbol,
                setKiteSymbol = setKiteSymbol,
                kiteSymbol = inputData.kiteSymbol,
                setDescription = setDescription,
                description = inputData.description,
                setRating = setRating,
                rating = inputData.rating,
                setTags = setTags,
                tags = inputData.tags?.toTypedArray(),
            )
        }
    }

    override fun deleteStock(stockId: Long): Boolean {
        return withStockDao(action = "delete stock '$stockId'") { stockSqlObject ->
            stockSqlObject.deleteStock(stockId = stockId) > 0
        }
    }

    override fun createWatchlist(inputData: CreateWatchlistInput): WatchlistRecord {
        return withWatchlistDao(action = "create watchlist") { watchlistSqlObject ->
            watchlistSqlObject.createWatchlist(
                name = inputData.name,
                description = inputData.description,
            )
        }
    }

    override fun getWatchlistById(watchlistId: Long): WatchlistRecord? {
        return withWatchlistDao(action = "get watchlist by id '$watchlistId'") { watchlistSqlObject ->
            watchlistSqlObject.getWatchlistById(watchlistId = watchlistId)
        }
    }

    override fun getWatchlistByName(name: String): WatchlistRecord? {
        return withWatchlistDao(action = "get watchlist by name '$name'") { watchlistSqlObject ->
            watchlistSqlObject.getWatchlistByName(name = name)
        }
    }

    override fun listWatchlists(limit: Int): List<WatchlistRecord> {
        return withWatchlistDao(action = "list watchlists") { watchlistSqlObject ->
            watchlistSqlObject.listWatchlists(limit = limit)
        }
    }

    override fun updateWatchlist(
        watchlistId: Long,
        inputData: UpdateWatchlistInput,
    ): WatchlistRecord? {
        if (inputData.fieldsToUpdate.isEmpty()) {
            throw WatchlistDaoError("update watchlist called with no fields to update")
        }

        val setName: Boolean = WatchlistUpdateField.NAME in inputData.fieldsToUpdate
        val setDescription: Boolean = WatchlistUpdateField.DESCRIPTION in inputData.fieldsToUpdate

        if (setName && inputData.name == null) {
            throw WatchlistDaoError("name cannot be null")
        }

        return withWatchlistDao(action = "update watchlist '$watchlistId'") { watchlistSqlObject ->
            watchlistSqlObject.updateWatchlist(
                watchlistId = watchlistId,
                setName = setName,
                name = inputData.name,
                setDescription = setDescription,
                description = inputData.description,
            )
        }
    }

    override fun deleteWatchlist(watchlistId: Long): Boolean {
        return withWatchlistDao(action = "delete watchlist '$watchlistId'") { watchlistSqlObject ->
            watchlistSqlObject.deleteWatchlist(watchlistId = watchlistId) > 0
        }
    }

    override fun createWatchlistStock(inputData: CreateWatchlistStockInput): WatchlistStockRecord {
        return withWatchlistStockDao(action = "create watchlist stock mapping") { watchlistStockSqlObject ->
            watchlistStockSqlObject.createWatchlistStock(
                watchlistId = inputData.watchlistId,
                stockId = inputData.stockId,
                notes = inputData.notes,
            )
        }
    }

    override fun getWatchlistStock(watchlistId: Long, stockId: Long): WatchlistStockRecord? {
        return withWatchlistStockDao(action = "get watchlist stock mapping '$watchlistId:$stockId'") {
            watchlistStockSqlObject ->
            watchlistStockSqlObject.getWatchlistStock(
                watchlistId = watchlistId,
                stockId = stockId,
            )
        }
    }

    override fun listStocksForWatchlist(watchlistId: Long): List<WatchlistStockRecord> {
        return withWatchlistStockDao(action = "list stocks for watchlist '$watchlistId'") {
            watchlistStockSqlObject ->
            watchlistStockSqlObject.listStocksForWatchlist(watchlistId = watchlistId)
        }
    }

    override fun updateWatchlistStock(
        watchlistId: Long,
        stockId: Long,
        inputData: UpdateWatchlistStockInput,
    ): WatchlistStockRecord? {
        if (inputData.fieldsToUpdate.isEmpty()) {
            throw WatchlistDaoError("update watchlist stock called with no fields to update")
        }

        val setNotes: Boolean = WatchlistStockUpdateField.NOTES in inputData.fieldsToUpdate

        return withWatchlistStockDao(action = "update watchlist stock mapping '$watchlistId:$stockId'") {
            watchlistStockSqlObject ->
            watchlistStockSqlObject.updateWatchlistStock(
                watchlistId = watchlistId,
                stockId = stockId,
                setNotes = setNotes,
                notes = inputData.notes,
            )
        }
    }

    override fun deleteWatchlistStock(watchlistId: Long, stockId: Long): Boolean {
        return withWatchlistStockDao(action = "delete watchlist stock mapping '$watchlistId:$stockId'") {
            watchlistStockSqlObject ->
            watchlistStockSqlObject.deleteWatchlistStock(
                watchlistId = watchlistId,
                stockId = stockId,
            ) > 0
        }
    }

    private fun createJdbi(config: WatchlistDatabaseConfig): Jdbi? {
        val jdbcUrl: String = config.jdbcUrl.trim()
        val user: String = config.user.trim()
        val password: String = config.password.trim()

        if (jdbcUrl.isEmpty() || user.isEmpty() || password.isEmpty()) {
            return null
        }

        val activeJdbi: Jdbi = Jdbi.create(jdbcUrl, user, password)
        activeJdbi.installPlugin(PostgresPlugin())
        activeJdbi.installPlugin(SqlObjectPlugin())
        activeJdbi.getConfig(SqlArrayTypes::class.java).register(String::class.java, "text")
        return activeJdbi
    }

    private fun <ResultT> withStockDao(
        action: String,
        operation: (StockSqlObject) -> ResultT,
    ): ResultT {
        return withDaoOperation(action = action) { activeJdbi ->
            activeJdbi.open().use { handle ->
                val stockSqlObject: StockSqlObject = handle.attach(StockSqlObject::class.java)
                operation(stockSqlObject)
            }
        }
    }

    private fun <ResultT> withWatchlistDao(
        action: String,
        operation: (WatchlistSqlObject) -> ResultT,
    ): ResultT {
        return withDaoOperation(action = action) { activeJdbi ->
            activeJdbi.open().use { handle ->
                val watchlistSqlObject: WatchlistSqlObject = handle.attach(WatchlistSqlObject::class.java)
                operation(watchlistSqlObject)
            }
        }
    }

    private fun <ResultT> withWatchlistStockDao(
        action: String,
        operation: (WatchlistStockSqlObject) -> ResultT,
    ): ResultT {
        return withDaoOperation(action = action) { activeJdbi ->
            activeJdbi.open().use { handle ->
                val watchlistStockSqlObject: WatchlistStockSqlObject = handle.attach(WatchlistStockSqlObject::class.java)
                operation(watchlistStockSqlObject)
            }
        }
    }

    private fun <ResultT> withDaoOperation(
        action: String,
        operation: (Jdbi) -> ResultT,
    ): ResultT {
        val activeJdbi: Jdbi = jdbi ?: throw WatchlistDaoNotConfiguredError(
            "Supabase DB is not configured. Set SUPABASE_DB_URL, SUPABASE_DB_USER and SUPABASE_DB_PASSWORD.",
        )

        return try {
            operation(activeJdbi)
        } catch (error: WatchlistDaoError) {
            throw error
        } catch (error: Exception) {
            throw WatchlistDaoError(
                message = "Unexpected database error while '$action': ${error.message}",
                cause = error,
            )
        }
    }

    private fun sanitizeIdentifier(identifier: String): String {
        if (IDENTIFIER_REGEX.matches(identifier).not()) {
            throw WatchlistDaoError("Invalid table name '$identifier'")
        }
        return identifier
    }

    private companion object {
        val IDENTIFIER_REGEX: Regex = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    }
}
