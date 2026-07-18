package com.tradingtool.core.strategy.accumulationanalysis

import com.tradingtool.core.strategy.chartinkevidence.ChartinkEvidenceSource
import java.time.LocalDate
import java.time.OffsetDateTime

enum class AccumulationShape { FLAT, CUP, DOWNWARD_DRIFT, UNCLASSIFIED, INVALID }
enum class AccumulationShapeDecision { VALID, NEEDS_REVIEW, INVALID }
enum class AccumulationRunStatus { RUNNING, COMPLETED, FAILED }

data class AccumulationAnalysisRunRequest(val universeKey: String, val months: Int)

data class AccumulationAnalysisRun(
    val id: Long,
    val universeKey: String,
    val months: Int,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val evidenceRevision: OffsetDateTime,
    val algorithmVersion: String,
    val status: AccumulationRunStatus,
    val details: String,
    val startedAt: OffsetDateTime,
    val completedAt: OffsetDateTime?,
)

data class AccumulationCaseSnapshot(
    val runId: Long,
    val symbol: String,
    val chainStartDate: LocalDate,
    val chainEndDate: LocalDate,
    val asOfDate: LocalDate,
    val chainLengthSessions: Int,
    val hitCount: Int,
    val shape: AccumulationShape,
    val shapeDecision: AccumulationShapeDecision,
    val isValid: Boolean,
    val firstPhaseDDate: LocalDate?,
    val firstBreakoutDate: LocalDate?,
    val sessionsToPhaseD: Int?,
    val sessionsToBreakout: Int?,
    val details: String,
)

data class AccumulationAnalysisSummary(
    val run: AccumulationAnalysisRun,
    val isStale: Boolean,
    val rows: List<AccumulationCaseSnapshot>,
)

data class AccumulationAnalysisTimeline(val run: AccumulationAnalysisRun, val isStale: Boolean, val rows: List<AccumulationCaseSnapshot>)

data class AnalysisEvidenceEvent(val source: ChartinkEvidenceSource, val eventDate: LocalDate, val symbol: String)

interface AccumulationAnalysisStore {
    suspend fun latestAccumulationDate(universeKey: String): LocalDate?
    suspend fun evidenceRevision(universeKey: String): OffsetDateTime?
    suspend fun findEvidence(universeKey: String, toDate: LocalDate): List<AnalysisEvidenceEvent>
    suspend fun createRun(request: AccumulationAnalysisRunRequest, fromDate: LocalDate, toDate: LocalDate, revision: OffsetDateTime): AccumulationAnalysisRun
    suspend fun replaceSnapshots(runId: Long, snapshots: List<AccumulationCaseSnapshot>)
    suspend fun completeRun(runId: Long)
    suspend fun failRun(runId: Long, message: String)
    suspend fun findRuns(): List<AccumulationAnalysisRun>
    suspend fun findRun(runId: Long): AccumulationAnalysisRun?
    suspend fun findLatestSnapshots(runId: Long): List<AccumulationCaseSnapshot>
    suspend fun findTimeline(runId: Long, symbol: String): List<AccumulationCaseSnapshot>
}
