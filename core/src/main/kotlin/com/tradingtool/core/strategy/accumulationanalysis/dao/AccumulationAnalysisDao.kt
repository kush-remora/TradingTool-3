package com.tradingtool.core.strategy.accumulationanalysis.dao

import com.tradingtool.core.strategy.accumulationanalysis.AccumulationAnalysisRun
import com.tradingtool.core.strategy.accumulationanalysis.AccumulationAnalysisPeriod
import com.tradingtool.core.strategy.accumulationanalysis.AccumulationCaseSnapshot
import com.tradingtool.core.strategy.accumulationanalysis.AccumulationRunStatus
import com.tradingtool.core.strategy.accumulationanalysis.AccumulationShape
import com.tradingtool.core.strategy.accumulationanalysis.AccumulationShapeDecision
import com.tradingtool.core.strategy.accumulationanalysis.AccumulationShapeMetrics
import com.tradingtool.core.strategy.accumulationanalysis.AccumulationLineFitMetrics
import com.tradingtool.core.strategy.accumulationanalysis.AccumulationGoldenFlatNode
import com.tradingtool.core.strategy.accumulationanalysis.AccumulationShapeChunk
import com.tradingtool.core.strategy.accumulationanalysis.AccumulationBaseRhythm
import com.tradingtool.core.strategy.accumulationanalysis.AccumulationBaseRhythmBlock
import com.tradingtool.core.strategy.accumulationanalysis.AccumulationBaseRhythmDirection
import com.tradingtool.core.strategy.accumulationanalysis.AccumulationBaseRhythmState
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
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

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

    @SqlQuery("SELECT * FROM accumulation_case_snapshots WHERE run_id = :runId AND symbol = :symbol AND (:chainStartDate IS NULL OR chain_start_date = :chainStartDate) AND (:chainEndDate IS NULL OR chain_end_date = :chainEndDate) ORDER BY as_of_date")
    fun findTimeline(@Bind("runId") runId: Long, @Bind("symbol") symbol: String, @Bind("chainStartDate") chainStartDate: LocalDate?, @Bind("chainEndDate") chainEndDate: LocalDate?): List<AccumulationCaseSnapshot>
}

interface AccumulationAnalysisWriteDao {
    @SqlUpdate("DELETE FROM accumulation_analysis_runs WHERE universe_key = :universeKey AND period_key = :period AND from_date = :fromDate AND to_date = :toDate")
    fun deleteRunScope(@Bind("universeKey") universeKey: String, @Bind("period") period: AccumulationAnalysisPeriod, @Bind("fromDate") fromDate: LocalDate, @Bind("toDate") toDate: LocalDate): Int

    @SqlUpdate("INSERT INTO accumulation_analysis_runs (universe_key, period_key, from_date, to_date, evidence_revision, algorithm_version, status, details) VALUES (:universeKey, :period, :fromDate, :toDate, :revision, :algorithmVersion, 'RUNNING', '{\"maxGapTradingSessions\":15,\"minimumHitCount\":1,\"shapeWindowSessions\":60}'::jsonb)")
    @GetGeneratedKeys
    fun createRun(@Bind("universeKey") universeKey: String, @Bind("period") period: AccumulationAnalysisPeriod, @Bind("fromDate") fromDate: LocalDate, @Bind("toDate") toDate: LocalDate, @Bind("revision") revision: OffsetDateTime, @Bind("algorithmVersion") algorithmVersion: String): Long

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
    override fun map(rs: ResultSet, ctx: StatementContext) = AccumulationAnalysisRun(rs.getLong("id"), rs.getString("universe_key"), AccumulationAnalysisPeriod.valueOf(rs.getString("period_key")), rs.getDate("from_date").toLocalDate(), rs.getDate("to_date").toLocalDate(), rs.getObject("evidence_revision", OffsetDateTime::class.java), rs.getString("algorithm_version"), AccumulationRunStatus.valueOf(rs.getString("status")), rs.getString("details"), rs.getObject("started_at", OffsetDateTime::class.java), rs.getObject("completed_at", OffsetDateTime::class.java))
}
class AccumulationCaseSnapshotMapper : RowMapper<AccumulationCaseSnapshot> {
    override fun map(rs: ResultSet, ctx: StatementContext): AccumulationCaseSnapshot {
        val details = rs.getString("details")
        return AccumulationCaseSnapshot(rs.getLong("run_id"), rs.getString("symbol"), rs.getDate("chain_start_date").toLocalDate(), rs.getDate("chain_end_date").toLocalDate(), rs.getDate("as_of_date").toLocalDate(), rs.getInt("chain_length_sessions"), rs.getInt("hit_count"), AccumulationShape.valueOf(rs.getString("shape")), AccumulationShapeDecision.valueOf(rs.getString("shape_decision")), rs.getBoolean("valid"), rs.getDate("first_phase_d_date")?.toLocalDate(), rs.getDate("first_breakout_date")?.toLocalDate(), rs.getObject("sessions_to_phase_d") as Int?, rs.getObject("sessions_to_breakout") as Int?, details, confirmationDatesFrom(details), shapeMetrics = shapeMetricsFrom(details), lineFit = lineFitFrom(details), goldenFlatNode = goldenFlatNodeFrom(details), shapeChunks = shapeChunksFrom(details), baseRhythm = baseRhythmFrom(details))
    }
}

internal fun confirmationDatesFrom(details: String): com.tradingtool.core.strategy.accumulationanalysis.AccumulationConfirmationDates {
    val node = objectMapper.readTree(details)
    fun dates(key: String) = node.path(key).mapNotNull(::localDateFromNode)
    return com.tradingtool.core.strategy.accumulationanalysis.AccumulationConfirmationDates(
        phaseD = dates("phaseDDates"),
        freshBreakout = dates("freshBreakoutDates"),
        fiftyTwoWeekHigh = dates("fiftyTwoWeekHighDates"),
    )
}

internal fun shapeMetricsFrom(details: String): AccumulationShapeMetrics? {
    return shapeMetricsFromNode(objectMapper.readTree(details).path("regression"))
}

internal fun lineFitFrom(details: String): AccumulationLineFitMetrics? =
    lineFitFromNode(objectMapper.readTree(details).path("lineFit"))

internal fun goldenFlatNodeFrom(details: String): AccumulationGoldenFlatNode? {
    val node = objectMapper.readTree(details).path("goldenFlatNode")
    if (!node.isObject) return null
    val sessions = node.path("windowSessions").takeIf { it.isInt }?.asInt() ?: return null
    val startDate = localDateFromNode(node.path("startDate")) ?: return null
    val endDate = localDateFromNode(node.path("endDate")) ?: return null
    val metrics = shapeMetricsFromNode(node.path("metrics")) ?: return null
    return AccumulationGoldenFlatNode(sessions, startDate, endDate, metrics, lineFitFromNode(node.path("lineFit")))
}

internal fun shapeChunksFrom(details: String): List<AccumulationShapeChunk> =
    objectMapper.readTree(details).path("latestShapeChunks").mapNotNull { node ->
        val position = node.path("position").takeIf { it.isInt }?.asInt() ?: return@mapNotNull null
        val startDate = localDateFromNode(node.path("startDate")) ?: return@mapNotNull null
        val endDate = localDateFromNode(node.path("endDate")) ?: return@mapNotNull null
        val shape = node.path("shape").asText().let { runCatching { AccumulationShape.valueOf(it) }.getOrNull() } ?: return@mapNotNull null
        val metrics = shapeMetricsFromNode(node.path("metrics")) ?: return@mapNotNull null
        AccumulationShapeChunk(position, startDate, endDate, shape, metrics, node.path("goldenFlat").asBoolean(false), lineFitFromNode(node.path("lineFit")))
    }

internal fun baseRhythmFrom(details: String): AccumulationBaseRhythm? {
    val node = objectMapper.readTree(details).path("baseRhythm")
    val startDate = localDateFromNode(node.path("startDate")) ?: return null
    val endDate = localDateFromNode(node.path("endDate")) ?: return null
    val blocks = node.path("blocks").mapNotNull { block ->
        val position = block.path("position").takeIf { it.isInt }?.asInt() ?: return@mapNotNull null
        val blockStartDate = localDateFromNode(block.path("startDate")) ?: return@mapNotNull null
        val blockEndDate = localDateFromNode(block.path("endDate")) ?: return@mapNotNull null
        val direction = block.path("direction").asText().let { value -> runCatching { AccumulationBaseRhythmDirection.valueOf(value) }.getOrNull() } ?: return@mapNotNull null
        val rangeState = block.path("rangeState").asText().let { value -> runCatching { AccumulationBaseRhythmState.valueOf(value) }.getOrNull() } ?: return@mapNotNull null
        val volumeState = block.path("volumeState").asText().let { value -> runCatching { AccumulationBaseRhythmState.valueOf(value) }.getOrNull() } ?: return@mapNotNull null
        fun number(name: String) = block.path(name).takeIf { it.isNumber }?.asDouble()
        val closeChangePercent = number("closeChangePercent") ?: return@mapNotNull null
        val rangePercent = number("rangePercent") ?: return@mapNotNull null
        val averageVolume = number("averageVolume") ?: return@mapNotNull null
        AccumulationBaseRhythmBlock(position, blockStartDate, blockEndDate, direction, rangeState, volumeState, closeChangePercent, rangePercent, averageVolume)
    }
    return AccumulationBaseRhythm(startDate, endDate, blocks)
}

private fun shapeMetricsFromNode(node: com.fasterxml.jackson.databind.JsonNode): AccumulationShapeMetrics? {
    if (!node.isObject) return null
    fun number(name: String): Double? = node.path(name).takeIf { it.isNumber }?.asDouble()
    val curvature = number("curvature") ?: return null
    val centerSlope = number("centerSlopePerTenSessions") ?: return null
    val startSlope = number("startSlopePerTenSessions") ?: return null
    val endSlope = number("endSlopePerTenSessions") ?: return null
    return AccumulationShapeMetrics(curvature, centerSlope, startSlope, endSlope, number("vertexPosition"))
}

private fun lineFitFromNode(node: com.fasterxml.jackson.databind.JsonNode): AccumulationLineFitMetrics? {
    if (!node.isObject) return null
    fun number(name: String): Double? = node.path(name).takeIf { it.isNumber }?.asDouble()
    val slope = number("slopePerTenSessions") ?: return null
    val typicalDeviation = number("typicalDeviationPercent") ?: return null
    val maximumDeviation = number("maximumDeviationPercent") ?: return null
    val ignoredDate = localDateFromNode(node.path("ignoredOutlierDate"))
    return AccumulationLineFitMetrics(slope, typicalDeviation, maximumDeviation, ignoredDate, number("ignoredOutlierDeviationPercent"))
}

private fun localDateFromNode(node: com.fasterxml.jackson.databind.JsonNode): LocalDate? = when {
    node.isTextual -> node.asText().trim().takeIf(String::isNotEmpty)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    node.isArray && node.size() == 3 && node.all { it.isInt } -> runCatching { LocalDate.of(node[0].asInt(), node[1].asInt(), node[2].asInt()) }.getOrNull()
    else -> null
}

private val objectMapper = jacksonObjectMapper().findAndRegisterModules()

class AnalysisEvidenceEventMapper : RowMapper<AnalysisEvidenceEvent> {
    override fun map(rs: ResultSet, ctx: StatementContext) = AnalysisEvidenceEvent(ChartinkEvidenceSource.valueOf(rs.getString("source")), rs.getDate("event_date").toLocalDate(), rs.getString("symbol"))
}
