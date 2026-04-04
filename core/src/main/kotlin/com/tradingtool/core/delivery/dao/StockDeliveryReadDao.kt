package com.tradingtool.core.delivery.dao

import com.tradingtool.core.constants.DatabaseConstants.StockDeliveryColumns as Cols
import com.tradingtool.core.constants.DatabaseConstants.Tables
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime

@RegisterRowMapper(StockDeliveryMapper::class)
interface StockDeliveryReadDao {

    @SqlQuery("SELECT * FROM ${Tables.STOCK_DELIVERY_DAILY} WHERE ${Cols.STOCK_ID} = :stockId AND ${Cols.TRADING_DATE} = :tradingDate")
    fun findByStockIdAndDate(
        @Bind("stockId") stockId: Int,
        @Bind("tradingDate") tradingDate: LocalDate
    ): StockDeliveryDaily?

    @SqlQuery(
        "SELECT * FROM ${Tables.STOCK_DELIVERY_DAILY} " +
        "WHERE ${Cols.STOCK_ID} = :stockId " +
        "AND ${Cols.TRADING_DATE} < :beforeDate " +
        "ORDER BY ${Cols.TRADING_DATE} DESC LIMIT :limit"
    )
    fun findRecentByStockId(
        @Bind("stockId") stockId: Int,
        @Bind("beforeDate") beforeDate: LocalDate,
        @Bind("limit") limit: Int
    ): List<StockDeliveryDaily>

    @SqlQuery("SELECT MAX(${Cols.TRADING_DATE}) FROM ${Tables.STOCK_DELIVERY_DAILY}")
    fun getLatestTradingDate(): LocalDate?
}

class StockDeliveryMapper : RowMapper<StockDeliveryDaily> {
    override fun map(rs: ResultSet, ctx: StatementContext): StockDeliveryDaily {
        return StockDeliveryDaily(
            stockId = rs.getInt(Cols.STOCK_ID),
            symbol = rs.getString(Cols.SYMBOL),
            exchange = rs.getString(Cols.EXCHANGE),
            tradingDate = rs.getDate(Cols.TRADING_DATE).toLocalDate(),
            series = rs.getString(Cols.SERIES),
            ttlTrdQnty = rs.getLong(Cols.TTL_TRD_QNTY).let { if (rs.wasNull()) null else it },
            delivQty = rs.getLong(Cols.DELIV_QTY).let { if (rs.wasNull()) null else it },
            delivPer = rs.getDouble(Cols.DELIV_PER).let { if (rs.wasNull()) null else it },
            sourceFileName = rs.getString(Cols.SOURCE_FILE_NAME),
            sourceUrl = rs.getString(Cols.SOURCE_URL),
            fetchedAt = rs.getObject(Cols.FETCHED_AT, OffsetDateTime::class.java)
        )
    }
}
