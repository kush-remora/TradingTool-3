package com.tradingtool.core.strategy.chartinkevidence.dao

import com.tradingtool.core.constants.DatabaseConstants.ChartinkScanEventColumns as Cols
import com.tradingtool.core.constants.DatabaseConstants.Tables
import com.tradingtool.core.strategy.chartinkevidence.ChartinkEvidenceSource
import com.tradingtool.core.strategy.chartinkevidence.ChartinkScanEvent
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.customizer.BindBean
import org.jdbi.v3.sqlobject.statement.SqlBatch
import org.jdbi.v3.sqlobject.statement.SqlUpdate

interface ChartinkEvidenceWriteDao {
    @SqlUpdate(
        """
        DELETE FROM public.${Tables.CHARTINK_SCAN_EVENTS}
        WHERE ${Cols.SOURCE} = :source
          AND ${Cols.UNIVERSE_KEY} = :universeKey
        """,
    )
    fun deleteAccumulation(
        @Bind("source") source: ChartinkEvidenceSource,
        @Bind("universeKey") universeKey: String,
    ): Int

    @SqlUpdate(
        """
        DELETE FROM public.${Tables.CHARTINK_SCAN_EVENTS}
        WHERE ${Cols.SOURCE} = :source
        """,
    )
    fun deleteCashSource(@Bind("source") source: ChartinkEvidenceSource): Int

    @SqlBatch(
        """
        INSERT INTO public.${Tables.CHARTINK_SCAN_EVENTS} (
            ${Cols.SOURCE}, ${Cols.UNIVERSE_KEY}, ${Cols.EVENT_DATE}, ${Cols.SYMBOL},
            ${Cols.MARKETCAP_NAME}, ${Cols.SECTOR}, ${Cols.SOURCE_FILE_NAME}
        ) VALUES (
            :source, :universeKey, :eventDate, :symbol,
            :marketcapName, :sector, :sourceFileName
        )
        """,
    )
    fun insertBatch(@BindBean events: List<ChartinkScanEvent>): IntArray
}
