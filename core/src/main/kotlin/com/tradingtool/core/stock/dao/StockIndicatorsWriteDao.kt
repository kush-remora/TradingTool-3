package com.tradingtool.core.stock.dao

import com.tradingtool.core.constants.DatabaseConstants.StockIndicatorColumns
import com.tradingtool.core.constants.DatabaseConstants.Tables
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlUpdate

interface StockIndicatorsWriteDao {

    @SqlUpdate(
        """
        INSERT INTO public.${Tables.STOCK_INDICATORS_SNAPSHOT}
            (${StockIndicatorColumns.INSTRUMENT_TOKEN}, ${StockIndicatorColumns.INDICATORS_PAYLOAD}, ${StockIndicatorColumns.COMPUTED_AT})
        VALUES (:instrumentToken, CAST(:indicatorsJson AS jsonb), NOW())
        ON CONFLICT (${StockIndicatorColumns.INSTRUMENT_TOKEN})
        DO UPDATE SET
            ${StockIndicatorColumns.INDICATORS_PAYLOAD} = EXCLUDED.${StockIndicatorColumns.INDICATORS_PAYLOAD},
            ${StockIndicatorColumns.COMPUTED_AT} = NOW()
        """
    )
    fun upsertIndicators(
        @Bind("instrumentToken") instrumentToken: Long,
        @Bind("indicatorsJson") indicatorsJson: String
    ): Int
}
