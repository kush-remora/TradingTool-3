package com.tradingtool.core.delivery.model

import java.time.LocalDate
import java.time.OffsetDateTime

data class StockDeliveryDaily(
    val stockId: Long?,
    val instrumentToken: Long,
    val symbol: String,
    val exchange: String,
    val tradingDate: LocalDate,
    val reconciliationStatus: DeliveryReconciliationStatus,
    val series: String?,
    val ttlTrdQnty: Long?,
    val delivQty: Long?,
    val delivPer: Double?,
    val sourceFileName: String?,
    val sourceUrl: String?,
    val fetchedAt: OffsetDateTime? = null
)
