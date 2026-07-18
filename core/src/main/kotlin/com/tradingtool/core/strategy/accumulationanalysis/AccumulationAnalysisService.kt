package com.tradingtool.core.strategy.accumulationanalysis

import com.tradingtool.core.candle.dao.CandleReadDao
import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.strategy.accumulationanalysis.dao.AccumulationAnalysisReadDao
import com.tradingtool.core.strategy.accumulationanalysis.dao.AccumulationAnalysisWriteDao
import com.tradingtool.core.strategy.chartinkevidence.ChartinkEvidenceSource
import java.time.LocalDate
import java.time.OffsetDateTime

typealias AccumulationAnalysisJdbiHandler = JdbiHandler<AccumulationAnalysisReadDao, AccumulationAnalysisWriteDao>

class JdbiAccumulationAnalysisStore(private val handler: AccumulationAnalysisJdbiHandler) : AccumulationAnalysisStore {
    override suspend fun latestAccumulationDate(universeKey: String) = handler.read { it.latestAccumulationDate(universeKey) }
    override suspend fun evidenceRevision(universeKey: String) = handler.read { it.evidenceRevision(universeKey) }
    override suspend fun findEvidence(universeKey: String, toDate: LocalDate) = handler.read { it.findEvidence(universeKey, toDate) }
    override suspend fun createRun(request: AccumulationAnalysisRunRequest, fromDate: LocalDate, toDate: LocalDate, revision: OffsetDateTime): AccumulationAnalysisRun = handler.transaction { read, write -> write.deleteRunScope(request.universeKey, request.months, fromDate, toDate); read.findRun(write.createRun(request.universeKey, request.months, fromDate, toDate, revision))!! }
    override suspend fun replaceSnapshots(runId: Long, snapshots: List<AccumulationCaseSnapshot>) { handler.transaction { _, write -> write.deleteSnapshots(runId); if (snapshots.isNotEmpty()) write.insertSnapshots(snapshots) } }
    override suspend fun completeRun(runId: Long) { handler.write { it.completeRun(runId) } }
    override suspend fun failRun(runId: Long, message: String) { handler.write { it.failRun(runId, "{\"error\":\"${message.replace("\"", "'")}\"}") } }
    override suspend fun findRuns() = handler.read { it.findRuns() }
    override suspend fun findRun(runId: Long) = handler.read { it.findRun(runId) }
    override suspend fun findLatestSnapshots(runId: Long) = handler.read { it.findLatestSnapshots(runId) }
    override suspend fun findTimeline(runId: Long, symbol: String) = handler.read { it.findTimeline(runId, symbol) }
}

class AccumulationAnalysisService(private val store: AccumulationAnalysisStore, private val candleHandler: CandleJdbiHandler, private val engine: AccumulationShapeEngine = AccumulationShapeEngine()) {
    suspend fun run(request: AccumulationAnalysisRunRequest): AccumulationAnalysisRun {
        require(request.months in setOf(1, 3, 6, 9)) { "months must be 1, 3, 6, or 9." }
        require(request.universeKey in UNIVERSES) { "Unsupported universe." }
        val toDate = requireNotNull(store.latestAccumulationDate(request.universeKey)) { "No Accumulation evidence uploaded for this universe." }
        val revision = requireNotNull(store.evidenceRevision(request.universeKey)) { "No Accumulation evidence uploaded for this universe." }
        val run = store.createRun(request, toDate.minusMonths(request.months.toLong()), toDate, revision)
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
        return AccumulationAnalysisSummary(run, store.evidenceRevision(run.universeKey)?.isAfter(run.evidenceRevision) == true, store.findLatestSnapshots(runId).sortedWith(compareBy<AccumulationCaseSnapshot> { !it.isValid }.thenBy { it.shape.ordinal }.thenByDescending { it.chainLengthSessions }))
    }
    suspend fun timeline(runId: Long, symbol: String): AccumulationAnalysisTimeline {
        val run = requireNotNull(store.findRun(runId)) { "Run not found." }
        return AccumulationAnalysisTimeline(run, store.evidenceRevision(run.universeKey)?.isAfter(run.evidenceRevision) == true, store.findTimeline(runId, symbol))
    }

    private suspend fun buildSnapshots(symbol: String, events: List<AnalysisEvidenceEvent>, run: AccumulationAnalysisRun): List<AccumulationCaseSnapshot> {
        val earliest = events.minOfOrNull { it.eventDate } ?: return emptyList()
        val candles = candleHandler.read { dao: CandleReadDao -> dao.getDailyCandlesBySymbol(symbol, earliest, run.toDate) }
        require(candles.isNotEmpty()) { "Missing Kite daily candles for $symbol." }
        val chains = engine.buildChains(events.filter { it.source == ChartinkEvidenceSource.ACCUMULATION }.map { it.eventDate }, candles)
        return chains.flatMap { chain ->
            val start = chain.first(); val end = chain.last()
            val shape = engine.classify(candles.filter { it.candleDate in start..end })
            candles.filter { it.candleDate >= end && !it.candleDate.isAfter(run.toDate) }.map { candle ->
                val phaseD = events.firstOrNull { it.source == ChartinkEvidenceSource.PHASE_D && it.eventDate >= end && it.eventDate <= candle.candleDate }?.eventDate
                val breakout = events.firstOrNull { it.source == ChartinkEvidenceSource.FRESH_BREAKOUT && it.eventDate >= end && it.eventDate <= candle.candleDate }?.eventDate
                AccumulationCaseSnapshot(run.id, symbol, start, end, candle.candleDate, engine.tradingSessionsBetween(start, end, candles), chain.size, shape, engine.decision(shape), engine.decision(shape) == AccumulationShapeDecision.VALID, phaseD, breakout, phaseD?.let { engine.tradingSessionsBetween(end, it, candles) }, breakout?.let { engine.tradingSessionsBetween(end, it, candles) }, "{\"hitDates\":[${chain.joinToString(",") { "\\\"$it\\\"" }}]}")
            }
        }
    }
    private companion object { val UNIVERSES = setOf("nifty_100", "nifty_midcap_150", "nifty_smallcap_250", "nifty_microcap_250") }
}
