package com.tradingtool.core.fundamentals.model

import com.tradingtool.core.delivery.model.DeliveryUniverse
import java.time.LocalDate
import java.time.OffsetDateTime

data class StockFundamentalsDaily(
    val stockId: Long?,
    val instrumentToken: Long,
    val symbol: String,
    val exchange: String,
    val universe: DeliveryUniverse,
    val snapshotDate: LocalDate,
    val companyName: String,
    val marketCapCr: Double?,
    val stockPe: Double?,
    val rocePercent: Double?,
    val roePercent: Double?,
    val promoterHoldingPercent: Double?,
    val broadIndustry: String?,
    val industry: String?,
    val sourceName: String,
    val sourceUrl: String,
    val fetchedAt: OffsetDateTime? = null,
)
