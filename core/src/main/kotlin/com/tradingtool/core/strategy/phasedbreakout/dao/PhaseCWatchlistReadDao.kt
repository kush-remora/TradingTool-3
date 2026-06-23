package com.tradingtool.core.strategy.phasedbreakout.dao

import com.tradingtool.core.constants.DatabaseConstants.Tables
import com.tradingtool.core.constants.DatabaseConstants.PhaseCWatchlistColumns as Cols
import com.tradingtool.core.strategy.phasedbreakout.PhaseCWatchlistRow
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery

@RegisterConstructorMapper(PhaseCWatchlistRow::class)
interface PhaseCWatchlistReadDao {

    @SqlQuery(
        """
        SELECT * FROM public.${Tables.PHASE_C_WATCHLIST}
        WHERE ${Cols.STATUS} = :status
        """
    )
    fun findByStatus(@Bind("status") status: String): List<PhaseCWatchlistRow>

    @SqlQuery(
        """
        SELECT * FROM public.${Tables.PHASE_C_WATCHLIST}
        """
    )
    fun findAll(): List<PhaseCWatchlistRow>
}
