package com.tradingtool.core.strategy.csvbacktest.dao

import com.tradingtool.core.strategy.csvbacktest.BacktestTradeReview
import org.jdbi.v3.sqlobject.customizer.BindBean
import org.jdbi.v3.sqlobject.statement.SqlUpdate

interface BacktestTradeReviewWriteDao {
    @SqlUpdate(
        """
        INSERT INTO backtest_trade_reviews (
            symbol, signal_date, market_cap, sector, entry_date, entry_price, 
            exit_date, exit_price, pnl_pct, days_held, sl_hit, pass, reason_tags, notes, updated_at
        ) VALUES (
            :symbol, :signalDate, :marketCap, :sector, :entryDate, :entryPrice,
            :exitDate, :exitPrice, :pnlPct, :daysHeld, :slHit, :pass, :reasonTags, :notes, NOW()
        )
        ON CONFLICT (symbol, signal_date) DO UPDATE SET
            market_cap = EXCLUDED.market_cap,
            sector = EXCLUDED.sector,
            entry_date = EXCLUDED.entry_date,
            entry_price = EXCLUDED.entry_price,
            exit_date = EXCLUDED.exit_date,
            exit_price = EXCLUDED.exit_price,
            pnl_pct = EXCLUDED.pnl_pct,
            days_held = EXCLUDED.days_held,
            sl_hit = EXCLUDED.sl_hit,
            pass = EXCLUDED.pass,
            reason_tags = EXCLUDED.reason_tags,
            notes = EXCLUDED.notes,
            updated_at = NOW()
        """
    )
    fun upsertReview(@BindBean review: BacktestTradeReview)
}
