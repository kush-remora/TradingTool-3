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
}
