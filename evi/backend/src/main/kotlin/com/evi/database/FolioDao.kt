package com.evi.database

import com.evi.model.FolioSummary
import com.evi.model.StoredFolio
import com.evi.model.StoredFolioPage
import com.evi.model.StoredFolioRecord
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.customizer.BindBean
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID

@RegisterRowMapper(FolioSummaryMapper::class)
@RegisterRowMapper(StoredFolioRecordMapper::class)
@RegisterRowMapper(StoredFolioPageMapper::class)
interface FolioReadDao {
    @SqlQuery(
        """
        SELECT id, title, objective, theme, source_file_name, source_file_size_bytes,
               status, created_at, updated_at
        FROM public.folios
        ORDER BY updated_at DESC
        LIMIT :limit
        """,
    )
    fun listRecent(@Bind("limit") limit: Int): List<FolioSummary>

    @SqlQuery(
        """
        SELECT id, title, objective, theme, source_pdf_path, source_file_name,
               source_file_size_bytes, page_count, status
        FROM public.folios
        WHERE id = :id
        """,
    )
    fun findById(@Bind("id") id: UUID): StoredFolioRecord?

    @SqlQuery(
        """
        SELECT id, folio_id, page_number, extracted_text, status
        FROM public.folio_pages
        WHERE folio_id = :folioId AND page_number = :pageNumber
        """,
    )
    fun findPage(
        @Bind("folioId") folioId: UUID,
        @Bind("pageNumber") pageNumber: Int,
    ): StoredFolioPage?
}

@RegisterRowMapper(FolioSummaryMapper::class)
@RegisterRowMapper(StoredFolioPageMapper::class)
interface FolioWriteDao {
    @SqlQuery(
        """
        INSERT INTO public.folios (
            id, title, objective, theme, source_pdf_path, source_file_name,
            source_file_size_bytes, status
        ) VALUES (
            :id, :title, :objective, :theme, :sourcePdfPath, :sourceFileName,
            :sourceFileSizeBytes, 'uploaded'
        )
        RETURNING id, title, objective, theme, source_file_name, source_file_size_bytes,
                  status, created_at, updated_at
        """,
    )
    fun insert(@BindBean folio: StoredFolio): FolioSummary

    @SqlUpdate(
        """
        UPDATE public.folios
        SET page_count = :pageCount, updated_at = NOW()
        WHERE id = :id
        """,
    )
    fun updatePageCount(
        @Bind("id") id: UUID,
        @Bind("pageCount") pageCount: Int,
    ): Int

    @SqlQuery(
        """
        INSERT INTO public.folio_pages (
            folio_id, page_number, extracted_text, status
        ) VALUES (
            :folioId, :pageNumber, :text, :status
        )
        ON CONFLICT (folio_id, page_number)
        DO UPDATE SET
            extracted_text = EXCLUDED.extracted_text,
            status = EXCLUDED.status,
            updated_at = NOW()
        RETURNING id, folio_id, page_number, extracted_text, status
        """,
    )
    fun savePageText(
        @Bind("folioId") folioId: UUID,
        @Bind("pageNumber") pageNumber: Int,
        @Bind("text") text: String,
        @Bind("status") status: String,
    ): StoredFolioPage
}

class FolioSummaryMapper : RowMapper<FolioSummary> {
    override fun map(rs: ResultSet, ctx: StatementContext): FolioSummary = FolioSummary(
        id = rs.getObject("id", UUID::class.java).toString(),
        title = rs.getString("title"),
        objective = rs.getString("objective"),
        theme = rs.getString("theme"),
        sourceFileName = rs.getString("source_file_name"),
        sourceFileSizeBytes = rs.getLong("source_file_size_bytes"),
        status = rs.getString("status"),
        createdAt = rs.getObject("created_at", OffsetDateTime::class.java).toInstant().toString(),
        updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java).toInstant().toString(),
    )
}

class StoredFolioRecordMapper : RowMapper<StoredFolioRecord> {
    override fun map(rs: ResultSet, ctx: StatementContext): StoredFolioRecord {
        val rawBytes = rs.getObject("source_file_size_bytes")
        val rawPageCount = rs.getObject("page_count")
        return StoredFolioRecord(
            id = rs.getObject("id", UUID::class.java),
            title = rs.getString("title"),
            objective = rs.getString("objective"),
            theme = rs.getString("theme"),
            sourcePdfPath = rs.getString("source_pdf_path"),
            sourceFileName = rs.getString("source_file_name"),
            sourceFileSizeBytes = (rawBytes as? Number)?.toLong(),
            pageCount = (rawPageCount as? Number)?.toInt(),
            status = rs.getString("status"),
        )
    }
}

class StoredFolioPageMapper : RowMapper<StoredFolioPage> {
    override fun map(rs: ResultSet, ctx: StatementContext): StoredFolioPage = StoredFolioPage(
        id = rs.getObject("id", UUID::class.java),
        folioId = rs.getObject("folio_id", UUID::class.java),
        pageNumber = rs.getInt("page_number"),
        extractedText = rs.getString("extracted_text"),
        status = rs.getString("status"),
    )
}
