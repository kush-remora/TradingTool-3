package com.tradingtool.core.strategy.phasedbreakout

import java.time.LocalDate

data class PhaseCWatchlistRow(
    val symbol: String,
    val instrumentToken: Long?,
    val addedOn: LocalDate,
    val lastSeenOn: LocalDate,
    val status: String, // 'chartinkFilter', 'BREAKOUT_TRIGGERED', 'EXPIRED'
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
