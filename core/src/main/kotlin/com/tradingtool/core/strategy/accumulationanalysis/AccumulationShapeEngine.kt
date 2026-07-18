package com.tradingtool.core.strategy.accumulationanalysis

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tradingtool.core.candle.DailyCandle
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

data class AccumulationShapeConfig(
    val algorithmVersion: String,
    val maxGapTradingSessions: Int,
    val minimumHitCount: Int,
    val shapeWindowSessions: Int,
    val normalizedCurvatureThreshold: Double,
    val normalizedFlatSlopeThreshold: Double,
    val normalizedTurningSlopeThreshold: Double,
    val shapeChunkSessions: Int,
    val shapeChunkCount: Int,
    val goldenFlatMaxAbsCenterSlopePerTenSessions: Double,
    val goldenFlatMaxAbsCurvature: Double,
    val note: String,
)

data class AccumulationShapeClassification(
    val shape: AccumulationShape,
    val decision: AccumulationShapeDecision,
    val metrics: AccumulationShapeMetrics?,
)

data class AccumulationHitShapeAnalysis(
    val hitDate: LocalDate,
    val chunks: List<AccumulationShapeChunk>,
    val classification: AccumulationShapeClassification,
    val goldenFlatNode: AccumulationGoldenFlatNode?,
)

data class AccumulationChainShapeAnalysis(
    val classification: AccumulationShapeClassification,
    val chunks: List<AccumulationShapeChunk>,
    val goldenFlatNode: AccumulationGoldenFlatNode?,
    val hitAnalyses: List<AccumulationHitShapeAnalysis>,
)

private data class ChunkClassification(
    val position: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val classification: AccumulationShapeClassification,
)

class AccumulationShapeEngine(private val config: AccumulationShapeConfig = AccumulationShapeConfigLoader.load()) {
    init {
        require(config.shapeWindowSessions == config.shapeChunkSessions * config.shapeChunkCount) {
            "Shape window must equal the configured chunk count multiplied by chunk size."
        }
    }

    val algorithmVersion: String = config.algorithmVersion

    fun buildChains(hitDates: List<LocalDate>, candles: List<DailyCandle>): List<List<LocalDate>> {
        val sortedHits = hitDates.distinct().sorted()
        return sortedHits.fold(mutableListOf<MutableList<LocalDate>>()) { chains, date ->
            val active = chains.lastOrNull()
            if (active == null || tradingSessionsBetween(active.last(), date, candles) > config.maxGapTradingSessions) chains += mutableListOf(date) else active += date
            chains
        }
    }

    fun windowEndingOn(candles: List<DailyCandle>, validationDate: LocalDate, sessions: Int = config.shapeWindowSessions): List<DailyCandle> =
        candles.filter { it.candleDate <= validationDate }.takeLast(sessions)

    fun analyzeChain(candles: List<DailyCandle>, hitDates: List<LocalDate>): AccumulationChainShapeAnalysis {
        val hitAnalyses = hitDates.distinct().sorted().mapNotNull { hitDate -> analyzeHit(candles, hitDate) }
        val latest = hitAnalyses.lastOrNull()
            ?: return AccumulationChainShapeAnalysis(AccumulationShapeClassification(AccumulationShape.UNCLASSIFIED, AccumulationShapeDecision.NEEDS_REVIEW, null), emptyList(), null, emptyList())
        return AccumulationChainShapeAnalysis(latest.classification, latest.chunks, latest.goldenFlatNode, hitAnalyses)
    }

    fun classify(candles: List<DailyCandle>): AccumulationShapeClassification {
        return classifyWindow(candles, config.shapeWindowSessions)
    }

    private fun analyzeHit(candles: List<DailyCandle>, hitDate: LocalDate): AccumulationHitShapeAnalysis? {
        val window = windowEndingOn(candles, hitDate)
        if (window.size < config.shapeWindowSessions) return null
        val chunks = window.chunked(config.shapeChunkSessions)
        if (chunks.size != config.shapeChunkCount || chunks.any { it.size != config.shapeChunkSessions }) return null
        val classifiedChunks = chunks.mapIndexed { index, chunk -> classifyChunk(index + 1, chunk) }
        val latestChunk = requireNotNull(classifiedChunks.lastOrNull())
        val latestMetrics = requireNotNull(latestChunk.classification.metrics)
        val goldenFlat = isGoldenFlat(latestMetrics)
        val goldenFlatNode = if (goldenFlat) AccumulationGoldenFlatNode(config.shapeChunkSessions, latestChunk.startDate, latestChunk.endDate, latestMetrics) else null
        val classification = goldenFlatNode?.let { AccumulationShapeClassification(AccumulationShape.FLAT_GOLDEN, AccumulationShapeDecision.VALID, it.metrics) }
            ?: latestChunk.classification
        return AccumulationHitShapeAnalysis(hitDate, classifiedChunks.map { chunk ->
            AccumulationShapeChunk(
                position = chunk.position,
                startDate = chunk.startDate,
                endDate = chunk.endDate,
                shape = chunk.classification.shape,
                metrics = requireNotNull(chunk.classification.metrics),
                goldenFlat = chunk == latestChunk && goldenFlat,
            )
        }, classification, goldenFlatNode)
    }

    private fun classifyChunk(position: Int, chunk: List<DailyCandle>): ChunkClassification {
        val classification = classifyWindow(chunk, config.shapeChunkSessions)
        return ChunkClassification(position, chunk.first().candleDate, chunk.last().candleDate, classification)
    }

    private fun isGoldenFlat(metrics: AccumulationShapeMetrics): Boolean =
        kotlin.math.abs(metrics.centerSlopePerTenSessions) <= config.goldenFlatMaxAbsCenterSlopePerTenSessions &&
            kotlin.math.abs(metrics.curvature) <= config.goldenFlatMaxAbsCurvature

    private fun classifyWindow(candles: List<DailyCandle>, requiredSessions: Int): AccumulationShapeClassification {
        if (candles.size < requiredSessions) {
            return AccumulationShapeClassification(AccumulationShape.UNCLASSIFIED, AccumulationShapeDecision.NEEDS_REVIEW, null)
        }

        val coefficients = quadraticRegression(candles.map(DailyCandle::close))
        val metrics = coefficients.metrics(candles.size)
        val turningSlopeThreshold = turningThresholdPerTenSessions(candles.size)
        val hasTurningPointInsideWindow = metrics.vertexPosition?.let { it in -1.0..1.0 } == true
        val isCup = coefficients.curvature >= config.normalizedCurvatureThreshold &&
            metrics.startSlopePerTenSessions <= -turningSlopeThreshold &&
            metrics.endSlopePerTenSessions >= turningSlopeThreshold &&
            hasTurningPointInsideWindow
        val isInvertedU = coefficients.curvature <= -config.normalizedCurvatureThreshold &&
            metrics.startSlopePerTenSessions >= turningSlopeThreshold &&
            metrics.endSlopePerTenSessions <= -turningSlopeThreshold &&
            hasTurningPointInsideWindow
        val shape = when {
            isInvertedU -> AccumulationShape.INVALID
            isCup -> AccumulationShape.CUP
            kotlin.math.abs(coefficients.slope) <= config.normalizedFlatSlopeThreshold -> AccumulationShape.FLAT
            coefficients.slope < 0 -> AccumulationShape.DOWNWARD_DRIFT
            else -> AccumulationShape.UPWARD_DRIFT
        }
        val decision = if (shape == AccumulationShape.INVALID) AccumulationShapeDecision.INVALID else AccumulationShapeDecision.VALID
        return AccumulationShapeClassification(shape, decision, metrics)
    }

    fun tradingSessionsBetween(from: LocalDate, to: LocalDate, candles: List<DailyCandle>): Int =
        candles.count { it.candleDate > from && it.candleDate <= to }

    private fun turningThresholdPerTenSessions(sessionCount: Int): Double =
        config.normalizedTurningSlopeThreshold * sessionScalePerTenSessions(sessionCount) * 100.0

    private fun quadraticRegression(closes: List<Double>): QuadraticCoefficients {
        val meanClose = closes.average()
        val xValues = closes.indices.map { index -> -1.0 + (2.0 * index / (closes.size - 1)) }
        val normalizedCloses = closes.map { close -> close / meanClose - 1.0 }
        val sumX2 = xValues.sumOf { it * it }
        val sumX4 = xValues.sumOf { it * it * it * it }
        val sumY = normalizedCloses.sum()
        val sumXY = xValues.zip(normalizedCloses).sumOf { (x, y) -> x * y }
        val sumX2Y = xValues.zip(normalizedCloses).sumOf { (x, y) -> x * x * y }
        val denominator = closes.size * sumX4 - sumX2 * sumX2
        return QuadraticCoefficients(
            curvature = (closes.size * sumX2Y - sumX2 * sumY) / denominator,
            slope = sumXY / sumX2,
        )
    }

    private fun sessionScalePerTenSessions(sessionCount: Int): Double = 20.0 / (sessionCount - 1)

    private data class QuadraticCoefficients(val curvature: Double, val slope: Double) {
        fun metrics(sessionCount: Int): AccumulationShapeMetrics {
            val scale = 20.0 / (sessionCount - 1) * 100.0
            val vertex = if (kotlin.math.abs(curvature) > 1e-9) -slope / (2.0 * curvature) else null
            return AccumulationShapeMetrics(
                curvature = curvature,
                centerSlopePerTenSessions = slope * scale,
                startSlopePerTenSessions = (slope - 2.0 * curvature) * scale,
                endSlopePerTenSessions = (slope + 2.0 * curvature) * scale,
                vertexPosition = vertex,
            )
        }
    }
}

object AccumulationShapeConfigLoader {
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private val paths = listOf(
        Path.of("config", "accumulation_analysis_config.json"),
        Path.of("..", "config", "accumulation_analysis_config.json"),
    )

    fun load(): AccumulationShapeConfig {
        val path = paths.firstOrNull(Files::exists)
            ?: error("Missing accumulation_analysis_config.json.")
        return mapper.readValue(path.toFile(), AccumulationShapeConfig::class.java)
    }
}
