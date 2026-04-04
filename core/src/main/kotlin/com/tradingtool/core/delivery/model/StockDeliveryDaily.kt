package com.tradingtool.core.delivery.model

import java.time.LocalDate
import java.time.OffsetDateTime

data class StockDeliveryDaily(
    val stockId: Int,
    val symbol: String,
    val exchange: String,
    val tradingDate: LocalDate,
    val series: String?,
    val ttlTrdQnty: Long?,
    val delivQty: Long?,
    val delivPer: Double?,
    val sourceFileName: String?,
    val sourceUrl: String?,
    val fetchedAt: OffsetDateTime? = null
)
