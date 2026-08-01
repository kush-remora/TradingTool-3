package com.tradingtool.core.strategy.accumulationanalysis

import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.strategy.accumulationanalysis.dao.AccumulationAnalysisReadDao
import com.tradingtool.core.strategy.accumulationanalysis.dao.AccumulationAnalysisWriteDao
import com.tradingtool.core.strategy.chartinkevidence.ChartinkEvidenceSource
import com.tradingtool.core.strategy.chartinkevidence.ChartinkUniverseMembershipStore
import com.tradingtool.core.strategy.chartinkevidence.IndexMembership
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import java.time.LocalDate
import java.time.OffsetDateTime

typealias AccumulationAnalysisJdbiHandler = JdbiHandler<AccumulationAnalysisReadDao, AccumulationAnalysisWriteDao>

class JdbiAccumulationAnalysisStore(private val handler: AccumulationAnalysisJdbiHandler) : AccumulationAnalysisStore {
    override suspend fun latestAccumulationDate(universeKey: String) = handler.read { it.latestAccumulationDate(universeKey) }
    override suspend fun evidenceRevision(universeKey: String) = handler.read { it.evidenceRevision(universeKey) }
    override suspend fun findEvidence(universeKey: String, toDate: LocalDate) = handler.read { it.findEvidence(universeKey, toDate) }
    override suspend fun createRun(request: AccumulationAnalysisRunRequest, fromDate: LocalDate, toDate: LocalDate, revision: OffsetDateTime, algorithmVersion: String): AccumulationAnalysisRun = handler.transaction { read, write -> write.deleteRunScope(request.universeKey, request.period, fromDate, toDate); read.findRun(write.createRun(request.universeKey, request.period, fromDate, toDate, revision, algorithmVersion))!! }
    override suspend fun replaceSnapshots(runId: Long, snapshots: List<AccumulationCaseSnapshot>) { handler.transaction { _, write -> write.deleteSnapshots(runId); if (snapshots.isNotEmpty()) write.insertSnapshots(snapshots) } }
    override suspend fun completeRun(runId: Long) { handler.write { it.completeRun(runId) } }
    override suspend fun failRun(runId: Long, message: String) { handler.write { it.failRun(runId, "{\"error\":\"analysis_failed\"}") } }
    override suspend fun findRuns() = handler.read { it.findRuns() }
    override suspend fun findRun(runId: Long) = handler.read { it.findRun(runId) }
    override suspend fun findLatestSnapshots(runId: Long) = handler.read { it.findLatestSnapshots(runId) }
    override suspend fun findTimeline(runId: Long, symbol: String, chainStartDate: LocalDate?, chainEndDate: LocalDate?) = handler.read { it.findTimeline(runId, symbol, chainStartDate, chainEndDate) }
}

class AccumulationAnalysisService(private val store: AccumulationAnalysisStore, private val candleCacheService: CandleCacheService, private val membershipStore: ChartinkUniverseMembershipStore, private val engine: AccumulationShapeEngine = AccumulationShapeEngine()) {
    suspend fun run(request: AccumulationAnalysisRunRequest): AccumulationAnalysisRun {
        require(request.universeKey in UNIVERSES) { "Unsupported universe." }
        val toDate = requireNotNull(store.latestAccumulationDate(request.universeKey)) { "No Accumulation evidence uploaded for this universe." }
        val revision = requireNotNull(store.evidenceRevision(request.universeKey)) { "No Accumulation evidence uploaded for this universe." }
        val run = store.createRun(request, request.period.fromDate(toDate), toDate, revision, engine.algorithmVersion)
        try {
            val evidence = store.findEvidence(request.universeKey, toDate)
            val candidates = evidence.filter { it.source == ChartinkEvidenceSource.ACCUMULATION && !it.eventDate.isBefore(run.fromDate) }.map { it.symbol }.distinct()
            val snapshots = candidates.flatMap { symbol -> buildSnapshots(symbol, evidence.filter { it.symbol == symbol }, run) }
            store.replaceSnapshots(run.id, snapshots)
            store.completeRun(run.id)
            return requireNotNull(store.findRun(run.id))
        } catch (error: Exception) {
            store.failRun(run.id, error.message ?: "Analysis failed")
            throw error
        }
    }

    suspend fun runs(): List<AccumulationAnalysisRun> = store.findRuns()
    suspend fun summary(runId: Long): AccumulationAnalysisSummary {
        val run = requireNotNull(store.findRun(runId)) { "Run not found." }
        val snapshots = store.findLatestSnapshots(runId).sortedWith(compareBy<AccumulationCaseSnapshot> { !it.valid }.thenBy { it.shape.ordinal }.thenByDescending { it.chainLengthSessions })
        return AccumulationAnalysisSummary(run, isStale(run), enrichSnapshots(withUnclassifiedShape(snapshots, run), run))
    }
    suspend fun timeline(runId: Long, symbol: String, chainStartDate: LocalDate?, chainEndDate: LocalDate?): AccumulationAnalysisTimeline {
        val run = requireNotNull(store.findRun(runId)) { "Run not found." }
        return AccumulationAnalysisTimeline(run, isStale(run), enrichSnapshots(withUnclassifiedShape(store.findTimeline(runId, symbol, chainStartDate, chainEndDate), run), run))
    }

    private suspend fun buildSnapshots(symbol: String, events: List<AnalysisEvidenceEvent>, run: AccumulationAnalysisRun): List<AccumulationCaseSnapshot> {
        val earliest = events.minOfOrNull { it.eventDate } ?: return emptyList()
        val candles = candleCacheService.getDailyCandles(symbol, earliest.minusDays(SHAPE_HISTORY_CALENDAR_DAYS), run.toDate)
        require(candles.isNotEmpty()) { "Missing Kite daily candles for $symbol." }
        val chains = engine.buildChains(events.filter { it.source == ChartinkEvidenceSource.ACCUMULATION }.map { it.eventDate }, candles)
        return chains.flatMap { chain ->
            val validationDate = chain.last()
            val shapeWindow = engine.windowEndingOn(candles, validationDate)
            val baseStartDate = shapeWindow.firstOrNull()?.candleDate ?: validationDate
            val shapeAnalysis = engine.analyzeChain(candles, chain)
            val classification = shapeAnalysis.classification
            candles.filter { it.candleDate >= validationDate && !it.candleDate.isAfter(run.toDate) }.map { candle ->
                fun dates(source: ChartinkEvidenceSource) = events.filter { it.source == source && it.eventDate >= validationDate && it.eventDate >= run.fromDate && it.eventDate <= candle.candleDate }.map { it.eventDate }
                val confirmations = AccumulationConfirmationDates(dates(ChartinkEvidenceSource.PHASE_D), dates(ChartinkEvidenceSource.FRESH_BREAKOUT), dates(ChartinkEvidenceSource.FIFTY_TWO_WEEK_HIGH))
                val phaseD = confirmations.phaseD.firstOrNull()
                val breakout = confirmations.freshBreakout.firstOrNull()
                val baseRhythm = engine.analyzeBaseRhythm(candles, candle.candleDate)
                val details = objectMapper.writeValueAsString(mapOf("hitDates" to chain, "chainHitStartDate" to chain.first(), "chainHitEndDate" to validationDate, "shapeWindowStartDate" to baseStartDate, "shapeWindowSessions" to shapeWindow.size, "regression" to classification.metrics, "lineFit" to classification.lineFit, "latestShapeChunks" to shapeAnalysis.chunks, "shapeHitAnalyses" to shapeAnalysis.hitAnalyses, "goldenFlatNode" to shapeAnalysis.goldenFlatNode, "baseRhythm" to baseRhythm, "phaseDDates" to confirmations.phaseD, "freshBreakoutDates" to confirmations.freshBreakout, "fiftyTwoWeekHighDates" to confirmations.fiftyTwoWeekHigh))
                AccumulationCaseSnapshot(run.id, symbol, baseStartDate, validationDate, candle.candleDate, shapeWindow.size, chain.size, classification.shape, classification.decision, classification.decision == AccumulationShapeDecision.VALID, phaseD, breakout, phaseD?.let { engine.tradingSessionsBetween(validationDate, it, candles) }, breakout?.let { engine.tradingSessionsBetween(validationDate, it, candles) }, details, confirmations, shapeMetrics = classification.metrics, lineFit = classification.lineFit, goldenFlatNode = shapeAnalysis.goldenFlatNode, shapeChunks = shapeAnalysis.chunks, baseRhythm = baseRhythm)
            }
        }
    }
    private fun buildSixMonthEvidence(events: List<AnalysisEvidenceEvent>, run: AccumulationAnalysisRun): AccumulationEvidenceLane {
        val fromDate = run.toDate.minusMonths(6)
        fun dates(source: ChartinkEvidenceSource) = events.filter { it.source == source && it.eventDate in fromDate..run.toDate }.map(AnalysisEvidenceEvent::eventDate)
        return AccumulationEvidenceLane(fromDate, run.toDate, dates(ChartinkEvidenceSource.ACCUMULATION), dates(ChartinkEvidenceSource.PHASE_D), dates(ChartinkEvidenceSource.FRESH_BREAKOUT), dates(ChartinkEvidenceSource.FIFTY_TWO_WEEK_HIGH))
    }
    private suspend fun enrichSnapshots(snapshots: List<AccumulationCaseSnapshot>, run: AccumulationAnalysisRun): List<AccumulationCaseSnapshot> {
        val evidenceBySymbol = store.findEvidence(run.universeKey, run.toDate).groupBy(AnalysisEvidenceEvent::symbol)
        val memberships = membershipStore.findActiveMemberships(snapshots.map(AccumulationCaseSnapshot::symbol).distinct()).groupBy(IndexMembership::symbol)
        return snapshots.map { snapshot ->
            snapshot.copy(
                curatedWatchlists = memberships[snapshot.symbol].orEmpty().map(IndexMembership::indexKey).filterNot(UNIVERSES::contains).sorted(),
                sixMonthEvidence = buildSixMonthEvidence(evidenceBySymbol[snapshot.symbol].orEmpty(), run),
            )
        }
    }

    private fun withUnclassifiedShape(snapshots: List<AccumulationCaseSnapshot>, run: AccumulationAnalysisRun): List<AccumulationCaseSnapshot> =
        if (run.algorithmVersion == engine.algorithmVersion) snapshots else snapshots.map { snapshot ->
            snapshot.copy(shape = AccumulationShape.UNCLASSIFIED, shapeDecision = AccumulationShapeDecision.NEEDS_REVIEW, valid = false, shapeMetrics = null, lineFit = null, goldenFlatNode = null, shapeChunks = emptyList())
        }

    private suspend fun isStale(run: AccumulationAnalysisRun): Boolean =
        run.algorithmVersion != engine.algorithmVersion || store.evidenceRevision(run.universeKey)?.isAfter(run.evidenceRevision) == true

    private companion object {
        const val SHAPE_HISTORY_CALENDAR_DAYS = 180L
        val UNIVERSES = setOf("nifty_100", "nifty_midcap_150", "nifty_smallcap_250", "nifty_microcap_250")
        val objectMapper = jacksonObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
}
