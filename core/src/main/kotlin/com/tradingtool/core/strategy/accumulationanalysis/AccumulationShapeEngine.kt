package com.tradingtool.core.strategy.accumulationanalysis

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate

class AccumulationShapeEngine {
    fun buildChains(hitDates: List<LocalDate>, candles: List<DailyCandle>): List<List<LocalDate>> {
        val sortedHits = hitDates.distinct().sorted()
        return sortedHits.fold(mutableListOf<MutableList<LocalDate>>()) { chains, date ->
            val active = chains.lastOrNull()
            if (active == null || tradingSessionsBetween(active.last(), date, candles) > MAX_GAP_SESSIONS) chains += mutableListOf(date) else active += date
            chains
        }
    }

    fun classify(candles: List<DailyCandle>): AccumulationShape {
        if (candles.size < MIN_CANDLES) return AccumulationShape.UNCLASSIFIED
        val first = candles.first().close
        val last = candles.last().close
        val minIndex = candles.indices.minBy { candles[it].close }
        val maxIndex = candles.indices.maxBy { candles[it].close }
        val changePct = (last - first) / first * 100.0
        if (minIndex in candles.size / 3..(candles.size * 2 / 3) && candles[minIndex].close <= first * CUP_DIP_RATIO && last >= first * CUP_RECOVERY_RATIO) return AccumulationShape.CUP
        if (kotlin.math.abs(changePct) <= FLAT_CHANGE_PCT) return AccumulationShape.FLAT
        if (changePct < 0 && changePct >= -MAX_DOWNWARD_DRIFT_PCT && maxIndex < candles.size / 2) return AccumulationShape.DOWNWARD_DRIFT
        if (changePct <= -MAX_DOWNWARD_DRIFT_PCT || (maxIndex in candles.size / 3..(candles.size * 2 / 3) && last <= first * 0.90)) return AccumulationShape.INVALID
        return AccumulationShape.UNCLASSIFIED
    }

    fun decision(shape: AccumulationShape): AccumulationShapeDecision = when (shape) {
        AccumulationShape.FLAT, AccumulationShape.CUP, AccumulationShape.DOWNWARD_DRIFT -> AccumulationShapeDecision.VALID
        AccumulationShape.UNCLASSIFIED -> AccumulationShapeDecision.NEEDS_REVIEW
        AccumulationShape.INVALID -> AccumulationShapeDecision.INVALID
    }

    fun tradingSessionsBetween(from: LocalDate, to: LocalDate, candles: List<DailyCandle>): Int =
        candles.count { it.candleDate > from && it.candleDate <= to }

    companion object {
        const val MAX_GAP_SESSIONS = 15
        private const val MIN_CANDLES = 5
        // Initial BHEL-calibrated boundaries; the version is persisted with every run.
        private const val FLAT_CHANGE_PCT = 8.0
        private const val CUP_RECOVERY_RATIO = 0.95
        private const val CUP_DIP_RATIO = 0.95
        private const val MAX_DOWNWARD_DRIFT_PCT = 20.0
    }
}
