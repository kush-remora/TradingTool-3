package com.tradingtool.core.screener

import com.tradingtool.core.technical.AdaptiveRsiStatus

// ── Overview list (Screen 1) ──────────────────────────────────────────────────

data class WeeklyPatternResult(
    val symbol: String,
    val exchange: String,
    val instrumentToken: Long,
    val companyName: String,
    val sourceBuckets: List<String> = emptyList(),
    val weeksAnalyzed: Int,
    val buyDay: String,
    val entryReboundPct: Double,
    val rsiLookbackDays: Int,
    val rsiOverboughtPercentile: Double,
    val stopLossPct: Double,
    val buyDayAvgDipPct: Double,
    val reboundConsistency: Int,
    val sellDay: String,
    val swingAvgPct: Double,
    val avgPotentialPct: Double,       // Week Low to Week High (absolute weekly swing)
    val swingConsistency: Int,
    val compositeScore: Int,
    val patternConfirmed: Boolean,
    val cycleType: String,
    val reason: String? = null,
    val buyDayLowMin: Double,
    val buyDayLowMax: Double,
    val currentRsiStatus: AdaptiveRsiStatus? = null,
    val targetRecommendation: TargetRecommendation? = null,
    val vcpTightnessPct: Double? = null,
    val volumeSignatureRatio: Double? = null,
    val mondayStrikeRatePct: Double? = null,
    val setupQualityScore: Int,
    val expectedSwingPct: Double,
    val baselineDistancePct: Double?,
    val swingSetup: SwingSetup? = null,
)

/** Response envelope for GET /api/screener/weekly-pattern — adds run metadata. */
data class WeeklyPatternListResponse(
    val runAt: String,
    val lookbackWeeks: Int,
    val buyZoneLookbackWeeks: Int,
    val universeSourceTags: List<String>,
    val results: List<WeeklyPatternResult>,
)

data class SwingSetup(
    val buyZoneMin: Double,
    val buyZoneMax: Double,
    val safeTargetPct: Double,
    val recommendedTargetPct: Double,
    val aggressiveTargetPct: Double,
    val expectedSwingPct: Double,
    val hardStopLossPct: Double,
    val invalidationCondition: String,
    val confidence: String,
    val reasoning: String,
)

// ── Detail view (Screens 2 + 3) ──────────────────────────────────────────────

data class DayProfile(
    val day: String,
    val action: String,
    val avgChangePct: Double,
)

data class AutocorrelationResult(
    val lag5: Double,
    val lag10: Double,
    val lag21: Double,
)

data class WeekHeatmapRow(
    val weekLabel: String,
    val startDate: String,
    val endDate: String,
    val mondayChangePct: Double?,
    val tuesdayChangePct: Double?,
    val wednesdayChangePct: Double?,
    val thursdayChangePct: Double?,
    val fridayChangePct: Double?,
    val entryTriggered: Boolean,
    val swingTargetHit: Boolean,
    val buyPriceActual: Double?,
    val sellPriceActual: Double?,
    val buyRsi: Double?,
    val netSwingPct: Double?,
    val maxPotentialPct: Double?,      // Week Low to Week High (absolute weekly swing)
    val reasoning: String?
)

data class TargetScenario(
    val targetPct: Double,
    val entries: Int,
    val winRatePct: Double,
    val stopLossRatePct: Double,
    val avgSwingPct: Double,
    val captureRatioPct: Double,
    val feasible: Boolean,
)

data class TargetRecommendation(
    val recommendedTargetPct: Double,
    val safeTargetPct: Double,
    val aggressiveTargetPct: Double,
    val confidence: String,
    val expectedSwingPct: Double,
    val expectedWinRatePct: Double,
    val expectedStopLossRatePct: Double,
    val captureRatioPct: Double,
)

/** Full detail response for GET /api/screener/weekly-pattern/{symbol} (Screens 2 + 3). */
data class WeeklyPatternDetail(
    val symbol: String,
    val exchange: String,
    val instrumentToken: Long,
    val companyName: String,
    val weeksAnalyzed: Int,
    val buyDay: String,
    val entryReboundPct: Double,
    val rsiLookbackDays: Int,
    val rsiOverboughtPercentile: Double,
    val stopLossPct: Double,
    val buyDayAvgDipPct: Double,
    val reboundConsistency: Int,
    val sellDay: String,
    val swingAvgPct: Double,
    val avgPotentialPct: Double,
    val swingConsistency: Int,
    val compositeScore: Int,
    val patternConfirmed: Boolean,
    val cycleType: String,
    val reason: String? = null,
    val buyDayLowMin: Double,
    val buyDayLowMax: Double,
    val vcpTightnessPct: Double? = null,
    val volumeSignatureRatio: Double? = null,
    val mondayStrikeRatePct: Double? = null,
    val dayOfWeekProfile: List<DayProfile>,
    val autocorrelation: AutocorrelationResult,
    val patternSummary: String,
    val weeklyHeatmap: List<WeekHeatmapRow>,
    val targetRecommendation: TargetRecommendation? = null,
    val targetScenarios: List<TargetScenario> = emptyList(),
    val setupQualityScore: Int,
    val expectedSwingPct: Double,
    val baselineDistancePct: Double?,
    val swingSetup: SwingSetup? = null,
)
