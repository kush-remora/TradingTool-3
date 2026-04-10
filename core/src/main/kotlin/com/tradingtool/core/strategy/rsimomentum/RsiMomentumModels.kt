package com.tradingtool.core.strategy.rsimomentum

import java.time.LocalDate

data class RsiMomentumConfig(
    val enabled: Boolean = true,
    val baseUniversePreset: String = DEFAULT_BASE_UNIVERSE_PRESET,
    val candidateCount: Int = 20,
    val holdingCount: Int = 10,
    val rsiPeriods: List<Int> = DEFAULT_RSI_PERIODS,
    val minAverageTradedValue: Double = DEFAULT_MIN_AVERAGE_TRADED_VALUE_CR,
    val rebalanceDay: String = "FRIDAY",
    val rebalanceTime: String = "15:40",
) {
    fun toSummary(): RsiMomentumConfigSummary = RsiMomentumConfigSummary(
        enabled = enabled,
        baseUniversePreset = baseUniversePreset,
        candidateCount = candidateCount,
        holdingCount = holdingCount,
        rsiPeriods = rsiPeriods,
        minAverageTradedValue = minAverageTradedValue,
        rebalanceDay = rebalanceDay,
        rebalanceTime = rebalanceTime,
    )

    companion object {
        const val DEFAULT_BASE_UNIVERSE_PRESET: String = "NIFTY_LARGEMIDCAP_250"
        const val DEFAULT_MIN_AVERAGE_TRADED_VALUE_CR: Double = 10.0
        val DEFAULT_RSI_PERIODS: List<Int> = listOf(22, 44, 66)
    }
}

data class RsiMomentumConfigSummary(
    val enabled: Boolean,
    val baseUniversePreset: String,
    val candidateCount: Int,
    val holdingCount: Int,
    val rsiPeriods: List<Int>,
    val minAverageTradedValue: Double,
    val rebalanceDay: String,
    val rebalanceTime: String,
)

data class RsiMomentumRankedStock(
    val rank: Int,
    val symbol: String,
    val companyName: String,
    val instrumentToken: Long,
    val avgRsi: Double,
    val rsi22: Double,
    val rsi44: Double,
    val rsi66: Double,
    val avgTradedValueCr: Double,
    val inBaseUniverse: Boolean,
    val inWatchlist: Boolean,
    val targetWeightPct: Double? = null,
)

data class RsiMomentumRebalance(
    val entries: List<String> = emptyList(),
    val exits: List<String> = emptyList(),
    val holds: List<String> = emptyList(),
)

data class RsiMomentumDiagnostics(
    val baseUniverseCount: Int = 0,
    val watchlistCount: Int = 0,
    val watchlistAdditionsCount: Int = 0,
    val unresolvedSymbols: List<String> = emptyList(),
    val insufficientHistorySymbols: List<String> = emptyList(),
    val illiquidSymbols: List<String> = emptyList(),
    val backfilledSymbols: List<String> = emptyList(),
    val failedSymbols: List<String> = emptyList(),
)

data class RsiMomentumSnapshot(
    val available: Boolean,
    val stale: Boolean,
    val message: String? = null,
    val config: RsiMomentumConfigSummary,
    val runAt: String? = null,
    val asOfDate: String? = null,
    val resolvedUniverseCount: Int = 0,
    val eligibleUniverseCount: Int = 0,
    val topCandidates: List<RsiMomentumRankedStock> = emptyList(),
    val holdings: List<RsiMomentumRankedStock> = emptyList(),
    val rebalance: RsiMomentumRebalance = RsiMomentumRebalance(),
    val diagnostics: RsiMomentumDiagnostics = RsiMomentumDiagnostics(),
)

data class UniverseMember(
    val symbol: String,
    val instrumentToken: Long,
    val companyName: String,
    val inBaseUniverse: Boolean,
    val inWatchlist: Boolean,
)

data class UniverseBuildResult(
    val members: List<UniverseMember>,
    val unresolvedSymbols: List<String>,
    val baseUniverseCount: Int,
    val watchlistCount: Int,
    val watchlistAdditionsCount: Int,
)

data class SecurityMetrics(
    val member: UniverseMember,
    val asOfDate: LocalDate,
    val avgRsi: Double,
    val rsi22: Double,
    val rsi44: Double,
    val rsi66: Double,
    val avgTradedValueCr: Double,
)

