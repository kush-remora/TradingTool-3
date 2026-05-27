package com.tradingtool.core.indexconstituents.dao

import com.tradingtool.core.constants.DatabaseConstants.IndexConstituentColumns
import com.tradingtool.core.constants.DatabaseConstants.Tables
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import java.sql.ResultSet
import org.jdbi.v3.sqlobject.customizer.BindList

@RegisterRowMapper(IndexSummaryMapper::class)
@RegisterRowMapper(InstrumentUniverseMapper::class)
interface IndexConstituentReadDao {
    @SqlQuery(
        """
        SELECT 
            ${IndexConstituentColumns.INDEX_KEY} AS index_key, 
            COUNT(*) AS total_count
        FROM public.${Tables.INDEX_CONSTITUENTS}
        WHERE ${IndexConstituentColumns.IS_ACTIVE} = true
        GROUP BY ${IndexConstituentColumns.INDEX_KEY}
        ORDER BY ${IndexConstituentColumns.INDEX_KEY}
        """,
    )
    fun listUniqueIndices(): List<IndexSummary>

    @SqlQuery(
        """
        SELECT COUNT(*)
        FROM public.${Tables.INDEX_CONSTITUENTS}
        WHERE ${IndexConstituentColumns.INDEX_KEY} = :indexKey
          AND ${IndexConstituentColumns.IS_ACTIVE} = true
        """,
    )
    fun countActiveByIndex(@Bind("indexKey") indexKey: String): Int

    @SqlQuery(
        """
        SELECT 
            ${IndexConstituentColumns.INDEX_KEY},
            ${IndexConstituentColumns.SYMBOL},
            ${IndexConstituentColumns.INSTRUMENT_TOKEN},
            ${IndexConstituentColumns.COMPANY_NAME},
            ${IndexConstituentColumns.INDUSTRY},
            ${IndexConstituentColumns.SERIES},
            ${IndexConstituentColumns.ISIN_CODE},
            ${IndexConstituentColumns.SOURCE_URL}
        FROM public.${Tables.INDEX_CONSTITUENTS}
        WHERE ${IndexConstituentColumns.INDEX_KEY} = :indexKey
          AND ${IndexConstituentColumns.IS_ACTIVE} = true
        ORDER BY ${IndexConstituentColumns.SYMBOL}
        """
    )
    fun listActiveByIndex(@Bind("indexKey") indexKey: String): List<IndexConstituentUpsertRow>

    @SqlQuery(
        """
        SELECT 
            ${IndexConstituentColumns.INDEX_KEY},
            ${IndexConstituentColumns.SYMBOL},
            ${IndexConstituentColumns.INSTRUMENT_TOKEN},
            ${IndexConstituentColumns.COMPANY_NAME},
            ${IndexConstituentColumns.INDUSTRY},
            ${IndexConstituentColumns.SERIES},
            ${IndexConstituentColumns.ISIN_CODE},
            ${IndexConstituentColumns.SOURCE_URL}
        FROM public.${Tables.INDEX_CONSTITUENTS}
        WHERE ${IndexConstituentColumns.IS_ACTIVE} = true
        ORDER BY ${IndexConstituentColumns.SYMBOL}
        """
    )
    fun listAllActive(): List<IndexConstituentUpsertRow>

    @SqlQuery(
        """
        SELECT
            ${IndexConstituentColumns.INSTRUMENT_TOKEN} AS instrument_token,
            MIN(${IndexConstituentColumns.INDEX_KEY}) AS universe
        FROM public.${Tables.INDEX_CONSTITUENTS}
        WHERE ${IndexConstituentColumns.IS_ACTIVE} = true
          AND ${IndexConstituentColumns.INSTRUMENT_TOKEN} IN (<instrumentTokens>)
        GROUP BY ${IndexConstituentColumns.INSTRUMENT_TOKEN}
        """,
    )
    fun findUniverseByInstrumentTokens(
        @BindList("instrumentTokens") instrumentTokens: List<Long>,
    ): List<InstrumentUniverseRow>
}

data class InstrumentUniverseRow(
    val instrumentToken: Long,
    val universe: String,
)

data class IndexSummary(
    val indexKey: String,
    val count: Int
)

class IndexSummaryMapper : RowMapper<IndexSummary> {
    override fun map(rs: ResultSet, ctx: StatementContext): IndexSummary {
        return IndexSummary(
            indexKey = rs.getString("index_key"),
            count = rs.getInt("total_count")
        )
    }
}

class InstrumentUniverseMapper : RowMapper<InstrumentUniverseRow> {
    override fun map(rs: ResultSet, ctx: StatementContext): InstrumentUniverseRow {
        return InstrumentUniverseRow(
            instrumentToken = rs.getLong("instrument_token"),
            universe = rs.getString("universe"),
        )
    }
}
