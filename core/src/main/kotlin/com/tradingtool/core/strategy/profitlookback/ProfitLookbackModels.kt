package com.tradingtool.core.strategy.profitlookback

import java.time.LocalDate

data class ProfitLookbackRequest(
    val symbol: String,
    val instrumentToken: Long,
    val sellDate: String,
    val lookbackDays: Int,
    val targetPercents: List<Double>,
)

data class ProfitLookbackBulkRequest(
    val lookbackDays: Int,
    val targetPercents: List<Double>,
    val rows: List<ProfitLookbackBulkRowRequest>,
)

data class ProfitLookbackBulkRowRequest(
    val rowId: String,
    val symbol: String,
    val instrumentToken: Long,
    val sellDate: String,
)

data class ProfitLookbackResponse(
    val symbol: String,
    val instrumentToken: Long,
    val requestedSellDate: String,
    val resolvedSellDate: String,
    val sellOpenPrice: Double,
    val results: List<ProfitLookbackTargetResult>,
)

data class ProfitLookbackBulkResponse(
    val rows: List<ProfitLookbackBulkRowResponse>,
)

data class ProfitLookbackBulkRowResponse(
    val rowId: String,
    val ok: Boolean,
    val data: ProfitLookbackResponse?,
    val error: String?,
)

data class ProfitLookbackTargetResult(
    val targetPercent: Double,
    val status: String,
    val suggestedBuyDate: String?,
    val buyOpenPrice: Double?,
    val daysBefore: Int?,
    val returnPercent: Double?,
    val maxDrawdownPercent: Double?,
    val maxDrawdownDays: Int?,
)

data class ProfitLookbackDailyCandle(
    val date: LocalDate,
    val open: Double,
    val low: Double,
)
