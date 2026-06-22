package com.tradingtool.core.volumeshocker.groww

import java.math.BigDecimal
import java.time.LocalDate

enum class MarketCapCategory {
    LARGE,
    MID,
    SMALL;

    companion object {
        private val LARGE_CAP_MINIMUM_CRORE = BigDecimal("105000")
        private val MID_CAP_MINIMUM_CRORE = BigDecimal("34700")

        fun fromMarketCap(marketCapCrore: BigDecimal): MarketCapCategory {
            return when {
                marketCapCrore >= LARGE_CAP_MINIMUM_CRORE -> LARGE
                marketCapCrore >= MID_CAP_MINIMUM_CRORE -> MID
                else -> SMALL
            }
        }
    }
}

data class GrowwVolumeShockerSourceRow(
    val sourceRank: Int,
    val exchange: String,
    val symbol: String,
    val companyName: String,
    val ltp: BigDecimal,
    val close: BigDecimal,
    val marketCapCrore: BigDecimal,
    val yearLow: BigDecimal,
    val yearHigh: BigDecimal,
    val volume: Long,
    val weeklyAverageVolume: BigDecimal,
)

data class GrowwVolumeShockerDailyRow(
    val tradeDate: LocalDate,
    val sourceRank: Int,
    val exchange: String,
    val symbol: String,
    val instrumentToken: Long,
    val companyName: String,
    val ltp: BigDecimal,
    val close: BigDecimal,
    val marketCapCrore: BigDecimal,
    val marketCapCategory: MarketCapCategory,
    val yearLow: BigDecimal,
    val yearHigh: BigDecimal,
    val volume: Long,
    val weeklyAverageVolume: BigDecimal,
)

data class GrowwVolumeShockerIngestionRequest(
    val tradeDate: LocalDate,
)

data class GrowwVolumeShockerIngestionResult(
    val fetchedCount: Int,
    val storedCount: Int,
)

interface GrowwVolumeShockerSource {
    suspend fun fetchRows(): List<GrowwVolumeShockerSourceRow>
}

interface GrowwVolumeShockerInstrumentTokenResolver {
    suspend fun resolve(exchange: String, symbol: String): Long?
}

interface GrowwVolumeShockerGateway {
    suspend fun replace(tradeDate: LocalDate, rows: List<GrowwVolumeShockerDailyRow>): Int
}
