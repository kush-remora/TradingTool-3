package com.tradingtool.core.earnings.dao

import com.tradingtool.core.constants.DatabaseConstants.EarningsResultColumns as Cols
import com.tradingtool.core.constants.DatabaseConstants.Tables
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import java.time.LocalDate

interface EarningsResultWriteDao {

    @SqlUpdate(
        """
        INSERT INTO public.${Tables.EARNINGS_RESULTS}
            (${Cols.STOCK_SYMBOL}, ${Cols.RESULT_DATE}, ${Cols.BEHAVIOR_PAYLOAD})
        VALUES
            (:stockSymbol, :resultDate, '{}'::jsonb)
        ON CONFLICT (${Cols.STOCK_SYMBOL}, ${Cols.RESULT_DATE}) DO UPDATE SET
            ${Cols.UPDATED_AT} = NOW()
        """
    )
    fun upsert(
        @Bind("stockSymbol") stockSymbol: String,
        @Bind("resultDate") resultDate: LocalDate,
    ): Int

    @SqlUpdate(
        """
        UPDATE public.${Tables.EARNINGS_RESULTS}
        SET
            ${Cols.BEHAVIOR_PAYLOAD} = CAST(:behaviorPayloadJson AS jsonb),
            ${Cols.UPDATED_AT} = NOW()
        WHERE ${Cols.ID} = :id
        """
    )
    fun updateBehaviorPayload(
        @Bind("id") id: Long,
        @Bind("behaviorPayloadJson") behaviorPayloadJson: String,
    ): Int
}
