package com.tradingtool.core.strategy.phasedbreakout

import java.time.LocalDate

data class Phase2DeliveryConfig(
    val baselineLookbackDays: Int = 60,
    val basementPercentile: Double = 0.10,
    val deliverySpikeThreshold: Double = 1.5,
    val deliveryPctSupportThreshold: Double = 55.0,
    val watchCount10d: Int = 1,
    val watchCount20d: Int = 2,
    val passCount10d: Int = 2,
    val passCount20d: Int = 4,
)

data class Phase2DeliveryMetrics(
    val status: String,
    val reason: String,
    val evaluatedOn: LocalDate?,
    val deliveryQuantityToday: Long?,
    val deliveryPctToday: Double?,
    val wholesaleBaseDq: Long?,
    val deliverySpikeRatio: Double?,
    val deliverySpikeDays10d: Int?,
    val deliverySpikeDays20d: Int?,
    val deliverySupportDays10d: Int?,
    val deliverySupportDays20d: Int?,
)

data class Phase2DeliveryUpdate(
    val symbol: String,
    val phase2DeliveryStatus: String,
    val phase2Reason: String,
    val phase2EvaluatedOn: LocalDate?,
    val deliveryQuantityToday: Long?,
    val deliveryPctToday: Double?,
    val wholesaleBaseDq: Long?,
    val deliverySpikeRatio: Double?,
    val deliverySpikeDays10d: Int?,
    val deliverySpikeDays20d: Int?,
    val deliverySupportDays10d: Int?,
    val deliverySupportDays20d: Int?,
)

data class Phase2DeliveryValidationRunResponse(
    val evaluatedOn: LocalDate?,
    val totalStocks: Int,
    val passed: Int,
    val watch: Int,
    val notPassed: Int,
    val notRun: Int,
    val dataMissing: Int,
)
