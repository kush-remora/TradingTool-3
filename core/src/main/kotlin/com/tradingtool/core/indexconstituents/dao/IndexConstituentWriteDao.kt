package com.tradingtool.core.indexconstituents.dao

import com.tradingtool.core.constants.DatabaseConstants.IndexConstituentColumns
import com.tradingtool.core.constants.DatabaseConstants.Tables
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.customizer.BindBean
import org.jdbi.v3.sqlobject.customizer.BindList
import org.jdbi.v3.sqlobject.statement.SqlBatch
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import java.time.OffsetDateTime

data class IndexConstituentUpsertRow(
    val indexKey: String,
    val symbol: String,
    val instrumentToken: Long,
    val companyName: String,
    val industry: String,
    val series: String,
    val isinCode: String,
    val sourceUrl: String,
)

interface IndexConstituentWriteDao {

    @SqlBatch(
        """
        INSERT INTO public.${Tables.INDEX_CONSTITUENTS} (
            ${IndexConstituentColumns.INDEX_KEY},
            ${IndexConstituentColumns.SYMBOL},
            ${IndexConstituentColumns.INSTRUMENT_TOKEN},
            ${IndexConstituentColumns.COMPANY_NAME},
            ${IndexConstituentColumns.INDUSTRY},
            ${IndexConstituentColumns.SERIES},
            ${IndexConstituentColumns.ISIN_CODE},
            ${IndexConstituentColumns.IS_ACTIVE},
            ${IndexConstituentColumns.SOURCE_URL},
            ${IndexConstituentColumns.LAST_SYNCED_AT},
            ${IndexConstituentColumns.UPDATED_AT}
        )
        VALUES (
            :indexKey,
            :symbol,
            :instrumentToken,
            :companyName,
            :industry,
            :series,
            :isinCode,
            true,
            :sourceUrl,
            :syncedAt,
            :syncedAt
        )
        ON CONFLICT (${IndexConstituentColumns.INDEX_KEY}, ${IndexConstituentColumns.SYMBOL})
        DO UPDATE SET
            ${IndexConstituentColumns.INSTRUMENT_TOKEN} = EXCLUDED.${IndexConstituentColumns.INSTRUMENT_TOKEN},
            ${IndexConstituentColumns.COMPANY_NAME} = EXCLUDED.${IndexConstituentColumns.COMPANY_NAME},
            ${IndexConstituentColumns.INDUSTRY} = EXCLUDED.${IndexConstituentColumns.INDUSTRY},
            ${IndexConstituentColumns.SERIES} = EXCLUDED.${IndexConstituentColumns.SERIES},
            ${IndexConstituentColumns.ISIN_CODE} = EXCLUDED.${IndexConstituentColumns.ISIN_CODE},
            ${IndexConstituentColumns.IS_ACTIVE} = true,
            ${IndexConstituentColumns.SOURCE_URL} = EXCLUDED.${IndexConstituentColumns.SOURCE_URL},
            ${IndexConstituentColumns.LAST_SYNCED_AT} = EXCLUDED.${IndexConstituentColumns.LAST_SYNCED_AT},
            ${IndexConstituentColumns.UPDATED_AT} = EXCLUDED.${IndexConstituentColumns.UPDATED_AT}
        """,
    )
    fun upsertBatch(
        @BindBean rows: List<IndexConstituentUpsertRow>,
        @Bind("syncedAt") syncedAt: OffsetDateTime,
    ): IntArray

    @SqlUpdate(
        """
        UPDATE public.${Tables.INDEX_CONSTITUENTS}
        SET
            ${IndexConstituentColumns.IS_ACTIVE} = false,
            ${IndexConstituentColumns.LAST_SYNCED_AT} = :syncedAt,
            ${IndexConstituentColumns.UPDATED_AT} = :syncedAt
        WHERE ${IndexConstituentColumns.INDEX_KEY} = :indexKey
          AND ${IndexConstituentColumns.SYMBOL} NOT IN (<activeSymbols>)
          AND ${IndexConstituentColumns.IS_ACTIVE} = true
        """,
    )
    fun deactivateMissing(
        @Bind("indexKey") indexKey: String,
        @BindList("activeSymbols") activeSymbols: List<String>,
        @Bind("syncedAt") syncedAt: OffsetDateTime,
    ): Int

    @SqlUpdate(
        """
        UPDATE public.${Tables.INDEX_CONSTITUENTS}
        SET
            ${IndexConstituentColumns.IS_ACTIVE} = false,
            ${IndexConstituentColumns.LAST_SYNCED_AT} = :syncedAt,
            ${IndexConstituentColumns.UPDATED_AT} = :syncedAt
        WHERE ${IndexConstituentColumns.INDEX_KEY} = :indexKey
          AND ${IndexConstituentColumns.IS_ACTIVE} = true
        """,
    )
    fun deactivateAllByIndex(
        @Bind("indexKey") indexKey: String,
        @Bind("syncedAt") syncedAt: OffsetDateTime,
    ): Int

    @SqlUpdate(
        """
        UPDATE public.${Tables.INDEX_CONSTITUENTS}
        SET
            ${IndexConstituentColumns.INSTRUMENT_TOKEN} = :instrumentToken,
            ${IndexConstituentColumns.UPDATED_AT} = :syncedAt
        WHERE ${IndexConstituentColumns.SYMBOL} = :symbol
          AND ${IndexConstituentColumns.IS_ACTIVE} = true
        """,
    )
    fun updateInstrumentTokenBySymbol(
        @Bind("symbol") symbol: String,
        @Bind("instrumentToken") instrumentToken: Long,
        @Bind("syncedAt") syncedAt: OffsetDateTime,
    ): Int
}
