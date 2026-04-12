package com.tradingtool.core.strategy.s4

import com.tradingtool.core.strategy.rsimomentum.UniverseBuildResult
import com.tradingtool.core.strategy.rsimomentum.UniverseMember
import com.tradingtool.core.strategy.volume.VolumeAnalysisResult

data class S4Config(
    val enabled: Boolean = true,
    val profiles: List<S4ProfileConfig> = S4ProfileConfig.defaultProfiles(),
)

data class S4ConfigSummary(
    val enabled: Boolean = true,
    val profileId: String = "",
    val profileLabel: String = "",
    val baseUniversePreset: String = S4ProfileConfig.DEFAULT_BASE_UNIVERSE_PRESET,
    val candidateCount: Int = S4ProfileConfig.DEFAULT_CANDIDATE_COUNT,
    val minAverageTradedValue: Double = S4ProfileConfig.DEFAULT_MIN_AVERAGE_TRADED_VALUE_CR,
    val minHistoryBars: Int = S4ProfileConfig.DEFAULT_MIN_HISTORY_BARS,
    val todayVolumeRatioThreshold: Double = S4ProfileConfig.DEFAULT_TODAY_VOLUME_RATIO_THRESHOLD,
    val recent3dAvgVolumeRatioThreshold: Double = S4ProfileConfig.DEFAULT_RECENT_3D_VOLUME_RATIO_THRESHOLD,
    val recent5dMaxVolumeRatioThreshold: Double = S4ProfileConfig.DEFAULT_RECENT_5D_MAX_VOLUME_RATIO_THRESHOLD,
    val spikePersistenceThreshold: Int = S4ProfileConfig.DEFAULT_SPIKE_PERSISTENCE_THRESHOLD,
    val breakoutPriceChangePctThreshold: Double = S4ProfileConfig.DEFAULT_TODAY_PRICE_CHANGE_PCT_THRESHOLD,
    val breakoutReturn3dPctThreshold: Double = S4ProfileConfig.DEFAULT_PRICE_RETURN_3D_PCT_THRESHOLD,
)

data class S4ProfileConfig(
    val id: String,
    val label: String,
    val baseUniversePreset: String,
    val candidateCount: Int = DEFAULT_CANDIDATE_COUNT,
    val minAverageTradedValue: Double = DEFAULT_MIN_AVERAGE_TRADED_VALUE_CR,
    val minHistoryBars: Int = DEFAULT_MIN_HISTORY_BARS,
    val todayVolumeRatioThreshold: Double = DEFAULT_TODAY_VOLUME_RATIO_THRESHOLD,
    val recent3dAvgVolumeRatioThreshold: Double = DEFAULT_RECENT_3D_VOLUME_RATIO_THRESHOLD,
    val recent5dMaxVolumeRatioThreshold: Double = DEFAULT_RECENT_5D_MAX_VOLUME_RATIO_THRESHOLD,
    val spikePersistenceThreshold: Int = DEFAULT_SPIKE_PERSISTENCE_THRESHOLD,
    val todayPriceChangePctThreshold: Double = DEFAULT_TODAY_PRICE_CHANGE_PCT_THRESHOLD,
    val priceReturn3dPctThreshold: Double = DEFAULT_PRICE_RETURN_3D_PCT_THRESHOLD,
) {
    fun toSummary(globalEnabled: Boolean): S4ConfigSummary = S4ConfigSummary(
        enabled = globalEnabled,
        profileId = id,
        profileLabel = label,
        baseUniversePreset = baseUniversePreset,
        candidateCount = candidateCount,
        minAverageTradedValue = minAverageTradedValue,
        minHistoryBars = minHistoryBars,
        todayVolumeRatioThreshold = todayVolumeRatioThreshold,
        recent3dAvgVolumeRatioThreshold = recent3dAvgVolumeRatioThreshold,
        recent5dMaxVolumeRatioThreshold = recent5dMaxVolumeRatioThreshold,
        spikePersistenceThreshold = spikePersistenceThreshold,
        breakoutPriceChangePctThreshold = todayPriceChangePctThreshold,
        breakoutReturn3dPctThreshold = priceReturn3dPctThreshold,
    )

    companion object {
        const val DEFAULT_BASE_UNIVERSE_PRESET: String = "NIFTY_LARGEMIDCAP_250"
        const val DEFAULT_SMALLCAP_UNIVERSE_PRESET: String = "NIFTY_SMALLCAP_250"
        const val DEFAULT_CANDIDATE_COUNT: Int = 25
        const val DEFAULT_MIN_AVERAGE_TRADED_VALUE_CR: Double = 10.0
        const val DEFAULT_MIN_HISTORY_BARS: Int = 45
        const val DEFAULT_TODAY_VOLUME_RATIO_THRESHOLD: Double = 2.0
        const val DEFAULT_RECENT_3D_VOLUME_RATIO_THRESHOLD: Double = 1.8
        const val DEFAULT_RECENT_5D_MAX_VOLUME_RATIO_THRESHOLD: Double = 2.0
        const val DEFAULT_SPIKE_PERSISTENCE_THRESHOLD: Int = 2
        const val DEFAULT_TODAY_PRICE_CHANGE_PCT_THRESHOLD: Double = 1.5
        const val DEFAULT_PRICE_RETURN_3D_PCT_THRESHOLD: Double = 3.0

        fun defaultProfiles(): List<S4ProfileConfig> = listOf(
            S4ProfileConfig(
                id = "largemidcap250",
                label = "LargeMidcap250",
                baseUniversePreset = DEFAULT_BASE_UNIVERSE_PRESET,
            ),
            S4ProfileConfig(
                id = "smallcap250",
                label = "Smallcap250",
                baseUniversePreset = DEFAULT_SMALLCAP_UNIVERSE_PRESET,
            ),
        )
    }
}

data class S4RankedCandidate(
    val rank: Int,
    val symbol: String,
    val companyName: String,
    val instrumentToken: Long,
    val profileId: String,
    val baseUniversePreset: String,
    val close: Double,
    val avgVolume20d: Double,
    val avgTradedValueCr20d: Double,
    val todayVolumeRatio: Double,
    val recent3dAvgVolumeRatio: Double,
    val recent5dMaxVolumeRatio: Double,
    val spikePersistenceDays5d: Int,
    val recent10dAvgVolumeRatio: Double,
    val elevatedVolumeDays10d: Int,
    val todayPriceChangePct: Double,
    val priceReturn3dPct: Double,
    val breakoutAbove20dHigh: Boolean,
    val indexRank: Int,
    val indexSize: Int,
    val indexLayer: String,
    val todayVolumeScore: Double,
    val recent3dVolumeScore: Double,
    val persistenceScore: Double,
    val priceScore: Double,
    val classification: String,
    val score: Double,
)

data class S4Diagnostics(
    val baseUniverseCount: Int = 0,
    val unresolvedSymbols: List<String> = emptyList(),
    val insufficientHistorySymbols: List<String> = emptyList(),
    val illiquidSymbols: List<String> = emptyList(),
    val disqualifiedSymbols: List<String> = emptyList(),
    val backfilledSymbols: List<String> = emptyList(),
    val failedSymbols: List<String> = emptyList(),
)

data class S4Snapshot(
    val profileId: String = "",
    val profileLabel: String = "",
    val available: Boolean,
    val stale: Boolean,
    val message: String? = null,
    val config: S4ConfigSummary,
    val runAt: String? = null,
    val asOfDate: String? = null,
    val resolvedUniverseCount: Int = 0,
    val eligibleUniverseCount: Int = 0,
    val topCandidates: List<S4RankedCandidate> = emptyList(),
    val diagnostics: S4Diagnostics = S4Diagnostics(),
)

data class S4ProfileError(
    val profileId: String,
    val message: String,
)

data class S4MultiSnapshot(
    val profiles: List<S4Snapshot> = emptyList(),
    val errors: List<S4ProfileError> = emptyList(),
    val partialSuccess: Boolean = false,
)

data class S4CandidateInput(
    val member: UniverseMember,
    val profile: S4ProfileConfig,
    val analysis: VolumeAnalysisResult,
)

data class S4QualifiedCandidate(
    val member: UniverseMember,
    val analysis: VolumeAnalysisResult,
    val classification: String,
    val todayVolumeScore: Double,
    val recent3dVolumeScore: Double,
    val persistenceScore: Double,
    val priceScore: Double,
    val score: Double,
)

data class S4ProfileAnalysis(
    val universe: UniverseBuildResult,
    val qualifiedCandidates: List<S4QualifiedCandidate>,
    val latestAnalyzedDate: java.time.LocalDate?,
    val insufficientHistorySymbols: List<String>,
    val illiquidSymbols: List<String>,
    val disqualifiedSymbols: List<String>,
    val backfilledSymbols: List<String>,
    val failedSymbols: List<String>,
)
