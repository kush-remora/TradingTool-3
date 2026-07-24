package com.tradingtool.core.strategy.accumulationanalysis

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tradingtool.core.candle.DailyCandle
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.sqrt

data class AccumulationShapeConfig(
    val algorithmVersion: String,
    val maxGapTradingSessions: Int,
    val minimumHitCount: Int,
    val shapeWindowSessions: Int,
    val normalizedCurvatureThreshold: Double,
    val normalizedTurningSlopeThreshold: Double,
    val shapeChunkSessions: Int,
    val shapeChunkCount: Int,
    val baseRhythmBlockSessions: Int,
    val baseRhythmFlatChangePercent: Double,
    val baseRhythmStateChangePercent: Double,
    val outlierMinimumDeviationPercent: Double,
    val flatMaxAbsSlopePerTenSessions: Double,
    val flatMaxTypicalDeviationPercent: Double,
    val flatMaxDeviationPercent: Double,
    val goldenFlatMaxAbsSlopePerTenSessions: Double,
    val goldenFlatMaxTypicalDeviationPercent: Double,
    val goldenFlatMaxDeviationPercent: Double,
    val note: String,
)

data class AccumulationShapeClassification(
    val shape: AccumulationShape,
    val decision: AccumulationShapeDecision,
    val metrics: AccumulationShapeMetrics?,
    val lineFit: AccumulationLineFitMetrics? = null,
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

private data class LineSample(val index: Int, val candle: DailyCandle)
private data class LineResidual(val sample: LineSample, val deviationPercent: Double)
private data class StraightLineFit(
    val meanClose: Double,
    val slope: Double,
    val residuals: List<LineResidual>,
)

class AccumulationShapeEngine(private val config: AccumulationShapeConfig = AccumulationShapeConfigLoader.load()) {
    init {
        require(config.shapeWindowSessions == config.shapeChunkSessions * config.shapeChunkCount) {
            "Shape window must equal the configured chunk count multiplied by chunk size."
        }
        require(config.shapeWindowSessions % config.baseRhythmBlockSessions == 0) {
            "Shape window must split evenly into Base Rhythm blocks."
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

    fun analyzeBaseRhythm(candles: List<DailyCandle>, asOfDate: LocalDate): AccumulationBaseRhythm? {
        val window = windowEndingOn(candles, asOfDate)
        val blockSessions = config.baseRhythmBlockSessions
        if (window.size < config.shapeWindowSessions || window.size % blockSessions != 0) return null

        var previousRangePercent: Double? = null
        var previousAverageVolume: Double? = null
        val blocks = window.chunked(blockSessions).mapIndexed { index, block ->
            val startClose = block.first().close
            val closeChangePercent = ((block.last().close - startClose) / startClose) * 100.0
            val rangePercent = ((block.maxOf(DailyCandle::high) - block.minOf(DailyCandle::low)) / startClose) * 100.0
            val averageVolume = block.map(DailyCandle::volume).average()
            val rhythmBlock = AccumulationBaseRhythmBlock(
                position = index + 1,
                startDate = block.first().candleDate,
                endDate = block.last().candleDate,
                direction = directionFor(closeChangePercent),
                rangeState = stateFor(rangePercent, previousRangePercent),
                volumeState = stateFor(averageVolume, previousAverageVolume),
                closeChangePercent = closeChangePercent,
                rangePercent = rangePercent,
                averageVolume = averageVolume,
            )
            previousRangePercent = rangePercent
            previousAverageVolume = averageVolume
            rhythmBlock
        }
        return AccumulationBaseRhythm(window.first().candleDate, window.last().candleDate, blocks)
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
        val goldenFlatNode = latestChunk.classification.takeIf { it.shape == AccumulationShape.FLAT_GOLDEN }?.let {
            AccumulationGoldenFlatNode(
                config.shapeChunkSessions,
                latestChunk.startDate,
                latestChunk.endDate,
                requireNotNull(it.metrics),
                it.lineFit,
            )
        }
        val classification = latestChunk.classification
        return AccumulationHitShapeAnalysis(hitDate, classifiedChunks.map { chunk ->
            AccumulationShapeChunk(
                position = chunk.position,
                startDate = chunk.startDate,
                endDate = chunk.endDate,
                shape = chunk.classification.shape,
                metrics = requireNotNull(chunk.classification.metrics),
                goldenFlat = chunk.classification.shape == AccumulationShape.FLAT_GOLDEN,
                lineFit = chunk.classification.lineFit,
            )
        }, classification, goldenFlatNode)
    }

    private fun classifyChunk(position: Int, chunk: List<DailyCandle>): ChunkClassification {
        val classification = classifyWindow(chunk, config.shapeChunkSessions, goldenFlatEligible = true)
        return ChunkClassification(position, chunk.first().candleDate, chunk.last().candleDate, classification)
    }

    private fun classifyWindow(
        candles: List<DailyCandle>,
        requiredSessions: Int,
        goldenFlatEligible: Boolean = false,
    ): AccumulationShapeClassification {
        if (candles.size < requiredSessions) {
            return AccumulationShapeClassification(AccumulationShape.UNCLASSIFIED, AccumulationShapeDecision.NEEDS_REVIEW, null)
        }

        val lineFit = lineFit(candles)
        val coefficients = quadraticRegression(candles.map(DailyCandle::close))
        val metrics = coefficients.metrics(candles.size)
        if (goldenFlatEligible && matchesGoldenFlat(lineFit)) {
            return AccumulationShapeClassification(AccumulationShape.FLAT_GOLDEN, AccumulationShapeDecision.VALID, metrics, lineFit)
        }
        if (matchesFlat(lineFit)) {
            return AccumulationShapeClassification(AccumulationShape.FLAT, AccumulationShapeDecision.VALID, metrics, lineFit)
        }
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
            coefficients.slope < 0 -> AccumulationShape.DOWNWARD_DRIFT
            else -> AccumulationShape.UPWARD_DRIFT
        }
        val decision = if (shape == AccumulationShape.INVALID) AccumulationShapeDecision.INVALID else AccumulationShapeDecision.VALID
        return AccumulationShapeClassification(shape, decision, metrics, lineFit)
    }

    private fun matchesFlat(lineFit: AccumulationLineFitMetrics): Boolean =
        abs(lineFit.slopePerTenSessions) <= config.flatMaxAbsSlopePerTenSessions &&
            lineFit.typicalDeviationPercent <= config.flatMaxTypicalDeviationPercent &&
            lineFit.maximumDeviationPercent < config.outlierMinimumDeviationPercent &&
            lineFit.maximumDeviationPercent <= config.flatMaxDeviationPercent

    private fun matchesGoldenFlat(lineFit: AccumulationLineFitMetrics): Boolean =
        abs(lineFit.slopePerTenSessions) <= config.goldenFlatMaxAbsSlopePerTenSessions &&
            lineFit.typicalDeviationPercent <= config.goldenFlatMaxTypicalDeviationPercent &&
            lineFit.maximumDeviationPercent < config.outlierMinimumDeviationPercent &&
            lineFit.maximumDeviationPercent <= config.goldenFlatMaxDeviationPercent

    private fun directionFor(closeChangePercent: Double): AccumulationBaseRhythmDirection = when {
        closeChangePercent <= -config.baseRhythmFlatChangePercent -> AccumulationBaseRhythmDirection.FALLING
        closeChangePercent >= config.baseRhythmFlatChangePercent -> AccumulationBaseRhythmDirection.RISING
        else -> AccumulationBaseRhythmDirection.FLAT
    }

    private fun stateFor(value: Double, previousValue: Double?): AccumulationBaseRhythmState {
        if (previousValue == null || previousValue == 0.0) return AccumulationBaseRhythmState.STEADY
        val changePercent = ((value - previousValue) / previousValue) * 100.0
        return when {
            changePercent <= -config.baseRhythmStateChangePercent -> AccumulationBaseRhythmState.CONTRACTING
            changePercent >= config.baseRhythmStateChangePercent -> AccumulationBaseRhythmState.EXPANDING
            else -> AccumulationBaseRhythmState.STEADY
        }
    }

    private fun lineFit(candles: List<DailyCandle>): AccumulationLineFitMetrics {
        val samples = candles.mapIndexed { index, candle -> LineSample(index, candle) }
        val initialFit = fitStraightLine(samples)
        val largestDeviation = initialFit.residuals.maxBy { abs(it.deviationPercent) }
        val ignoredOutlier = largestDeviation.takeIf { abs(it.deviationPercent) >= config.outlierMinimumDeviationPercent }
        val finalFit = if (ignoredOutlier == null) initialFit else fitStraightLine(samples.filterNot { it == ignoredOutlier.sample })
        val deviations = finalFit.residuals.map { abs(it.deviationPercent) }

        return AccumulationLineFitMetrics(
            slopePerTenSessions = finalFit.slope / finalFit.meanClose * 1_000.0,
            typicalDeviationPercent = sqrt(deviations.sumOf { it * it } / deviations.size),
            maximumDeviationPercent = deviations.max(),
            ignoredOutlierDate = ignoredOutlier?.sample?.candle?.candleDate,
            ignoredOutlierDeviationPercent = ignoredOutlier?.deviationPercent?.let(::abs),
        )
    }

    private fun fitStraightLine(samples: List<LineSample>): StraightLineFit {
        val meanIndex = samples.map(LineSample::index).average()
        val meanClose = samples.map { it.candle.close }.average()
        val slope = samples.sumOf { (it.index - meanIndex) * (it.candle.close - meanClose) } /
            samples.sumOf { (it.index - meanIndex) * (it.index - meanIndex) }
        val intercept = meanClose - slope * meanIndex
        val residuals = samples.map { sample ->
            LineResidual(sample, (sample.candle.close - (intercept + slope * sample.index)) / meanClose * 100.0)
        }
        return StraightLineFit(meanClose, slope, residuals)
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
    private const val RESOURCE_NAME = "accumulation_analysis_config.json"
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private val paths = listOf(
        Path.of("config", RESOURCE_NAME),
        Path.of("..", "config", RESOURCE_NAME),
    )

    fun load(filePaths: List<Path> = paths): AccumulationShapeConfig {
        val path = filePaths.firstOrNull(Files::exists)
        if (path != null) {
            return mapper.readValue(path.toFile(), AccumulationShapeConfig::class.java)
        }

        val resource = requireNotNull(AccumulationShapeConfigLoader::class.java.classLoader.getResourceAsStream(RESOURCE_NAME)) {
            "Missing $RESOURCE_NAME from both the local config directory and the application classpath."
        }
        return resource.use { mapper.readValue(it, AccumulationShapeConfig::class.java) }
    }
}
