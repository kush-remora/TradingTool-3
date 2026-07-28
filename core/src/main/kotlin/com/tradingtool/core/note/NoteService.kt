package com.tradingtool.core.note

import com.google.inject.Inject
import com.tradingtool.core.database.JdbiHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.customizer.BindBean
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RegisterRowMapper(NoteMapper::class)
interface NoteReadDao {
    @SqlQuery("SELECT id, instrument_token, notes, created_at, updated_at FROM public.notes WHERE instrument_token = :instrumentToken ORDER BY created_at DESC")
    fun findByInstrumentToken(@Bind("instrumentToken") instrumentToken: Long): List<Note>
}

@RegisterRowMapper(NoteMapper::class)
interface NoteWriteDao {
    @SqlQuery("INSERT INTO public.notes (instrument_token, notes) VALUES (:instrumentToken, :notes) RETURNING id, instrument_token, notes, created_at, updated_at")
    fun create(@BindBean request: CreateNoteRequest): Note

    @SqlUpdate("DELETE FROM public.notes WHERE id = :id")
    fun delete(@Bind("id") id: Long): Int
}

class NoteMapper : RowMapper<Note> {
    override fun map(rs: ResultSet, ctx: StatementContext): Note = Note(
        id = rs.getLong("id"),
        instrumentToken = rs.getLong("instrument_token"),
        notes = rs.getString("notes"),
        createdAt = readTimestamp(rs, "created_at"),
        updatedAt = readTimestamp(rs, "updated_at"),
    )

    private fun readTimestamp(rs: ResultSet, column: String): OffsetDateTime {
        return runCatching { rs.getObject(column, OffsetDateTime::class.java) }
            .getOrElse { rs.getTimestamp(column).toInstant().atOffset(ZoneOffset.UTC) }
    }
}

class NoteService @Inject constructor(private val notes: JdbiHandler<NoteReadDao, NoteWriteDao>) {
    suspend fun list(instrumentToken: Long): List<Note> = withContext(Dispatchers.IO) { notes.read { it.findByInstrumentToken(instrumentToken) } }
    suspend fun create(request: CreateNoteRequest): Note = withContext(Dispatchers.IO) { notes.write { it.create(request) } }
    suspend fun delete(id: Long): Boolean = withContext(Dispatchers.IO) { notes.write { it.delete(id) > 0 } }
}
