package com.tradingtool.core.screener

// ── Overview list (Screen 1) ──────────────────────────────────────────────────

data class WeeklyPatternResult(
    val symbol: String,
    val instrumentToken: Long,
    val companyName: String,
    val weeksAnalyzed: Int,
    val buyDay: String,
    val buyDayAvgDipPct: Double,
    val reboundConsistency: Int,
    val sellDay: String,
    val swingAvgPct: Double,
    val swingConsistency: Int,         // weeks where swing >= 4%
    val compositeScore: Int,           // 0–100
    val patternConfirmed: Boolean,
    val cycleType: String,             // "Weekly" | "Biweekly" | "Monthly" | "None"
    val reason: String? = null,        // set when patternConfirmed = false
    val buyDayLowMin: Double,
    val buyDayLowMax: Double,
)

/** Response envelope for GET /api/screener/weekly-pattern — adds run metadata. */
data class WeeklyPatternListResponse(
    val runAt: String,          // ISO-8601 timestamp of when this response was computed
    val lookbackWeeks: Int,
    val results: List<WeeklyPatternResult>,
)

// ── Detail view (Screens 2 + 3) ──────────────────────────────────────────────

/** Avg return and suggested action for one day of the trading week. */
data class DayProfile(
    val day: String,           // "Mon" | "Tue" | "Wed" | "Thu" | "Fri"
    val action: String,        // "Buy zone" | "Watch" | "Hold" | "Sell zone" | "Exit"
    val avgChangePct: Double,  // Mon = avg morning dip (negative); others = avg close-to-close
)

/** Pearson autocorrelation of daily returns at three lag periods. */
data class AutocorrelationResult(
    val lag5: Double,   // weekly cycle (5 trading days)
    val lag10: Double,  // biweekly (10 trading days)
    val lag21: Double,  // monthly  (21 trading days)
)

/**
 * One row in the week-by-week heatmap (Screen 3).
 * mondayDipPct is NEGATIVE (price fell); thursdaySwingPct is POSITIVE (Mon low → Thu high).
 * Frontend applies green highlight when |mondayDipPct| >= 2 and thursdaySwingPct >= 4.
 */
data class WeekHeatmapRow(
    val weekLabel: String,             // "W-8" (oldest) → "W-1" (most recent)
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
    val reasoning: String?
)

/** Full detail response for GET /api/screener/weekly-pattern/{symbol} (Screens 2 + 3). */
data class WeeklyPatternDetail(
    // ── summary (same fields as WeeklyPatternResult) ──────────────────────
    val symbol: String,
    val instrumentToken: Long,
    val companyName: String,
    val weeksAnalyzed: Int,
    val buyDay: String,
    val buyDayAvgDipPct: Double,
    val reboundConsistency: Int,
    val sellDay: String,
    val swingAvgPct: Double,
    val swingConsistency: Int,
    val compositeScore: Int,
    val patternConfirmed: Boolean,
    val cycleType: String,
    val reason: String? = null,
    // ── detail fields (Screen 2) ──────────────────────────────────────────
    val buyDayLowMin: Double,
    val buyDayLowMax: Double,
    val dayOfWeekProfile: List<DayProfile>,
    val autocorrelation: AutocorrelationResult,
    val patternSummary: String,        // plain-language human readable summary
    // ── heatmap (Screen 3) ───────────────────────────────────────────────
    val weeklyHeatmap: List<WeekHeatmapRow>,
)

// ── Internal ─────────────────────────────────────────────────────────────────

/** One Monday→Thursday pair used during computation. Not exposed in API responses. */
internal data class WeekInstance(
    val isoYear: Int,
    val isoWeek: Int,
    val mondayDipPct: Double?,   // positive magnitude
    val mondayLow: Double?,      // raw price — used for swing calculation
    val swingPct: Double?,
)
