package com.tradingtool.core.strategy.adaptivebreakout

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.technical.calculateAtr
import com.tradingtool.core.technical.getDoubleValue
import com.tradingtool.core.technical.toTa4jSeries

internal object AdaptiveBreakoutEngine {
    fun evaluate(
        candles: List<DailyCandle>,
        config: AdaptiveBreakoutConfig = AdaptiveBreakoutConfig(),
    ): AdaptiveBreakoutEvaluation? {
        validateConfig(config)
        val orderedCandles = candles.distinctBy(DailyCandle::candleDate).sortedBy(DailyCandle::candleDate)
        if (orderedCandles.size <= config.atrPeriod) return null

        val atrIndicator = orderedCandles.toTa4jSeries("adaptive-breakout").calculateAtr(config.atrPeriod)
        val atrValues = orderedCandles.indices.map { index -> atrIndicator.getDoubleValue(index) }
        val state = ReplayState()
        val steps = orderedCandles.mapIndexed { index, candle ->
            replayCandle(index, candle, orderedCandles, atrValues, config, state)
        }
        val latest = orderedCandles.last()
        val latestAtr = atrValues.last()
        val status = if (steps.last().decision == AdaptiveBreakoutDecision.FRESH_BREAKOUT) {
            AdaptiveBreakoutStatus.FRESH_BREAKOUT
        } else {
            currentStatus(latest, latestAtr, orderedCandles, state, config)
        }
        val ceilingAgeSessions = state.ceiling?.let { ceiling -> orderedCandles.lastIndex - ceiling.confirmedIndex }
        val latestFiftyTwoWeekHigh = fiftyTwoWeekHigh(orderedCandles)

        return AdaptiveBreakoutEvaluation(
            status = status,
            latestDate = latest.candleDate.toString(),
            latestOpen = latest.open,
            latestHigh = latest.high,
            latestLow = latest.low,
            latestClose = latest.close,
            latestVolume = latest.volume,
            latestAtr = latestAtr,
            ceiling = state.ceiling?.toModel(),
            majorCeiling = state.majorCeiling?.toModel(),
            ceilingAgeSessions = ceilingAgeSessions,
            closeVsCeilingPct = state.ceiling?.upperBoundary?.percentageDifference(latest.close),
            closePositionPct = closePositionPct(latest),
            volumeVsTenDayAverage = volumeVsTenDayAverage(orderedCandles),
            fiftyTwoWeekHigh = latestFiftyTwoWeekHigh,
            distanceFromFiftyTwoWeekHighPct = latestFiftyTwoWeekHigh?.percentageDifference(latest.close),
            breakoutEvidence = breakoutEvidence(orderedCandles, state.ceiling?.breakoutDate),
            rawSteps = steps,
        )
    }

    private fun replayCandle(
        index: Int,
        candle: DailyCandle,
        candles: List<DailyCandle>,
        atrValues: List<Double>,
        config: AdaptiveBreakoutConfig,
        state: ReplayState,
    ): AdaptiveBreakoutRawStep {
        updateExtremes(index, candles, state)
        val atr = atrValues[index]
        var decision = AdaptiveBreakoutDecision.BUILDING_STRUCTURE
        var explanation = "Waiting for a meaningful rise and rejection before creating a ceiling."

        val freshBreakout = detectFreshBreakout(index, candles, state.ceiling)
        if (freshBreakout) {
            state.ceiling = state.ceiling?.copy(breakoutDate = candle.candleDate.toString())
            decision = AdaptiveBreakoutDecision.FRESH_BREAKOUT
            explanation = "First close above the active ceiling; the previous close was not above it."
        }

        val confirmedLocalCeiling = if (!freshBreakout) {
            confirmPendingCeiling(index, candle, state)
        } else {
            null
        }
        if (confirmedLocalCeiling != null) {
            decision = AdaptiveBreakoutDecision.CEILING_CONFIRMED
            explanation = confirmedLocalCeiling
        }

        if (!freshBreakout && confirmedLocalCeiling == null && index >= config.atrPeriod && atr > 0.0) {
            val turn = detectTurn(index, candle, candles, atrValues, config, state)
            if (turn != null) {
                decision = turn.first
                explanation = turn.second
            }
        }

        if (!freshBreakout && decision == AdaptiveBreakoutDecision.BUILDING_STRUCTURE) {
            val status = currentStatus(candle, atr, candles, state, config)
            decision = status.toDecision()
            explanation = status.explanation()
        }

        return AdaptiveBreakoutRawStep(
            date = candle.candleDate.toString(),
            open = candle.open,
            high = candle.high,
            low = candle.low,
            close = candle.close,
            volume = candle.volume,
            atr = atr,
            candidateFloor = candles[state.lowIndex].low,
            candidatePeak = candles[state.highIndex].high,
            ceilingAnchor = state.ceiling?.anchorPrice,
            ceilingUpperBoundary = state.ceiling?.upperBoundary,
            majorCeilingUpperBoundary = state.majorCeiling?.upperBoundary,
            decision = decision,
            explanation = explanation,
        )
    }

    private fun updateExtremes(index: Int, candles: List<DailyCandle>, state: ReplayState) {
        val candle = candles[index]
        if (candle.high >= candles[state.highIndex].high) state.highIndex = index
        if (candle.low <= candles[state.lowIndex].low) state.lowIndex = index
    }

    private fun detectFreshBreakout(
        index: Int,
        candles: List<DailyCandle>,
        ceiling: ReplayCeiling?,
    ): Boolean {
        if (index == 0 || ceiling == null || ceiling.breakoutDate != null) return false
        return candles[index].close > ceiling.upperBoundary && candles[index - 1].close <= ceiling.upperBoundary
    }

    private fun detectTurn(
        index: Int,
        candle: DailyCandle,
        candles: List<DailyCandle>,
        atrValues: List<Double>,
        config: AdaptiveBreakoutConfig,
        state: ReplayState,
    ): Pair<AdaptiveBreakoutDecision, String>? {
        return when (state.direction) {
            ReplayDirection.UNKNOWN -> detectInitialDirection(index, candle, candles, atrValues, config, state)
            ReplayDirection.UP -> detectPeakRejection(index, candle, candles, atrValues, config, state)
            ReplayDirection.DOWN -> detectFloorRebound(index, candle, candles, atrValues, config, state)
        }
    }

    private fun detectInitialDirection(
        index: Int,
        candle: DailyCandle,
        candles: List<DailyCandle>,
        atrValues: List<Double>,
        config: AdaptiveBreakoutConfig,
        state: ReplayState,
    ): Pair<AdaptiveBreakoutDecision, String>? {
        val lowPrice = candles[state.lowIndex].low
        val floorReboundDistance = atrValues[index] * config.floorReboundAtrMultiple
        if (candle.close - lowPrice >= floorReboundDistance) {
            state.direction = ReplayDirection.UP
            state.highIndex = index
            return AdaptiveBreakoutDecision.FLOOR_CONFIRMED to
                "Price closed at least ${config.floorReboundAtrMultiple} ATR above $lowPrice, confirming the current floor."
        }

        val highIndex = state.highIndex
        val highPrice = candles[highIndex].high
        val peakRejectionDistance = atrValues[index] * config.peakRejectionAtrMultiple
        if (highPrice - candle.close >= peakRejectionDistance) {
            state.direction = ReplayDirection.DOWN
            state.lowIndex = index
            state.pendingCeiling = buildPendingCeiling(highIndex, index, candles, atrValues, config)
            return AdaptiveBreakoutDecision.CEILING_CANDIDATE to
                "Price rejected $highPrice by at least ${config.peakRejectionAtrMultiple} ATR; one later test is required to confirm a local ceiling."
        }
        return null
    }

    private fun detectPeakRejection(
        index: Int,
        candle: DailyCandle,
        candles: List<DailyCandle>,
        atrValues: List<Double>,
        config: AdaptiveBreakoutConfig,
        state: ReplayState,
    ): Pair<AdaptiveBreakoutDecision, String>? {
        val highIndex = state.highIndex
        val highPrice = candles[highIndex].high
        val peakRejectionDistance = atrValues[index] * config.peakRejectionAtrMultiple
        if (highPrice - candle.close < peakRejectionDistance) return null

        state.direction = ReplayDirection.DOWN
        state.lowIndex = index
        state.pendingCeiling = buildPendingCeiling(highIndex, index, candles, atrValues, config)
        return AdaptiveBreakoutDecision.CEILING_CANDIDATE to
            "Price rejected $highPrice by at least ${config.peakRejectionAtrMultiple} ATR; one later test is required to confirm a local ceiling."
    }

    private fun detectFloorRebound(
        index: Int,
        candle: DailyCandle,
        candles: List<DailyCandle>,
        atrValues: List<Double>,
        config: AdaptiveBreakoutConfig,
        state: ReplayState,
    ): Pair<AdaptiveBreakoutDecision, String>? {
        val lowPrice = candles[state.lowIndex].low
        val floorReboundDistance = atrValues[index] * config.floorReboundAtrMultiple
        if (candle.close - lowPrice < floorReboundDistance) return null

        state.direction = ReplayDirection.UP
        state.highIndex = index
        val activeCeiling = state.ceiling
        val maximumLocalDistance = atrValues[index] * config.maximumLocalCeilingDistanceAtrMultiple
        val distantCeilingDemoted = activeCeiling != null &&
            activeCeiling.upperBoundary - candle.close > maximumLocalDistance
        if (distantCeilingDemoted) {
            state.majorCeiling = nearestHigherCeiling(candle.close, state.majorCeiling, activeCeiling)
            state.ceiling = null
        }
        return AdaptiveBreakoutDecision.FLOOR_CONFIRMED to
            if (distantCeilingDemoted) {
                "Price closed at least ${config.floorReboundAtrMultiple} ATR above $lowPrice; the distant ceiling moved to major overhead while a local ceiling forms."
            } else {
                "Price closed at least ${config.floorReboundAtrMultiple} ATR above $lowPrice; the system is tracking the rebound."
            }
    }

    private fun buildPendingCeiling(
        anchorIndex: Int,
        rejectedIndex: Int,
        candles: List<DailyCandle>,
        atrValues: List<Double>,
        config: AdaptiveBreakoutConfig,
    ): PendingCeiling {
        val anchor = candles[anchorIndex]
        val atrAtAnchor = atrValues[anchorIndex]
        return PendingCeiling(
            anchorIndex = anchorIndex,
            anchorDate = anchor.candleDate.toString(),
            rejectedIndex = rejectedIndex,
            anchorPrice = anchor.high,
            upperBoundary = anchor.high + atrAtAnchor * config.ceilingWidthAtrMultiple,
            atrAtAnchor = atrAtAnchor,
        )
    }

    private fun confirmPendingCeiling(
        index: Int,
        candle: DailyCandle,
        state: ReplayState,
    ): String? {
        val pending = state.pendingCeiling ?: return null
        if (index <= pending.rejectedIndex) return null
        if (candle.close > pending.upperBoundary) {
            state.pendingCeiling = null
            return null
        }
        if (candle.high < pending.lowerBoundary) return null

        val localCeiling = pending.toCeiling(index, candle.candleDate.toString())
        state.majorCeiling = nearestHigherCeiling(
            localCeiling.upperBoundary,
            state.majorCeiling,
            state.ceiling,
        )
        state.ceiling = localCeiling
        state.pendingCeiling = null
        return "Price returned to the rejected ${pending.anchorPrice} area; the second test confirms the local ceiling."
    }

    private fun nearestHigherCeiling(
        referencePrice: Double,
        vararg ceilings: ReplayCeiling?,
    ): ReplayCeiling? = ceilings
        .filterNotNull()
        .filter { ceiling -> ceiling.upperBoundary > referencePrice }
        .minByOrNull(ReplayCeiling::upperBoundary)

    private fun currentStatus(
        candle: DailyCandle,
        atr: Double,
        candles: List<DailyCandle>,
        state: ReplayState,
        config: AdaptiveBreakoutConfig,
    ): AdaptiveBreakoutStatus {
        val ceiling = state.ceiling
        if (ceiling == null) {
            val floorPrice = candles[state.lowIndex].low
            return if (atr > 0.0 && candle.close - floorPrice >= atr * config.strongReboundAtrMultiple) {
                AdaptiveBreakoutStatus.STRONG_REBOUND
            } else {
                AdaptiveBreakoutStatus.NO_CEILING
            }
        }
        if (ceiling.breakoutDate != null) return AdaptiveBreakoutStatus.BREAKOUT_CONTINUATION
        return if (candle.close >= ceiling.anchorPrice) {
            AdaptiveBreakoutStatus.TESTING_CEILING
        } else {
            AdaptiveBreakoutStatus.BELOW_CEILING
        }
    }

    private fun AdaptiveBreakoutStatus.toDecision(): AdaptiveBreakoutDecision = when (this) {
        AdaptiveBreakoutStatus.NO_CEILING -> AdaptiveBreakoutDecision.BUILDING_STRUCTURE
        AdaptiveBreakoutStatus.BELOW_CEILING -> AdaptiveBreakoutDecision.BELOW_CEILING
        AdaptiveBreakoutStatus.TESTING_CEILING -> AdaptiveBreakoutDecision.CEILING_TEST
        AdaptiveBreakoutStatus.STRONG_REBOUND -> AdaptiveBreakoutDecision.STRONG_REBOUND
        AdaptiveBreakoutStatus.FRESH_BREAKOUT -> AdaptiveBreakoutDecision.FRESH_BREAKOUT
        AdaptiveBreakoutStatus.BREAKOUT_CONTINUATION -> AdaptiveBreakoutDecision.BREAKOUT_CONTINUATION
    }

    private fun AdaptiveBreakoutStatus.explanation(): String = when (this) {
        AdaptiveBreakoutStatus.NO_CEILING -> "No rejected rebound has created a ceiling yet."
        AdaptiveBreakoutStatus.BELOW_CEILING -> "Close remains below the active ceiling area."
        AdaptiveBreakoutStatus.TESTING_CEILING -> "Close is inside the active ceiling area but has not cleared its upper boundary."
        AdaptiveBreakoutStatus.STRONG_REBOUND -> "Price has advanced at least two ATR from the floor, but no local ceiling is confirmed."
        AdaptiveBreakoutStatus.FRESH_BREAKOUT -> "First completed close above the active ceiling."
        AdaptiveBreakoutStatus.BREAKOUT_CONTINUATION -> "The active ceiling was already broken on an earlier session."
    }

    private fun closePositionPct(candle: DailyCandle): Double? {
        val range = candle.high - candle.low
        return if (range > 0.0) ((candle.close - candle.low) / range) * 100.0 else null
    }

    private fun volumeVsTenDayAverage(candles: List<DailyCandle>): Double? {
        if (candles.size < 11) return null
        val average = candles.dropLast(1).takeLast(10).map(DailyCandle::volume).average()
        return if (average > 0.0) candles.last().volume / average else null
    }

    private fun breakoutEvidence(
        candles: List<DailyCandle>,
        breakoutDate: String?,
    ): AdaptiveBreakoutConfirmationEvidence? {
        if (breakoutDate == null) return null
        val breakoutIndex = candles.indexOfFirst { candle -> candle.candleDate.toString() == breakoutDate }
        if (breakoutIndex < 0) return null
        val breakoutCandle = candles[breakoutIndex]
        val priorTen = candles.subList(maxOf(0, breakoutIndex - 10), breakoutIndex)
        val averageVolume = priorTen.takeIf { it.size == 10 }?.map(DailyCandle::volume)?.average()
        val volumeMultiple = averageVolume?.takeIf { it > 0.0 }?.let { breakoutCandle.volume / it }
        val periodHigh = candles
            .subList(maxOf(0, breakoutIndex - FIFTY_TWO_WEEK_SESSIONS + 1), breakoutIndex + 1)
            .maxOfOrNull(DailyCandle::high)
        return AdaptiveBreakoutConfirmationEvidence(
            date = breakoutDate,
            closePositionPct = closePositionPct(breakoutCandle),
            volumeVsTenDayAverage = volumeMultiple,
            distanceFromFiftyTwoWeekHighPct = periodHigh?.percentageDifference(breakoutCandle.close),
        )
    }

    private fun fiftyTwoWeekHigh(candles: List<DailyCandle>): Double? =
        candles.takeLast(FIFTY_TWO_WEEK_SESSIONS).maxOfOrNull(DailyCandle::high)

    private fun Double.percentageDifference(value: Double): Double? =
        if (this > 0.0) ((value - this) / this) * 100.0 else null

    private fun validateConfig(config: AdaptiveBreakoutConfig) {
        require(config.atrPeriod > 0) { "atrPeriod must be greater than zero." }
        require(config.floorReboundAtrMultiple > 0.0) { "floorReboundAtrMultiple must be greater than zero." }
        require(config.peakRejectionAtrMultiple > 0.0) { "peakRejectionAtrMultiple must be greater than zero." }
        require(config.ceilingWidthAtrMultiple >= 0.0) { "ceilingWidthAtrMultiple must not be negative." }
        require(config.maximumLocalCeilingDistanceAtrMultiple > 0.0) {
            "maximumLocalCeilingDistanceAtrMultiple must be greater than zero."
        }
        require(config.strongReboundAtrMultiple > 0.0) { "strongReboundAtrMultiple must be greater than zero." }
    }

    private enum class ReplayDirection { UNKNOWN, UP, DOWN }

    private data class ReplayState(
        var direction: ReplayDirection = ReplayDirection.UNKNOWN,
        var highIndex: Int = 0,
        var lowIndex: Int = 0,
        var ceiling: ReplayCeiling? = null,
        var majorCeiling: ReplayCeiling? = null,
        var pendingCeiling: PendingCeiling? = null,
    )

    private data class PendingCeiling(
        val anchorIndex: Int,
        val anchorDate: String,
        val rejectedIndex: Int,
        val anchorPrice: Double,
        val upperBoundary: Double,
        val atrAtAnchor: Double,
    ) {
        val lowerBoundary: Double
            get() = anchorPrice - (upperBoundary - anchorPrice)

        fun toCeiling(confirmedIndex: Int, confirmedDate: String): ReplayCeiling = ReplayCeiling(
            anchorIndex = anchorIndex,
            anchorDate = anchorDate,
            confirmedIndex = confirmedIndex,
            confirmedDate = confirmedDate,
            anchorPrice = anchorPrice,
            upperBoundary = upperBoundary,
            atrAtAnchor = atrAtAnchor,
        )
    }

    private data class ReplayCeiling(
        val anchorIndex: Int,
        val anchorDate: String,
        val confirmedIndex: Int,
        val confirmedDate: String,
        val anchorPrice: Double,
        val upperBoundary: Double,
        val atrAtAnchor: Double,
        val breakoutDate: String? = null,
    ) {
        val lowerBoundary: Double
            get() = anchorPrice - (upperBoundary - anchorPrice)

        fun toModel(): AdaptiveBreakoutCeiling = AdaptiveBreakoutCeiling(
            anchorDate = anchorDate,
            confirmedDate = confirmedDate,
            anchorPrice = anchorPrice,
            upperBoundary = upperBoundary,
            atrAtAnchor = atrAtAnchor,
            breakoutDate = breakoutDate,
        )
    }

    private const val FIFTY_TWO_WEEK_SESSIONS = 252
}
