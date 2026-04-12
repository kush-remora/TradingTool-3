package com.tradingtool.core.delivery.reconciliation

import com.tradingtool.core.delivery.model.DeliveryReconciliationStatus
import com.tradingtool.core.delivery.model.DeliverySourceRow
import java.time.LocalDate

data class DeliveryExpectation(
    val stockId: Long?,
    val instrumentToken: Long,
    val symbol: String,
    val exchange: String,
)

data class DeliveryReconciliationUpsert(
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
)

data class DeliveryDateReconciliationResult(
    val tradingDate: LocalDate,
    val expectedCount: Int,
    val alreadyComplete: Boolean,
    val fetchedFromSource: Boolean,
    val presentCount: Int,
    val missingFromSourceCount: Int,
)

data class DeliverySourceRowsBySymbol(
    val tradingDate: LocalDate,
    val rowsBySymbol: Map<String, DeliverySourceRow>,
)
