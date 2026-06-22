package com.tradingtool.core.volumeshocker.groww.dao

import com.tradingtool.core.constants.DatabaseConstants.GrowwVolumeShockerColumns as Cols
import com.tradingtool.core.constants.DatabaseConstants.Tables
import com.tradingtool.core.volumeshocker.groww.GrowwVolumeShockerDailyRow
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.customizer.BindBean
import org.jdbi.v3.sqlobject.statement.SqlBatch
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import java.time.LocalDate

interface GrowwVolumeShockerWriteDao {

    @SqlUpdate(
        """
        DELETE FROM public.${Tables.GROWW_VOLUME_SHOCKER_DAILY}
        WHERE ${Cols.TRADE_DATE} = :tradeDate
        """,
    )
    fun deleteByTradeDate(@Bind("tradeDate") tradeDate: LocalDate): Int

    @SqlBatch(
        """
        INSERT INTO public.${Tables.GROWW_VOLUME_SHOCKER_DAILY} (
            ${Cols.TRADE_DATE},
            ${Cols.SOURCE_RANK},
            ${Cols.EXCHANGE},
            ${Cols.SYMBOL},
            ${Cols.INSTRUMENT_TOKEN},
            ${Cols.COMPANY_NAME},
            ${Cols.LTP},
            ${Cols.CLOSE},
            ${Cols.MARKET_CAP_CRORE},
            ${Cols.MARKET_CAP_CATEGORY},
            ${Cols.YEAR_LOW},
            ${Cols.YEAR_HIGH},
            ${Cols.VOLUME},
            ${Cols.WEEKLY_AVERAGE_VOLUME}
        ) VALUES (
            :tradeDate,
            :sourceRank,
            :exchange,
            :symbol,
            :instrumentToken,
            :companyName,
            :ltp,
            :close,
            :marketCapCrore,
            :marketCapCategory,
            :yearLow,
            :yearHigh,
            :volume,
            :weeklyAverageVolume
        )
        """,
    )
    fun insertBatch(@BindBean rows: List<GrowwVolumeShockerDailyRow>): IntArray
}
