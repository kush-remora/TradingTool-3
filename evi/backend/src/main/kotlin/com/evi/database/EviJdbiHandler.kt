package com.evi.database

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.postgres.PostgresPlugin
import org.jdbi.v3.sqlobject.SqlObjectPlugin

open class EviJdbiHandler<R : Any, W : Any>(
    val jdbi: Jdbi,
    private val readDaoClass: Class<R>,
    private val writeDaoClass: Class<W>,
) {
    suspend fun <T> read(operation: (R) -> T): T = withContext(Dispatchers.IO) {
        try {
            jdbi.withHandle<T, Exception> { handle ->
                val dao = handle.attach(readDaoClass)
                operation(dao)
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    suspend fun <T> write(operation: (W) -> T): T = withContext(Dispatchers.IO) {
        try {
            jdbi.withHandle<T, Exception> { handle ->
                val dao = handle.attach(writeDaoClass)
                operation(dao)
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    companion object {
        fun <R : Any, W : Any> create(
            jdbcUrl: String,
            readDaoClass: Class<R>,
            writeDaoClass: Class<W>,
        ): EviJdbiHandler<R, W> {
            val jdbi = Jdbi.create(jdbcUrl).apply {
                installPlugin(PostgresPlugin())
                installPlugin(SqlObjectPlugin())
                installPlugin(KotlinPlugin())
            }
            return EviJdbiHandler(jdbi, readDaoClass, writeDaoClass)
        }
    }
}
