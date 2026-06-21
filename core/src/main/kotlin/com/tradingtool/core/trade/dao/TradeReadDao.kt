package com.tradingtool.core.trade.dao

import com.tradingtool.core.constants.DatabaseConstants.Tables
import com.tradingtool.core.constants.DatabaseConstants.TradeColumns
import com.tradingtool.core.model.trade.Trade
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import java.sql.ResultSet
import java.time.OffsetDateTime

/**
 * Read-only DAO for trade journal queries.
 */
@RegisterRowMapper(TradeMapper::class)
interface TradeReadDao {

    // ==================== Trade Queries ====================

    @SqlQuery(
        """
        SELECT ${TradeColumns.ALL}
        FROM public.${Tables.TRADES}
        WHERE ${TradeColumns.ID} = :tradeId
        LIMIT 1
        """
    )
    fun getTradeById(@Bind("tradeId") tradeId: Long): Trade?

    @SqlQuery(
        """
        SELECT ${TradeColumns.ALL}
        FROM public.${Tables.TRADES}
        WHERE ${TradeColumns.INSTRUMENT_TOKEN} = :instrumentToken
        LIMIT 1
        """
    )
    fun getTradeByInstrumentToken(@Bind("instrumentToken") instrumentToken: Long): Trade?

    @SqlQuery(
        """
        SELECT ${TradeColumns.ALL}
        FROM public.${Tables.TRADES}
        ORDER BY ${TradeColumns.CREATED_AT} DESC
        """
    )
    fun getAllTrades(): List<Trade>
}

// ==================== Row Mapper ====================

class TradeMapper : RowMapper<Trade> {
    override fun map(rs: ResultSet, ctx: StatementContext): Trade {
        return Trade(
            id = rs.getLong(TradeColumns.ID),
            instrumentToken = rs.getLong(TradeColumns.INSTRUMENT_TOKEN),
            nseSymbol = rs.getString(TradeColumns.NSE_SYMBOL),
            quantity = rs.getInt(TradeColumns.QUANTITY),
            avgBuyPrice = rs.getBigDecimal(TradeColumns.AVG_BUY_PRICE).toPlainString(),
            todayLow = rs.getBigDecimal(TradeColumns.TODAY_LOW)?.toPlainString(),
            stopLossPercent = rs.getBigDecimal(TradeColumns.STOP_LOSS_PERCENT).toPlainString(),
            stopLossPrice = rs.getBigDecimal(TradeColumns.STOP_LOSS_PRICE).toPlainString(),
            notes = rs.getString(TradeColumns.NOTES),
            tradeDate = rs.getDate(TradeColumns.TRADE_DATE).toLocalDate().toString(),
            closePrice = rs.getBigDecimal(TradeColumns.CLOSE_PRICE)?.toPlainString(),
            closeDate = rs.getDate(TradeColumns.CLOSE_DATE)?.toLocalDate()?.toString(),
            createdAt = toUtcString(rs.getObject(TradeColumns.CREATED_AT, OffsetDateTime::class.java)),
            updatedAt = toUtcString(rs.getObject(TradeColumns.UPDATED_AT, OffsetDateTime::class.java)),
            strategy = rs.getString(TradeColumns.STRATEGY),
        )
    }

    private fun toUtcString(value: OffsetDateTime?): String {
        return value?.toInstant()?.toString()
            ?: throw IllegalStateException("Unexpected null timestamp in database row")
    }
}
