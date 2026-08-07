package com.tradingtool.core.strategy.netwebcycle

import com.tradingtool.core.candle.DailyCandle
import kotlin.math.abs
import kotlin.math.round

class NetwebCycleEngine {
    fun run(
        symbol: String,
        candles: List<DailyCandle>,
        config: NetwebCycleConfig,
    ): NetwebCycleReport {
        val sortedCandles = candles.sortedBy(DailyCandle::candleDate)
        require(sortedCandles.size >= config.minimumHistoryTradingDays) {
            "At least ${config.minimumHistoryTradingDays} daily candles are required for NETWEB cycle analysis."
        }

        var state = initialState(sortedCandles, config)
        val snapshots = mutableListOf<NetwebCycleSnapshot>()

        sortedCandles.indices
            .drop(config.minimumHistoryTradingDays)
            .forEach { index ->
                val evaluation = evaluateDay(sortedCandles, index, state, config)
                state = evaluation.state
                snapshots += buildSnapshot(sortedCandles, index, state, evaluation, snapshots, config)
            }

        require(snapshots.isNotEmpty()) { "No daily snapshots could be created for $symbol." }
        return NetwebCycleReport(
            symbol = symbol,
            testedFromDate = snapshots.first().date,
            testedToDate = snapshots.last().date,
            current = snapshots.last(),
            segments = buildSegments(snapshots),
            dailySnapshots = snapshots,
        )
    }

    private fun initialState(candles: List<DailyCandle>, config: NetwebCycleConfig): CycleState {
        val base = findBase(candles, config.minimumHistoryTradingDays, config)
        return CycleState(
            phase = NetwebCyclePhase.WEEKLY_ROTATION,
            phaseStartIndex = config.minimumHistoryTradingDays,
            base = base,
            expansionPeak = null,
            stableBaseDays = 0,
            breakoutDays = 0,
        )
    }

    private fun evaluateDay(
        candles: List<DailyCandle>,
        index: Int,
        previousState: CycleState,
        config: NetwebCycleConfig,
    ): Evaluation {
        val candle = candles[index]
        val previousCandle = candles[index - 1]
        val baseCandidate = findBase(candles, index, config)
        val dailyChangePct = percentageChange(previousCandle.close, candle.close)

        return when (previousState.phase) {
            NetwebCyclePhase.WEEKLY_ROTATION -> evaluateRotation(
                candles,
                index,
                previousState,
                baseCandidate,
                dailyChangePct,
                config,
            )

            NetwebCyclePhase.BULL_RUN -> evaluateBullRun(
                candles,
                index,
                previousState,
                dailyChangePct,
                config,
            )

            NetwebCyclePhase.DRAWDOWN -> evaluateDrawdown(
                candles,
                index,
                previousState,
                baseCandidate,
                dailyChangePct,
                config,
            )

            NetwebCyclePhase.NEW_BASE -> evaluateNewBase(
                candles,
                index,
                previousState,
                baseCandidate,
                dailyChangePct,
                config,
            )
        }
    }

    private fun evaluateRotation(
        candles: List<DailyCandle>,
        index: Int,
        previousState: CycleState,
        baseCandidate: BaseRange?,
        dailyChangePct: Double,
        config: NetwebCycleConfig,
    ): Evaluation {
        val base = baseCandidate ?: previousState.base
        val breakout = base?.let { range -> isAboveBreakout(candles[index].close, range, config) } ?: false
        val breakoutDays = if (breakout) previousState.breakoutDays + 1 else 0
        val strongBreakout = breakout && dailyChangePct >= config.strongBreakoutMovePct
        val isBullRun = breakout && (strongBreakout || breakoutDays >= 2)

        if (isBullRun) {
            return Evaluation(
                state = previousState.copy(
                    phase = NetwebCyclePhase.BULL_RUN,
                    phaseStartIndex = index,
                    base = base,
                    expansionPeak = candles[index].high,
                    stableBaseDays = 0,
                    breakoutDays = breakoutDays,
                ),
                transitionReason = "Price broke above the active base with breakout follow-through.",
            )
        }

        return Evaluation(
            state = previousState.copy(
                base = base,
                stableBaseDays = if (base != null) previousState.stableBaseDays + 1 else 0,
                breakoutDays = breakoutDays,
            ),
            transitionReason = if (base != null) {
                "Price remains within the active base; the move is treated as rotation."
            } else {
                "A stable base is not yet available; waiting for more structure."
            },
        )
    }

    private fun evaluateBullRun(
        candles: List<DailyCandle>,
        index: Int,
        previousState: CycleState,
        dailyChangePct: Double,
        config: NetwebCycleConfig,
    ): Evaluation {
        val peak = maxOf(previousState.expansionPeak ?: candles[index].high, candles[index].high)
        val drawdownPct = percentageChange(peak, candles[index].close)
        val isDownDay = dailyChangePct < 0.0
        val drawdownDays = if (isDownDay) previousState.stableBaseDays + 1 else 0
        val entersDrawdown = drawdownPct <= -config.drawdownTriggerPct && drawdownDays >= 2

        return Evaluation(
            state = previousState.copy(
                phase = if (entersDrawdown) NetwebCyclePhase.DRAWDOWN else NetwebCyclePhase.BULL_RUN,
                phaseStartIndex = if (entersDrawdown) index else previousState.phaseStartIndex,
                expansionPeak = peak,
                stableBaseDays = drawdownDays,
                breakoutDays = 0,
            ),
            transitionReason = if (entersDrawdown) {
                "Price is down ${format(abs(drawdownPct))}% from the expansion peak across multiple sessions."
            } else {
                "Price remains in expansion mode above the prior base."
            },
        )
    }

    private fun evaluateDrawdown(
        candles: List<DailyCandle>,
        index: Int,
        previousState: CycleState,
        baseCandidate: BaseRange?,
        dailyChangePct: Double,
        config: NetwebCycleConfig,
    ): Evaluation {
        val newBaseCandidate = findBase(candles, index, config.newBaseLookbackTradingDays, config)
        val base = newBaseCandidate ?: baseCandidate ?: previousState.base
        val breakout = base?.let { range -> isAboveBreakout(candles[index].close, range, config) } ?: false
        val strongBreakout = breakout && dailyChangePct >= config.strongBreakoutMovePct
        if (strongBreakout) {
            return Evaluation(
                state = previousState.copy(
                    phase = NetwebCyclePhase.BULL_RUN,
                    phaseStartIndex = index,
                    base = base,
                    expansionPeak = candles[index].high,
                    stableBaseDays = 0,
                    breakoutDays = 1,
                ),
                transitionReason = "Price broke out of the stabilizing range with a strong expansion day.",
            )
        }

        val stable = base != null && isStableNewBase(candles, index, config)
        val stableBaseDays = if (stable) previousState.stableBaseDays + 1 else 0
        val formsNewBase = stableBaseDays >= config.minimumNewBaseTradingDays

        return Evaluation(
            state = previousState.copy(
                phase = if (formsNewBase) NetwebCyclePhase.NEW_BASE else NetwebCyclePhase.DRAWDOWN,
                phaseStartIndex = if (formsNewBase) index else previousState.phaseStartIndex,
                base = base,
                stableBaseDays = stableBaseDays,
                breakoutDays = 0,
            ),
            transitionReason = if (formsNewBase) {
                "The drawdown has stabilized into a new contained range."
            } else {
                "Price is still resetting after the expansion; a stable new base is not confirmed."
            },
        )
    }

    private fun evaluateNewBase(
        candles: List<DailyCandle>,
        index: Int,
        previousState: CycleState,
        baseCandidate: BaseRange?,
        dailyChangePct: Double,
        config: NetwebCycleConfig,
    ): Evaluation {
        val base = findBase(candles, index, config.newBaseLookbackTradingDays, config)
            ?: baseCandidate
            ?: previousState.base
        val breakout = base?.let { range -> isAboveBreakout(candles[index].close, range, config) } ?: false
        val breakoutDays = if (breakout) previousState.breakoutDays + 1 else 0
        val strongBreakout = breakout && dailyChangePct >= config.strongBreakoutMovePct
        val isBullRun = breakout && (strongBreakout || breakoutDays >= 2)
        val becomesRotation = !isBullRun && base != null && previousState.stableBaseDays >= config.minimumNewBaseTradingDays

        return Evaluation(
            state = previousState.copy(
                phase = when {
                    isBullRun -> NetwebCyclePhase.BULL_RUN
                    becomesRotation -> NetwebCyclePhase.WEEKLY_ROTATION
                    else -> NetwebCyclePhase.NEW_BASE
                },
                phaseStartIndex = when {
                    isBullRun || becomesRotation -> index
                    else -> previousState.phaseStartIndex
                },
                base = base,
                expansionPeak = if (isBullRun) candles[index].high else previousState.expansionPeak,
                stableBaseDays = if (becomesRotation) 0 else previousState.stableBaseDays + 1,
                breakoutDays = breakoutDays,
            ),
            transitionReason = when {
                isBullRun -> "Price broke out of the new base with follow-through."
                becomesRotation -> "The new base is stable enough to treat as a weekly rotation range."
                else -> "The new base is still being established."
            },
        )
    }

    private fun buildSnapshot(
        candles: List<DailyCandle>,
        index: Int,
        state: CycleState,
        evaluation: Evaluation,
        snapshots: List<NetwebCycleSnapshot>,
        config: NetwebCycleConfig,
    ): NetwebCycleSnapshot {
        val candle = candles[index]
        val previousCandle = candles.getOrNull(index - 1)
        val base = state.base
        val peak = state.expansionPeak
        val positionInBasePct = base?.let { range ->
            percentageChange(range.low, candle.close).coerceIn(0.0, percentageChange(range.low, range.high))
                .let { position ->
                    val width = percentageChange(range.low, range.high)
                    if (width == 0.0) 50.0 else position / width * 100.0
                }
        }
        val fiveDayReturn = candles.getOrNull(index - 5)?.let { percentageChange(it.close, candle.close) }
        val twentyDayReturn = candles.getOrNull(index - 20)?.let { percentageChange(it.close, candle.close) }
        val volumeRatio = averageVolume(candles.subList(maxOf(0, index - 20), index))
            ?.takeIf { average -> average > 0.0 }
            ?.let { average -> candle.volume / average }
        val fivePercentMoveCount = countFivePercentMoves(candles, index, base, config.rotationMoveTargetPct)
        val breakoutAboveBase = base?.let { range -> candle.close > range.high } ?: false
        val drawdownPct = peak?.let { value -> percentageChange(value, candle.close) }
        val evidence = buildEvidence(
            candle = candle,
            previousCandle = previousCandle,
            state = state,
            evaluation = evaluation,
            base = base,
            positionInBasePct = positionInBasePct,
            fiveDayReturn = fiveDayReturn,
            twentyDayReturn = twentyDayReturn,
            volumeRatio = volumeRatio,
            drawdownPct = drawdownPct,
        )

        return NetwebCycleSnapshot(
            date = candle.candleDate.toString(),
            phase = state.phase,
            currentPrice = roundTo2(candle.close),
            baseLow = base?.low?.let(::roundTo2),
            baseHigh = base?.high?.let(::roundTo2),
            baseWidthPct = base?.let { range -> roundTo2(percentageChange(range.low, range.high)) },
            positionInBasePct = positionInBasePct?.let(::roundTo2),
            dailyChangePct = previousCandle?.let { roundTo2(percentageChange(it.close, candle.close)) },
            fiveDayReturnPct = fiveDayReturn?.let(::roundTo2),
            twentyDayReturnPct = twentyDayReturn?.let(::roundTo2),
            volumeRatio20Day = volumeRatio?.let(::roundTo2),
            expansionPeak = peak?.let(::roundTo2),
            drawdownFromPeakPct = drawdownPct?.let(::roundTo2),
            phaseStartDate = candles[state.phaseStartIndex].candleDate.toString(),
            phaseAgeTradingDays = index - state.phaseStartIndex + 1,
            fivePercentMoveCount = fivePercentMoveCount,
            breakoutAboveBase = breakoutAboveBase,
            confidencePct = calculateConfidence(state, base, evaluation, volumeRatio),
            action = actionFor(state.phase),
            evidence = evidence,
        )
    }

    private fun buildEvidence(
        candle: DailyCandle,
        previousCandle: DailyCandle?,
        state: CycleState,
        evaluation: Evaluation,
        base: BaseRange?,
        positionInBasePct: Double?,
        fiveDayReturn: Double?,
        twentyDayReturn: Double?,
        volumeRatio: Double?,
        drawdownPct: Double?,
    ): List<String> = buildList {
        add(evaluation.transitionReason)
        base?.let { range ->
            add("Active base: ₹${format(range.low)}–₹${format(range.high)} (${format(percentageChange(range.low, range.high))}% wide).")
            positionInBasePct?.let { position -> add("Price is ${format(position)}% of the way from the base low to the base high.") }
        }
        previousCandle?.let { previous -> add("Daily move: ${format(percentageChange(previous.close, candle.close))}%.") }
        fiveDayReturn?.let { value -> add("5-session return: ${format(value)}%.") }
        twentyDayReturn?.let { value -> add("20-session return: ${format(value)}%.") }
        drawdownPct?.let { value -> add("Drawdown from expansion peak: ${format(value)}%.") }
        volumeRatio?.let { value -> add("Volume is ${format(value)}x the prior 20-session average.") }
        if (state.phase == NetwebCyclePhase.WEEKLY_ROTATION) {
            add("Use the active range for rotation monitoring; do not treat a range-bound 5% move as a bull run.")
        }
    }

    private fun calculateConfidence(
        state: CycleState,
        base: BaseRange?,
        evaluation: Evaluation,
        volumeRatio: Double?,
    ): Int {
        var score = when (state.phase) {
            NetwebCyclePhase.WEEKLY_ROTATION -> if (base != null) 65 else 40
            NetwebCyclePhase.BULL_RUN -> 70
            NetwebCyclePhase.DRAWDOWN -> 65
            NetwebCyclePhase.NEW_BASE -> if (base != null) 60 else 45
        }
        if (evaluation.transitionReason.contains("follow-through", ignoreCase = true)) score += 10
        if (volumeRatio != null && volumeRatio >= 1.5) score += 5
        return score.coerceIn(0, 95)
    }

    private fun countFivePercentMoves(
        candles: List<DailyCandle>,
        index: Int,
        base: BaseRange?,
        targetPct: Double,
    ): Int {
        if (base == null) return 0
        val start = maxOf(1, index - 20)
        return (start..index).count { moveIndex ->
            val current = candles[moveIndex]
            val previous = candles[moveIndex - 1]
            val move = abs(percentageChange(previous.close, current.close))
            current.close in base.low..base.high && move >= targetPct
        }
    }

    private fun findBase(
        candles: List<DailyCandle>,
        endExclusive: Int,
        config: NetwebCycleConfig,
    ): BaseRange? = findBase(candles, endExclusive, config.baseLookbackTradingDays, config)

    private fun findBase(
        candles: List<DailyCandle>,
        endExclusive: Int,
        lookbackTradingDays: Int,
        config: NetwebCycleConfig,
    ): BaseRange? {
        val start = maxOf(0, endExclusive - lookbackTradingDays)
        val window = candles.subList(start, endExclusive)
        if (window.size < config.minimumBaseHistoryTradingDays) return null
        val closes = window.map(DailyCandle::close)
        val low = closes.minOrNull() ?: return null
        val high = closes.maxOrNull() ?: return null
        val widthPct = percentageChange(low, high)
        val driftPct = percentageChange(closes.first(), closes.last())
        if (
            widthPct > config.maximumBaseWidthPct + FLOAT_TOLERANCE ||
            abs(driftPct) > config.maximumBaseDriftPct + FLOAT_TOLERANCE
        ) return null
        return BaseRange(low, high)
    }

    private fun isStableNewBase(candles: List<DailyCandle>, index: Int, config: NetwebCycleConfig): Boolean {
        val start = maxOf(0, index - config.minimumNewBaseTradingDays + 1)
        val window = candles.subList(start, index + 1)
        if (window.size < config.minimumNewBaseTradingDays) return false
        val closes = window.map(DailyCandle::close)
        val widthPct = percentageChange(closes.minOrNull() ?: return false, closes.maxOrNull() ?: return false)
        val driftPct = percentageChange(closes.first(), closes.last())
        return widthPct <= config.maximumBaseWidthPct + FLOAT_TOLERANCE &&
            abs(driftPct) <= config.maximumBaseDriftPct + FLOAT_TOLERANCE
    }

    private fun isAboveBreakout(close: Double, base: BaseRange, config: NetwebCycleConfig): Boolean =
        close > base.high * (1.0 + config.breakoutBufferPct / 100.0)

    private fun averageVolume(candles: List<DailyCandle>): Double? =
        candles.map(DailyCandle::volume).takeIf { values -> values.isNotEmpty() }?.average()

    private fun buildSegments(snapshots: List<NetwebCycleSnapshot>): List<NetwebCycleSegment> {
        val segments = mutableListOf<NetwebCycleSegment>()
        var start = 0
        for (index in 1..snapshots.size) {
            val phaseChanged = index == snapshots.size || snapshots[index].phase != snapshots[start].phase
            if (phaseChanged) {
                val first = snapshots[start]
                val last = snapshots[index - 1]
                segments += NetwebCycleSegment(
                    phase = first.phase,
                    startDate = first.date,
                    endDate = last.date,
                    tradingDays = index - start,
                    startPrice = first.currentPrice,
                    endPrice = last.currentPrice,
                    returnPct = roundTo2(percentageChange(first.currentPrice, last.currentPrice)),
                )
                start = index
            }
        }
        return segments
    }

    private fun actionFor(phase: NetwebCyclePhase): String = when (phase) {
        NetwebCyclePhase.WEEKLY_ROTATION -> "Monitor lower and upper rotation zones."
        NetwebCyclePhase.BULL_RUN -> "Ride the expansion; monitor for a controlled pullback."
        NetwebCyclePhase.DRAWDOWN -> "Wait for selling pressure to settle and a new base to form."
        NetwebCyclePhase.NEW_BASE -> "Map the emerging range; wait for stability or a confirmed breakout."
    }

    private fun percentageChange(from: Double, to: Double): Double =
        if (from == 0.0) 0.0 else ((to / from) - 1.0) * 100.0

    private fun roundTo2(value: Double): Double = round(value * 100.0) / 100.0

    private fun format(value: Double): String = "%.2f".format(java.util.Locale.ROOT, value)

    private data class BaseRange(val low: Double, val high: Double)

    private data class CycleState(
        val phase: NetwebCyclePhase,
        val phaseStartIndex: Int,
        val base: BaseRange?,
        val expansionPeak: Double?,
        val stableBaseDays: Int,
        val breakoutDays: Int,
    )

    private data class Evaluation(
        val state: CycleState,
        val transitionReason: String,
    )

    private companion object {
        const val FLOAT_TOLERANCE = 0.000001
    }
}
