package com.tradingtool.core.strategy.rsimomentum.dao

import com.tradingtool.core.constants.DatabaseConstants.Tables
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime

@RegisterRowMapper(RsiMomentumSnapshotDailyMapper::class)
interface RsiMomentumSnapshotReadDao {

    @SqlQuery(
        """
        SELECT profile_id, as_of_date, run_at, snapshot_json
        FROM public.${Tables.RSI_MOMENTUM_SNAPSHOT_DAILY}
        WHERE profile_id = :profileId
          AND as_of_date BETWEEN :from AND :to
        ORDER BY as_of_date ASC
        """
    )
    fun listByProfileAndDateRange(
        @Bind("profileId") profileId: String,
        @Bind("from") from: LocalDate,
        @Bind("to") to: LocalDate,
    ): List<RsiMomentumSnapshotDailyRecord>

    @SqlQuery(
        """
        SELECT profile_id, as_of_date, run_at, snapshot_json
        FROM public.${Tables.RSI_MOMENTUM_SNAPSHOT_DAILY}
        WHERE profile_id = :profileId
          AND as_of_date = :asOfDate
        LIMIT 1
        """
    )
    fun getByProfileAndDate(
        @Bind("profileId") profileId: String,
        @Bind("asOfDate") asOfDate: LocalDate,
    ): RsiMomentumSnapshotDailyRecord?

    @SqlQuery(
        """
        SELECT profile_id, as_of_date, run_at, snapshot_json
        FROM public.${Tables.RSI_MOMENTUM_SNAPSHOT_DAILY}
        WHERE profile_id = :profileId
          AND as_of_date <= :asOfDate
        ORDER BY as_of_date DESC
        LIMIT 1
        """
    )
    fun getLatestOnOrBefore(
        @Bind("profileId") profileId: String,
        @Bind("asOfDate") asOfDate: LocalDate,
    ): RsiMomentumSnapshotDailyRecord?
}

class RsiMomentumSnapshotDailyMapper : RowMapper<RsiMomentumSnapshotDailyRecord> {
    override fun map(rs: ResultSet, ctx: StatementContext) = RsiMomentumSnapshotDailyRecord(
        profileId = rs.getString("profile_id"),
        asOfDate = rs.getDate("as_of_date").toLocalDate(),
        runAt = rs.getObject("run_at", OffsetDateTime::class.java),
        snapshotJson = extractJsonString(rs, "snapshot_json"),
    )

    private fun extractJsonString(rs: ResultSet, column: String): String =
        try {
            rs.getString(column)?.takeIf { it.isNotEmpty() } ?: "{}"
        } catch (_: Exception) {
            rs.getObject(column)?.toString() ?: "{}"
        }
}
