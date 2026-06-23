package com.tradingtool.core.strategy.phasedbreakout

data class PhaseCWatchlistDto(
    val symbol: String,
    val stockName: String?,
    val marketcapname: String?,
    val closePrice: Double?,
    val pctChange: String?,
    val volume: Long?,
    val sector: String?,
    val industry: String?,
    val roce: Double?,
    val ronw: Double?,
    val netProfit3qAgo: Double?,
    val debtEquity: Double?,
    val volDry200Min: Int?,
    val volDry60Min: Int?,
    val volDry200Min105: Int?,
    val volDry60Min105: Int?,
    val promoterHolding: Double?,
    val foreignPromoterHolding: Double?,
    val grossSales: Double?,
    val high252d: Double?,
    val min20dHigh: Double?,
    val dist200dHigh: Double?,
    val brackets2: Double?,
    val atrCount: Int?
)

data class PhaseCWatchlistUploadRequest(
    val rows: List<PhaseCWatchlistDto>
)

data class PhaseCWatchlistUploadResponse(
    val insertedCount: Int,
    val updatedCount: Int
)
