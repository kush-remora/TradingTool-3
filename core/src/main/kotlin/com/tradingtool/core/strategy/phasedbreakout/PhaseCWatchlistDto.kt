package com.tradingtool.core.strategy.phasedbreakout

data class PhaseCWatchlistDto(
    val symbol: String,
    val stockName: String?,
    val marketCapBucket: String?,
    val closePrice: Double?,
    val pctChange: String?,
    val volume: Long?,
    val sector: String?,
    val industry: String?,
    val rocePct: Double?,
    val ronwPct: Double?,
    val netProfitAfterTax: Double?,
    val debtEquityRatio: Double?,
    val volDry200dMinCount: Int?,
    val volDry60dMinCount: Int?,
    val volDry200dMin105Count: Int?,
    val volDry60dMin105Count: Int?,
    val indianPromoterPct: Double?,
    val foreignPromoterPct: Double?,
    val quarterlyGrossSales: Double?,
    val high52w: Double?,
    val low52w: Double?,
    val dist200dHighPct: Double?,
    val dist200dLowPct: Double?,
    val atrLt2pctCount: Int?
)

data class PhaseCWatchlistUploadRequest(
    val rows: List<PhaseCWatchlistDto>
)

data class PhaseCWatchlistUploadResponse(
    val insertedCount: Int,
    val updatedCount: Int
)

data class PhaseCFreshFieldRefreshResponse(
    val refreshedCount: Int,
    val refreshedOn: java.time.LocalDate?,
)

data class PhaseCFreshFieldUpdate(
    val symbol: String,
    val closePrice: Double,
    val pctChange: String,
    val volume: Long,
    val previousDayVolume: Long,
    val high52w: Double,
    val low52w: Double,
    val dist200dHighPct: Double,
    val dist200dLowPct: Double,
    val marketFieldsUpdatedOn: java.time.LocalDate,
)
