package com.tradingtool.core.delivery.dao

import com.tradingtool.core.constants.DatabaseConstants.StockDeliveryColumns as Cols
import com.tradingtool.core.constants.DatabaseConstants.Tables
import com.tradingtool.core.delivery.model.DeliveryReconciliationStatus
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.customizer.BindList
import org.jdbi.v3.sqlobject.statement.SqlQuery
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RegisterRowMapper(StockDeliveryMapper::class)
interface StockDeliveryReadDao {

    @SqlQuery("SELECT * FROM ${Tables.STOCK_DELIVERY_DAILY} WHERE ${Cols.INSTRUMENT_TOKEN} = :instrumentToken AND ${Cols.TRADING_DATE} = :tradingDate")
    fun findByInstrumentTokenAndDate(
        @Bind("instrumentToken") instrumentToken: Long,
        @Bind("tradingDate") tradingDate: LocalDate
    ): StockDeliveryDaily?

    @SqlQuery(
        "SELECT * FROM ${Tables.STOCK_DELIVERY_DAILY} " +
        "WHERE ${Cols.INSTRUMENT_TOKEN} = :instrumentToken " +
        "AND ${Cols.TRADING_DATE} < :beforeDate " +
        "ORDER BY ${Cols.TRADING_DATE} DESC LIMIT :limit"
    )
    fun findRecentByInstrumentToken(
        @Bind("instrumentToken") instrumentToken: Long,
        @Bind("beforeDate") beforeDate: LocalDate,
        @Bind("limit") limit: Int
    ): List<StockDeliveryDaily>

    @SqlQuery(
        "SELECT * FROM ${Tables.STOCK_DELIVERY_DAILY} " +
            "WHERE ${Cols.TRADING_DATE} = :tradingDate " +
            "ORDER BY ${Cols.INSTRUMENT_TOKEN}"
    )
    fun findAllByTradingDate(
        @Bind("tradingDate") tradingDate: LocalDate,
    ): List<StockDeliveryDaily>

    @SqlQuery(
        "SELECT * FROM ${Tables.STOCK_DELIVERY_DAILY} " +
            "WHERE ${Cols.TRADING_DATE} = :tradingDate " +
            "AND ${Cols.INSTRUMENT_TOKEN} IN (<instrumentTokens>) " +
            "ORDER BY ${Cols.INSTRUMENT_TOKEN}"
    )
    fun findByTradingDateAndInstrumentTokens(
        @Bind("tradingDate") tradingDate: LocalDate,
        @BindList("instrumentTokens") instrumentTokens: List<Long>,
    ): List<StockDeliveryDaily>

    @SqlQuery("SELECT MAX(${Cols.TRADING_DATE}) FROM ${Tables.STOCK_DELIVERY_DAILY}")
    fun getLatestTradingDate(): LocalDate?
}

class StockDeliveryMapper : RowMapper<StockDeliveryDaily> {
    override fun map(rs: ResultSet, ctx: StatementContext): StockDeliveryDaily {
        return StockDeliveryDaily(
            stockId = rs.getLong(Cols.STOCK_ID).let { if (rs.wasNull()) null else it },
            instrumentToken = rs.getLong(Cols.INSTRUMENT_TOKEN),
            symbol = rs.getString(Cols.SYMBOL),
            exchange = rs.getString(Cols.EXCHANGE),
            tradingDate = rs.getDate(Cols.TRADING_DATE).toLocalDate(),
            reconciliationStatus = DeliveryReconciliationStatus.valueOf(rs.getString(Cols.RECONCILIATION_STATUS)),
            series = rs.getString(Cols.SERIES),
            ttlTrdQnty = rs.getLong(Cols.TTL_TRD_QNTY).let { if (rs.wasNull()) null else it },
            delivQty = rs.getLong(Cols.DELIV_QTY).let { if (rs.wasNull()) null else it },
            delivPer = rs.getDouble(Cols.DELIV_PER).let { if (rs.wasNull()) null else it },
            sourceFileName = rs.getString(Cols.SOURCE_FILE_NAME),
            sourceUrl = rs.getString(Cols.SOURCE_URL),
            fetchedAt = readFetchedAt(rs),
        )
    }

    private fun readFetchedAt(rs: ResultSet): OffsetDateTime? {
        return runCatching {
            rs.getObject(Cols.FETCHED_AT, OffsetDateTime::class.java)
        }.getOrElse {
            rs.getTimestamp(Cols.FETCHED_AT)
                ?.toInstant()
                ?.atOffset(ZoneOffset.UTC)
        }
    }
}
