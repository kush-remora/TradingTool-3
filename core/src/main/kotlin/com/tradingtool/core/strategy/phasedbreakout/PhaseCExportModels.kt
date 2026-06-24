package com.tradingtool.core.strategy.phasedbreakout

data class PhaseCExportResponse(
    val metadataSchema: Map<String, String>,
    val stocks: List<PhaseCExportStockData>
)

data class PhaseCExportStockData(
    val profile: PhaseCWatchlistRow,
    val history10d: List<PhaseCHistoryRow>,
    val wakeUpVolume: PhaseCWakeUpVolumeExport?
)

data class PhaseCHistoryRow(
    val date: java.time.LocalDate,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val deliveryQuantity: Long?,
    val deliveryPct: Double?
)

data class PhaseCWakeUpVolumeExport(
    val latestDayVolume: Long,
    val previousDayVolume: Long,
    val volumeRatioVsPreviousDay: Double,
    val volumeIs2xOrMore: Boolean,
)
