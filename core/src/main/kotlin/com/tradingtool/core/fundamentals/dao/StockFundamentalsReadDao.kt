package com.tradingtool.core.fundamentals.dao

import com.tradingtool.core.constants.DatabaseConstants.StockFundamentalsColumns as Cols
import com.tradingtool.core.constants.DatabaseConstants.Tables
import com.tradingtool.core.delivery.model.DeliveryUniverse
import com.tradingtool.core.fundamentals.model.StockFundamentalsDaily
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

@RegisterRowMapper(StockFundamentalsMapper::class)
interface StockFundamentalsReadDao {

    @SqlQuery(
        "SELECT * FROM ${Tables.STOCK_FUNDAMENTALS_DAILY} " +
            "WHERE ${Cols.INSTRUMENT_TOKEN} = :instrumentToken AND ${Cols.SNAPSHOT_DATE} = :snapshotDate"
    )
    fun findByInstrumentTokenAndDate(
        @Bind("instrumentToken") instrumentToken: Long,
        @Bind("snapshotDate") snapshotDate: LocalDate,
    ): StockFundamentalsDaily?

    @SqlQuery(
        "SELECT * FROM ${Tables.STOCK_FUNDAMENTALS_DAILY} " +
            "WHERE ${Cols.SNAPSHOT_DATE} = :snapshotDate " +
            "ORDER BY ${Cols.INSTRUMENT_TOKEN}"
    )
    fun findAllBySnapshotDate(
        @Bind("snapshotDate") snapshotDate: LocalDate,
    ): List<StockFundamentalsDaily>

    @SqlQuery(
        "SELECT * FROM ${Tables.STOCK_FUNDAMENTALS_DAILY} " +
            "WHERE ${Cols.SNAPSHOT_DATE} = :snapshotDate " +
            "AND ${Cols.INSTRUMENT_TOKEN} IN (<instrumentTokens>) " +
            "ORDER BY ${Cols.INSTRUMENT_TOKEN}"
    )
    fun findBySnapshotDateAndInstrumentTokens(
        @Bind("snapshotDate") snapshotDate: LocalDate,
        @BindList("instrumentTokens") instrumentTokens: List<Long>,
    ): List<StockFundamentalsDaily>

    @SqlQuery(
        """
        SELECT DISTINCT ON (${Cols.INSTRUMENT_TOKEN}) *
        FROM ${Tables.STOCK_FUNDAMENTALS_DAILY}
        WHERE ${Cols.INSTRUMENT_TOKEN} IN (<instrumentTokens>)
        ORDER BY ${Cols.INSTRUMENT_TOKEN}, ${Cols.SNAPSHOT_DATE} DESC
        """
    )
    fun findLatestByInstrumentTokens(
        @BindList("instrumentTokens") instrumentTokens: List<Long>,
    ): List<StockFundamentalsDaily>

    @SqlQuery(
        """
        SELECT DISTINCT ON (${Cols.SYMBOL}) *
        FROM ${Tables.STOCK_FUNDAMENTALS_DAILY}
        WHERE ${Cols.SYMBOL} IN (<symbols>)
        ORDER BY ${Cols.SYMBOL}, ${Cols.SNAPSHOT_DATE} DESC
        """
    )
    fun findLatestBySymbols(
        @BindList("symbols") symbols: List<String>,
    ): List<StockFundamentalsDaily>

    @SqlQuery(
        """
        SELECT DISTINCT ON (${Cols.SYMBOL}) *
        FROM ${Tables.STOCK_FUNDAMENTALS_DAILY}
        WHERE ${Cols.SYMBOL} IN (<symbols>)
          AND ${Cols.SNAPSHOT_DATE} < :snapshotDate
        ORDER BY ${Cols.SYMBOL}, ${Cols.SNAPSHOT_DATE} DESC
        """
    )
    fun findLatestBySymbolsBeforeDate(
        @BindList("symbols") symbols: List<String>,
        @Bind("snapshotDate") snapshotDate: LocalDate,
    ): List<StockFundamentalsDaily>
}

class StockFundamentalsMapper : RowMapper<StockFundamentalsDaily> {
    override fun map(rs: ResultSet, ctx: StatementContext): StockFundamentalsDaily {
        return StockFundamentalsDaily(
            stockId = rs.getLong(Cols.STOCK_ID).let { if (rs.wasNull()) null else it },
            instrumentToken = rs.getLong(Cols.INSTRUMENT_TOKEN),
            symbol = rs.getString(Cols.SYMBOL),
            exchange = rs.getString(Cols.EXCHANGE),
            universe = DeliveryUniverse.fromStorageValue(rs.getString(Cols.UNIVERSE)),
            snapshotDate = rs.getDate(Cols.SNAPSHOT_DATE).toLocalDate(),
            companyName = rs.getString(Cols.COMPANY_NAME),
            marketCapCr = rs.getDouble(Cols.MARKET_CAP_CR).let { if (rs.wasNull()) null else it },
            stockPe = rs.getDouble(Cols.STOCK_PE).let { if (rs.wasNull()) null else it },
            rocePercent = rs.getDouble(Cols.ROCE_PERCENT).let { if (rs.wasNull()) null else it },
            roePercent = rs.getDouble(Cols.ROE_PERCENT).let { if (rs.wasNull()) null else it },
            promoterHoldingPercent = rs.getDouble(Cols.PROMOTER_HOLDING_PERCENT).let { if (rs.wasNull()) null else it },
            broadIndustry = rs.getString(Cols.BROAD_INDUSTRY),
            industry = rs.getString(Cols.INDUSTRY),
            sourceName = rs.getString(Cols.SOURCE_NAME),
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
