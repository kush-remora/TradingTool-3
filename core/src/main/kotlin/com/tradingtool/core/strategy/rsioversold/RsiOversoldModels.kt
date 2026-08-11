package com.tradingtool.core.strategy.rsioversold

data class RsiOversoldScanRequest(
    val indexKeys: List<String> = emptyList(),
)

data class RsiOversoldRow(
    val symbol: String,
    val companyName: String?,
    val watchlistKeys: List<String>,
    val signalDate: String,
    val signalRsi: Double,
    val signalPrice: Double,
    val signalVolume: Long,
    val baselineRsiLow: Double,
    val latestDate: String,
    val latestClose: Double,
    val latestVolume: Long,
)

data class RsiOversoldScanConfig(
    val rsiPeriod: Int,
    val baselineSessions: Int,
    val signalWindowSessions: Int,
    val signalOffset: Double,
    val asOfDate: String,
)

data class RsiOversoldScanResponse(
    val selectedIndexKeys: List<String>,
    val config: RsiOversoldScanConfig,
    val scannedStockCount: Int,
    val resultCount: Int,
    val insufficientDataSymbols: List<String>,
    val noSignalSymbols: List<String>,
    val rows: List<RsiOversoldRow>,
)
