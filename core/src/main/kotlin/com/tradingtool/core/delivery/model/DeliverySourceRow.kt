package com.tradingtool.core.delivery.model

import java.time.LocalDate

enum class DeliverySourceType {
    BHAVDATA_FULL,
    MTO,
}

data class DeliverySourceRow(
    val symbol: String,
    val series: String,
    val tradingDate: LocalDate,
    val tradedQuantity: Long,
    val deliverableQuantity: Long,
    val deliveryPercent: Double,
    val source: DeliverySourceType,
)
