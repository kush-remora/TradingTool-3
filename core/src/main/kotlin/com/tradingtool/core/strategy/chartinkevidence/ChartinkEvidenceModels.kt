package com.tradingtool.core.strategy.chartinkevidence

import java.time.LocalDate

enum class ChartinkEvidenceSource {
    ACCUMULATION,
    PHASE_D,
    T2_HIGH,
    FRESH_BREAKOUT,
}

enum class ChartinkEvidenceSlot(
    val source: ChartinkEvidenceSource,
    val universeKey: String?,
) {
    ACCUMULATION_NIFTY_100(ChartinkEvidenceSource.ACCUMULATION, "nifty_100"),
    ACCUMULATION_NIFTY_MIDCAP_150(ChartinkEvidenceSource.ACCUMULATION, "nifty_midcap_150"),
    ACCUMULATION_NIFTY_SMALLCAP_250(ChartinkEvidenceSource.ACCUMULATION, "nifty_smallcap_250"),
    ACCUMULATION_NIFTY_MICROCAP_250(ChartinkEvidenceSource.ACCUMULATION, "nifty_microcap_250"),
    PHASE_D(ChartinkEvidenceSource.PHASE_D, null),
    T2_HIGH(ChartinkEvidenceSource.T2_HIGH, null),
    FRESH_BREAKOUT(ChartinkEvidenceSource.FRESH_BREAKOUT, null),
    ;

    companion object {
        fun fromValue(value: String): ChartinkEvidenceSlot = entries.firstOrNull {
            slot -> slot.name.equals(value.trim(), ignoreCase = true)
        } ?: throw IllegalArgumentException("Unknown Chartink upload slot: $value")
    }
}

data class ChartinkEvidenceUploadRequest(
    val slot: String,
    val csvContent: String,
    val fileName: String,
)

data class ChartinkScanEvent(
    val source: ChartinkEvidenceSource,
    val universeKey: String,
    val eventDate: LocalDate,
    val symbol: String,
    val marketcapName: String,
    val sector: String,
    val sourceFileName: String,
)

data class ChartinkEvidenceUploadResult(
    val source: ChartinkEvidenceSource,
    val storedCount: Int,
    val skippedOutsideUniverseCount: Int,
    val skippedSymbols: List<ChartinkEvidenceSkippedSymbol>,
    val duplicateCount: Int,
)

data class ChartinkEvidenceSkippedSymbol(
    val symbol: String,
    val reason: ChartinkEvidenceSkipReason,
    val resolvedUniverseKey: String?,
)

enum class ChartinkEvidenceSkipReason {
    NO_ACTIVE_BASE_UNIVERSE_MEMBERSHIP,
    ACTIVE_IN_DIFFERENT_UNIVERSE,
}

data class ChartinkEvidenceDashboardResponse(
    val months: Int,
    val fromDate: LocalDate,
    val uploadStatuses: List<ChartinkEvidenceUploadStatus>,
    val rows: List<ChartinkEvidenceDashboardRow>,
)

data class ChartinkEvidenceUploadStatus(
    val slot: String,
    val sourceFileName: String,
    val uploadedAt: String,
)

data class StoredChartinkEvidenceUpload(
    val source: ChartinkEvidenceSource,
    val universeKey: String,
    val sourceFileName: String,
    val uploadedAt: String,
)

data class ChartinkEvidenceDashboardRow(
    val symbol: String,
    val universeKey: String,
    val curatedWatchlists: List<String>,
    val accumulationLatestDate: LocalDate?,
    val phaseDLatestDate: LocalDate?,
    val t2HighLatestDate: LocalDate?,
    val freshBreakoutLatestDate: LocalDate?,
)

data class IndexMembership(
    val symbol: String,
    val indexKey: String,
)

interface ChartinkEvidenceStore {
    suspend fun replaceAccumulation(universeKey: String, events: List<ChartinkScanEvent>)
    suspend fun replaceCashSource(source: ChartinkEvidenceSource, events: List<ChartinkScanEvent>)
    suspend fun findFromDate(fromDate: LocalDate): List<ChartinkScanEvent>
    suspend fun findLatestUploads(): List<StoredChartinkEvidenceUpload>
}

interface ChartinkUniverseMembershipStore {
    suspend fun findActiveMemberships(symbols: List<String>): List<IndexMembership>
}
