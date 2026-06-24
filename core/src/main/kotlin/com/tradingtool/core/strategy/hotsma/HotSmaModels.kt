package com.tradingtool.core.strategy.hotsma

import java.time.Instant
import java.time.LocalDate

data class HotSmaRunRequest(
    val indexKeys: List<String>,
)

data class HotSmaRunConfig(
    val indexKeys: List<String>,
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

enum class HotSmaZoneStatus {
    BUY_ZONE,
    ABOVE_BUY_ZONE,
    NO_SMA200,
}

data class HotSmaRow(
    val symbol: String,
    val companyName: String,
    val indexKey: String,
    val instrumentToken: Long,
    val latestDate: String,
    val currentPrice: Double,
    val previousCloseChangePct: Double?,
    val sma50: Double?,
    val sma100: Double?,
    val sma200: Double?,
    val pctToSma50: Double?,
    val pctToSma100: Double?,
    val pctToSma200: Double?,
    val distanceToSma200AbsPct: Double?,
    val rsi14: Double?,
    val drawdownFromHigh20Pct: Double?,
    val drawdownFromHigh60Pct: Double?,
    val consecutiveRedDays: Int,
    val move3dPct: Double?,
    val sma100TouchedInLast5d: Boolean,
    val sma100TouchDate: LocalDate?,
    val sma200TouchedInLast5d: Boolean,
    val sma200TouchDate: LocalDate?,
    val signalTag: HotSmaSignalTag?,
    val zoneStatus: HotSmaZoneStatus,
)

data class HotSmaSummary(
    val totalStocks: Int,
    val buyZoneCount: Int,
    val aboveBuyZoneCount: Int,
    val noSma200Count: Int,
)

data class HotSmaConfigSnapshot(
    val rsiPeriod: Int,
    val sma50Window: Int,
    val sma100Window: Int,
    val sma200Window: Int,
    val buyZoneUpperPct: Double,
    val drawdownWindow20: Int,
    val drawdownWindow60: Int,
    val useAvailableHistoryForSma200: Boolean,
)

data class HotSmaRunResponse(
    val runAt: String = Instant.now().toString(),
    val selectedIndexKeys: List<String>,
    val config: HotSmaConfigSnapshot,
    val summary: HotSmaSummary,
    val rows: List<HotSmaRow>,
)
