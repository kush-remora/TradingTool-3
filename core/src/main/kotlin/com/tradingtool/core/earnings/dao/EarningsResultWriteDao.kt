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
            (${Cols.STOCK_SYMBOL}, ${Cols.INSTRUMENT_TOKEN}, ${Cols.RESULT_DATE}, ${Cols.BEHAVIOR_PAYLOAD})
        VALUES
            (:stockSymbol, :instrumentToken, :resultDate, '{}'::jsonb)
        ON CONFLICT (${Cols.STOCK_SYMBOL}, ${Cols.RESULT_DATE}) DO UPDATE SET
            ${Cols.INSTRUMENT_TOKEN} = EXCLUDED.${Cols.INSTRUMENT_TOKEN},
            ${Cols.UPDATED_AT} = NOW()
        """
    )
    fun upsert(
        @Bind("stockSymbol") stockSymbol: String,
        @Bind("instrumentToken") instrumentToken: Long,
        @Bind("resultDate") resultDate: LocalDate,
    ): Int

    @SqlUpdate(
        """
        UPDATE public.${Tables.EARNINGS_RESULTS} er
        SET
            ${Cols.INSTRUMENT_TOKEN} = s.instrument_token,
            ${Cols.UPDATED_AT} = NOW()
        FROM public.stocks s
        WHERE er.${Cols.INSTRUMENT_TOKEN} IS NULL
          AND UPPER(s.symbol) = UPPER(er.${Cols.STOCK_SYMBOL})
          AND s.exchange = 'NSE'
        """
    )
    fun backfillInstrumentTokenFromStocks(): Int

    @SqlUpdate(
        """
        UPDATE public.${Tables.EARNINGS_RESULTS}
        SET
            ${Cols.INSTRUMENT_TOKEN} = :instrumentToken,
            ${Cols.UPDATED_AT} = NOW()
        WHERE ${Cols.ID} = :id
          AND ${Cols.INSTRUMENT_TOKEN} IS NULL
        """
    )
    fun updateInstrumentToken(
        @Bind("id") id: Long,
        @Bind("instrumentToken") instrumentToken: Long,
    ): Int

    @SqlUpdate(
        """
        ALTER TABLE public.${Tables.EARNINGS_RESULTS}
        ALTER COLUMN ${Cols.INSTRUMENT_TOKEN} SET NOT NULL
        """
    )
    fun enforceInstrumentTokenNotNull()

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
