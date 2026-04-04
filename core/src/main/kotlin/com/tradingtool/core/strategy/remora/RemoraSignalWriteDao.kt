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
             price_change_pct, consecutive_days, signal_date, delivery_pct, delivery_ratio, computed_at)
        VALUES
            (:stockId, :symbol, :companyName, :exchange, :signalType, :volumeRatio,
             :priceChangePct, :consecutiveDays, :signalDate, :deliveryPct, :deliveryRatio, NOW())
        ON CONFLICT (stock_id, signal_date) DO UPDATE SET
            volume_ratio = EXCLUDED.volume_ratio,
            price_change_pct = EXCLUDED.price_change_pct,
            consecutive_days = EXCLUDED.consecutive_days,
            delivery_pct = EXCLUDED.delivery_pct,
            delivery_ratio = EXCLUDED.delivery_ratio,
            computed_at = NOW()
        """
    )
    fun upsert(
        @Bind("stockId") stockId: Int,
        @Bind("symbol") symbol: String,
        @Bind("companyName") companyName: String,
        @Bind("exchange") exchange: String,
        @Bind("signalType") signalType: String,
        @Bind("volumeRatio") volumeRatio: Double,
        @Bind("priceChangePct") priceChangePct: Double,
        @Bind("consecutiveDays") consecutiveDays: Int,
        @Bind("signalDate") signalDate: java.time.LocalDate,
        @Bind("deliveryPct") deliveryPct: Double,
        @Bind("deliveryRatio") deliveryRatio: Double,
    ): Int
}
