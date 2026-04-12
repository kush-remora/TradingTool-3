package com.tradingtool.core.strategy.rsimomentum

import java.time.LocalDate

data class RsiMomentumConfig(
    val enabled: Boolean = true,
    val profiles: List<RsiMomentumProfileConfig> = RsiMomentumProfileConfig.defaultProfiles(),
)

data class RsiMomentumConfigSummary(
    val enabled: Boolean = true,
    val profileId: String = "",
    val profileLabel: String = "",
    val baseUniversePreset: String = RsiMomentumProfileConfig.DEFAULT_BASE_UNIVERSE_PRESET,
    val candidateCount: Int = 20,
    val boardDisplayCount: Int = RsiMomentumProfileConfig.DEFAULT_BOARD_DISPLAY_COUNT,
    val replacementPoolCount: Int = RsiMomentumProfileConfig.DEFAULT_REPLACEMENT_POOL_COUNT,
    val holdingCount: Int = 10,
    val rsiPeriods: List<Int> = RsiMomentumProfileConfig.DEFAULT_RSI_PERIODS,
    val minAverageTradedValue: Double = RsiMomentumProfileConfig.DEFAULT_MIN_AVERAGE_TRADED_VALUE_CR,
    val maxExtensionAboveSma20ForNewEntry: Double = RsiMomentumProfileConfig.DEFAULT_MAX_EXTENSION_ABOVE_SMA20_FOR_NEW_ENTRY,
    val maxExtensionAboveSma20ForNewEntryPct: Double = RsiMomentumProfileConfig.DEFAULT_MAX_EXTENSION_ABOVE_SMA20_FOR_NEW_ENTRY * 100.0,
    val rebalanceDay: String = "FRIDAY",
    val rebalanceTime: String = "15:40",
    val rsiCalibrationRunAt: String? = null,
    val rsiCalibrationMethod: String? = null,
    val rsiCalibrationSampleRange: String? = null,
)

data class RsiMomentumProfileConfig(
    val id: String,
    val label: String,
    val baseUniversePreset: String,
    val candidateCount: Int = 20,
    val boardDisplayCount: Int = DEFAULT_BOARD_DISPLAY_COUNT,
    val replacementPoolCount: Int = DEFAULT_REPLACEMENT_POOL_COUNT,
    val holdingCount: Int = 10,
    val rsiPeriods: List<Int> = DEFAULT_RSI_PERIODS,
    val minAverageTradedValue: Double = DEFAULT_MIN_AVERAGE_TRADED_VALUE_CR,
    val maxExtensionAboveSma20ForNewEntry: Double = DEFAULT_MAX_EXTENSION_ABOVE_SMA20_FOR_NEW_ENTRY,
    val maxExtensionAboveSma20ForNewEntryPct: Double? = null,
    val rebalanceDay: String = "FRIDAY",
    val rebalanceTime: String = "15:40",
    val rsiCalibrationRunAt: String? = null,
    val rsiCalibrationMethod: String? = null,
    val rsiCalibrationSampleRange: String? = null,
) {
    fun toSummary(globalEnabled: Boolean): RsiMomentumConfigSummary = RsiMomentumConfigSummary(
        enabled = globalEnabled,
        profileId = id,
        profileLabel = label,
        baseUniversePreset = baseUniversePreset,
        candidateCount = candidateCount,
        boardDisplayCount = boardDisplayCount,
        replacementPoolCount = replacementPoolCount,
        holdingCount = holdingCount,
        rsiPeriods = rsiPeriods,
        minAverageTradedValue = minAverageTradedValue,
        maxExtensionAboveSma20ForNewEntry = maxExtensionAboveSma20ForNewEntry,
        maxExtensionAboveSma20ForNewEntryPct = maxExtensionAboveSma20ForNewEntry * 100.0,
        rebalanceDay = rebalanceDay,
        rebalanceTime = rebalanceTime,
        rsiCalibrationRunAt = rsiCalibrationRunAt,
        rsiCalibrationMethod = rsiCalibrationMethod,
        rsiCalibrationSampleRange = rsiCalibrationSampleRange,
    )

    companion object {
        const val DEFAULT_BASE_UNIVERSE_PRESET: String = "NIFTY_LARGEMIDCAP_250"
        const val DEFAULT_SMALLCAP_UNIVERSE_PRESET: String = "NIFTY_SMALLCAP_250"
        const val DEFAULT_MIN_AVERAGE_TRADED_VALUE_CR: Double = 10.0
        const val DEFAULT_BOARD_DISPLAY_COUNT: Int = 40
        const val DEFAULT_REPLACEMENT_POOL_COUNT: Int = 40
        const val DEFAULT_MAX_EXTENSION_ABOVE_SMA20_FOR_NEW_ENTRY: Double = 0.20
        val DEFAULT_RSI_PERIODS: List<Int> = listOf(22, 44, 66)

        fun defaultProfiles(): List<RsiMomentumProfileConfig> = listOf(
            RsiMomentumProfileConfig(
                id = "largemidcap250",
                label = "LargeMidcap250",
                baseUniversePreset = DEFAULT_BASE_UNIVERSE_PRESET,
            ),
            RsiMomentumProfileConfig(
                id = "smallcap250",
                label = "Smallcap250",
                baseUniversePreset = DEFAULT_SMALLCAP_UNIVERSE_PRESET,
            ),
        )
    }
}

data class RsiMomentumRankedStock(
    val rank: Int,
    val symbol: String,
    val companyName: String,
    val instrumentToken: Long,
    val avgRsi: Double,
    val rsi22: Double,
    val rsi44: Double,
    val rsi66: Double,
    val close: Double,
    val sma20: Double,
    val extensionAboveSma20Pct: Double,
    val buyZoneLow10w: Double,
    val buyZoneHigh10w: Double,
    val lowestRsi50d: Double,
    val highestRsi50d: Double,
    val avgTradedValueCr: Double,
    val inBaseUniverse: Boolean,
    val inWatchlist: Boolean,
    val entryBlocked: Boolean = false,
    val entryBlockReason: String? = null,
    val entryAction: String = "WATCH",
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
    val profileId: String = "",
    val profileLabel: String = "",
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

data class RsiMomentumProfileError(
    val profileId: String,
    val message: String,
)

data class RsiMomentumMultiSnapshot(
    val profiles: List<RsiMomentumSnapshot> = emptyList(),
    val errors: List<RsiMomentumProfileError> = emptyList(),
    val partialSuccess: Boolean = false,
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
    val close: Double,
    val sma20: Double,
    val buyZoneLow10w: Double,
    val buyZoneHigh10w: Double,
    val lowestRsi50d: Double,
    val highestRsi50d: Double,
    val avgTradedValueCr: Double,
)
