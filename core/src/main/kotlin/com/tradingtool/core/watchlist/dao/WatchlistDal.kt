package com.tradingtool.core.watchlist.dao

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
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.array.SqlArrayTypes
import org.jdbi.v3.postgres.PostgresPlugin
import org.jdbi.v3.sqlobject.SqlObjectPlugin

data class WatchlistDatabaseConfig(
    val jdbcUrl: String,
    val user: String,
    val password: String,
)

open class WatchlistDalError(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class WatchlistDalNotConfiguredError(
    message: String,
) : WatchlistDalError(message)

class WatchlistDal(
    config: WatchlistDatabaseConfig,
) {
    private val jdbi: Jdbi? = createJdbi(config)

    fun checkTablesAccess(
        tableNames: List<String> = listOf("stocks", "watchlists", "watchlist_stocks"),
    ): List<TableAccessStatus> {
        return tableNames.map { tableName ->
            try {
                val safeTableName: String = sanitizeIdentifier(tableName)
                val sampleRowCount: Int = withReadHandle(
                    action = "check table access for '$tableName'",
                ) { handle ->
                    val sql = """
                        SELECT COUNT(*) AS sample_count
                        FROM (SELECT 1 FROM "$safeTableName" LIMIT 1) AS sample
                    """.trimIndent()
                    handle.createQuery(sql)
                        .mapTo(Int::class.javaObjectType)
                        .one()
                }

                TableAccessStatus(
                    tableName = tableName,
                    accessible = true,
                    sampleRowCount = sampleRowCount,
                )
            } catch (error: WatchlistDalError) {
                TableAccessStatus(
                    tableName = tableName,
                    accessible = false,
                    error = error.message,
                )
            }
        }
    }

    fun createStock(inputData: CreateStockInput): StockRecord {
        return withWriteHandle(action = "create stock") { handle ->
            handle.stockQueryDao().createStock(
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

    fun getStockById(stockId: Long): StockRecord? {
        return withReadHandle(action = "get stock by id '$stockId'") { handle ->
            handle.stockQueryDao().getStockById(stockId = stockId)
        }
    }

    fun getStockByNseSymbol(nseSymbol: String): StockRecord? {
        return withReadHandle(action = "get stock by symbol '$nseSymbol'") { handle ->
            handle.stockQueryDao().getStockByNseSymbol(nseSymbol = nseSymbol)
        }
    }

    fun listStocks(limit: Int = 200): List<StockRecord> {
        return withReadHandle(action = "list stocks") { handle ->
            handle.stockQueryDao().listStocks(limit = limit)
        }
    }

    fun updateStock(stockId: Long, inputData: UpdateStockInput): StockRecord? {
        if (inputData.fieldsToUpdate.isEmpty()) {
            throw WatchlistDalError("update stock called with no fields to update")
        }

        val setCompanyName: Boolean = StockUpdateField.COMPANY_NAME in inputData.fieldsToUpdate
        val setGrowwSymbol: Boolean = StockUpdateField.GROWW_SYMBOL in inputData.fieldsToUpdate
        val setKiteSymbol: Boolean = StockUpdateField.KITE_SYMBOL in inputData.fieldsToUpdate
        val setDescription: Boolean = StockUpdateField.DESCRIPTION in inputData.fieldsToUpdate
        val setRating: Boolean = StockUpdateField.RATING in inputData.fieldsToUpdate
        val setTags: Boolean = StockUpdateField.TAGS in inputData.fieldsToUpdate

        if (setCompanyName && inputData.companyName == null) {
            throw WatchlistDalError("company_name cannot be null")
        }

        return withWriteHandle(action = "update stock '$stockId'") { handle ->
            handle.stockQueryDao().updateStock(
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

    fun deleteStock(stockId: Long): Boolean {
        return withWriteHandle(action = "delete stock '$stockId'") { handle ->
            handle.stockQueryDao().deleteStock(stockId = stockId) > 0
        }
    }

    fun createWatchlist(inputData: CreateWatchlistInput): WatchlistRecord {
        return withWriteHandle(action = "create watchlist") { handle ->
            handle.watchlistQueryDao().createWatchlist(
                name = inputData.name,
                description = inputData.description,
            )
        }
    }

    fun getWatchlistById(watchlistId: Long): WatchlistRecord? {
        return withReadHandle(action = "get watchlist by id '$watchlistId'") { handle ->
            handle.watchlistQueryDao().getWatchlistById(watchlistId = watchlistId)
        }
    }

    fun getWatchlistByName(name: String): WatchlistRecord? {
        return withReadHandle(action = "get watchlist by name '$name'") { handle ->
            handle.watchlistQueryDao().getWatchlistByName(name = name)
        }
    }

    fun listWatchlists(limit: Int = 200): List<WatchlistRecord> {
        return withReadHandle(action = "list watchlists") { handle ->
            handle.watchlistQueryDao().listWatchlists(limit = limit)
        }
    }

    fun updateWatchlist(
        watchlistId: Long,
        inputData: UpdateWatchlistInput,
    ): WatchlistRecord? {
        if (inputData.fieldsToUpdate.isEmpty()) {
            throw WatchlistDalError("update watchlist called with no fields to update")
        }

        val setName: Boolean = WatchlistUpdateField.NAME in inputData.fieldsToUpdate
        val setDescription: Boolean = WatchlistUpdateField.DESCRIPTION in inputData.fieldsToUpdate

        if (setName && inputData.name == null) {
            throw WatchlistDalError("name cannot be null")
        }

        return withWriteHandle(action = "update watchlist '$watchlistId'") { handle ->
            handle.watchlistQueryDao().updateWatchlist(
                watchlistId = watchlistId,
                setName = setName,
                name = inputData.name,
                setDescription = setDescription,
                description = inputData.description,
            )
        }
    }

    fun deleteWatchlist(watchlistId: Long): Boolean {
        return withWriteHandle(action = "delete watchlist '$watchlistId'") { handle ->
            handle.watchlistQueryDao().deleteWatchlist(watchlistId = watchlistId) > 0
        }
    }

    fun createWatchlistStock(inputData: CreateWatchlistStockInput): WatchlistStockRecord {
        return withWriteHandle(action = "create watchlist stock mapping") { handle ->
            handle.watchlistStockQueryDao().createWatchlistStock(
                watchlistId = inputData.watchlistId,
                stockId = inputData.stockId,
                notes = inputData.notes,
            )
        }
    }

    fun getWatchlistStock(watchlistId: Long, stockId: Long): WatchlistStockRecord? {
        return withReadHandle(action = "get watchlist stock mapping '$watchlistId:$stockId'") { handle ->
            handle.watchlistStockQueryDao().getWatchlistStock(
                watchlistId = watchlistId,
                stockId = stockId,
            )
        }
    }

    fun listStocksForWatchlist(watchlistId: Long): List<WatchlistStockRecord> {
        return withReadHandle(action = "list stocks for watchlist '$watchlistId'") { handle ->
            handle.watchlistStockQueryDao().listStocksForWatchlist(watchlistId = watchlistId)
        }
    }

    fun updateWatchlistStock(
        watchlistId: Long,
        stockId: Long,
        inputData: UpdateWatchlistStockInput,
    ): WatchlistStockRecord? {
        if (inputData.fieldsToUpdate.isEmpty()) {
            throw WatchlistDalError("update watchlist stock called with no fields to update")
        }

        val setNotes: Boolean = WatchlistStockUpdateField.NOTES in inputData.fieldsToUpdate

        return withWriteHandle(action = "update watchlist stock mapping '$watchlistId:$stockId'") {
            handle ->
            handle.watchlistStockQueryDao().updateWatchlistStock(
                watchlistId = watchlistId,
                stockId = stockId,
                setNotes = setNotes,
                notes = inputData.notes,
            )
        }
    }

    fun deleteWatchlistStock(watchlistId: Long, stockId: Long): Boolean {
        return withWriteHandle(action = "delete watchlist stock mapping '$watchlistId:$stockId'") { handle ->
            handle.watchlistStockQueryDao().deleteWatchlistStock(
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

    private fun <ResultT> withReadHandle(
        action: String,
        operation: (Handle) -> ResultT,
    ): ResultT {
        val activeJdbi: Jdbi = requireJdbi()
        return runDatabaseAction(action = action) {
            activeJdbi.open().use { handle ->
                operation(handle)
            }
        }
    }

    private fun <ResultT> withWriteHandle(
        action: String,
        operation: (Handle) -> ResultT,
    ): ResultT {
        val activeJdbi: Jdbi = requireJdbi()
        return runDatabaseAction(action = action) {
            activeJdbi.inTransaction<ResultT, Exception> { handle ->
                operation(handle)
            }
        }
    }

    private fun <ResultT> runDatabaseAction(
        action: String,
        operation: () -> ResultT,
    ): ResultT {
        return try {
            operation()
        } catch (error: WatchlistDalError) {
            throw error
        } catch (error: Exception) {
            throw WatchlistDalError(
                message = "Unexpected database error while '$action': ${error.message}",
                cause = error,
            )
        }
    }

    private fun requireJdbi(): Jdbi {
        return jdbi ?: throw WatchlistDalNotConfiguredError(
            "Supabase DB is not configured. Set SUPABASE_DB_URL, SUPABASE_DB_USER and SUPABASE_DB_PASSWORD.",
        )
    }

    private fun sanitizeIdentifier(identifier: String): String {
        if (IDENTIFIER_REGEX.matches(identifier).not()) {
            throw WatchlistDalError("Invalid table name '$identifier'")
        }
        return identifier
    }

    private fun Handle.stockQueryDao(): StockQueryDao {
        return attach(StockQueryDao::class.java)
    }

    private fun Handle.watchlistQueryDao(): WatchlistQueryDao {
        return attach(WatchlistQueryDao::class.java)
    }

    private fun Handle.watchlistStockQueryDao(): WatchlistTextQueryDao {
        return attach(WatchlistTextQueryDao::class.java)
    }

    private companion object {
        val IDENTIFIER_REGEX: Regex = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    }
}
