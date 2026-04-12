package com.tradingtool.core.strategy.rsimomentum

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RsiMomentumCalibrationEngineTest {

    @Test
    fun `annualized sortino rewards positive asymmetric returns`() {
        val weeklyReturns = listOf(0.01, 0.008, -0.002, 0.012, 0.007, -0.001, 0.009)

        val sortino = annualizedSortino(weeklyReturns)
        val sharpe = annualizedSharpe(weeklyReturns)

        assertTrue(sortino > 0.0)
        assertTrue(sortino >= sharpe)
    }

    @Test
    fun `max drawdown captures peak to trough loss`() {
        val weeklyReturns = listOf(0.10, -0.20, 0.05, -0.10)

        val maxDrawdown = maxDrawdownPct(weeklyReturns)

        assertEquals(24.4, maxDrawdown, 0.2)
    }

    @Test
    fun `selection prefers stable low-turnover option when sortino is close`() {
        val candidates = listOf(
            candidate(periods = listOf(22, 44, 66), sortino = 1.32, turnoverPct = 88.0, stable = true),
            candidate(periods = listOf(30, 60, 90), sortino = 1.28, turnoverPct = 62.0, stable = true),
            candidate(periods = listOf(21, 42, 84), sortino = 0.95, turnoverPct = 55.0, stable = true),
        )

        val selected = selectCalibrationCandidate(candidates, closeBand = 0.15)

        assertEquals(listOf(30, 60, 90), selected.periods)
        assertTrue(selected.reason.contains("lower-turnover"))
    }

    @Test
    fun `large midcap candidate sets exclude 252-day windows`() {
        val profile = RsiMomentumProfileConfig(
            id = "largemidcap250",
            label = "LargeMidcap250",
            baseUniversePreset = RsiMomentumProfileConfig.DEFAULT_BASE_UNIVERSE_PRESET,
            rsiPeriods = listOf(63, 126, 252),
        )
        val options = RsiMomentumCalibrationOptions(
            largeMidcapPeriodSets = listOf(
                listOf(22, 44, 66),
                listOf(63, 126, 252),
            ),
        )

        val sets = resolveCandidatePeriodSets(profile, options)

        assertTrue(sets.contains(listOf(22, 44, 66)))
        assertTrue(sets.none { periods -> periods.maxOrNull() ?: 0 >= 252 })
    }

    @Test
    fun `smallcap candidate sets include 252-day windows`() {
        val profile = RsiMomentumProfileConfig(
            id = "smallcap250",
            label = "Smallcap250",
            baseUniversePreset = RsiMomentumProfileConfig.DEFAULT_SMALLCAP_UNIVERSE_PRESET,
            rsiPeriods = listOf(21, 63, 252),
        )
        val options = RsiMomentumCalibrationOptions(
            smallcapPeriodSets = listOf(
                listOf(22, 44, 66),
                listOf(63, 126, 252),
            ),
        )

        val sets = resolveCandidatePeriodSets(profile, options)

        assertTrue(sets.contains(listOf(22, 44, 66)))
        assertTrue(sets.contains(listOf(63, 126, 252)))
        assertTrue(sets.contains(listOf(21, 63, 252)))
    }

    private fun candidate(
        periods: List<Int>,
        sortino: Double,
        turnoverPct: Double,
        stable: Boolean,
    ): RsiMomentumCalibrationCandidateResult = RsiMomentumCalibrationCandidateResult(
        rsiPeriods = periods,
        sampleWeeks = 120,
        annualizedSortino = sortino,
        annualizedSharpe = sortino - 0.1,
        cagrPct = 18.0,
        maxDrawdownPct = 23.0,
        annualizedVolatilityPct = 17.0,
        averageWeeklyReturnPct = 0.35,
        averageTurnoverPct = turnoverPct,
        firstHalfSortino = sortino,
        secondHalfSortino = sortino - 0.1,
        isStable = stable,
        rejectionReasons = if (stable) emptyList() else listOf("UNSTABLE_SPLIT_SORTINO"),
    )
}
