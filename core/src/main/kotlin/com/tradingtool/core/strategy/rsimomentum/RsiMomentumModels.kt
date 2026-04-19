package com.tradingtool.core.strategy.rsimomentum

import java.time.LocalDate

data class RsiMomentumConfig(
    val enabled: Boolean = true,
    val profiles: List<RsiMomentumProfileConfig> = RsiMomentumProfileConfig.defaultProfiles(),
)

data class UniverseMember(
    val symbol: String,
    val companyName: String,
    val instrumentToken: Long,
    val inBaseUniverse: Boolean,
    val inWatchlist: Boolean,
)

data class UniverseBuildResult(
    val members: List<UniverseMember>,
    val baseUniverseCount: Int,
    val watchlistCount: Int,
    val watchlistAdditionsCount: Int,
    val unresolvedSymbols: List<String>,
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
    val maxDailyMove5dPct: Double = 0.0,
    val buyZoneLow10w: Double,
    val buyZoneHigh10w: Double,
    val lowestRsi50d: Double,
    val highestRsi50d: Double,
    val avgTradedValueCr: Double,
    val lowestLow30d: Double = 0.0,
    val avgVol3d: Double = 0.0,
    val avgVol20d: Double = 0.0,
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
    val maxExtensionAboveSma20ForSkipNewEntry: Double = RsiMomentumProfileConfig.DEFAULT_MAX_EXTENSION_ABOVE_SMA20_FOR_SKIP_NEW_ENTRY,
    val maxExtensionAboveSma20ForSkipNewEntryPct: Double = RsiMomentumProfileConfig.DEFAULT_MAX_EXTENSION_ABOVE_SMA20_FOR_SKIP_NEW_ENTRY * 100.0,
    val rebalanceDay: String = "FRIDAY",
    val rebalanceTime: String = "15:40",
    val rsiCalibrationRunAt: String? = null,
    val rsiCalibrationMethod: String? = null,
    val rsiCalibrationSampleRange: String? = null,
    val safeRules: SafeRulesConfig = SafeRulesConfig(),
    val blockedEntryDays: List<String> = emptyList(),
)

data class SafeRulesConfig(
    val initialRankFilter: Int = 25,
    val maxMoveFrom30DayLowPct: Double = 20.0,
    val maxDailyMove5dPct: Double = 8.0,
    val displayCount: Int = 15,
    val minVolumeExhaustionRatio: Double? = null,
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
    val maxExtensionAboveSma20ForSkipNewEntry: Double = DEFAULT_MAX_EXTENSION_ABOVE_SMA20_FOR_SKIP_NEW_ENTRY,
    val maxExtensionAboveSma20ForSkipNewEntryPct: Double? = null,
    val rebalanceDay: String = "FRIDAY",
    val rebalanceTime: String = "15:40",
    val rsiCalibrationRunAt: String? = null,
    val rsiCalibrationMethod: String? = null,
    val rsiCalibrationSampleRange: String? = null,
    val safeRules: SafeRulesConfig = SafeRulesConfig(),
    val blockedEntryDays: List<String> = emptyList(),
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
        maxExtensionAboveSma20ForSkipNewEntry = maxExtensionAboveSma20ForSkipNewEntry,
        maxExtensionAboveSma20ForSkipNewEntryPct = maxExtensionAboveSma20ForSkipNewEntry * 100.0,
        rebalanceDay = rebalanceDay,
        rebalanceTime = rebalanceTime,
        rsiCalibrationRunAt = rsiCalibrationRunAt,
        rsiCalibrationMethod = rsiCalibrationMethod,
        rsiCalibrationSampleRange = rsiCalibrationSampleRange,
        safeRules = safeRules,
        blockedEntryDays = blockedEntryDays,
    )

    companion object {
        const val DEFAULT_BASE_UNIVERSE_PRESET: String = "NIFTY_LARGEMIDCAP_250"
        const val DEFAULT_SMALLCAP_UNIVERSE_PRESET: String = "NIFTY_SMALLCAP_250"
        const val DEFAULT_NIFTY50_UNIVERSE_PRESET: String = "NIFTY_50"
        const val DEFAULT_MIN_AVERAGE_TRADED_VALUE_CR: Double = 10.0
        const val DEFAULT_BOARD_DISPLAY_COUNT: Int = 40
        const val DEFAULT_REPLACEMENT_POOL_COUNT: Int = 40
        const val DEFAULT_MAX_EXTENSION_ABOVE_SMA20_FOR_NEW_ENTRY: Double = 0.20
        const val DEFAULT_MAX_EXTENSION_ABOVE_SMA20_FOR_SKIP_NEW_ENTRY: Double = 0.30
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
            RsiMomentumProfileConfig(
                id = "nifty50",
                label = "Nifty50",
                baseUniversePreset = DEFAULT_NIFTY50_UNIVERSE_PRESET,
            ),
        )
    }
}

data class RsiMomentumRankedStock(
    val rank: Int,
    val rank5DaysAgo: Int? = null,
    val rankImprovement: Int? = null,
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
    val moveFrom30DayLowPct: Double = 0.0,
    val maxDailyMove5dPct: Double = 0.0,
    val buyZoneLow10w: Double,
    val buyZoneHigh10w: Double,
    val lowestRsi50d: Double,
    val highestRsi50d: Double,
    val avgTradedValueCr: Double,
    val avgVol3d: Double = 0.0,
    val avgVol20d: Double = 0.0,
    val volumeRatio: Double = 1.0,
    val inBaseUniverse: Boolean = false,
    val inWatchlist: Boolean = false,
    val entryBlocked: Boolean = false,
    val entryBlockReason: String? = null,
    val targetWeightPct: Double = 0.0,
    val entryAction: String? = null,
)

data class RsiMomentumRebalance(
    val entries: List<String>,
    val exits: List<String>,
    val holds: List<String>,
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
    val profileId: String,
    val profileLabel: String? = null,
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
    val rebalance: RsiMomentumRebalance? = null,
    val diagnostics: RsiMomentumDiagnostics? = null,
)

enum class RsiBacktestLogicType {
    LEADER,
    JUMPER,
    HYBRID
}

enum class RsiBacktestExitMode {
    T_PLUS_3,
    RSI_60,
    T_PLUS_3_OR_RSI_60,
}

data class RsiMomentumBacktestRequest(
    val profileId: String,
    val logicType: RsiBacktestLogicType = RsiBacktestLogicType.HYBRID,
    val fromDate: String? = null,
    val toDate: String? = null,
    val initialCapital: Double = 100000.0,
    val targetPct: Double = 10.0,
    val stopLossPct: Double = 3.0,
    val runBackfill: Boolean = true,
    val entryRankMin: Int = 21,
    val entryRankMax: Int = 40,
    val rankLookbackDays: Int = 5,
    val jumpMin: Int = 0,
    val jumpMax: Int = 3,
    val blockedEntryDays: List<String> = emptyList(),
    val exitMode: RsiBacktestExitMode = RsiBacktestExitMode.T_PLUS_3_OR_RSI_60,
    val rsiExitThreshold: Double = 60.0,
)

data class BacktestTrade(
    val symbol: String,
    val companyName: String,
    val entryDate: String,
    val exitDate: String,
    val entryPrice: Double,
    val exitPrice: Double,
    val targetPrice: Double,
    val stopLossPrice: Double,
    val result: String, // "PROFIT" or "LOSS"
    val profitPct: Double,
    val profitAmount: Double,
    val holdingDays: Int,
    val entryRank: Int,
    val entryRankImprovement: Int?,
    val entryRsi22: Double?,
    val exitRsi22: Double?,
    val entryFarthestRankInLookback: Int?,
    val entryJumpFromFarthest: Int?,
    val exitReason: String,
)

data class RsiMomentumBacktestReport(
    val profileId: String,
    val logicType: RsiBacktestLogicType,
    val fromDate: String,
    val toDate: String,
    val initialCapital: Double,
    val finalCapital: Double,
    val totalProfit: Double,
    val totalProfitPct: Double,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val winRate: Double,
    val avgHoldingDays: Double,
    val trades: List<BacktestTrade>,
    val entryRankMin: Int,
    val entryRankMax: Int,
    val rankLookbackDays: Int,
    val jumpMin: Int,
    val jumpMax: Int,
    val blockedEntryDays: List<String>,
    val exitMode: RsiBacktestExitMode,
    val rsiExitThreshold: Double,
)

data class RsiMomentumMultiSnapshot(
    val profiles: List<RsiMomentumSnapshot>,
    val errors: List<RsiMomentumProfileError> = emptyList(),
    val partialSuccess: Boolean = false,
)

data class RsiMomentumProfileError(
    val profileId: String,
    val message: String,
)

data class RsiMomentumHistoryEntry(
    val profileId: String,
    val asOfDate: String,
    val runAt: String,
    val snapshot: RsiMomentumSnapshot,
)

data class DrawdownBucketSummary(
    val atLeast20Pct: Int,
    val atLeast30Pct: Int,
    val atLeast40Pct: Int,
    val atLeast50Pct: Int,
    val atLeast60Pct: Int,
)

data class DrawdownBucketFlags(
    val atLeast20Pct: Boolean,
    val atLeast30Pct: Boolean,
    val atLeast40Pct: Boolean,
    val atLeast50Pct: Boolean,
    val atLeast60Pct: Boolean,
)

data class MomentumLeaderRow(
    val symbol: String,
    val companyName: String,
    val instrumentToken: Long,
    val profileIds: List<String>,
    val entryCount: Int,
    val bestRank: Int,
    val firstSeen: String,
    val lastSeen: String,
    val high1yClose: Double?,
    val todayClose: Double?,
    val minClose20d: Double?,
    val ddTodayPct: Double?,
    val dd20dMinPct: Double?,
    val ddTodayBuckets: DrawdownBucketFlags,
    val dd20dMinBuckets: DrawdownBucketFlags,
)

data class LeadersDrawdownProfileSection(
    val profileId: String,
    val profileLabel: String,
    val rowCount: Int,
    val rows: List<MomentumLeaderRow>,
    val ddTodayBucketSummary: DrawdownBucketSummary,
    val dd20dMinBucketSummary: DrawdownBucketSummary,
    val warnings: List<String> = emptyList(),
)

data class LeadersDrawdownCombinedSection(
    val rowCount: Int,
    val rows: List<MomentumLeaderRow>,
    val ddTodayBucketSummary: DrawdownBucketSummary,
    val dd20dMinBucketSummary: DrawdownBucketSummary,
    val warnings: List<String> = emptyList(),
)

data class LeadersDrawdownMeta(
    val fromDate: String,
    val toDate: String,
    val asOfDate: String,
    val topN: Int,
    val profileIds: List<String>,
)

data class LeadersDrawdownResponse(
    val meta: LeadersDrawdownMeta,
    val profiles: List<LeadersDrawdownProfileSection>,
    val combined: LeadersDrawdownCombinedSection,
)

data class BacktestRequest(
    val profileId: String,
    val fromDate: String? = null,
    val toDate: String? = null,
    val topN: Int? = 10,
    val statefulConfig: StatefulBacktestConfig? = null,
)

data class SimpleMomentumBacktestRequest(
    val profileId: String,
    val fromDate: String? = null,
    val toDate: String? = null,
    val initialCapital: Double = 200000.0,
    val entryRankMin: Int = 1,
    val entryRankMax: Int = 5,
    val holdRankMax: Int = 10,
)

data class SimpleMomentumTrade(
    val symbol: String,
    val companyName: String,
    val entryDate: String,
    val entryRank: Int,
    val entryPrice: Double,
    val quantity: Int,
    val investedAmount: Double,
    val exitDate: String?,
    val exitRank: Int?,
    val exitPrice: Double?,
    val exitAmount: Double?,
    val pnlAmount: Double?,
    val pnlPct: Double?,
    val daysHeld: Int,
    val status: String, // "OPEN" or "CLOSED"
    val exitReason: String?,
    val peakCloseSinceEntry: Double?,
    val trailingStopPriceAtExit: Double?,
)

data class SimpleMomentumBacktestSummary(
    val totalTrades: Int,
    val closedTrades: Int,
    val openPositions: Int,
    val winRate: Double?,
    val finalCapital: Double,
    val totalProfit: Double,
    val totalProfitPct: Double,
    val cashBalance: Double,
    val entriesSkippedByDrawdownGuard: Int,
    val exitsByTrailingStop: Int,
)

data class SimpleMomentumBacktestResult(
    val profileId: String,
    val fromDate: String,
    val toDate: String,
    val firstSnapshotDate: String?,
    val lastSnapshotDate: String?,
    val initialCapital: Double,
    val entryRankMin: Int,
    val entryRankMax: Int,
    val holdRankMax: Int,
    val drawdownGuardLookbackDays: Int,
    val drawdownGuardThresholdPct: Double,
    val trailingStopPct: Double,
    val snapshotDaysUsed: Int,
    val summary: SimpleMomentumBacktestSummary,
    val trades: List<SimpleMomentumTrade>,
)

data class StatefulBacktestConfig(
    val enabled: Boolean = false,
    val entryRankMax: Int = 10,
    val takeProfitRank: Int = 3,
    val exitOnTakeProfitLeave: Boolean = true,
    val giveUpRankMin: Int = 20,
)

data class BacktestResult(
    val profileId: String,
    val fromDate: String,
    val toDate: String,
    val topN: Int?,
    val statefulConfig: StatefulBacktestConfig? = null,
    val snapshotDaysUsed: Int,
    val summary: BacktestSummary,
    val trades: List<StockTrade>,
)

data class BacktestSummary(
    val totalTrades: Int,
    val closedTrades: Int,
    val openPositions: Int,
    val winRate: Double?,
    val avgReturnPct: Double?,
    val avgDaysHeld: Double,
    val totalTurnover: Int,
)

data class StockTrade(
    val symbol: String,
    val companyName: String,
    val entryDate: String,
    val entryPrice: Double,
    val entryRank: Int,
    val entryAvgRsi: Double,
    val exitDate: String?,
    val exitPrice: Double?,
    val exitRank: Int?,
    val exitAvgRsi: Double?,
    val daysHeld: Int,
    val returnPct: Double?,
    val status: String, // "OPEN" or "CLOSED"
)

data class LifecycleEpisode(
    val symbol: String,
    val entryDate: String,
    val exitDate: String?,
    val daysInTop10: Int,
    val bestRank: Int,
    val bestRankDate: String,
    val exitReason: String,
    val rankTimeline: List<RankTimelinePoint>,
)

data class RankTimelinePoint(
    val date: String,
    val rank: Int?,
    val inTop10: Boolean,
    val price: Double?,
    val avgRsi: Double?,
)

data class LifecycleSymbolDetail(
    val profileId: String,
    val symbol: String,
    val fromDate: String,
    val toDate: String,
    val episodes: List<LifecycleEpisode>,
)

data class LifecycleSummary(
    val profileId: String,
    val fromDate: String,
    val toDate: String,
    val totalEpisodes: Int,
    val avgDaysInTop10: Double,
    val medianDaysInTop10: Double,
    val shortStayChurnRate: Double,
    val rankBucketTransitions: List<RankBucketTransition>,
)

data class RankBucketTransition(
    val from: String,
    val to: String,
    val count: Int,
)

data class MultiSymbolHistoryResponse(
    val profileId: String,
    val fromDate: String,
    val toDate: String,
    val symbols: List<String>,
    val timelines: Map<String, List<RankTimelinePoint>>,
)
