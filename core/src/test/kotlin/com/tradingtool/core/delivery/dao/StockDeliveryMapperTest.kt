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
    fun `map reads instrument token and delivery fields without a stock id`() {
        val rowSet = RowSetProvider.newFactory().createCachedRowSet()
        val metadata = RowSetMetaDataImpl()
        val columns = listOf(
            Cols.INSTRUMENT_TOKEN to Types.BIGINT,
            Cols.SYMBOL to Types.VARCHAR,
            Cols.EXCHANGE to Types.VARCHAR,
            Cols.TRADING_DATE to Types.DATE,
            Cols.UNIVERSE to Types.VARCHAR,
            Cols.RECONCILIATION_STATUS to Types.VARCHAR,
            Cols.SERIES to Types.VARCHAR,
            Cols.TTL_TRD_QNTY to Types.BIGINT,
            Cols.DELIV_QTY to Types.BIGINT,
            Cols.DELIV_PER to Types.DOUBLE,
            Cols.SOURCE_FILE_NAME to Types.VARCHAR,
            Cols.SOURCE_URL to Types.VARCHAR,
            Cols.FETCHED_AT to Types.TIMESTAMP,
        )
        metadata.columnCount = columns.size
        columns.forEachIndexed { index, (name, type) ->
            metadata.setColumnName(index + 1, name)
            metadata.setColumnType(index + 1, type)
        }
        rowSet.setMetaData(metadata)

        rowSet.moveToInsertRow()
        rowSet.updateLong(Cols.INSTRUMENT_TOKEN, 738561L)
        rowSet.updateString(Cols.SYMBOL, "RELIANCE")
        rowSet.updateString(Cols.EXCHANGE, "NSE")
        rowSet.updateString(Cols.UNIVERSE, "LARGEMIDCAP_250")
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

        assertEquals(738561L, mapped.instrumentToken)
        assertEquals("RELIANCE", mapped.symbol)
        assertEquals("NSE", mapped.exchange)
        assertEquals("LARGEMIDCAP_250", mapped.universe)
        assertEquals(DeliveryReconciliationStatus.PRESENT, mapped.reconciliationStatus)
        assertEquals(64.0, mapped.delivPer)
        assertEquals("sec_bhavdata_full_10042026.csv", mapped.sourceFileName)
        assertNull(mapped.fetchedAt)
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
