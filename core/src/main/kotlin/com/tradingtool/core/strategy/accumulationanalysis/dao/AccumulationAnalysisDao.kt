package com.tradingtool.core.strategy.accumulationanalysis.dao

import com.tradingtool.core.strategy.accumulationanalysis.AccumulationAnalysisRun
import com.tradingtool.core.strategy.accumulationanalysis.AccumulationCaseSnapshot
import com.tradingtool.core.strategy.accumulationanalysis.AccumulationRunStatus
import com.tradingtool.core.strategy.accumulationanalysis.AccumulationShape
import com.tradingtool.core.strategy.accumulationanalysis.AccumulationShapeDecision
import com.tradingtool.core.strategy.accumulationanalysis.AnalysisEvidenceEvent
import com.tradingtool.core.strategy.chartinkevidence.ChartinkEvidenceSource
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.customizer.BindBean
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys
import org.jdbi.v3.sqlobject.statement.SqlBatch
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime

@RegisterRowMapper(AccumulationAnalysisRunMapper::class)
@RegisterRowMapper(AccumulationCaseSnapshotMapper::class)
@RegisterRowMapper(AnalysisEvidenceEventMapper::class)
interface AccumulationAnalysisReadDao {
    @SqlQuery("SELECT MAX(event_date) FROM chartink_scan_events WHERE source = 'ACCUMULATION' AND universe_key = :universeKey")
    fun latestAccumulationDate(@Bind("universeKey") universeKey: String): LocalDate?

    @SqlQuery("SELECT MAX(uploaded_at) FROM chartink_scan_events WHERE source = 'ACCUMULATION' AND universe_key = :universeKey")
    fun evidenceRevision(@Bind("universeKey") universeKey: String): OffsetDateTime?

    @SqlQuery("SELECT source, event_date, symbol FROM chartink_scan_events WHERE universe_key = :universeKey AND event_date <= :toDate ORDER BY symbol, event_date")
    fun findEvidence(@Bind("universeKey") universeKey: String, @Bind("toDate") toDate: LocalDate): List<AnalysisEvidenceEvent>

    @SqlQuery("SELECT * FROM accumulation_analysis_runs ORDER BY started_at DESC")
    fun findRuns(): List<AccumulationAnalysisRun>

    @SqlQuery("SELECT * FROM accumulation_analysis_runs WHERE id = :runId")
    fun findRun(@Bind("runId") runId: Long): AccumulationAnalysisRun?

    @SqlQuery("SELECT DISTINCT ON (symbol, chain_start_date, chain_end_date) * FROM accumulation_case_snapshots WHERE run_id = :runId ORDER BY symbol, chain_start_date, chain_end_date, as_of_date DESC")
    fun findLatestSnapshots(@Bind("runId") runId: Long): List<AccumulationCaseSnapshot>

    @SqlQuery("SELECT * FROM accumulation_case_snapshots WHERE run_id = :runId AND symbol = :symbol ORDER BY as_of_date")
    fun findTimeline(@Bind("runId") runId: Long, @Bind("symbol") symbol: String): List<AccumulationCaseSnapshot>
}

interface AccumulationAnalysisWriteDao {
    @SqlUpdate("DELETE FROM accumulation_analysis_runs WHERE universe_key = :universeKey AND months = :months AND from_date = :fromDate AND to_date = :toDate")
    fun deleteRunScope(@Bind("universeKey") universeKey: String, @Bind("months") months: Int, @Bind("fromDate") fromDate: LocalDate, @Bind("toDate") toDate: LocalDate): Int

    @SqlUpdate("INSERT INTO accumulation_analysis_runs (universe_key, months, from_date, to_date, evidence_revision, algorithm_version, status, details) VALUES (:universeKey, :months, :fromDate, :toDate, :revision, 'v1-bhel-calibrated', 'RUNNING', '{\"maxGapTradingSessions\":15,\"minimumHitCount\":2}'::jsonb)")
    @GetGeneratedKeys
    fun createRun(@Bind("universeKey") universeKey: String, @Bind("months") months: Int, @Bind("fromDate") fromDate: LocalDate, @Bind("toDate") toDate: LocalDate, @Bind("revision") revision: OffsetDateTime): Long

    @SqlUpdate("DELETE FROM accumulation_case_snapshots WHERE run_id = :runId")
    fun deleteSnapshots(@Bind("runId") runId: Long): Int

    @SqlBatch("INSERT INTO accumulation_case_snapshots (run_id, symbol, chain_start_date, chain_end_date, as_of_date, chain_length_sessions, hit_count, shape, shape_decision, valid, first_phase_d_date, first_breakout_date, sessions_to_phase_d, sessions_to_breakout, details) VALUES (:runId, :symbol, :chainStartDate, :chainEndDate, :asOfDate, :chainLengthSessions, :hitCount, :shape, :shapeDecision, :valid, :firstPhaseDDate, :firstBreakoutDate, :sessionsToPhaseD, :sessionsToBreakout, CAST(:details AS jsonb))")
    fun insertSnapshots(@BindBean snapshots: List<AccumulationCaseSnapshot>): IntArray

    @SqlUpdate("UPDATE accumulation_analysis_runs SET status = 'COMPLETED', completed_at = NOW() WHERE id = :runId")
    fun completeRun(@Bind("runId") runId: Long): Int

    @SqlUpdate("UPDATE accumulation_analysis_runs SET status = 'FAILED', details = CAST(:message AS jsonb), completed_at = NOW() WHERE id = :runId")
    fun failRun(@Bind("runId") runId: Long, @Bind("message") message: String): Int
}

class AccumulationAnalysisRunMapper : RowMapper<AccumulationAnalysisRun> {
    override fun map(rs: ResultSet, ctx: StatementContext) = AccumulationAnalysisRun(rs.getLong("id"), rs.getString("universe_key"), rs.getInt("months"), rs.getDate("from_date").toLocalDate(), rs.getDate("to_date").toLocalDate(), rs.getObject("evidence_revision", OffsetDateTime::class.java), "v1-bhel-calibrated", AccumulationRunStatus.valueOf(rs.getString("status")), rs.getString("details"), rs.getObject("started_at", OffsetDateTime::class.java), rs.getObject("completed_at", OffsetDateTime::class.java))
}
class AccumulationCaseSnapshotMapper : RowMapper<AccumulationCaseSnapshot> {
    override fun map(rs: ResultSet, ctx: StatementContext) = AccumulationCaseSnapshot(rs.getLong("run_id"), rs.getString("symbol"), rs.getDate("chain_start_date").toLocalDate(), rs.getDate("chain_end_date").toLocalDate(), rs.getDate("as_of_date").toLocalDate(), rs.getInt("chain_length_sessions"), rs.getInt("hit_count"), AccumulationShape.valueOf(rs.getString("shape")), AccumulationShapeDecision.valueOf(rs.getString("shape_decision")), rs.getBoolean("valid"), rs.getDate("first_phase_d_date")?.toLocalDate(), rs.getDate("first_breakout_date")?.toLocalDate(), rs.getObject("sessions_to_phase_d") as Int?, rs.getObject("sessions_to_breakout") as Int?, rs.getString("details"))
}
class AnalysisEvidenceEventMapper : RowMapper<AnalysisEvidenceEvent> {
    override fun map(rs: ResultSet, ctx: StatementContext) = AnalysisEvidenceEvent(ChartinkEvidenceSource.valueOf(rs.getString("source")), rs.getDate("event_date").toLocalDate(), rs.getString("symbol"))
}
