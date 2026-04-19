package com.tradingtool.resources

enum class WeeklyCycleUniverse {
    ALL,
    MIDCAP_250,
    SMALLCAP_250,
    BOTH,
    NIFTY_50,
    WATCHLIST,
    NIFTY_150,
}

data class WeeklyCycleSuccessRequest(
    val universe: WeeklyCycleUniverse,
    val weeks: Int,
    val highLowPct: Double,
    val rocPct: Double,
    val stableBaseDriftPct: Double,
    val prepareMissingDaily: Boolean,
) {
    companion object {
        private const val DEFAULT_WEEKS = 8
        private const val DEFAULT_HIGH_LOW = 10.0
        private const val DEFAULT_ROC = 2.0
        private const val DEFAULT_STABLE_BASE_DRIFT = 4.0
        private const val MIN_WEEKS = 1
        private const val MAX_WEEKS = 52

        fun fromQuery(
            universeRaw: String?,
            weeksRaw: Int?,
            highLowPctRaw: Double?,
            rocPctRaw: Double?,
            stableBaseDriftPctRaw: Double?,
            prepareRaw: String?,
        ): WeeklyCycleSuccessRequest? {
            val universe = parseUniverse(universeRaw) ?: return null
            val weeks = weeksRaw ?: DEFAULT_WEEKS
            if (weeks !in MIN_WEEKS..MAX_WEEKS) return null

            val highLowPct = highLowPctRaw ?: DEFAULT_HIGH_LOW
            val rocPct = rocPctRaw ?: DEFAULT_ROC
            val stableBaseDriftPct = stableBaseDriftPctRaw ?: DEFAULT_STABLE_BASE_DRIFT
            if (highLowPct < 0.0 || rocPct < 0.0 || stableBaseDriftPct < 0.0) return null
            val prepareMissingDaily = parsePrepare(prepareRaw)

            return WeeklyCycleSuccessRequest(
                universe = universe,
                weeks = weeks,
                highLowPct = highLowPct,
                rocPct = rocPct,
                stableBaseDriftPct = stableBaseDriftPct,
                prepareMissingDaily = prepareMissingDaily,
            )
        }

        private fun parseUniverse(raw: String?): WeeklyCycleUniverse? {
            if (raw.isNullOrBlank()) {
                return WeeklyCycleUniverse.BOTH
            }
            return when (raw.trim().uppercase()) {
                "ALL", "FULL", "COMPLETE" -> WeeklyCycleUniverse.ALL
                "MIDCAP_250", "MIDCAP250", "NIFTY_MIDCAP_250" -> WeeklyCycleUniverse.MIDCAP_250
                "SMALLCAP_250", "SMALLCAP250", "NIFTY_SMALLCAP_250" -> WeeklyCycleUniverse.SMALLCAP_250
                "BOTH" -> WeeklyCycleUniverse.BOTH
                "NIFTY_50", "NIFTY50" -> WeeklyCycleUniverse.NIFTY_50
                "WATCHLIST" -> WeeklyCycleUniverse.WATCHLIST
                "NIFTY_150", "NIFTY150", "NIFTY_MIDCAP_150", "MIDCAP_150", "MIDCAP150" -> WeeklyCycleUniverse.NIFTY_150
                else -> null
            }
        }

        private fun parsePrepare(raw: String?): Boolean {
            if (raw.isNullOrBlank()) return false
            return when (raw.trim().lowercase()) {
                "1", "true", "yes", "y" -> true
                else -> false
            }
        }
    }
}
