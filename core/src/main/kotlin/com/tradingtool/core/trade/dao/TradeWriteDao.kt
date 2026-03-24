package com.tradingtool.core.trade.dao

import com.tradingtool.core.constants.DatabaseConstants.Tables
import com.tradingtool.core.constants.DatabaseConstants.TradeColumns
import com.tradingtool.core.model.trade.Trade
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate

/**
 * Write DAO for trade journal INSERT/UPDATE/DELETE operations.
 * Handles consolidation via UPSERT (INSERT ... ON CONFLICT).
 */
@RegisterRowMapper(TradeMapper::class)
interface TradeWriteDao {

    /**
     * Upsert trade: insert new trade or consolidate with existing trade for the same stock.
     * If stock_id already exists, updates quantity and avg_buy_price via weighted average formula:
     * new_avg = (qty1 * price1 + qty2 * price2) / (qty1 + qty2)
     *
     * Note: Uses @SqlQuery (not @SqlUpdate) because PostgreSQL's RETURNING clause makes this
     * return a result row. JDBI3 treats INSERT/UPDATE/DELETE with RETURNING as a query operation.
     */
    @SqlQuery(
        """
        INSERT INTO public.${Tables.TRADES} (
            ${TradeColumns.STOCK_ID},
            ${TradeColumns.NSE_SYMBOL},
            ${TradeColumns.QUANTITY},
            ${TradeColumns.AVG_BUY_PRICE},
            ${TradeColumns.TODAY_LOW},
            ${TradeColumns.STOP_LOSS_PERCENT},
            ${TradeColumns.STOP_LOSS_PRICE},
            ${TradeColumns.NOTES},
            ${TradeColumns.TRADE_DATE}
        ) VALUES (
            :stockId,
            :nseSymbol,
            :quantity,
            CAST(:avgBuyPrice AS NUMERIC(10,2)),
            CASE WHEN :todayLow IS NOT NULL THEN CAST(:todayLow AS NUMERIC(10,2)) ELSE NULL END,
            CAST(:stopLossPercent AS NUMERIC(5,2)),
            CAST(:stopLossPrice AS NUMERIC(10,2)),
            :notes,
            CAST(:tradeDate AS DATE)
        )
        ON CONFLICT (${TradeColumns.STOCK_ID}) DO UPDATE SET
            ${TradeColumns.QUANTITY} = EXCLUDED.${TradeColumns.QUANTITY} + ${Tables.TRADES}.${TradeColumns.QUANTITY},
            ${TradeColumns.AVG_BUY_PRICE} = (
                (${Tables.TRADES}.${TradeColumns.QUANTITY} * ${Tables.TRADES}.${TradeColumns.AVG_BUY_PRICE}) +
                (EXCLUDED.${TradeColumns.QUANTITY} * EXCLUDED.${TradeColumns.AVG_BUY_PRICE})
            ) / (${Tables.TRADES}.${TradeColumns.QUANTITY} + EXCLUDED.${TradeColumns.QUANTITY}),
            ${TradeColumns.STOP_LOSS_PERCENT} = EXCLUDED.${TradeColumns.STOP_LOSS_PERCENT},
            ${TradeColumns.STOP_LOSS_PRICE} = (
                ((${Tables.TRADES}.${TradeColumns.QUANTITY} * ${Tables.TRADES}.${TradeColumns.AVG_BUY_PRICE}) +
                 (EXCLUDED.${TradeColumns.QUANTITY} * EXCLUDED.${TradeColumns.AVG_BUY_PRICE})) /
                (${Tables.TRADES}.${TradeColumns.QUANTITY} + EXCLUDED.${TradeColumns.QUANTITY})
            ) * (1 - EXCLUDED.${TradeColumns.STOP_LOSS_PERCENT} / 100),
            ${TradeColumns.TODAY_LOW} = COALESCE(EXCLUDED.${TradeColumns.TODAY_LOW}, ${Tables.TRADES}.${TradeColumns.TODAY_LOW}),
            ${TradeColumns.NOTES} = COALESCE(EXCLUDED.${TradeColumns.NOTES}, ${Tables.TRADES}.${TradeColumns.NOTES}),
            ${TradeColumns.UPDATED_AT} = NOW()
        RETURNING ${TradeColumns.ALL}
        """
    )
    fun upsertTrade(
        @Bind("stockId") stockId: Long,
        @Bind("nseSymbol") nseSymbol: String,
        @Bind("quantity") quantity: Int,
        @Bind("avgBuyPrice") avgBuyPrice: String,
        @Bind("todayLow") todayLow: String?,
        @Bind("stopLossPercent") stopLossPercent: String,
        @Bind("stopLossPrice") stopLossPrice: String,
        @Bind("notes") notes: String?,
        @Bind("tradeDate") tradeDate: String
    ): Trade

    /**
     * Delete trade by ID.
     */
    @SqlUpdate(
        """
        DELETE FROM public.${Tables.TRADES}
        WHERE ${TradeColumns.ID} = :tradeId
        """
    )
    fun deleteTrade(@Bind("tradeId") tradeId: Long): Int
}
