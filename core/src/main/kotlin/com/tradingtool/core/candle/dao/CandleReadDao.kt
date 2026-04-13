package com.tradingtool.core.candle.dao

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.candle.IntradayCandle
import com.tradingtool.core.constants.DatabaseConstants.Tables
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import java.sql.ResultSet
import java.time.LocalDate
import java.time.LocalDateTime

@RegisterRowMapper(DailyCandleMapper::class)
@RegisterRowMapper(IntradayCandleMapper::class)
interface CandleReadDao {

    @SqlQuery(
        """
        SELECT * FROM public.${Tables.DAILY_CANDLES}
        WHERE instrument_token = :token
          AND candle_date BETWEEN :from AND :to
        ORDER BY candle_date
        """
    )
    fun getDailyCandles(
        @Bind("token") token: Long,
        @Bind("from") from: LocalDate,
        @Bind("to") to: LocalDate,
    ): List<DailyCandle>

    /**
     * Returns Monday morning candles (09:15–09:45 IST) in [from, to] range.
     * Used for Monday dip calculation in the weekly pattern screener.
     * ISODOW = 1 → Monday.
     */
    @SqlQuery(
        """
        SELECT * FROM public.${Tables.INTRADAY_CANDLES}
        WHERE instrument_token = :token
          AND interval = :interval
          AND candle_timestamp BETWEEN :from AND :to
          AND EXTRACT(ISODOW FROM candle_timestamp) = 1
          AND candle_timestamp::time >= '09:15:00'
          AND candle_timestamp::time <= '09:45:00'
        ORDER BY candle_timestamp
        """
    )
    fun getMondayMorningCandles(
        @Bind("token") token: Long,
        @Bind("interval") interval: String,
        @Bind("from") from: LocalDateTime,
        @Bind("to") to: LocalDateTime,
    ): List<IntradayCandle>

    /**
     * Returns Thursday daily candles in [from, to] range.
     * ISODOW = 4 → Thursday.
     */
    @SqlQuery(
        """
        SELECT * FROM public.${Tables.DAILY_CANDLES}
        WHERE instrument_token = :token
          AND candle_date BETWEEN :from AND :to
          AND EXTRACT(ISODOW FROM candle_date) = 4
        ORDER BY candle_date
        """
    )
    fun getThursdayCandles(
        @Bind("token") token: Long,
        @Bind("from") from: LocalDate,
        @Bind("to") to: LocalDate,
    ): List<DailyCandle>

    @SqlQuery(
        """
        SELECT * FROM public.${Tables.DAILY_CANDLES}
        WHERE instrument_token = :token
        ORDER BY candle_date DESC
        LIMIT :limit
        """
    )
    fun getRecentDailyCandles(
        @Bind("token") token: Long,
        @Bind("limit") limit: Int,
    ): List<DailyCandle>

    @SqlQuery(
        """
        SELECT DISTINCT candle_date
        FROM public.${Tables.DAILY_CANDLES}
        WHERE candle_date BETWEEN :from AND :to
        ORDER BY candle_date
        """
    )
    fun getDistinctTradingDates(
        @Bind("from") from: LocalDate,
        @Bind("to") to: LocalDate,
    ): List<LocalDate>

    @SqlQuery(
        """
        SELECT * FROM public.${Tables.INTRADAY_CANDLES}
        WHERE instrument_token = :token
          AND interval = :interval
          AND candle_timestamp BETWEEN :from AND :to
        ORDER BY candle_timestamp
        """
    )
    fun getIntradayCandles(
        @Bind("token") token: Long,
        @Bind("interval") interval: String,
        @Bind("from") from: LocalDateTime,
        @Bind("to") to: LocalDateTime,
    ): List<IntradayCandle>
}

class DailyCandleMapper : RowMapper<DailyCandle> {
    override fun map(rs: ResultSet, ctx: StatementContext) = DailyCandle(
        instrumentToken = rs.getLong("instrument_token"),
        symbol = rs.getString("symbol"),
        candleDate = rs.getDate("candle_date").toLocalDate(),
        open = rs.getDouble("open"),
        high = rs.getDouble("high"),
        low = rs.getDouble("low"),
        close = rs.getDouble("close"),
        volume = rs.getLong("volume"),
    )
}

class IntradayCandleMapper : RowMapper<IntradayCandle> {
    override fun map(rs: ResultSet, ctx: StatementContext) = IntradayCandle(
        instrumentToken = rs.getLong("instrument_token"),
        symbol = rs.getString("symbol"),
        interval = rs.getString("interval"),
        candleTimestamp = rs.getTimestamp("candle_timestamp").toLocalDateTime(),
        open = rs.getDouble("open"),
        high = rs.getDouble("high"),
        low = rs.getDouble("low"),
        close = rs.getDouble("close"),
        volume = rs.getLong("volume"),
    )
}
