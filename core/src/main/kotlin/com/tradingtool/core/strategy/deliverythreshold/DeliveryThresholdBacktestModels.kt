package com.tradingtool.core.strategy.deliverythreshold

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import java.time.LocalDate

data class DeliveryThresholdBacktestRequest(
    val indexKeys: List<String> = emptyList(),
    val symbols: List<String> = emptyList(),
    val config: DeliveryThresholdBacktestConfig = DeliveryThresholdBacktestConfig(),
)

data class DeliveryThresholdBacktestConfig(
    val thresholds: Map<String, Double> = mapOf(
        "NIFTY_50" to 55.0,
        "NIFTY_MIDCAP_50" to 60.0,
        "NIFTY_MIDCAP_150" to 60.0,
        "NIFTY_SMALLCAP_50" to 70.0,
        "NIFTY_LARGEMIDCAP_250" to 55.0,
        "NIFTY_SMALLCAP_250" to 70.0,
        "NIFTY_MICROCAP_250" to 85.0,
        "NIFTY_NANOCAP_250" to 92.0,
    ),
    val profitPct: Double = 10.0,
    val fromDate: String? = null,
    val toDate: String? = null,
)

data class DeliveryThresholdBacktestRunConfig(
    val indexKeys: List<String>,
    val symbols: List<String>,
    val thresholdsByIndex: Map<String, Double>,
    val profitPct: Double,
    val fromDate: LocalDate,
    val toDate: LocalDate,
)

data class DeliveryThresholdBacktestConfigSnapshot(
    val indexKeys: List<String>,
    val symbols: List<String>,
    val thresholds: Map<String, Double>,
    val profitPct: Double,
    val fromDate: String,
    val toDate: String,
)

data class DeliveryThresholdBacktestRow(
    val symbol: String,
    val index: String,
    val entryDate: String,
    val entryPrice: Double,
    val entryDeliveryPct: Double,
    val totalVolumeCount: Long?,
    val avg20dVolumeAtSignal: Double?,
    val signalVolumeVs20dPct: Double?,
    val targetPrice: Double,
    val fiftyTwoWeekHighAtBuy: Double?,
    val fiftyTwoWeekLowAtBuy: Double?,
    val pctFrom52WeekHighAtBuy: Double?,
    val pctFrom52WeekLowAtBuy: Double?,
    val buyDayOfWeek: String,
    val exitDate: String?,
    val exitPrice: Double?,
    val holdingDays: Int,
    val rsiBuy: Double?,
    val rsiSell: Double?,
    val maxDrawdownAtBuyPct: Double?,
    val status: String,
    val currentPrice: Double,
    val floatingPnlPct: Double?,
    val thresholdUsed: Double,
)

data class DeliveryThresholdBacktestSummary(
    val totalBuys: Int,
    val hitCount: Int,
    val hitRatePct: Double,
    val daysToHitAvg: Double?,
    val daysToHitMedian: Double?,
    val daysToHitMin: Int?,
    val daysToHitMax: Int?,
    val openCount: Int,
)

data class DeliveryThresholdBacktestResponse(
    val config: DeliveryThresholdBacktestConfigSnapshot,
    val summary: DeliveryThresholdBacktestSummary,
    val rows: List<DeliveryThresholdBacktestRow>,
)

data class DeliveryThresholdSymbolContext(
    val symbol: String,
    val instrumentToken: Long,
    val companyName: String,
    val resolvedIndexKey: String,
    val threshold: Double,
    val candles: List<DailyCandle>,
    val deliveries: List<StockDeliveryDaily>,
)

data class DeliveryThresholdResolvedUniverse(
    val symbols: List<DeliveryThresholdResolvedSymbol>,
)

data class DeliveryThresholdResolvedSymbol(
    val symbol: String,
    val instrumentToken: Long,
    val companyName: String,
    val resolvedIndexKey: String,
    val threshold: Double,
)
