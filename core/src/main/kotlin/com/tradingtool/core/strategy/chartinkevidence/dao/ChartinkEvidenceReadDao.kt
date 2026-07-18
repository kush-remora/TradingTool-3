package com.tradingtool.core.strategy.chartinkevidence.dao

import com.tradingtool.core.constants.DatabaseConstants.ChartinkScanEventColumns as Cols
import com.tradingtool.core.constants.DatabaseConstants.Tables
import com.tradingtool.core.strategy.chartinkevidence.ChartinkEvidenceSource
import com.tradingtool.core.strategy.chartinkevidence.ChartinkScanEvent
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import java.sql.ResultSet
import java.time.LocalDate

@RegisterRowMapper(ChartinkScanEventMapper::class)
interface ChartinkEvidenceReadDao {
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
