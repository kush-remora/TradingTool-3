package com.tradingtool.core.strategy.priceacceptance

data class PriceAcceptanceScanResponse(
    val selectedIndexKey: String,
    val requestedAsOfDate: String,
    val scannedStockCount: Int,
    val resultCount: Int,
    val rows: List<PriceAcceptanceRow>,
)

data class PriceAcceptanceRow(
    val symbol: String,
    val companyName: String,
    val indexKey: String,
    val instrumentToken: Long,
    val anchorDate: String,
    val open: Double,
    val close: Double,
    val bodyLow: Double,
    val bodyHigh: Double,
    val bodyRangePct: Double,
    val priorSessionCount: Int,
    val closeHits20: Int,
    val closeHitRate20Pct: Double,
    val closeHits40: Int,
    val closeHitRate40Pct: Double,
    val closeHits60: Int,
    val closeHitRate60Pct: Double,
    val closeHits80: Int,
    val closeHitRate80Pct: Double,
    val closeHits100: Int,
    val closeHitRate100Pct: Double,
)
