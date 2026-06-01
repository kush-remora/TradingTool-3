package com.tradingtool.core.strategy.hotsma

import java.time.Instant
import java.time.LocalDate

data class HotSmaRunRequest(
    val indexKey: String,
)

data class HotSmaRunConfig(
    val indexKey: String,
)

data class HotSmaTelegramRequest(
    val indexKey: String,
    val symbol: String,
    val signalTag: HotSmaSignalTag,
    val currentPrice: Double,
    val sma50: Double?,
    val sma100: Double?,
    val sma200: Double?,
    val pctToSma50: Double?,
    val pctToSma100: Double?,
    val pctToSma200: Double?,
    val rsi14: Double?,
)

data class HotSmaUniverseOption(
    val value: String,
    val count: Int,
)

enum class HotSmaSignalTag {
    AGGRESSIVE_BUY,
    STANDARD_BUY,
    WATCH_ZONE,
}

data class HotSmaRow(
    val symbol: String,
    val companyName: String,
    val indexKey: String,
    val instrumentToken: Long,
    val latestDate: String,
    val currentPrice: Double,
    val sma50: Double?,
    val sma100: Double?,
    val sma200: Double?,
    val pctToSma50: Double?,
    val pctToSma100: Double?,
    val pctToSma200: Double?,
    val rsi14: Double?,
    val sma100TouchedInLast5d: Boolean,
    val sma100TouchDate: LocalDate?,
    val sma200TouchedInLast5d: Boolean,
    val sma200TouchDate: LocalDate?,
    val signalTag: HotSmaSignalTag,
)

data class HotSmaSummary(
    val totalSignals: Int,
    val aggressiveCount: Int,
    val standardCount: Int,
    val watchCount: Int,
)

data class HotSmaConfigSnapshot(
    val touchLookbackDays: Int,
    val watchZoneUpperPct: Double,
    val rsiPeriod: Int,
    val sma50Window: Int,
    val sma100Window: Int,
    val sma200Window: Int,
    val useAvailableHistoryForSma200: Boolean,
)

data class HotSmaRunResponse(
    val runAt: String = Instant.now().toString(),
    val selectedIndexKey: String,
    val config: HotSmaConfigSnapshot,
    val summary: HotSmaSummary,
    val rows: List<HotSmaRow>,
)
