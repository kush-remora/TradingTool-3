package com.tradingtool.core.note

import org.jdbi.v3.core.config.ConfigRegistry
import org.jdbi.v3.core.statement.StatementContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.sql.Types
import java.time.OffsetDateTime
import javax.sql.rowset.RowSetMetaDataImpl
import javax.sql.rowset.RowSetProvider

class NoteMapperTest {

    @Test
    fun `maps native database column names to a note`() {
        val rowSet = RowSetProvider.newFactory().createCachedRowSet()
        val metadata = RowSetMetaDataImpl()
        val columns = listOf(
            "id" to Types.BIGINT,
            "instrument_token" to Types.BIGINT,
            "notes" to Types.VARCHAR,
            "created_at" to Types.TIMESTAMP,
            "updated_at" to Types.TIMESTAMP,
        )
        metadata.columnCount = columns.size
        columns.forEachIndexed { index, (name, type) ->
            metadata.setColumnName(index + 1, name)
            metadata.setColumnType(index + 1, type)
        }
        rowSet.setMetaData(metadata)

        val createdAt = OffsetDateTime.parse("2026-07-28T10:00:00Z")
        val updatedAt = OffsetDateTime.parse("2026-07-28T11:00:00Z")
        rowSet.moveToInsertRow()
        rowSet.updateLong("id", 12L)
        rowSet.updateLong("instrument_token", 738561L)
        rowSet.updateString("notes", "Review the base")
        rowSet.updateTimestamp("created_at", Timestamp.from(createdAt.toInstant()))
        rowSet.updateTimestamp("updated_at", Timestamp.from(updatedAt.toInstant()))
        rowSet.insertRow()
        rowSet.moveToCurrentRow()
        rowSet.beforeFirst()
        rowSet.next()

        val mapped = NoteMapper().map(rowSet, statementContext())

        assertEquals(12L, mapped.id)
        assertEquals(738561L, mapped.instrumentToken)
        assertEquals("Review the base", mapped.notes)
        assertEquals(createdAt, mapped.createdAt)
        assertEquals(updatedAt, mapped.updatedAt)
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
