package com.tradingtool.core.volumeshocker.groww.dao

import com.tradingtool.core.constants.DatabaseConstants.GrowwVolumeShockerColumns as Cols
import com.tradingtool.core.constants.DatabaseConstants.Tables
import com.tradingtool.core.volumeshocker.groww.GrowwVolumeShockerDailyRow
import com.tradingtool.core.volumeshocker.groww.MarketCapCategory
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.customizer.BindList
import org.jdbi.v3.sqlobject.statement.SqlQuery
import java.sql.ResultSet
import java.math.BigDecimal
import java.time.LocalDate

@RegisterRowMapper(GrowwVolumeShockerDailyRowMapper::class)
interface GrowwVolumeShockerReadDao {

    @SqlQuery(
        """
        SELECT COUNT(*)
        FROM public.${Tables.GROWW_VOLUME_SHOCKER_DAILY}
        WHERE ${Cols.TRADE_DATE} = :tradeDate
        """,
    )
    fun countByTradeDate(@Bind("tradeDate") tradeDate: LocalDate): Int

    @SqlQuery(
        """
        SELECT DISTINCT ${Cols.TRADE_DATE}
        FROM public.${Tables.GROWW_VOLUME_SHOCKER_DAILY}
        ORDER BY ${Cols.TRADE_DATE} DESC
        """,
    )
    fun listAvailableTradeDates(): List<LocalDate>

    @SqlQuery(
        """
        SELECT *
        FROM public.${Tables.GROWW_VOLUME_SHOCKER_DAILY}
        WHERE ${Cols.TRADE_DATE} = :tradeDate
        ORDER BY ${Cols.SOURCE_RANK}
        """,
    )
    fun findByTradeDate(@Bind("tradeDate") tradeDate: LocalDate): List<GrowwVolumeShockerDailyRow>

    @SqlQuery(
        """
        SELECT *
        FROM public.${Tables.GROWW_VOLUME_SHOCKER_DAILY}
        WHERE ${Cols.TRADE_DATE} = :tradeDate
          AND ${Cols.SYMBOL} = :symbol
        LIMIT 1
        """,
    )
    fun findByTradeDateAndSymbol(
        @Bind("tradeDate") tradeDate: LocalDate,
        @Bind("symbol") symbol: String,
    ): GrowwVolumeShockerDailyRow?

    @SqlQuery(
        """
        SELECT *
        FROM public.${Tables.GROWW_VOLUME_SHOCKER_DAILY}
        WHERE ${Cols.TRADE_DATE} IN (<tradeDates>)
        ORDER BY ${Cols.TRADE_DATE}, ${Cols.SOURCE_RANK}
        """,
    )
    fun findByTradeDates(@BindList("tradeDates") tradeDates: List<LocalDate>): List<GrowwVolumeShockerDailyRow>
}

class GrowwVolumeShockerDailyRowMapper : RowMapper<GrowwVolumeShockerDailyRow> {
    override fun map(rs: ResultSet, ctx: StatementContext): GrowwVolumeShockerDailyRow {
        return GrowwVolumeShockerDailyRow(
            tradeDate = rs.getDate(Cols.TRADE_DATE).toLocalDate(),
            sourceRank = rs.getInt(Cols.SOURCE_RANK),
            exchange = rs.getString(Cols.EXCHANGE),
            symbol = rs.getString(Cols.SYMBOL),
            instrumentToken = rs.getLong(Cols.INSTRUMENT_TOKEN),
            companyName = rs.getString(Cols.COMPANY_NAME),
            ltp = rs.getBigDecimal(Cols.LTP) ?: BigDecimal.ZERO,
            close = rs.getBigDecimal(Cols.CLOSE) ?: BigDecimal.ZERO,
            marketCapCrore = rs.getBigDecimal(Cols.MARKET_CAP_CRORE) ?: BigDecimal.ZERO,
            marketCapCategory = MarketCapCategory.valueOf(rs.getString(Cols.MARKET_CAP_CATEGORY)),
            yearLow = rs.getBigDecimal(Cols.YEAR_LOW) ?: BigDecimal.ZERO,
            yearHigh = rs.getBigDecimal(Cols.YEAR_HIGH) ?: BigDecimal.ZERO,
            volume = rs.getLong(Cols.VOLUME),
            weeklyAverageVolume = rs.getBigDecimal(Cols.WEEKLY_AVERAGE_VOLUME) ?: BigDecimal.ZERO,
        )
    }
}
