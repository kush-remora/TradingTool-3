package com.tradingtool.core.strategy.chartinkevidence.dao

import com.tradingtool.core.constants.DatabaseConstants.ChartinkScanEventColumns as Cols
import com.tradingtool.core.constants.DatabaseConstants.Tables
import com.tradingtool.core.strategy.chartinkevidence.ChartinkEvidenceSource
import com.tradingtool.core.strategy.chartinkevidence.ChartinkScanEvent
import com.tradingtool.core.strategy.chartinkevidence.StoredChartinkEvidenceUpload
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime

@RegisterRowMapper(ChartinkScanEventMapper::class)
@RegisterRowMapper(StoredChartinkEvidenceUploadMapper::class)
interface ChartinkEvidenceReadDao {
    @SqlQuery(
        """
        SELECT DISTINCT ON (
            ${Cols.SOURCE},
            CASE WHEN ${Cols.SOURCE} = 'ACCUMULATION' THEN ${Cols.UNIVERSE_KEY} ELSE '' END
        ) ${Cols.SOURCE}, ${Cols.UNIVERSE_KEY}, ${Cols.SOURCE_FILE_NAME}, ${Cols.UPLOADED_AT}
        FROM public.${Tables.CHARTINK_SCAN_EVENTS}
        ORDER BY ${Cols.SOURCE},
                 CASE WHEN ${Cols.SOURCE} = 'ACCUMULATION' THEN ${Cols.UNIVERSE_KEY} ELSE '' END,
                 ${Cols.UPLOADED_AT} DESC
        """,
    )
    fun findLatestUploads(): List<StoredChartinkEvidenceUpload>

    @SqlQuery(
        """
        SELECT ${Cols.SOURCE}, ${Cols.UNIVERSE_KEY}, ${Cols.EVENT_DATE}, ${Cols.SYMBOL},
               ${Cols.MARKETCAP_NAME}, ${Cols.SECTOR}, ${Cols.SOURCE_FILE_NAME}
        FROM public.${Tables.CHARTINK_SCAN_EVENTS}
        WHERE ${Cols.EVENT_DATE} >= :fromDate
        ORDER BY ${Cols.EVENT_DATE} DESC, ${Cols.SYMBOL}
        """,
    )
    fun findFromDate(@Bind("fromDate") fromDate: LocalDate): List<ChartinkScanEvent>
}

class ChartinkScanEventMapper : RowMapper<ChartinkScanEvent> {
    override fun map(rs: ResultSet, ctx: StatementContext): ChartinkScanEvent = ChartinkScanEvent(
        source = ChartinkEvidenceSource.valueOf(rs.getString(Cols.SOURCE)),
        universeKey = rs.getString(Cols.UNIVERSE_KEY),
        eventDate = rs.getDate(Cols.EVENT_DATE).toLocalDate(),
        symbol = rs.getString(Cols.SYMBOL),
        marketcapName = rs.getString(Cols.MARKETCAP_NAME),
        sector = rs.getString(Cols.SECTOR),
        sourceFileName = rs.getString(Cols.SOURCE_FILE_NAME),
    )
}

class StoredChartinkEvidenceUploadMapper : RowMapper<StoredChartinkEvidenceUpload> {
    override fun map(rs: ResultSet, ctx: StatementContext): StoredChartinkEvidenceUpload = StoredChartinkEvidenceUpload(
        source = ChartinkEvidenceSource.valueOf(rs.getString(Cols.SOURCE)),
        universeKey = rs.getString(Cols.UNIVERSE_KEY),
        sourceFileName = rs.getString(Cols.SOURCE_FILE_NAME),
        uploadedAt = rs.getObject(Cols.UPLOADED_AT, OffsetDateTime::class.java).toInstant().toString(),
    )
}
