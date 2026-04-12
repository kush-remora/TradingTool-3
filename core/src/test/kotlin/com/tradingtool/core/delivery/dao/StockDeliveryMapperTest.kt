package com.tradingtool.core.delivery.dao

import com.tradingtool.core.constants.DatabaseConstants.StockDeliveryColumns as Cols
import com.tradingtool.core.delivery.model.DeliveryReconciliationStatus
import org.jdbi.v3.core.config.ConfigRegistry
import org.jdbi.v3.core.statement.StatementContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.sql.Date
import java.sql.Types
import javax.sql.rowset.RowSetMetaDataImpl
import javax.sql.rowset.RowSetProvider

class StockDeliveryMapperTest {

    @Test
    fun `map reads instrument token and delivery fields`() {
        val rowSet = RowSetProvider.newFactory().createCachedRowSet()
        val metadata = RowSetMetaDataImpl()
        metadata.columnCount = 13
        metadata.setColumnName(1, Cols.STOCK_ID)
        metadata.setColumnType(1, Types.BIGINT)
        metadata.setColumnName(2, Cols.INSTRUMENT_TOKEN)
        metadata.setColumnType(2, Types.BIGINT)
        metadata.setColumnName(3, Cols.SYMBOL)
        metadata.setColumnType(3, Types.VARCHAR)
        metadata.setColumnName(4, Cols.EXCHANGE)
        metadata.setColumnType(4, Types.VARCHAR)
        metadata.setColumnName(5, Cols.TRADING_DATE)
        metadata.setColumnType(5, Types.DATE)
        metadata.setColumnName(6, Cols.RECONCILIATION_STATUS)
        metadata.setColumnType(6, Types.VARCHAR)
        metadata.setColumnName(7, Cols.SERIES)
        metadata.setColumnType(7, Types.VARCHAR)
        metadata.setColumnName(8, Cols.TTL_TRD_QNTY)
        metadata.setColumnType(8, Types.BIGINT)
        metadata.setColumnName(9, Cols.DELIV_QTY)
        metadata.setColumnType(9, Types.BIGINT)
        metadata.setColumnName(10, Cols.DELIV_PER)
        metadata.setColumnType(10, Types.DOUBLE)
        metadata.setColumnName(11, Cols.SOURCE_FILE_NAME)
        metadata.setColumnType(11, Types.VARCHAR)
        metadata.setColumnName(12, Cols.SOURCE_URL)
        metadata.setColumnType(12, Types.VARCHAR)
        metadata.setColumnName(13, Cols.FETCHED_AT)
        metadata.setColumnType(13, Types.TIMESTAMP)
        rowSet.setMetaData(metadata)

        rowSet.moveToInsertRow()
        rowSet.updateLong(Cols.STOCK_ID, 7L)
        rowSet.updateLong(Cols.INSTRUMENT_TOKEN, 738561L)
        rowSet.updateString(Cols.SYMBOL, "RELIANCE")
        rowSet.updateString(Cols.EXCHANGE, "NSE")
        rowSet.updateDate(Cols.TRADING_DATE, Date.valueOf("2026-04-10"))
        rowSet.updateString(Cols.RECONCILIATION_STATUS, DeliveryReconciliationStatus.PRESENT.name)
        rowSet.updateString(Cols.SERIES, "EQ")
        rowSet.updateLong(Cols.TTL_TRD_QNTY, 1000L)
        rowSet.updateLong(Cols.DELIV_QTY, 640L)
        rowSet.updateDouble(Cols.DELIV_PER, 64.0)
        rowSet.updateString(Cols.SOURCE_FILE_NAME, "sec_bhavdata_full_10042026.csv")
        rowSet.updateString(Cols.SOURCE_URL, "https://example.com/sec_bhavdata_full_10042026.csv")
        rowSet.updateNull(Cols.FETCHED_AT)
        rowSet.insertRow()
        rowSet.moveToCurrentRow()
        rowSet.beforeFirst()
        rowSet.next()

        val mapped = StockDeliveryMapper().map(rowSet, statementContext())

        assertEquals(7L, mapped.stockId)
        assertEquals(738561L, mapped.instrumentToken)
        assertEquals("RELIANCE", mapped.symbol)
        assertEquals("NSE", mapped.exchange)
        assertEquals(DeliveryReconciliationStatus.PRESENT, mapped.reconciliationStatus)
        assertEquals(64.0, mapped.delivPer)
        assertEquals("sec_bhavdata_full_10042026.csv", mapped.sourceFileName)
        assertNull(mapped.fetchedAt)
    }

    @Test
    fun `map handles null stock id for non watchlist rows`() {
        val rowSet = RowSetProvider.newFactory().createCachedRowSet()
        val metadata = RowSetMetaDataImpl()
        metadata.columnCount = 13
        metadata.setColumnName(1, Cols.STOCK_ID)
        metadata.setColumnType(1, Types.BIGINT)
        metadata.setColumnName(2, Cols.INSTRUMENT_TOKEN)
        metadata.setColumnType(2, Types.BIGINT)
        metadata.setColumnName(3, Cols.SYMBOL)
        metadata.setColumnType(3, Types.VARCHAR)
        metadata.setColumnName(4, Cols.EXCHANGE)
        metadata.setColumnType(4, Types.VARCHAR)
        metadata.setColumnName(5, Cols.TRADING_DATE)
        metadata.setColumnType(5, Types.DATE)
        metadata.setColumnName(6, Cols.RECONCILIATION_STATUS)
        metadata.setColumnType(6, Types.VARCHAR)
        metadata.setColumnName(7, Cols.SERIES)
        metadata.setColumnType(7, Types.VARCHAR)
        metadata.setColumnName(8, Cols.TTL_TRD_QNTY)
        metadata.setColumnType(8, Types.BIGINT)
        metadata.setColumnName(9, Cols.DELIV_QTY)
        metadata.setColumnType(9, Types.BIGINT)
        metadata.setColumnName(10, Cols.DELIV_PER)
        metadata.setColumnType(10, Types.DOUBLE)
        metadata.setColumnName(11, Cols.SOURCE_FILE_NAME)
        metadata.setColumnType(11, Types.VARCHAR)
        metadata.setColumnName(12, Cols.SOURCE_URL)
        metadata.setColumnType(12, Types.VARCHAR)
        metadata.setColumnName(13, Cols.FETCHED_AT)
        metadata.setColumnType(13, Types.TIMESTAMP)
        rowSet.setMetaData(metadata)

        rowSet.moveToInsertRow()
        rowSet.updateNull(Cols.STOCK_ID)
        rowSet.updateLong(Cols.INSTRUMENT_TOKEN, 992244L)
        rowSet.updateString(Cols.SYMBOL, "ABFRL")
        rowSet.updateString(Cols.EXCHANGE, "NSE")
        rowSet.updateDate(Cols.TRADING_DATE, Date.valueOf("2026-04-10"))
        rowSet.updateString(Cols.RECONCILIATION_STATUS, DeliveryReconciliationStatus.MISSING_FROM_SOURCE.name)
        rowSet.updateNull(Cols.SERIES)
        rowSet.updateNull(Cols.TTL_TRD_QNTY)
        rowSet.updateNull(Cols.DELIV_QTY)
        rowSet.updateNull(Cols.DELIV_PER)
        rowSet.updateString(Cols.SOURCE_FILE_NAME, "sec_bhavdata_full_10042026.csv")
        rowSet.updateString(Cols.SOURCE_URL, "https://example.com/sec_bhavdata_full_10042026.csv")
        rowSet.updateNull(Cols.FETCHED_AT)
        rowSet.insertRow()
        rowSet.moveToCurrentRow()
        rowSet.beforeFirst()
        rowSet.next()

        val mapped = StockDeliveryMapper().map(rowSet, statementContext())

        assertNull(mapped.stockId)
        assertEquals(992244L, mapped.instrumentToken)
        assertEquals(DeliveryReconciliationStatus.MISSING_FROM_SOURCE, mapped.reconciliationStatus)
        assertNull(mapped.delivPer)
        assertNull(mapped.series)
    }

    private fun statementContext(): StatementContext {
        val factory = StatementContext::class.java.getDeclaredMethod(
            "create",
            ConfigRegistry::class.java,
            Class.forName("org.jdbi.v3.core.extension.ExtensionMethod"),
            java.lang.reflect.Type::class.java,
        )
        factory.isAccessible = true
        return factory.invoke(null, ConfigRegistry(), null, null) as StatementContext
    }
}
