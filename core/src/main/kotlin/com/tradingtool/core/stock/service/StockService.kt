package com.tradingtool.core.stock.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.constants.DatabaseConstants.Tables
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.model.stock.CreateStockInput
import com.tradingtool.core.model.stock.Stock
import com.tradingtool.core.model.stock.StockTag
import com.tradingtool.core.model.stock.TableAccessStatus
import com.tradingtool.core.model.stock.UpdateStockPayload
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@Singleton
class StockService @Inject constructor(
    private val db: StockJdbiHandler,
) {
    private val jackson = ObjectMapper()

    suspend fun listAll(): List<Stock> = db.read { dao -> dao.listAll() }

    suspend fun listByTag(tagName: String): List<Stock> = db.read { dao -> dao.listByTagName(tagName) }

    suspend fun getById(id: Long): Stock? = db.read { dao -> dao.getById(id) }

    suspend fun getBySymbol(symbol: String, exchange: String): Stock? =
        db.read { dao -> dao.getBySymbol(symbol, exchange) }

    suspend fun listAllTags(): List<StockTag> = db.read { dao -> dao.listAllTags() }

    suspend fun create(input: CreateStockInput): Stock = db.transaction { _, writeDao ->
        writeDao.create(
            symbol = input.symbol.trim().uppercase(),
            instrumentToken = input.instrumentToken,
            companyName = input.companyName.trim(),
            exchange = input.exchange.trim().uppercase(),
            notes = input.notes?.trim(),
            priority = input.priority,
            tagsJson = toJson(input.tags),
        )
    }

    suspend fun update(id: Long, payload: UpdateStockPayload): Stock? = db.transaction { _, writeDao ->
        writeDao.update(
            id = id,
            setNotes = payload.notes != null,
            notes = payload.notes,
            setPriority = payload.priority != null,
            priority = payload.priority,
            setTags = payload.tags != null,
            tagsJson = payload.tags?.let { toJson(it) },
        )
    }

    suspend fun delete(id: Long): Boolean = db.transaction { _, writeDao ->
        writeDao.delete(id) > 0
    }

    suspend fun checkTablesAccess(): List<TableAccessStatus> = coroutineScope {
        listOf(Tables.STOCKS, Tables.TRADES, Tables.KITE_TOKENS).map { tableName ->
            async {
                val accessible = db.checkTableAccess(tableName)
                TableAccessStatus(
                    tableName = tableName,
                    accessible = accessible,
                    error = if (accessible) null else "Table is not accessible",
                )
            }
        }.awaitAll()
    }

    private fun toJson(tags: List<StockTag>): String = jackson.writeValueAsString(tags)
}
