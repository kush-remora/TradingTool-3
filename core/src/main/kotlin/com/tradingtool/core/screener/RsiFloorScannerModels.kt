package com.tradingtool.core.screener

import java.time.Instant
import java.time.LocalDate

data class RsiFloorScannerRequest(
    val universe: String = "ALL_NSE",
    val freshScan: Boolean = false,
    val lookbackMatchDays: Int = DEFAULT_LOOKBACK_MATCH_DAYS,
    val rsiPeriod: Int = DEFAULT_RSI_PERIOD,
    val yearWindowDays: Int = DEFAULT_YEAR_WINDOW_DAYS,
    val hardRsiLimit: Double = DEFAULT_HARD_RSI_LIMIT,
) {
    companion object {
        const val DEFAULT_LOOKBACK_MATCH_DAYS: Int = 14
        const val DEFAULT_RSI_PERIOD: Int = 14
        const val DEFAULT_YEAR_WINDOW_DAYS: Int = 365
        const val DEFAULT_HARD_RSI_LIMIT: Double = 20.0
    }
}

data class RsiFloorScannerRow(
    val symbol: String,
    val companyName: String,
    val exchange: String,
    val instrumentToken: Long,
    val currentRsi: Double,
    val yearLowRsiAtMatchedDay: Double,
    val matchedByYearLow: Boolean,
    val matchedByHardLimit: Boolean,
    val matchedDate: LocalDate,
    val ltp: Double?,
    val drawdownPct: Double?,
    val high52w: Double?,
    val low52w: Double?,
    val marketCapCr: Double?,
    val capBucket: MarketCapBucket,
    val historyType: RsiHistoryType,
)

data class RsiFloorScannerResult(
    val runAt: String = Instant.now().toString(),
    val universe: String,
    val requestedSymbols: Int,
    val scannedSymbols: Int,
    val skippedInsufficientHistory: Int,
    val matchedCount: Int,
    val lookbackMatchDays: Int,
    val rsiPeriod: Int,
    val yearWindowDays: Int,
    val hardRsiLimit: Double,
    val source: RsiFloorScanSource,
    val rows: List<RsiFloorScannerRow>,
)

enum class RsiFloorScanSource {
    CACHE,
    FRESH,
}

enum class RsiHistoryType {
    FULL_1Y,
    PARTIAL_IPO,
}

enum class MarketCapBucket {
    LARGE,
    MID,
    SMALL,
    UNKNOWN,
}

fun classifyMarketCapBucket(marketCapCr: Double?): MarketCapBucket {
    val value = marketCapCr ?: return MarketCapBucket.UNKNOWN
    return when {
        value >= LARGE_CAP_MIN_CR -> MarketCapBucket.LARGE
        value >= MID_CAP_MIN_CR -> MarketCapBucket.MID
        value >= 0.0 -> MarketCapBucket.SMALL
        else -> MarketCapBucket.UNKNOWN
    }
}

fun matchesRsiFloorCondition(
    currentRsi: Double,
    yearLowRsiAtMatchedDay: Double,
    hardRsiLimit: Double,
): Boolean {
    return currentRsi <= yearLowRsiAtMatchedDay || currentRsi <= hardRsiLimit
}

private const val LARGE_CAP_MIN_CR: Double = 20_000.0
private const val MID_CAP_MIN_CR: Double = 5_000.0
