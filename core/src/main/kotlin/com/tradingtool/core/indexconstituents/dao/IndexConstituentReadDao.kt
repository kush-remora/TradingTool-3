package com.tradingtool.core.indexconstituents.dao

import com.tradingtool.core.constants.DatabaseConstants.IndexConstituentColumns
import com.tradingtool.core.constants.DatabaseConstants.Tables
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery

interface IndexConstituentReadDao {
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
}
