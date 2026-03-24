package com.tradingtool.core.strategy.remora

import com.tradingtool.core.constants.DatabaseConstants.Tables
import com.tradingtool.core.model.remora.RemoraSignal
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import java.sql.ResultSet
import java.time.OffsetDateTime

@RegisterRowMapper(RemoraSignalMapper::class)
interface RemoraSignalReadDao {

    @SqlQuery("SELECT * FROM public.${Tables.REMORA_SIGNALS} ORDER BY signal_date DESC, consecutive_days DESC, volume_ratio DESC")
    fun findAll(): List<RemoraSignal>

    @SqlQuery("SELECT * FROM public.${Tables.REMORA_SIGNALS} WHERE signal_type = :type ORDER BY signal_date DESC, consecutive_days DESC, volume_ratio DESC")
    fun findByType(@Bind("type") type: String): List<RemoraSignal>

    @SqlQuery("SELECT * FROM public.${Tables.REMORA_SIGNALS} WHERE signal_date = CURRENT_DATE ORDER BY consecutive_days DESC, volume_ratio DESC")
    fun findToday(): List<RemoraSignal>

    @SqlQuery("SELECT * FROM public.${Tables.REMORA_SIGNALS} WHERE signal_date >= CURRENT_DATE - INTERVAL ':days days' ORDER BY signal_date DESC, consecutive_days DESC")
    fun findRecent(@Bind("days") days: Int): List<RemoraSignal>
}

class RemoraSignalMapper : RowMapper<RemoraSignal> {
    override fun map(rs: ResultSet, ctx: StatementContext): RemoraSignal {
        return RemoraSignal(
            id = rs.getInt("id"),
            stockId = rs.getInt("stock_id"),
            symbol = rs.getString("symbol"),
            companyName = rs.getString("company_name"),
            exchange = rs.getString("exchange"),
            signalType = rs.getString("signal_type"),
            volumeRatio = rs.getDouble("volume_ratio"),
            priceChangePct = rs.getDouble("price_change_pct"),
            consecutiveDays = rs.getInt("consecutive_days"),
            signalDate = rs.getDate("signal_date").toLocalDate().toString(),
            computedAt = rs.getObject("computed_at", OffsetDateTime::class.java).toInstant().toString(),
        )
    }
}
