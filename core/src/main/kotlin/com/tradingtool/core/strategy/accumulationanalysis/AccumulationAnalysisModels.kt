package com.tradingtool.core.strategy.accumulationanalysis

import com.tradingtool.core.strategy.chartinkevidence.ChartinkEvidenceSource
import java.time.LocalDate
import java.time.OffsetDateTime

enum class AccumulationShape { FLAT_GOLDEN, FLAT, CUP, DOWNWARD_DRIFT, UPWARD_DRIFT, UNCLASSIFIED, INVALID }
enum class AccumulationShapeDecision { VALID, NEEDS_REVIEW, INVALID }
enum class AccumulationRunStatus { RUNNING, COMPLETED, FAILED }
enum class AccumulationAnalysisPeriod {
    ONE_DAY,
    ONE_WEEK,
    ONE_MONTH,
    THREE_MONTHS,
    SIX_MONTHS,
    NINE_MONTHS,
    ;

    fun fromDate(toDate: LocalDate): LocalDate = when (this) {
        ONE_DAY -> toDate
        ONE_WEEK -> toDate.minusWeeks(1)
        ONE_MONTH -> toDate.minusMonths(1)
        THREE_MONTHS -> toDate.minusMonths(3)
        SIX_MONTHS -> toDate.minusMonths(6)
        NINE_MONTHS -> toDate.minusMonths(9)
    }
}

data class AccumulationAnalysisRunRequest(val universeKey: String, val period: AccumulationAnalysisPeriod)

data class AccumulationAnalysisRun(
    val id: Long,
    val universeKey: String,
    val period: AccumulationAnalysisPeriod,
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
    val valid: Boolean,
    val firstPhaseDDate: LocalDate?,
    val firstBreakoutDate: LocalDate?,
    val sessionsToPhaseD: Int?,
    val sessionsToBreakout: Int?,
    val details: String,
    val confirmationDates: AccumulationConfirmationDates = AccumulationConfirmationDates(),
    val curatedWatchlists: List<String> = emptyList(),
    val sixMonthEvidence: AccumulationEvidenceLane? = null,
    val shapeMetrics: AccumulationShapeMetrics? = null,
    val lineFit: AccumulationLineFitMetrics? = null,
    val goldenFlatNode: AccumulationGoldenFlatNode? = null,
    val shapeChunks: List<AccumulationShapeChunk> = emptyList(),
    val baseRhythm: AccumulationBaseRhythm? = null,
)

data class AccumulationShapeMetrics(
    val curvature: Double,
    val centerSlopePerTenSessions: Double,
    val startSlopePerTenSessions: Double,
    val endSlopePerTenSessions: Double,
    val vertexPosition: Double?,
)

data class AccumulationLineFitMetrics(
    val slopePerTenSessions: Double,
    val typicalDeviationPercent: Double,
    val maximumDeviationPercent: Double,
    val ignoredOutlierDate: LocalDate?,
    val ignoredOutlierDeviationPercent: Double?,
)

data class AccumulationGoldenFlatNode(
    val windowSessions: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val metrics: AccumulationShapeMetrics,
    val lineFit: AccumulationLineFitMetrics? = null,
)

data class AccumulationShapeChunk(
    val position: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val shape: AccumulationShape,
    val metrics: AccumulationShapeMetrics,
    val goldenFlat: Boolean,
    val lineFit: AccumulationLineFitMetrics? = null,
)

enum class AccumulationBaseRhythmDirection { FALLING, FLAT, RISING }
enum class AccumulationBaseRhythmState { CONTRACTING, STEADY, EXPANDING }

data class AccumulationBaseRhythm(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val blocks: List<AccumulationBaseRhythmBlock>,
)

data class AccumulationBaseRhythmBlock(
    val position: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val direction: AccumulationBaseRhythmDirection,
    val rangeState: AccumulationBaseRhythmState,
    val volumeState: AccumulationBaseRhythmState,
    val closeChangePercent: Double,
    val rangePercent: Double,
    val averageVolume: Double,
)

data class AccumulationConfirmationDates(
    val phaseD: List<LocalDate> = emptyList(),
    val freshBreakout: List<LocalDate> = emptyList(),
    val fiftyTwoWeekHigh: List<LocalDate> = emptyList(),
)

data class AccumulationEvidenceLane(
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val accumulation: List<LocalDate> = emptyList(),
    val phaseD: List<LocalDate> = emptyList(),
    val freshBreakout: List<LocalDate> = emptyList(),
    val fiftyTwoWeekHigh: List<LocalDate> = emptyList(),
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
    suspend fun createRun(request: AccumulationAnalysisRunRequest, fromDate: LocalDate, toDate: LocalDate, revision: OffsetDateTime, algorithmVersion: String): AccumulationAnalysisRun
    suspend fun replaceSnapshots(runId: Long, snapshots: List<AccumulationCaseSnapshot>)
    suspend fun completeRun(runId: Long)
    suspend fun failRun(runId: Long, message: String)
    suspend fun findRuns(): List<AccumulationAnalysisRun>
    suspend fun findRun(runId: Long): AccumulationAnalysisRun?
    suspend fun findLatestSnapshots(runId: Long): List<AccumulationCaseSnapshot>
    suspend fun findTimeline(runId: Long, symbol: String, chainStartDate: LocalDate?, chainEndDate: LocalDate?): List<AccumulationCaseSnapshot>
}
