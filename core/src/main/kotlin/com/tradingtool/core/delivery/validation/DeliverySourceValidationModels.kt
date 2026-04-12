package com.tradingtool.core.delivery.validation

import com.tradingtool.core.delivery.model.DeliverySourceType
import java.time.LocalDate

data class DeliveryFileDescriptor(
    val tradingDate: LocalDate,
    val url: String,
    val fileName: String,
)

data class DeliveryDiscoveryResult(
    val resolvedTradingDate: LocalDate,
    val bucket: String,
    val bhavDataFull: DeliveryFileDescriptor?,
    val mto: DeliveryFileDescriptor?,
)

data class DeliverySourceAssessment(
    val source: DeliverySourceType,
    val available: Boolean,
    val downloadSucceeded: Boolean,
    val parseSucceeded: Boolean,
    val fileName: String? = null,
    val fileUrl: String? = null,
    val rowCount: Int = 0,
    val targetSymbolCount: Int = 0,
    val matchedTargetCount: Int = 0,
    val missingTargetSymbols: List<String> = emptyList(),
    val coverageRatio: Double = 0.0,
    val error: String? = null,
)

data class DeliveryMismatch(
    val symbol: String,
    val bhavDeliveryPercent: Double,
    val mtoDeliveryPercent: Double,
    val absoluteDifference: Double,
)

data class DeliveryComparisonSummary(
    val comparedSymbolCount: Int = 0,
    val bhavOnlySymbols: List<String> = emptyList(),
    val mtoOnlySymbols: List<String> = emptyList(),
    val materialMismatches: List<DeliveryMismatch> = emptyList(),
    val materiallyConsistent: Boolean = false,
)

enum class DeliveryRecommendation {
    BHAVDATA_FULL,
    MTO,
    PIVOT_REQUIRED,
}

data class DeliverySourceValidationReport(
    val requestedDate: LocalDate? = null,
    val resolvedTradingDate: LocalDate? = null,
    val discoveryWorked: Boolean,
    val discoveryBucket: String? = null,
    val discoveryError: String? = null,
    val targetSymbols: List<String> = emptyList(),
    val bhavDataFull: DeliverySourceAssessment,
    val mto: DeliverySourceAssessment,
    val comparison: DeliveryComparisonSummary = DeliveryComparisonSummary(),
    val recommendedSource: DeliveryRecommendation,
    val verdict: String,
)
