package com.tradingtool.core.strategy.remora

import com.tradingtool.core.constants.DatabaseConstants.Tables
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlUpdate

interface RemoraSignalWriteDao {

    /**
     * Inserts a new signal row. Returns 1 if inserted, 0 if a signal for this
     * (stock_id, signal_date) already exists — allowing safe re-runs of the cron.
     */
    @SqlUpdate(
        """
        INSERT INTO public.${Tables.REMORA_SIGNALS}
            (stock_id, symbol, company_name, exchange, signal_type, volume_ratio,
             price_change_pct, consecutive_days, signal_date, computed_at)
        VALUES
            (:stockId, :symbol, :companyName, :exchange, :signalType, :volumeRatio,
             :priceChangePct, :consecutiveDays, CURRENT_DATE, NOW())
        ON CONFLICT (stock_id, signal_date) DO NOTHING
        """
    )
    fun insertIfAbsent(
        @Bind("stockId") stockId: Int,
        @Bind("symbol") symbol: String,
        @Bind("companyName") companyName: String,
        @Bind("exchange") exchange: String,
        @Bind("signalType") signalType: String,
        @Bind("volumeRatio") volumeRatio: Double,
        @Bind("priceChangePct") priceChangePct: Double,
        @Bind("consecutiveDays") consecutiveDays: Int,
    ): Int
}
