package com.tradingtool.core.strategy.volumeeventbacktest

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.technical.calculateRsiValues
import java.time.LocalDate
import kotlin.math.round

class VolumeEventConfirmationBacktestEngine {
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
            val priorVolumes = sortedCandles
                .subList(maxOf(0, eventIndex - config.volumeBaselineDays), eventIndex)
                .map { candle -> candle.volume.toDouble() }
            if (priorVolumes.size != config.volumeBaselineDays) continue

            val priorVolumeAverage = priorVolumes.average()
            if (priorVolumeAverage <= 0.0 || !priorVolumeAverage.isFinite()) continue

            val volumeRatio = eventCandle.volume.toDouble() / priorVolumeAverage
            val eventRsi = rsiValues[eventIndex]
            if (volumeRatio < config.volumeShockMultiplier || eventRsi > config.lowRsiThreshold) continue

            if (eventIndex < nextEligibleEventIndex) {
                observations += buildSkippedObservation(eventCandle, priorVolumeAverage, volumeRatio, eventRsi)
                continue
            }

            val confirmationIndex = eventIndex + config.confirmationDays
            val entryIndex = confirmationIndex + 1
            if (entryIndex > sortedCandles.lastIndex) {
                observations += buildInsufficientObservation(eventCandle, priorVolumeAverage, volumeRatio, eventRsi)
                continue
            }

            val confirmationCandle = sortedCandles[confirmationIndex]
            val confirmationRsi = rsiValues[confirmationIndex]
            val rsiChangePoints = confirmationRsi - eventRsi
            if (confirmationRsi <= eventRsi) {
                observations += buildNoConfirmationObservation(
                    eventCandle = eventCandle,
                    priorVolumeAverage = priorVolumeAverage,
                    volumeRatio = volumeRatio,
                    eventRsi = eventRsi,
                    confirmationCandle = confirmationCandle,
                    confirmationRsi = confirmationRsi,
                    rsiChangePoints = rsiChangePoints,
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
                confirmationDate = confirmationCandle.candleDate.toString(),
                confirmationRsi = confirmationRsi.roundTo2(),
                rsiChangePoints = rsiChangePoints.roundTo2(),
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
            summary = summarizeVolumeEventObservations(observations),
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
    ): VolumeEventConfirmationObservation = baseObservation(
        eventCandle = eventCandle,
        priorVolumeAverage = priorVolumeAverage,
        volumeRatio = volumeRatio,
        eventRsi = eventRsi,
        status = VolumeEventConfirmationStatuses.SKIPPED_WHILE_IN_POSITION,
    )

    private fun buildInsufficientObservation(
        eventCandle: DailyCandle,
        priorVolumeAverage: Double,
        volumeRatio: Double,
        eventRsi: Double,
    ): VolumeEventConfirmationObservation = baseObservation(
        eventCandle = eventCandle,
        priorVolumeAverage = priorVolumeAverage,
        volumeRatio = volumeRatio,
        eventRsi = eventRsi,
        status = VolumeEventConfirmationStatuses.INSUFFICIENT_FORWARD_DATA,
    )

    private fun buildNoConfirmationObservation(
        eventCandle: DailyCandle,
        priorVolumeAverage: Double,
        volumeRatio: Double,
        eventRsi: Double,
        confirmationCandle: DailyCandle,
        confirmationRsi: Double,
        rsiChangePoints: Double,
    ): VolumeEventConfirmationObservation = baseObservation(
        eventCandle = eventCandle,
        priorVolumeAverage = priorVolumeAverage,
        volumeRatio = volumeRatio,
        eventRsi = eventRsi,
        status = VolumeEventConfirmationStatuses.NO_CONFIRMATION,
    ).copy(
        confirmationDate = confirmationCandle.candleDate.toString(),
        confirmationRsi = confirmationRsi.roundTo2(),
        rsiChangePoints = rsiChangePoints.roundTo2(),
    )

    private fun baseObservation(
        eventCandle: DailyCandle,
        priorVolumeAverage: Double,
        volumeRatio: Double,
        eventRsi: Double,
        status: String,
    ): VolumeEventConfirmationObservation = VolumeEventConfirmationObservation(
        symbol = eventCandle.symbol,
        eventDate = eventCandle.candleDate.toString(),
        eventClose = eventCandle.close.roundTo2(),
        eventVolume = eventCandle.volume,
        priorVolumeAverage = priorVolumeAverage.roundTo2(),
        volumeRatio = volumeRatio.roundTo2(),
        eventRsi = eventRsi.roundTo2(),
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

    private fun validateConfig(config: VolumeEventConfirmationBacktestConfig) {
        require(config.volumeBaselineDays > 0) { "volumeBaselineDays must be positive." }
        require(config.volumeShockMultiplier > 0.0) { "volumeShockMultiplier must be positive." }
        require(config.lowRsiThreshold in 0.0..100.0) { "lowRsiThreshold must be between 0 and 100." }
        require(config.confirmationDays > 0) { "confirmationDays must be positive." }
        require(config.targetPct > 0.0) { "targetPct must be positive." }
        require(config.maxHoldingDays > 0) { "maxHoldingDays must be positive." }
        require(config.rsiPeriod > 0) { "rsiPeriod must be positive." }
    }
}

internal fun summarizeVolumeEventObservations(
    observations: List<VolumeEventConfirmationObservation>,
): VolumeEventConfirmationBacktestSummary {
    val targetHitCount = observations.count { observation -> observation.status == VolumeEventConfirmationStatuses.TARGET_HIT }
    val unresolvedCount = observations.count { observation -> observation.status == VolumeEventConfirmationStatuses.UNRESOLVED }
    val noConfirmationCount = observations.count { observation -> observation.status == VolumeEventConfirmationStatuses.NO_CONFIRMATION }
    val skippedCount = observations.count { observation -> observation.status == VolumeEventConfirmationStatuses.SKIPPED_WHILE_IN_POSITION }
    val insufficientForwardDataCount = observations.count { observation -> observation.status == VolumeEventConfirmationStatuses.INSUFFICIENT_FORWARD_DATA }
    val confirmedSignalCount = targetHitCount + unresolvedCount
    val confirmationDecisions = confirmedSignalCount + noConfirmationCount
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
        confirmationRatePct = confirmationDecisions.takeIf { it > 0 }?.let { confirmedSignalCount * 100.0 / it }?.roundTo2(),
        targetHitRatePct = confirmedSignalCount.takeIf { it > 0 }?.let { targetHitCount * 100.0 / it }?.roundTo2(),
        averageHoldingTradingDays = holdingDays.takeIf { it.isNotEmpty() }?.average()?.roundTo2(),
    )
}

private fun Double.roundTo2(): Double = round(this * 100.0) / 100.0
