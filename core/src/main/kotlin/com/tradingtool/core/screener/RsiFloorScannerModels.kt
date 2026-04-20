package com.tradingtool.core.screener

import java.time.Instant
import java.time.LocalDate

data class RsiFloorScannerRequest(
    val universe: String = "ALL_NSE",
    val rsiPeriod: Int = DEFAULT_RSI_PERIOD,
    val lookbackDays: Int = DEFAULT_LOOKBACK_DAYS,
    val hardRsiLimit: Double = DEFAULT_HARD_RSI_LIMIT,
) {
    companion object {
        const val DEFAULT_RSI_PERIOD: Int = 14
        const val DEFAULT_LOOKBACK_DAYS: Int = 252
        const val DEFAULT_HARD_RSI_LIMIT: Double = 20.0
    }
}

data class RsiFloorScannerRow(
    val symbol: String,
    val companyName: String,
    val exchange: String,
    val instrumentToken: Long,
    val currentRsi: Double,
    val lowestRsiLookback: Double,
    val matchedByLookbackLow: Boolean,
    val matchedByHardLimit: Boolean,
    val latestCandleDate: LocalDate,
    val marketCapCr: Double?,
    val capBucket: MarketCapBucket,
)

data class RsiFloorScannerResult(
    val runAt: String = Instant.now().toString(),
    val universe: String,
    val requestedSymbols: Int,
    val scannedSymbols: Int,
    val skippedInsufficientHistory: Int,
    val matchedCount: Int,
    val rsiPeriod: Int,
    val lookbackDays: Int,
    val hardRsiLimit: Double,
    val rows: List<RsiFloorScannerRow>,
)

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
    lowestRsiLookback: Double,
    hardRsiLimit: Double,
): Boolean {
    return currentRsi <= lowestRsiLookback || currentRsi <= hardRsiLimit
}

private const val LARGE_CAP_MIN_CR: Double = 20_000.0
private const val MID_CAP_MIN_CR: Double = 5_000.0
