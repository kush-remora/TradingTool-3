package com.tradingtool.core.strategy.csvbacktest.dao

import com.tradingtool.core.strategy.csvbacktest.BacktestTradeReview
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper
import org.jdbi.v3.sqlobject.statement.SqlQuery

@RegisterConstructorMapper(BacktestTradeReview::class)
interface BacktestTradeReviewReadDao {
    @SqlQuery(
        """
        SELECT 
            id,
            symbol,
            signal_date,
            market_cap,
            sector,
            entry_date,
            entry_price,
            exit_date,
            exit_price,
            pnl_pct,
            days_held,
            sl_hit,
            pass,
            reason_tags,
            notes,
            created_at,
            updated_at
        FROM backtest_trade_reviews
        ORDER BY created_at DESC
        """
    )
    fun getAllReviews(): List<BacktestTradeReview>
}
