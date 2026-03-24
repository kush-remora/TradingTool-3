package com.tradingtool.core.model.remora

data class RemoraSignal(
    val id: Int,
    val stockId: Int,
    val symbol: String,
    val companyName: String,
    val exchange: String,
    val signalType: String,       // "ACCUMULATION" or "DISTRIBUTION"
    val volumeRatio: Double,      // e.g. 2.1 means 2.1x the 20-day avg
    val priceChangePct: Double,   // daily price change % on the signal day
    val consecutiveDays: Int,
    val signalDate: String,       // ISO date: "2026-03-25"
    val computedAt: String,
)
