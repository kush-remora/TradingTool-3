package com.tradingtool.core.strategy.volumeeventbacktest

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.technical.calculateRsiValues
import java.time.LocalDate
import kotlin.math.round

class VolumeEventConfirmationBacktestEngine(
    private val adaptiveRsiCalibrationCalculator: AdaptiveRsiCalibrationProvider = AdaptiveRsiCalibrationCalculator(),
) {

    internal fun run(
        member: VolumeEventConfirmationMember,
        candles: List<DailyCandle>,
        fromDate: LocalDate,
        toDate: LocalDate,
        config: VolumeEventConfirmationBacktestConfig = VolumeEventConfirmationBacktestConfig(),
    ): VolumeEventConfirmationSymbolReport {
        validateConfig(config)
        val sortedCandles = candles
            .filter { candle -> !candle.candleDate.isAfter(toDate) }
            .distinctBy(DailyCandle::candleDate)
            .sortedBy(DailyCandle::candleDate)
        if (sortedCandles.isEmpty()) return emptyReport(member, VolumeEventConfirmationDataStatuses.NO_CANDLES)

        val firstTestIndex = sortedCandles.indexOfFirst { candle -> !candle.candleDate.isBefore(fromDate) }
        if (firstTestIndex < 0) return emptyReport(member, VolumeEventConfirmationDataStatuses.INSUFFICIENT_HISTORY)

        val rsiValues = sortedCandles.calculateRsiValues(period = config.rsiPeriod)
        val observations = mutableListOf<VolumeEventConfirmationObservation>()
        var nextEligibleEventIndex = firstTestIndex

        for (eventIndex in firstTestIndex..sortedCandles.lastIndex) {
            val eventCandle = sortedCandles[eventIndex]
            val priorCandles = sortedCandles.previousCandles(eventIndex, config.volumeBaselineDays) ?: continue
            val priorVolumes = priorCandles
                .map { candle -> candle.volume.toDouble() }
            val priorVolumeAverage = priorVolumes.average()
            if (priorVolumeAverage <= 0.0 || !priorVolumeAverage.isFinite()) continue

            val volumeRatio = eventCandle.volume.toDouble() / priorVolumeAverage
            val eventRsi = rsiValues[eventIndex]
            if (volumeRatio < config.volumeShockMultiplier || !eventRsi.isFinite()) continue

            val priceContext = calculateLookbackPriceContext(priorCandles, eventCandle)
            val lookbackReturnPct = priceContext.returnPct
            val lookbackDrawdownPct = priceContext.drawdownPct

            if (priceContext.isBearish(config.maxLookbackDrawdownPct)) {
                observations += buildRejectedBearishContextObservation(
                    eventCandle = eventCandle,
                    priorVolumeAverage = priorVolumeAverage,
                    volumeRatio = volumeRatio,
                    eventRsi = eventRsi,
                    lookbackReturnPct = lookbackReturnPct,
                    lookbackDrawdownPct = lookbackDrawdownPct,
                )
                continue
            }

            val calibration = adaptiveRsiCalibrationCalculator.calculate(
                candles = sortedCandles,
                rsiValues = rsiValues,
                currentEventIndex = eventIndex,
                config = config,
            )
            if (calibration.threshold == null) {
                observations += buildInsufficientRsiCalibrationObservation(
                    eventCandle = eventCandle,
                    priorVolumeAverage = priorVolumeAverage,
                    volumeRatio = volumeRatio,
                    eventRsi = eventRsi,
                    lookbackReturnPct = lookbackReturnPct,
                    lookbackDrawdownPct = lookbackDrawdownPct,
                    calibration = calibration,
                )
                continue
            }

            if (eventRsi > calibration.threshold) {
                observations += buildRsiAboveAdaptiveCeilingObservation(
                    eventCandle = eventCandle,
                    priorVolumeAverage = priorVolumeAverage,
                    volumeRatio = volumeRatio,
                    eventRsi = eventRsi,
                    lookbackReturnPct = lookbackReturnPct,
                    lookbackDrawdownPct = lookbackDrawdownPct,
                    calibration = calibration,
                )
                continue
            }

            val pastRsiTrend = if (config.entryMode == VolumeEventEntryModes.FIVE_DAY_PAST_RSI_EARLY_ENTRY) {
                calculatePastRsiTrend(
                    rsiValues = rsiValues,
                    eventIndex = eventIndex,
                    lookbackDays = config.pastRsiLookbackDays,
                )
            } else {
                null
            }
            if (config.entryMode == VolumeEventEntryModes.FIVE_DAY_PAST_RSI_EARLY_ENTRY &&
                (pastRsiTrend == null || pastRsiTrend.changePoints <= 0.0)
            ) {
                observations += buildPastRsiTrendRejectedObservation(
                    eventCandle = eventCandle,
                    priorVolumeAverage = priorVolumeAverage,
                    volumeRatio = volumeRatio,
                    eventRsi = eventRsi,
                    lookbackReturnPct = lookbackReturnPct,
                    lookbackDrawdownPct = lookbackDrawdownPct,
                    calibration = calibration,
                    pastRsiTrend = pastRsiTrend,
                )
                continue
            }

            if (eventIndex < nextEligibleEventIndex) {
                observations += buildSkippedObservation(
                    eventCandle = eventCandle,
                    priorVolumeAverage = priorVolumeAverage,
                    volumeRatio = volumeRatio,
                    eventRsi = eventRsi,
                    lookbackReturnPct = lookbackReturnPct,
                    lookbackDrawdownPct = lookbackDrawdownPct,
                    calibration = calibration,
                )
                continue
            }

            val isEarlyEntry = config.entryMode == VolumeEventEntryModes.FIVE_DAY_PAST_RSI_EARLY_ENTRY
            val confirmationIndex = eventIndex + config.confirmationDays
            val entryIndex = if (isEarlyEntry) eventIndex + 1 else confirmationIndex + 1
            if (entryIndex > sortedCandles.lastIndex) {
                observations += buildInsufficientObservation(
                    eventCandle = eventCandle,
                    priorVolumeAverage = priorVolumeAverage,
                    volumeRatio = volumeRatio,
                    eventRsi = eventRsi,
                    lookbackReturnPct = lookbackReturnPct,
                    lookbackDrawdownPct = lookbackDrawdownPct,
                    calibration = calibration,
                )
                continue
            }

            val confirmationCandle = if (isEarlyEntry) null else sortedCandles[confirmationIndex]
            val confirmationRsi = if (isEarlyEntry) null else rsiValues[confirmationIndex]
            val rsiChangePoints = confirmationRsi?.minus(eventRsi)

            if (!isEarlyEntry && confirmationRsi!! <= eventRsi) {
                observations += buildNoConfirmationObservation(
                    eventCandle = eventCandle,
                    priorVolumeAverage = priorVolumeAverage,
                    volumeRatio = volumeRatio,
                    eventRsi = eventRsi,
                    confirmationCandle = confirmationCandle!!,
                    confirmationRsi = confirmationRsi,
                    rsiChangePoints = rsiChangePoints!!,
                    lookbackReturnPct = lookbackReturnPct,
                    lookbackDrawdownPct = lookbackDrawdownPct,
                    calibration = calibration,
                )
                continue
            }

            val entryCandle = sortedCandles[entryIndex]
            val entryPrice = entryCandle.open
            if (entryPrice <= 0.0 || !entryPrice.isFinite()) continue

            val outcomeEndIndex = minOf(sortedCandles.lastIndex, entryIndex + config.maxHoldingDays - 1)
            val targetPrice = entryPrice * (1.0 + config.targetPct / 100.0)
            val targetIndex = (entryIndex..outcomeEndIndex).firstOrNull { index -> sortedCandles[index].high >= targetPrice }
            val exitIndex = targetIndex ?: outcomeEndIndex
            val outcome = if (targetIndex != null) {
                VolumeEventConfirmationStatuses.TARGET_HIT
            } else {
                VolumeEventConfirmationStatuses.UNRESOLVED
            }

            observations += VolumeEventConfirmationObservation(
                symbol = member.symbol,
                eventDate = eventCandle.candleDate.toString(),
                eventClose = eventCandle.close.roundTo2(),
                eventVolume = eventCandle.volume,
                priorVolumeAverage = priorVolumeAverage.roundTo2(),
                volumeRatio = volumeRatio.roundTo2(),
                eventRsi = eventRsi.roundTo2(),
                lookbackReturnPct = lookbackReturnPct.roundTo2(),
                lookbackDrawdownPct = lookbackDrawdownPct.roundTo2(),
                adaptiveRsiThreshold = calibration.threshold.roundTo2(),
                rsiCalibrationSampleCount = calibration.sampleCount,
                rsiCalibrationBaselineHitRatePct = calibration.baselineHitRatePct,
                rsiCalibrationSelectedHitRatePct = calibration.selectedHitRatePct,
                confirmationDate = confirmationCandle?.candleDate?.toString(),
                confirmationRsi = confirmationRsi?.roundTo2(),
                rsiChangePoints = rsiChangePoints?.roundTo2(),
                pastRsiChangePoints = pastRsiTrend?.changePoints?.roundTo2(),
                pastRsiTrendPassed = pastRsiTrend?.let { it.changePoints > 0.0 },
                entryDate = entryCandle.candleDate.toString(),
                entryPrice = entryPrice.roundTo2(),
                targetPrice = targetPrice.roundTo2(),
                status = outcome,
                exitDate = sortedCandles[exitIndex].candleDate.toString(),
                exitPrice = if (targetIndex != null) targetPrice.roundTo2() else sortedCandles[exitIndex].close.roundTo2(),
                holdingTradingDays = exitIndex - entryIndex + 1,
                maximumHighSinceEntryPct = sortedCandles
                    .subList(entryIndex, exitIndex + 1)
                    .maxOf { candle -> ((candle.high / entryPrice) - 1.0) * 100.0 }
                    .roundTo2(),
                unresolvedCloseReturnPct = targetIndex?.let { null }
                    ?: (((sortedCandles[exitIndex].close / entryPrice) - 1.0) * 100.0).roundTo2(),
            )
            nextEligibleEventIndex = exitIndex + 1
        }

        val testedCandles = sortedCandles.drop(firstTestIndex)
        return VolumeEventConfirmationSymbolReport(
            symbol = member.symbol,
            companyName = member.companyName,
            instrumentToken = member.instrumentToken,
            dataStatus = VolumeEventConfirmationDataStatuses.AVAILABLE,
            testedFromDate = testedCandles.first().candleDate.toString(),
            testedToDate = testedCandles.last().candleDate.toString(),
            summary = summarizeVolumeEventObservations(observations, config.entryMode),
            observations = observations,
        )
    }

    private fun emptyReport(
        member: VolumeEventConfirmationMember,
        dataStatus: String,
    ): VolumeEventConfirmationSymbolReport = VolumeEventConfirmationSymbolReport(
        symbol = member.symbol,
        companyName = member.companyName,
        instrumentToken = member.instrumentToken,
        dataStatus = dataStatus,
        testedFromDate = null,
        testedToDate = null,
        summary = summarizeVolumeEventObservations(emptyList()),
        observations = emptyList(),
    )

    private fun buildSkippedObservation(
        eventCandle: DailyCandle,
        priorVolumeAverage: Double,
        volumeRatio: Double,
        eventRsi: Double,
        lookbackReturnPct: Double,
        lookbackDrawdownPct: Double,
        calibration: AdaptiveRsiCalibration,
    ): VolumeEventConfirmationObservation = baseObservation(
        eventCandle = eventCandle,
        priorVolumeAverage = priorVolumeAverage,
        volumeRatio = volumeRatio,
        eventRsi = eventRsi,
        lookbackReturnPct = lookbackReturnPct,
        lookbackDrawdownPct = lookbackDrawdownPct,
        status = VolumeEventConfirmationStatuses.SKIPPED_WHILE_IN_POSITION,
    ).withCalibration(calibration)

    private fun buildRejectedBearishContextObservation(
        eventCandle: DailyCandle,
        priorVolumeAverage: Double,
        volumeRatio: Double,
        eventRsi: Double,
        lookbackReturnPct: Double,
        lookbackDrawdownPct: Double,
    ): VolumeEventConfirmationObservation = baseObservation(
        eventCandle = eventCandle,
        priorVolumeAverage = priorVolumeAverage,
        volumeRatio = volumeRatio,
        eventRsi = eventRsi,
        lookbackReturnPct = lookbackReturnPct,
        lookbackDrawdownPct = lookbackDrawdownPct,
        status = VolumeEventConfirmationStatuses.REJECTED_BEARISH_CONTEXT,
    )

    private fun buildInsufficientRsiCalibrationObservation(
        eventCandle: DailyCandle,
        priorVolumeAverage: Double,
        volumeRatio: Double,
        eventRsi: Double,
        lookbackReturnPct: Double,
        lookbackDrawdownPct: Double,
        calibration: AdaptiveRsiCalibration,
    ): VolumeEventConfirmationObservation = baseObservation(
        eventCandle = eventCandle,
        priorVolumeAverage = priorVolumeAverage,
        volumeRatio = volumeRatio,
        eventRsi = eventRsi,
        lookbackReturnPct = lookbackReturnPct,
        lookbackDrawdownPct = lookbackDrawdownPct,
        status = VolumeEventConfirmationStatuses.INSUFFICIENT_RSI_CALIBRATION,
    ).withCalibration(calibration)

    private fun buildRsiAboveAdaptiveCeilingObservation(
        eventCandle: DailyCandle,
        priorVolumeAverage: Double,
        volumeRatio: Double,
        eventRsi: Double,
        lookbackReturnPct: Double,
        lookbackDrawdownPct: Double,
        calibration: AdaptiveRsiCalibration,
    ): VolumeEventConfirmationObservation = baseObservation(
        eventCandle = eventCandle,
        priorVolumeAverage = priorVolumeAverage,
        volumeRatio = volumeRatio,
        eventRsi = eventRsi,
        lookbackReturnPct = lookbackReturnPct,
        lookbackDrawdownPct = lookbackDrawdownPct,
        status = VolumeEventConfirmationStatuses.RSI_ABOVE_ADAPTIVE_CEILING,
    ).withCalibration(calibration)

    private fun buildPastRsiTrendRejectedObservation(
        eventCandle: DailyCandle,
        priorVolumeAverage: Double,
        volumeRatio: Double,
        eventRsi: Double,
        lookbackReturnPct: Double,
        lookbackDrawdownPct: Double,
        calibration: AdaptiveRsiCalibration,
        pastRsiTrend: PastRsiTrend?,
    ): VolumeEventConfirmationObservation = baseObservation(
        eventCandle = eventCandle,
        priorVolumeAverage = priorVolumeAverage,
        volumeRatio = volumeRatio,
        eventRsi = eventRsi,
        lookbackReturnPct = lookbackReturnPct,
        lookbackDrawdownPct = lookbackDrawdownPct,
        status = VolumeEventConfirmationStatuses.PAST_RSI_TREND_NOT_CONFIRMED,
    ).copy(
        pastRsiChangePoints = pastRsiTrend?.changePoints?.roundTo2(),
        pastRsiTrendPassed = false,
    ).withCalibration(calibration)

    private fun buildInsufficientObservation(
        eventCandle: DailyCandle,
        priorVolumeAverage: Double,
        volumeRatio: Double,
        eventRsi: Double,
        lookbackReturnPct: Double,
        lookbackDrawdownPct: Double,
        calibration: AdaptiveRsiCalibration,
    ): VolumeEventConfirmationObservation = baseObservation(
        eventCandle = eventCandle,
        priorVolumeAverage = priorVolumeAverage,
        volumeRatio = volumeRatio,
        eventRsi = eventRsi,
        lookbackReturnPct = lookbackReturnPct,
        lookbackDrawdownPct = lookbackDrawdownPct,
        status = VolumeEventConfirmationStatuses.INSUFFICIENT_FORWARD_DATA,
    ).withCalibration(calibration)

    private fun buildNoConfirmationObservation(
        eventCandle: DailyCandle,
        priorVolumeAverage: Double,
        volumeRatio: Double,
        eventRsi: Double,
        confirmationCandle: DailyCandle,
        confirmationRsi: Double,
        rsiChangePoints: Double,
        lookbackReturnPct: Double,
        lookbackDrawdownPct: Double,
        calibration: AdaptiveRsiCalibration,
    ): VolumeEventConfirmationObservation = baseObservation(
        eventCandle = eventCandle,
        priorVolumeAverage = priorVolumeAverage,
        volumeRatio = volumeRatio,
        eventRsi = eventRsi,
        lookbackReturnPct = lookbackReturnPct,
        lookbackDrawdownPct = lookbackDrawdownPct,
        status = VolumeEventConfirmationStatuses.NO_CONFIRMATION,
    ).copy(
        confirmationDate = confirmationCandle.candleDate.toString(),
        confirmationRsi = confirmationRsi.roundTo2(),
        rsiChangePoints = rsiChangePoints.roundTo2(),
    ).withCalibration(calibration)

    private fun baseObservation(
        eventCandle: DailyCandle,
        priorVolumeAverage: Double,
        volumeRatio: Double,
        eventRsi: Double,
        lookbackReturnPct: Double,
        lookbackDrawdownPct: Double,
        status: String,
    ): VolumeEventConfirmationObservation = VolumeEventConfirmationObservation(
        symbol = eventCandle.symbol,
        eventDate = eventCandle.candleDate.toString(),
        eventClose = eventCandle.close.roundTo2(),
        eventVolume = eventCandle.volume,
        priorVolumeAverage = priorVolumeAverage.roundTo2(),
        volumeRatio = volumeRatio.roundTo2(),
        eventRsi = eventRsi.roundTo2(),
        lookbackReturnPct = lookbackReturnPct.roundTo2(),
        lookbackDrawdownPct = lookbackDrawdownPct.roundTo2(),
        adaptiveRsiThreshold = null,
        rsiCalibrationSampleCount = null,
        rsiCalibrationBaselineHitRatePct = null,
        rsiCalibrationSelectedHitRatePct = null,
        pastRsiChangePoints = null,
        pastRsiTrendPassed = null,
        confirmationDate = null,
        confirmationRsi = null,
        rsiChangePoints = null,
        entryDate = null,
        entryPrice = null,
        targetPrice = null,
        status = status,
        exitDate = null,
        exitPrice = null,
        holdingTradingDays = null,
        maximumHighSinceEntryPct = null,
        unresolvedCloseReturnPct = null,
    )

    private fun VolumeEventConfirmationObservation.withCalibration(
        calibration: AdaptiveRsiCalibration,
    ): VolumeEventConfirmationObservation = copy(
        adaptiveRsiThreshold = calibration.threshold?.roundTo2(),
        rsiCalibrationSampleCount = calibration.sampleCount,
        rsiCalibrationBaselineHitRatePct = calibration.baselineHitRatePct,
        rsiCalibrationSelectedHitRatePct = calibration.selectedHitRatePct,
    )

    private fun validateConfig(config: VolumeEventConfirmationBacktestConfig) {
        require(config.volumeBaselineDays > 0) { "volumeBaselineDays must be positive." }
        require(config.volumeShockMultiplier > 0.0) { "volumeShockMultiplier must be positive." }
        require(config.confirmationDays > 0) { "confirmationDays must be positive." }
        require(config.targetPct > 0.0) { "targetPct must be positive." }
        require(config.maxHoldingDays > 0) { "maxHoldingDays must be positive." }
        require(config.rsiPeriod > 0) { "rsiPeriod must be positive." }
        require(config.maxLookbackDrawdownPct >= 0.0) { "maxLookbackDrawdownPct must not be negative." }
        require(config.adaptiveRsiCalibrationDays > 0) { "adaptiveRsiCalibrationDays must be positive." }
        require(config.adaptiveRsiBinSize > 0.0) { "adaptiveRsiBinSize must be positive." }
        require(config.adaptiveRsiMinimumSampleCount > 0) { "adaptiveRsiMinimumSampleCount must be positive." }
        require(config.pastRsiLookbackDays > 0) { "pastRsiLookbackDays must be positive." }
    }
}

internal fun summarizeVolumeEventObservations(
    observations: List<VolumeEventConfirmationObservation>,
    entryMode: String = VolumeEventEntryModes.FIVE_DAY_FUTURE_RSI_CONFIRMATION,
): VolumeEventConfirmationBacktestSummary {
    val targetHitCount = observations.count { observation -> observation.status == VolumeEventConfirmationStatuses.TARGET_HIT }
    val unresolvedCount = observations.count { observation -> observation.status == VolumeEventConfirmationStatuses.UNRESOLVED }
    val noConfirmationCount = observations.count { observation -> observation.status == VolumeEventConfirmationStatuses.NO_CONFIRMATION }
    val skippedCount = observations.count { observation -> observation.status == VolumeEventConfirmationStatuses.SKIPPED_WHILE_IN_POSITION }
    val insufficientForwardDataCount = observations.count { observation -> observation.status == VolumeEventConfirmationStatuses.INSUFFICIENT_FORWARD_DATA }
    val rejectedBearishContextCount = observations.count { observation -> observation.status == VolumeEventConfirmationStatuses.REJECTED_BEARISH_CONTEXT }
    val insufficientRsiCalibrationCount = observations.count { observation -> observation.status == VolumeEventConfirmationStatuses.INSUFFICIENT_RSI_CALIBRATION }
    val rsiAboveAdaptiveCeilingCount = observations.count { observation -> observation.status == VolumeEventConfirmationStatuses.RSI_ABOVE_ADAPTIVE_CEILING }
    val pastRsiTrendRejectedCount = observations.count { observation -> observation.status == VolumeEventConfirmationStatuses.PAST_RSI_TREND_NOT_CONFIRMED }
    val confirmedSignalCount = targetHitCount + unresolvedCount
    val confirmationDecisions = if (entryMode == VolumeEventEntryModes.FIVE_DAY_PAST_RSI_EARLY_ENTRY) {
        confirmedSignalCount + pastRsiTrendRejectedCount
    } else {
        confirmedSignalCount + noConfirmationCount
    }
    val holdingDays = observations.mapNotNull { observation ->
        observation.takeIf { it.status == VolumeEventConfirmationStatuses.TARGET_HIT || it.status == VolumeEventConfirmationStatuses.UNRESOLVED }
            ?.holdingTradingDays
    }
    return VolumeEventConfirmationBacktestSummary(
        setupCount = observations.size,
        confirmedSignalCount = confirmedSignalCount,
        targetHitCount = targetHitCount,
        unresolvedCount = unresolvedCount,
        noConfirmationCount = noConfirmationCount,
        skippedWhileInPositionCount = skippedCount,
        insufficientForwardDataCount = insufficientForwardDataCount,
        rejectedBearishContextCount = rejectedBearishContextCount,
        insufficientRsiCalibrationCount = insufficientRsiCalibrationCount,
        rsiAboveAdaptiveCeilingCount = rsiAboveAdaptiveCeilingCount,
        pastRsiTrendRejectedCount = pastRsiTrendRejectedCount,
        confirmationRatePct = confirmationDecisions.takeIf { it > 0 }?.let { confirmedSignalCount * 100.0 / it }?.roundTo2(),
        targetHitRatePct = confirmedSignalCount.takeIf { it > 0 }?.let { targetHitCount * 100.0 / it }?.roundTo2(),
        averageHoldingTradingDays = holdingDays.takeIf { it.isNotEmpty() }?.average()?.roundTo2(),
    )
}

private fun Double.roundTo2(): Double = round(this * 100.0) / 100.0
