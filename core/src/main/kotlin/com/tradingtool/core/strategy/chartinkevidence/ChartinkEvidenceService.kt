package com.tradingtool.core.strategy.chartinkevidence

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.StringReader
import java.time.LocalDate

class ChartinkEvidenceService(
    private val evidenceStore: ChartinkEvidenceStore,
    private val membershipStore: ChartinkUniverseMembershipStore,
) {
    suspend fun upload(request: ChartinkEvidenceUploadRequest): ChartinkEvidenceUploadResult {
        require(request.fileName.isNotBlank()) { "fileName is required." }
        val slot = ChartinkEvidenceSlot.fromValue(request.slot)
        val parsedRows = parseRows(request.csvContent)
        val uniqueRows = parsedRows.distinctBy { row -> row.eventDate to row.symbol }
        val memberships = membershipStore.findActiveMemberships(uniqueRows.map { row -> row.symbol })
            .groupBy(IndexMembership::symbol)
        val eligibleRows = uniqueRows.mapNotNull { row ->
            val universeKey = memberships[row.symbol]
                .orEmpty()
                .map(IndexMembership::indexKey)
                .firstOrNull(BASE_UNIVERSE_KEYS::contains)
                ?: return@mapNotNull null
            if (slot.universeKey != null && slot.universeKey != universeKey) {
                return@mapNotNull null
            }
            ChartinkScanEvent(
                source = slot.source,
                universeKey = universeKey,
                eventDate = row.eventDate,
                symbol = row.symbol,
                marketcapName = row.marketcapName,
                sector = row.sector,
                sourceFileName = request.fileName.trim(),
            )
        }

        if (slot.universeKey != null) {
            evidenceStore.replaceAccumulation(slot.universeKey, eligibleRows)
        } else {
            evidenceStore.replaceCashSource(slot.source, eligibleRows)
        }

        return ChartinkEvidenceUploadResult(
            source = slot.source,
            storedCount = eligibleRows.size,
            skippedOutsideUniverseCount = uniqueRows.size - eligibleRows.size,
            duplicateCount = parsedRows.size - uniqueRows.size,
        )
    }

    suspend fun getDashboard(months: Int, asOfDate: LocalDate = LocalDate.now()): ChartinkEvidenceDashboardResponse {
        require(months in DASHBOARD_MONTHS) { "months must be one of ${DASHBOARD_MONTHS.joinToString()}." }
        val fromDate = asOfDate.minusMonths(months.toLong())
        val events = evidenceStore.findFromDate(fromDate)
        val memberships = membershipStore.findActiveMemberships(events.map(ChartinkScanEvent::symbol).distinct())
            .groupBy(IndexMembership::symbol)
        val rows = events.groupBy(ChartinkScanEvent::symbol).map { (symbol, symbolEvents) ->
            val latestBySource = symbolEvents.groupBy(ChartinkScanEvent::source)
                .mapValues { (_, sourceEvents) -> sourceEvents.maxOf(ChartinkScanEvent::eventDate) }
            val universeKey = symbolEvents.first().universeKey
            val curatedWatchlists = memberships[symbol]
                .orEmpty()
                .map(IndexMembership::indexKey)
                .filterNot(BASE_UNIVERSE_KEYS::contains)
                .sorted()
            ChartinkEvidenceDashboardRow(
                symbol = symbol,
                universeKey = universeKey,
                curatedWatchlists = curatedWatchlists,
                accumulationLatestDate = latestBySource[ChartinkEvidenceSource.ACCUMULATION],
                phaseDLatestDate = latestBySource[ChartinkEvidenceSource.PHASE_D],
                t2HighLatestDate = latestBySource[ChartinkEvidenceSource.T2_HIGH],
                freshBreakoutLatestDate = latestBySource[ChartinkEvidenceSource.FRESH_BREAKOUT],
            )
        }.sortedWith(
            compareByDescending<ChartinkEvidenceDashboardRow> { row -> row.curatedWatchlists.isNotEmpty() }
                .thenByDescending { row -> listOfNotNull(row.phaseDLatestDate, row.freshBreakoutLatestDate, row.t2HighLatestDate, row.accumulationLatestDate).maxOrNull() }
                .thenBy(ChartinkEvidenceDashboardRow::symbol),
        )
        return ChartinkEvidenceDashboardResponse(months = months, fromDate = fromDate, rows = rows)
    }

    private fun parseRows(csvContent: String): List<ParsedChartinkRow> {
        require(csvContent.isNotBlank()) { "CSV content is required." }
        val csvWithoutByteOrderMark = csvContent.removePrefix("\uFEFF")
        CSVParser(StringReader(csvWithoutByteOrderMark), CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build()).use { parser ->
            require(parser.headerMap.keys == REQUIRED_HEADERS) {
                "CSV must contain exactly these headers: ${REQUIRED_HEADERS.joinToString()}."
            }
            return parser.records.mapIndexed { index, record ->
                val rowNumber = index + 2
                val symbol = record.get("Symbol").trim().uppercase()
                require(symbol.isNotBlank()) { "CSV row $rowNumber has an empty Symbol." }
                ParsedChartinkRow(
                    eventDate = runCatching { LocalDate.parse(record.get("Date").trim()) }
                        .getOrElse { throw IllegalArgumentException("CSV row $rowNumber has an invalid Date.") },
                    symbol = symbol,
                    marketcapName = record.get("Marketcapname").trim(),
                    sector = record.get("Sector").trim(),
                )
            }
        }
    }

    private data class ParsedChartinkRow(
        val eventDate: LocalDate,
        val symbol: String,
        val marketcapName: String,
        val sector: String,
    )

    private companion object {
        val BASE_UNIVERSE_KEYS = setOf(
            "nifty_100",
            "nifty_midcap_150",
            "nifty_smallcap_250",
            "nifty_microcap_250",
        )
        val DASHBOARD_MONTHS = setOf(1, 2, 3, 9)
        val REQUIRED_HEADERS = linkedSetOf("Date", "Symbol", "Marketcapname", "Sector")
    }
}
