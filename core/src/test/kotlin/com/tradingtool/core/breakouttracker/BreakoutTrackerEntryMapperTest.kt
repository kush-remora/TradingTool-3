package com.tradingtool.core.breakouttracker

import org.jdbi.v3.core.config.ConfigRegistry
import org.jdbi.v3.core.statement.StatementContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Date
import java.sql.Types
import javax.sql.rowset.RowSetMetaDataImpl
import javax.sql.rowset.RowSetProvider

class BreakoutTrackerEntryMapperTest {
    @Test
    fun `maps database columns to a breakout tracker entry`() {
        val rowSet = RowSetProvider.newFactory().createCachedRowSet()
        val metadata = RowSetMetaDataImpl()
        val columns = listOf(
            "id" to Types.BIGINT,
            "instrument_token" to Types.BIGINT,
            "symbol" to Types.VARCHAR,
            "company_name" to Types.VARCHAR,
            "breakout_date" to Types.DATE,
            "breakout_price" to Types.NUMERIC,
            "notes" to Types.VARCHAR,
        )
        metadata.columnCount = columns.size
        columns.forEachIndexed { index, (name, type) ->
            metadata.setColumnName(index + 1, name)
            metadata.setColumnType(index + 1, type)
        }
        rowSet.setMetaData(metadata)

        rowSet.moveToInsertRow()
        rowSet.updateLong("id", 12L)
        rowSet.updateLong("instrument_token", 738561L)
        rowSet.updateString("symbol", "RELIANCE")
        rowSet.updateString("company_name", "Reliance Industries")
        rowSet.updateDate("breakout_date", Date.valueOf("2026-07-28"))
        rowSet.updateDouble("breakout_price", 1450.5)
        rowSet.updateString("notes", "Delivery confirmation")
        rowSet.insertRow()
        rowSet.moveToCurrentRow()
        rowSet.beforeFirst()
        rowSet.next()

        val entry = BreakoutTrackerEntryMapper().map(rowSet, statementContext())

        assertEquals(12L, entry.id)
        assertEquals(738561L, entry.instrumentToken)
        assertEquals("RELIANCE", entry.symbol)
        assertEquals("Reliance Industries", entry.companyName)
        assertEquals("2026-07-28", entry.breakoutDate.toString())
        assertEquals(1450.5, entry.breakoutPrice)
        assertEquals("Delivery confirmation", entry.notes)
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
