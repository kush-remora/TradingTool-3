package com.tradingtool.core.earnings.dao

import com.tradingtool.core.constants.DatabaseConstants.EarningsResultColumns as Cols
import com.tradingtool.core.constants.DatabaseConstants.Tables
import com.tradingtool.core.earnings.EarningsResultRow
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import java.sql.ResultSet
import java.time.LocalDate

@RegisterRowMapper(EarningsResultRowMapper::class)
interface EarningsResultReadDao {

    @SqlQuery(
        """
        SELECT
            ${Cols.ID},
            ${Cols.STOCK_SYMBOL},
            ${Cols.RESULT_DATE},
            ${Cols.BEHAVIOR_PAYLOAD}::text AS behavior_payload_json
        FROM public.${Tables.EARNINGS_RESULTS}
        WHERE ${Cols.RESULT_DATE} BETWEEN :from AND :to
        ORDER BY ${Cols.RESULT_DATE}, ${Cols.STOCK_SYMBOL}
        """
    )
    fun findByResultDateRange(
        @Bind("from") from: LocalDate,
        @Bind("to") to: LocalDate,
    ): List<EarningsResultRow>
}

class EarningsResultRowMapper : RowMapper<EarningsResultRow> {
    override fun map(rs: ResultSet, ctx: StatementContext): EarningsResultRow {
        return EarningsResultRow(
            id = rs.getLong(Cols.ID),
            stockSymbol = rs.getString(Cols.STOCK_SYMBOL),
            resultDate = rs.getDate(Cols.RESULT_DATE).toLocalDate(),
            behaviorPayloadJson = rs.getString("behavior_payload_json") ?: "{}",
        )
    }
}
