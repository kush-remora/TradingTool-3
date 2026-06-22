package com.tradingtool.core.volumeshocker.groww.dao

import com.tradingtool.core.constants.DatabaseConstants.GrowwVolumeShockerColumns as Cols
import com.tradingtool.core.constants.DatabaseConstants.Tables
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import java.time.LocalDate

interface GrowwVolumeShockerReadDao {

    @SqlQuery(
        """
        SELECT COUNT(*)
        FROM public.${Tables.GROWW_VOLUME_SHOCKER_DAILY}
        WHERE ${Cols.TRADE_DATE} = :tradeDate
        """,
    )
    fun countByTradeDate(@Bind("tradeDate") tradeDate: LocalDate): Int
}
