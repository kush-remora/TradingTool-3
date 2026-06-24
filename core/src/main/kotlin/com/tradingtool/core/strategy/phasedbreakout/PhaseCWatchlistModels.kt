package com.tradingtool.core.strategy.phasedbreakout

import java.time.LocalDate

data class PhaseCWatchlistRow(
    val symbol: String,
    val instrumentToken: Long?,
    val addedOn: LocalDate,
    val lastSeenOn: LocalDate,
    val status: String, // 'chartinkFilter', 'BREAKOUT_TRIGGERED', 'EXPIRED'
    val stockName: String?,
    val marketCapBucket: String?,
    val closePrice: Double?,
    val pctChange: String?,
    val volume: Long?,
    val sector: String?,
    val industry: String?,
    val rocePct: Double?,
    val ronwPct: Double?,
    val netProfit3qAgo: Double?,
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
