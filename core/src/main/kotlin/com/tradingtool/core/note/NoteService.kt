package com.tradingtool.core.note

import com.tradingtool.core.database.JdbiHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.customizer.BindBean
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate

@RegisterConstructorMapper(Note::class)
interface NoteReadDao {
    @SqlQuery("SELECT id, instrument_token AS instrumentToken, notes, created_at AS createdAt, updated_at AS updatedAt FROM public.notes WHERE instrument_token = :instrumentToken ORDER BY created_at DESC")
    fun findByInstrumentToken(@Bind("instrumentToken") instrumentToken: Long): List<Note>
}

interface NoteWriteDao {
    @SqlQuery("INSERT INTO public.notes (instrument_token, notes) VALUES (:instrumentToken, :notes) RETURNING id, instrument_token AS instrumentToken, notes, created_at AS createdAt, updated_at AS updatedAt")
    @RegisterConstructorMapper(Note::class)
    fun create(@BindBean request: CreateNoteRequest): Note

    @SqlUpdate("DELETE FROM public.notes WHERE id = :id")
    fun delete(@Bind("id") id: Long): Int
}

class NoteService(private val notes: JdbiHandler<NoteReadDao, NoteWriteDao>) {
    suspend fun list(instrumentToken: Long): List<Note> = withContext(Dispatchers.IO) { notes.read { it.findByInstrumentToken(instrumentToken) } }
    suspend fun create(request: CreateNoteRequest): Note = withContext(Dispatchers.IO) { notes.write { it.create(request) } }
    suspend fun delete(id: Long): Boolean = withContext(Dispatchers.IO) { notes.write { it.delete(id) > 0 } }
}
