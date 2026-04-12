package com.tradingtool.core.strategy.rsimomentum

import java.time.LocalDate

data class RsiMomentumCalibrationOptions(
    val profileIds: Set<String> = emptySet(),
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
    val transactionCostBps: Double = DEFAULT_TRANSACTION_COST_BPS,
    val annualizationWeeks: Int = 52,
    val largeMidcapPeriodSets: List<List<Int>> = DEFAULT_LARGEMIDCAP_PERIOD_SETS,
    val smallcapPeriodSets: List<List<Int>> = DEFAULT_SMALLCAP_PERIOD_SETS,
) {
    companion object {
        const val DEFAULT_TRANSACTION_COST_BPS: Double = 10.0
        val DEFAULT_LARGEMIDCAP_PERIOD_SETS: List<List<Int>> = listOf(
            listOf(20, 40, 60),
            listOf(22, 44, 66),
            listOf(30, 60, 90),
            listOf(21, 42, 84),
        )
        val DEFAULT_SMALLCAP_PERIOD_SETS: List<List<Int>> = listOf(
            listOf(22, 44, 66),
            listOf(30, 60, 90),
            listOf(63, 126, 252),
            listOf(21, 63, 252),
        )
    }
}

data class RsiMomentumCalibrationCandidateResult(
    val rsiPeriods: List<Int>,
    val sampleWeeks: Int,
    val annualizedSortino: Double,
    val annualizedSharpe: Double,
    val cagrPct: Double,
    val maxDrawdownPct: Double,
    val annualizedVolatilityPct: Double,
    val averageWeeklyReturnPct: Double,
    val averageTurnoverPct: Double,
    val firstHalfSortino: Double,
    val secondHalfSortino: Double,
    val isStable: Boolean,
    val rejectionReasons: List<String>,
)

data class RsiMomentumProfileCalibrationResult(
    val profileId: String,
    val profileLabel: String,
    val baseUniversePreset: String,
    val sampleRange: String,
    val selectedRsiPeriods: List<Int>,
    val selectionReason: String,
    val candidates: List<RsiMomentumCalibrationCandidateResult>,
)

data class RsiMomentumCalibrationReport(
    val runAt: String,
    val method: String,
    val sampleRange: String,
    val transactionCostBps: Double,
    val profileResults: List<RsiMomentumProfileCalibrationResult>,
)
