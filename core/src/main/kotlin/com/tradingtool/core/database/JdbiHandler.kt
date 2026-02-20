package com.tradingtool.core.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.Handle
import org.jdbi.v3.postgres.PostgresPlugin
import org.jdbi.v3.sqlobject.SqlObjectPlugin

/**
 * Database configuration for JDBI connection.
 */
data class DatabaseConfig(
    val jdbcUrl: String,
    val user: String,
    val password: String,
)

/**
 * JDBI Handler exceptions.
 */
open class JdbiHandlerError(override val message: String) : Exception(message)
class JdbiNotConfiguredError(message: String) : JdbiHandlerError(message)

/**
 * JDBI 3 Handler that executes blocking database calls on Dispatchers.IO.
 *
 * Provides:
 * - Read-only DAO access via `read { }`
 * - Write DAO access via `write { }`
 * - Transactional operations via `transaction { read, write -> }`
 *
 * Usage:
 *   val handler = JdbiHandler(config)
 *
 *   // Read operation
 *   val stocks = handler.read { dao -> dao.listStocks(100) }
 *
 *   // Write operation
 *   val newStock = handler.write { dao -> dao.createStock(...) }
 *
 *   // Transaction
 *   handler.transaction { read, write ->
 *       val tag = write.getOrCreateTag("NIFTY")
 *       write.createStockTag(stockId, tag.id)
 *   }
 */
class JdbiHandler<R, W>(
    private val config: DatabaseConfig,
    private val readDaoClass: Class<R>,
    private val writeDaoClass: Class<W>
) {
    private val jdbi: Jdbi? = createJdbi(config)

    /**
     * Execute a read-only operation using the read DAO.
     * Runs on Dispatchers.IO thread pool.
     */
    suspend fun <T> read(operation: (R) -> T): T = withContext(Dispatchers.IO) {
        val activeJdbi = requireJdbi()
        activeJdbi.withHandle<T, Exception> { handle ->
            val dao = handle.attach(readDaoClass)
            operation(dao)
        }
    }

    /**
     * Execute a write operation using the write DAO.
     * Runs on Dispatchers.IO thread pool.
     */
    suspend fun <T> write(operation: (W) -> T): T = withContext(Dispatchers.IO) {
        val activeJdbi = requireJdbi()
        activeJdbi.withHandle<T, Exception> { handle ->
            val dao = handle.attach(writeDaoClass)
            operation(dao)
        }
    }

    /**
     * Execute operations within a transaction.
     * Both read and write DAOs are available within the transaction.
     * Runs on Dispatchers.IO thread pool.
     *
     * Example:
     *   handler.transaction { read, write ->
     *       val stock = write.createStock(...)
     *       val tag = write.getOrCreateTag("NIFTY")
     *       write.createStockTag(stock.id, tag.id)
     *   }
     */
    suspend fun <T> transaction(operation: (R, W) -> T): T = withContext(Dispatchers.IO) {
        val activeJdbi = requireJdbi()
        activeJdbi.inTransaction<T, Exception> { handle ->
            val readDao = handle.attach(readDaoClass)
            val writeDao = handle.attach(writeDaoClass)
            operation(readDao, writeDao)
        }
    }

    /**
     * Health check - execute a simple query to verify database connectivity.
     * Returns true if connection is successful, false otherwise.
     */
    suspend fun checkConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val activeJdbi = requireJdbi()
            activeJdbi.withHandle<Boolean, Exception> { handle ->
                handle.createQuery("SELECT 1")
                    .mapTo(Int::class.javaObjectType)
                    .one()
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Health check - verify access to a specific table.
     * Returns true if table is accessible, false otherwise.
     */
    suspend fun checkTableAccess(tableName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val activeJdbi = requireJdbi()
            val sanitizedTableName = sanitizeTableName(tableName)
            activeJdbi.withHandle<Boolean, Exception> { handle ->
                val sql = """
                    SELECT COUNT(*) AS sample_count
                    FROM (SELECT 1 FROM "$sanitizedTableName" LIMIT 1) AS sample
                """.trimIndent()
                handle.createQuery(sql)
                    .mapTo(Int::class.javaObjectType)
                    .one()
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun createJdbi(config: DatabaseConfig): Jdbi? {
        val jdbcUrl = config.jdbcUrl.trim()
        val user = config.user.trim()
        val password = config.password.trim()

        if (jdbcUrl.isEmpty() || user.isEmpty() || password.isEmpty()) {
            return null
        }

        return Jdbi.create(jdbcUrl, user, password)
            .installPlugin(PostgresPlugin())
            .installPlugin(SqlObjectPlugin())
    }

    private fun requireJdbi(): Jdbi {
        return jdbi ?: throw JdbiNotConfiguredError(
            "Database is not configured. Set SUPABASE_DB_URL, SUPABASE_DB_USER and SUPABASE_DB_PASSWORD."
        )
    }

    private fun sanitizeTableName(tableName: String): String {
        if (!IDENTIFIER_REGEX.matches(tableName)) {
            throw JdbiHandlerError("Invalid table name '$tableName'")
        }
        return tableName
    }

    private companion object {
        val IDENTIFIER_REGEX: Regex = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    }
}
