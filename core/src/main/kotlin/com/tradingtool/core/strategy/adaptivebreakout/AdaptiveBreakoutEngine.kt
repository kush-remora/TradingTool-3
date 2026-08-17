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
        val status = when (steps.last().decision) {
            AdaptiveBreakoutDecision.FRESH_BREAKOUT -> AdaptiveBreakoutStatus.FRESH_BREAKOUT
            AdaptiveBreakoutDecision.EARLY_BREAKOUT -> AdaptiveBreakoutStatus.EARLY_BREAKOUT
            else -> currentStatus(latest, orderedCandles, atrValues, state, config)
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
            breakoutEvidence = breakoutEvidence(orderedCandles, state.latestBreakoutDate),
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
        val atr = atrValues[index]
        val ceilingAtStart = state.ceiling
        val freshBreakout = detectFreshBreakout(index, candles, ceilingAtStart)
        val events = mutableListOf<ReplayEvent>()
        if (freshBreakout) {
            val breakoutDate = candle.candleDate.toString()
            state.ceiling = ceilingAtStart?.copy(breakoutDate = breakoutDate, readyForRetest = false)
            state.latestBreakoutDate = breakoutDate
            events += ReplayEvent(
                AdaptiveBreakoutDecision.FRESH_BREAKOUT,
                "First close above the active ceiling; the previous close was not above it.",
            )
        }
        if (!freshBreakout) {
            detectEarlyBreakout(index, candle, candles, atrValues, config, state.compactCeilingCandidate)
                ?.let(events::add)
        }

        if (index == config.atrPeriod) {
            state.highIndex = index
            state.lowIndex = index
        } else {
            updateExtremes(index, candles, state)
        }
        if (index >= config.atrPeriod && atr.isUsableAtr()) {
            val turn = detectTurn(index, candle, candles, atrValues, config, state)
            if (turn != null) {
                events += turn
            }
        }

        if (!freshBreakout && state.ceiling == ceilingAtStart) {
            val ceilingTest = updateCeilingTest(index, candle, state)
            if (ceilingTest != null) events += ceilingTest
        }

        val primaryEvent = events.minByOrNull { event -> event.decision.priority }
        val status = currentStatus(candle, candles, atrValues, state, config)
        val decision = primaryEvent?.decision ?: status.toDecision()
        val explanation = events
            .takeIf { recordedEvents -> recordedEvents.isNotEmpty() }
            ?.joinToString(" Also: ") { event -> event.explanation }
            ?: status.explanation()
        val compactEvent = events.firstOrNull { event -> event.decision == AdaptiveBreakoutDecision.EARLY_BREAKOUT }
            ?: events.lastOrNull { event -> event.compactCandidateAnchor != null }
        val compactCandidate = state.compactCeilingCandidate

        return AdaptiveBreakoutRawStep(
            date = candle.candleDate.toString(),
            open = candle.open,
            high = candle.high,
            low = candle.low,
            close = candle.close,
            volume = candle.volume,
            atr = atr,
            candidateFloor = candles[state.lowIndex].low,
            candidateFloorAtr = atrValues[state.lowIndex],
            candidatePeak = candles[state.highIndex].high,
            candidatePeakAtr = atrValues[state.highIndex],
            ceilingAnchor = state.ceiling?.anchorPrice,
            ceilingUpperBoundary = state.ceiling?.upperBoundary,
            majorCeilingUpperBoundary = state.majorCeiling?.upperBoundary,
            ceilingTestCount = state.ceiling?.testCount,
            ceilingType = state.ceiling?.type,
            breakoutBoundary = when {
                freshBreakout -> ceilingAtStart?.upperBoundary
                else -> events.firstOrNull { event -> event.decision == AdaptiveBreakoutDecision.EARLY_BREAKOUT }
                    ?.breakoutBoundary
            },
            compactCeilingCandidate = compactEvent?.compactCandidateAnchor
                ?: compactCandidate?.let { candidate -> candles[candidate.anchorIndex].high },
            compactCeilingConfirmationCount = compactEvent?.compactConfirmationCount
                ?: compactCandidate?.confirmationCount,
            decision = decision,
            explanation = explanation,
        )
    }

    private fun updateExtremes(index: Int, candles: List<DailyCandle>, state: ReplayState) {
        val candle = candles[index]
        if (candle.high > candles[state.highIndex].high) state.highIndex = index
        if (candle.low < candles[state.lowIndex].low) state.lowIndex = index
    }

    private fun detectFreshBreakout(
        index: Int,
        candles: List<DailyCandle>,
        ceiling: ReplayCeiling?,
    ): Boolean {
        if (index == 0 || ceiling == null || ceiling.breakoutDate != null) return false
        return candles[index].close > ceiling.upperBoundary && candles[index - 1].close <= ceiling.upperBoundary
    }

    private fun detectEarlyBreakout(
        index: Int,
        candle: DailyCandle,
        candles: List<DailyCandle>,
        atrValues: List<Double>,
        config: AdaptiveBreakoutConfig,
        candidate: CompactCeilingCandidate?,
    ): ReplayEvent? {
        if (index == 0 || candidate == null || index <= candidate.anchorIndex) return null
        val candidatePeak = candles[candidate.anchorIndex].high
        val candidateAtr = atrValues[candidate.anchorIndex]
        if (!candidateAtr.isUsableAtr()) return null
        val earlyBoundary = candidatePeak + candidateAtr * config.earlyBreakoutBufferAtrMultiple
        if (candle.close <= earlyBoundary || candles[index - 1].close > earlyBoundary) return null

        return ReplayEvent(
            decision = AdaptiveBreakoutDecision.EARLY_BREAKOUT,
            explanation = "Watch only: the close cleared the unconfirmed $candidatePeak compact peak plus ${config.earlyBreakoutBufferAtrMultiple} ATR. The confirmed ceiling and fresh-breakout state are unchanged.",
            compactCandidateAnchor = candidatePeak,
            compactConfirmationCount = candidate.confirmationCount,
            breakoutBoundary = earlyBoundary,
        )
    }

    private fun detectTurn(
        index: Int,
        candle: DailyCandle,
        candles: List<DailyCandle>,
        atrValues: List<Double>,
        config: AdaptiveBreakoutConfig,
        state: ReplayState,
    ): ReplayEvent? {
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
    ): ReplayEvent? {
        val lowPrice = candles[state.lowIndex].low
        val floorAtr = atrValues[state.lowIndex]
        val highIndex = state.highIndex
        val highPrice = candles[highIndex].high
        val peakAtr = atrValues[highIndex]
        val floorConfirmed = floorAtr.isUsableAtr() &&
            candle.close - lowPrice >= floorAtr * config.floorReboundAtrMultiple
        val ceilingConfirmed = peakAtr.isUsableAtr() &&
            highPrice - candle.close >= peakAtr * config.peakRejectionAtrMultiple

        if (floorConfirmed && ceilingConfirmed) {
            return ReplayEvent(
                AdaptiveBreakoutDecision.AMBIGUOUS_OUTSIDE_DAY,
                "This candle confirms both an upward and downward turn from anchored extremes; daily OHLC cannot prove which structure came first, so direction stays unchanged.",
            )
        }
        if (floorConfirmed) {
            state.direction = ReplayDirection.UP
            state.highIndex = index
            return ReplayEvent(
                AdaptiveBreakoutDecision.FLOOR_CONFIRMED,
                "Price closed at least ${config.floorReboundAtrMultiple} ATR above $lowPrice using the floor-day ATR $floorAtr, confirming the current floor.",
            )
        }
        if (ceilingConfirmed) {
            state.direction = ReplayDirection.DOWN
            state.lowIndex = index
            activateCeiling(
                buildCeiling(
                    highIndex,
                    index,
                    AdaptiveBreakoutCeilingType.STRONG_REJECTION,
                    candles,
                    atrValues,
                    config,
                ),
                state,
            )
            return ReplayEvent(
                AdaptiveBreakoutDecision.CEILING_CONFIRMED,
                "Price rejected $highPrice by at least ${config.peakRejectionAtrMultiple} ATR using the peak-day ATR $peakAtr; the peak is now the active ceiling and later returns increase its test count.",
            )
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
    ): ReplayEvent? {
        val highIndex = state.highIndex
        val highPrice = candles[highIndex].high
        val peakAtr = atrValues[highIndex]
        if (!peakAtr.isUsableAtr()) return null
        val peakRejectionDistance = peakAtr * config.peakRejectionAtrMultiple
        if (highPrice - candle.close >= peakRejectionDistance) {
            return confirmStrongCeiling(index, highIndex, highPrice, peakAtr, candles, atrValues, config, state)
        }

        val existingCandidate = state.compactCeilingCandidate
        if (existingCandidate != null && existingCandidate.anchorIndex == highIndex) {
            return advanceCompactCeilingCandidate(
                index,
                candle,
                highPrice,
                existingCandidate,
                candles,
                atrValues,
                config,
                state,
            )
        }

        state.compactCeilingCandidate = null
        val compactRejectionDistance = peakAtr * config.compactPeakRejectionAtrMultiple
        if (highPrice - candle.close < compactRejectionDistance) return null

        state.compactCeilingCandidate = CompactCeilingCandidate(
            anchorIndex = highIndex,
            confirmationCount = 0,
            lowIndex = index,
        )
        return ReplayEvent(
            decision = AdaptiveBreakoutDecision.COMPACT_CEILING_CANDIDATE,
            explanation = "Price rejected $highPrice by at least ${config.compactPeakRejectionAtrMultiple} ATR. It is only a candidate until ${config.compactCeilingConfirmationSessions} later sessions remain below that peak.",
            compactCandidateAnchor = highPrice,
            compactConfirmationCount = 0,
        )
    }

    private fun confirmStrongCeiling(
        index: Int,
        highIndex: Int,
        highPrice: Double,
        peakAtr: Double,
        candles: List<DailyCandle>,
        atrValues: List<Double>,
        config: AdaptiveBreakoutConfig,
        state: ReplayState,
    ): ReplayEvent {
        state.direction = ReplayDirection.DOWN
        state.lowIndex = index
        state.compactCeilingCandidate = null
        activateCeiling(
            buildCeiling(
                highIndex,
                index,
                AdaptiveBreakoutCeilingType.STRONG_REJECTION,
                candles,
                atrValues,
                config,
            ),
            state,
        )
        return ReplayEvent(
            AdaptiveBreakoutDecision.CEILING_CONFIRMED,
            "Price rejected $highPrice by at least ${config.peakRejectionAtrMultiple} ATR using the peak-day ATR $peakAtr; the peak is now the active strong-rejection ceiling.",
        )
    }

    private fun advanceCompactCeilingCandidate(
        index: Int,
        candle: DailyCandle,
        highPrice: Double,
        candidate: CompactCeilingCandidate,
        candles: List<DailyCandle>,
        atrValues: List<Double>,
        config: AdaptiveBreakoutConfig,
        state: ReplayState,
    ): ReplayEvent {
        val lowIndex = if (candle.low < candles[candidate.lowIndex].low) index else candidate.lowIndex
        val confirmationCount = candidate.confirmationCount + 1
        if (confirmationCount < config.compactCeilingConfirmationSessions) {
            state.compactCeilingCandidate = candidate.copy(
                confirmationCount = confirmationCount,
                lowIndex = lowIndex,
            )
            return ReplayEvent(
                decision = AdaptiveBreakoutDecision.COMPACT_CEILING_CANDIDATE,
                explanation = "$highPrice remains a compact-ceiling candidate; $confirmationCount of ${config.compactCeilingConfirmationSessions} later sessions has stayed below the peak.",
                compactCandidateAnchor = highPrice,
                compactConfirmationCount = confirmationCount,
            )
        }

        state.direction = ReplayDirection.DOWN
        state.lowIndex = lowIndex
        state.compactCeilingCandidate = null
        activateCeiling(
            buildCeiling(
                candidate.anchorIndex,
                index,
                AdaptiveBreakoutCeilingType.COMPACT_RANGE,
                candles,
                atrValues,
                config,
            ),
            state,
        )
        return ReplayEvent(
            decision = AdaptiveBreakoutDecision.CEILING_CONFIRMED,
            explanation = "$highPrice is now a compact ceiling: the first rejection was at least ${config.compactPeakRejectionAtrMultiple} ATR and $confirmationCount later sessions stayed below that peak.",
            compactCandidateAnchor = highPrice,
            compactConfirmationCount = confirmationCount,
        )
    }

    private fun detectFloorRebound(
        index: Int,
        candle: DailyCandle,
        candles: List<DailyCandle>,
        atrValues: List<Double>,
        config: AdaptiveBreakoutConfig,
        state: ReplayState,
    ): ReplayEvent? {
        val lowPrice = candles[state.lowIndex].low
        val floorAtr = atrValues[state.lowIndex]
        if (!floorAtr.isUsableAtr()) return null
        val floorReboundDistance = floorAtr * config.floorReboundAtrMultiple
        if (candle.close - lowPrice < floorReboundDistance) return null

        state.direction = ReplayDirection.UP
        state.highIndex = index
        state.compactCeilingCandidate = null
        val activeCeiling = state.ceiling
        val maximumLocalDistance = floorAtr * config.maximumLocalCeilingDistanceAtrMultiple
        val distantCeilingDemoted = activeCeiling != null &&
            activeCeiling.upperBoundary - candle.close > maximumLocalDistance
        if (distantCeilingDemoted) {
            state.majorCeiling = nearestHigherCeiling(candle.close, state.majorCeiling, activeCeiling)
            state.ceiling = null
        }
        return ReplayEvent(
            AdaptiveBreakoutDecision.FLOOR_CONFIRMED,
            if (distantCeilingDemoted) {
                "Price closed at least ${config.floorReboundAtrMultiple} ATR above $lowPrice using the floor-day ATR $floorAtr; the distant ceiling moved to major overhead while a local ceiling forms."
            } else {
                "Price closed at least ${config.floorReboundAtrMultiple} ATR above $lowPrice using the floor-day ATR $floorAtr; the system is tracking the rebound."
            },
        )
    }

    private fun buildCeiling(
        anchorIndex: Int,
        confirmedIndex: Int,
        type: AdaptiveBreakoutCeilingType,
        candles: List<DailyCandle>,
        atrValues: List<Double>,
        config: AdaptiveBreakoutConfig,
    ): ReplayCeiling {
        val anchor = candles[anchorIndex]
        val atrAtAnchor = atrValues[anchorIndex]
        return ReplayCeiling(
            anchorIndex = anchorIndex,
            anchorDate = anchor.candleDate.toString(),
            confirmedIndex = confirmedIndex,
            confirmedDate = candles[confirmedIndex].candleDate.toString(),
            anchorPrice = anchor.high,
            upperBoundary = anchor.high + atrAtAnchor * config.ceilingWidthAtrMultiple,
            atrAtAnchor = atrAtAnchor,
            type = type,
            testCount = 1,
            lastTestDate = candles[confirmedIndex].candleDate.toString(),
        )
    }

    private fun activateCeiling(
        newCeiling: ReplayCeiling,
        state: ReplayState,
    ) {
        val currentCeiling = state.ceiling
        val shouldMerge = currentCeiling != null && currentCeiling.breakoutDate == null &&
            currentCeiling.overlaps(newCeiling)
        state.ceiling = if (currentCeiling != null && shouldMerge) {
            mergeCeilings(currentCeiling, newCeiling)
        } else {
            state.majorCeiling = nearestHigherCeiling(
                newCeiling.upperBoundary,
                state.majorCeiling,
                currentCeiling,
            )
            newCeiling
        }
    }

    private fun mergeCeilings(current: ReplayCeiling, incoming: ReplayCeiling): ReplayCeiling {
        val strongerAnchor = if (incoming.anchorPrice >= current.anchorPrice) incoming else current
        return strongerAnchor.copy(
            testCount = current.testCount + 1,
            lastTestDate = incoming.confirmedDate,
            readyForRetest = false,
            breakoutDate = null,
        )
    }

    private fun updateCeilingTest(
        index: Int,
        candle: DailyCandle,
        state: ReplayState,
    ): ReplayEvent? {
        val ceiling = state.ceiling ?: return null
        if (ceiling.breakoutDate != null || index <= ceiling.confirmedIndex) return null
        if (!ceiling.readyForRetest) {
            if (candle.close < ceiling.lowerBoundary) {
                state.ceiling = ceiling.copy(readyForRetest = true)
            }
            return null
        }
        if (candle.high < ceiling.lowerBoundary || candle.close > ceiling.upperBoundary) return null

        val updated = ceiling.copy(
            testCount = ceiling.testCount + 1,
            lastTestDate = candle.candleDate.toString(),
            readyForRetest = false,
        )
        state.ceiling = updated
        return ReplayEvent(
            AdaptiveBreakoutDecision.CEILING_TEST,
            "Price returned to the ${ceiling.anchorPrice} ceiling area and failed to close above it; this is test ${updated.testCount}.",
        )
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
        candles: List<DailyCandle>,
        atrValues: List<Double>,
        state: ReplayState,
        config: AdaptiveBreakoutConfig,
    ): AdaptiveBreakoutStatus {
        val ceiling = state.ceiling
        if (ceiling == null) {
            val floorPrice = candles[state.lowIndex].low
            val floorAtr = atrValues[state.lowIndex]
            return if (floorAtr.isUsableAtr() &&
                candle.close - floorPrice >= floorAtr * config.strongReboundAtrMultiple
            ) {
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
        AdaptiveBreakoutStatus.EARLY_BREAKOUT -> AdaptiveBreakoutDecision.EARLY_BREAKOUT
        AdaptiveBreakoutStatus.FRESH_BREAKOUT -> AdaptiveBreakoutDecision.FRESH_BREAKOUT
        AdaptiveBreakoutStatus.BREAKOUT_CONTINUATION -> AdaptiveBreakoutDecision.BREAKOUT_CONTINUATION
    }

    private fun AdaptiveBreakoutStatus.explanation(): String = when (this) {
        AdaptiveBreakoutStatus.NO_CEILING -> "No rejected rebound has created a ceiling yet."
        AdaptiveBreakoutStatus.BELOW_CEILING -> "Close remains below the active ceiling area."
        AdaptiveBreakoutStatus.TESTING_CEILING -> "Close is inside the active ceiling area but has not cleared its upper boundary."
        AdaptiveBreakoutStatus.STRONG_REBOUND -> "Price has advanced at least two ATR from the floor, but no local ceiling is confirmed."
        AdaptiveBreakoutStatus.EARLY_BREAKOUT -> "A completed close cleared an unconfirmed compact ceiling; watch only until the main structure confirms."
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

    private fun Double.isUsableAtr(): Boolean = isFinite() && this > 0.0

    private fun validateConfig(config: AdaptiveBreakoutConfig) {
        require(config.atrPeriod > 0) { "atrPeriod must be greater than zero." }
        require(config.floorReboundAtrMultiple > 0.0) { "floorReboundAtrMultiple must be greater than zero." }
        require(config.peakRejectionAtrMultiple > 0.0) { "peakRejectionAtrMultiple must be greater than zero." }
        require(config.compactPeakRejectionAtrMultiple > 0.0) {
            "compactPeakRejectionAtrMultiple must be greater than zero."
        }
        require(config.compactPeakRejectionAtrMultiple < config.peakRejectionAtrMultiple) {
            "compactPeakRejectionAtrMultiple must be lower than peakRejectionAtrMultiple."
        }
        require(config.compactCeilingConfirmationSessions > 0) {
            "compactCeilingConfirmationSessions must be greater than zero."
        }
        require(config.earlyBreakoutBufferAtrMultiple >= 0.0) {
            "earlyBreakoutBufferAtrMultiple must not be negative."
        }
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
        var latestBreakoutDate: String? = null,
        var compactCeilingCandidate: CompactCeilingCandidate? = null,
    )

    private data class CompactCeilingCandidate(
        val anchorIndex: Int,
        val confirmationCount: Int,
        val lowIndex: Int,
    )

    private data class ReplayCeiling(
        val anchorIndex: Int,
        val anchorDate: String,
        val confirmedIndex: Int,
        val confirmedDate: String,
        val anchorPrice: Double,
        val upperBoundary: Double,
        val atrAtAnchor: Double,
        val type: AdaptiveBreakoutCeilingType,
        val testCount: Int,
        val lastTestDate: String?,
        val readyForRetest: Boolean = false,
        val breakoutDate: String? = null,
    ) {
        val lowerBoundary: Double
            get() = anchorPrice - (upperBoundary - anchorPrice)

        fun overlaps(other: ReplayCeiling): Boolean =
            lowerBoundary <= other.upperBoundary && other.lowerBoundary <= upperBoundary

        fun toModel(): AdaptiveBreakoutCeiling = AdaptiveBreakoutCeiling(
            anchorDate = anchorDate,
            confirmedDate = confirmedDate,
            anchorPrice = anchorPrice,
            upperBoundary = upperBoundary,
            atrAtAnchor = atrAtAnchor,
            type = type,
            testCount = testCount,
            lastTestDate = lastTestDate,
            breakoutDate = breakoutDate,
        )
    }

    private data class ReplayEvent(
        val decision: AdaptiveBreakoutDecision,
        val explanation: String,
        val compactCandidateAnchor: Double? = null,
        val compactConfirmationCount: Int? = null,
        val breakoutBoundary: Double? = null,
    )

    private val AdaptiveBreakoutDecision.priority: Int
        get() = when (this) {
            AdaptiveBreakoutDecision.FRESH_BREAKOUT -> 0
            AdaptiveBreakoutDecision.EARLY_BREAKOUT -> 1
            AdaptiveBreakoutDecision.AMBIGUOUS_OUTSIDE_DAY -> 2
            AdaptiveBreakoutDecision.CEILING_CONFIRMED -> 3
            AdaptiveBreakoutDecision.FLOOR_CONFIRMED -> 4
            AdaptiveBreakoutDecision.COMPACT_CEILING_CANDIDATE -> 5
            AdaptiveBreakoutDecision.CEILING_TEST -> 6
            else -> 6
        }

    private const val FIFTY_TWO_WEEK_SESSIONS = 252
}
