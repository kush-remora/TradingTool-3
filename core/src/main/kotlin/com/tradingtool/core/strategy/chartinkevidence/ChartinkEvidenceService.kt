package com.tradingtool.core.strategy.chartinkevidence

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.StringReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

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
        val resolvedRows = uniqueRows.map { row ->
            val universeKey = memberships[row.symbol]
                .orEmpty()
                .map(IndexMembership::indexKey)
                .firstOrNull(BASE_UNIVERSE_KEYS::contains)
            ResolvedChartinkRow(row, universeKey)
        }
        val eligibleRows = resolvedRows.mapNotNull { resolvedRow ->
            val universeKey = resolvedRow.universeKey ?: return@mapNotNull null
            if (slot.universeKey != null && slot.universeKey != universeKey) return@mapNotNull null
            resolvedRow.toScanEvent(slot.source, request.fileName.trim())
        }
        val skippedSymbols = resolvedRows.mapNotNull { resolvedRow ->
            val reason = when {
                resolvedRow.universeKey == null -> ChartinkEvidenceSkipReason.NO_ACTIVE_BASE_UNIVERSE_MEMBERSHIP
                slot.universeKey != null && slot.universeKey != resolvedRow.universeKey -> ChartinkEvidenceSkipReason.ACTIVE_IN_DIFFERENT_UNIVERSE
                else -> null
            } ?: return@mapNotNull null
            ChartinkEvidenceSkippedSymbol(
                symbol = resolvedRow.row.symbol,
                reason = reason,
                resolvedUniverseKey = resolvedRow.universeKey,
            )
        }.distinctBy(ChartinkEvidenceSkippedSymbol::symbol).sortedBy(ChartinkEvidenceSkippedSymbol::symbol)

        if (slot.universeKey != null) {
            evidenceStore.replaceAccumulation(slot.universeKey, eligibleRows)
        } else {
            evidenceStore.replaceCashSource(slot.source, eligibleRows)
        }

        return ChartinkEvidenceUploadResult(
            source = slot.source,
            storedCount = eligibleRows.size,
            skippedOutsideUniverseCount = uniqueRows.size - eligibleRows.size,
            skippedSymbols = skippedSymbols,
            duplicateCount = parsedRows.size - uniqueRows.size,
        )
    }

    suspend fun getDashboard(months: Int, asOfDate: LocalDate = LocalDate.now()): ChartinkEvidenceDashboardResponse {
        require(months in DASHBOARD_MONTHS) { "months must be one of ${DASHBOARD_MONTHS.joinToString()}." }
        val fromDate = asOfDate.minusMonths(months.toLong())
        val events = evidenceStore.findFromDate(fromDate)
        val uploadStatuses = evidenceStore.findLatestUploads().mapNotNull { upload ->
            val slot = ChartinkEvidenceSlot.entries.firstOrNull { candidate ->
                candidate.source == upload.source && candidate.universeKey == if (upload.source == ChartinkEvidenceSource.ACCUMULATION) upload.universeKey else null
            } ?: return@mapNotNull null
            ChartinkEvidenceUploadStatus(
                slot = slot.name,
                sourceFileName = upload.sourceFileName,
                uploadedAt = upload.uploadedAt,
            )
        }
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
        return ChartinkEvidenceDashboardResponse(
            months = months,
            fromDate = fromDate,
            uploadStatuses = uploadStatuses,
            rows = rows,
        )
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
                    eventDate = parseEventDate(record.get("Date"), rowNumber),
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

    private data class ResolvedChartinkRow(
        val row: ParsedChartinkRow,
        val universeKey: String?,
    ) {
        fun toScanEvent(source: ChartinkEvidenceSource, fileName: String): ChartinkScanEvent = ChartinkScanEvent(
            source = source,
            universeKey = requireNotNull(universeKey),
            eventDate = row.eventDate,
            symbol = row.symbol,
            marketcapName = row.marketcapName,
            sector = row.sector,
            sourceFileName = fileName,
        )
    }

    private fun parseEventDate(value: String, rowNumber: Int): LocalDate {
        val trimmedValue = value.trim()
        for (formatter in EVENT_DATE_FORMATTERS) {
            try {
                return LocalDate.parse(trimmedValue, formatter)
            } catch (_: DateTimeParseException) {
                // Try the next supported Chartink date format.
            }
        }
        throw IllegalArgumentException("CSV row $rowNumber has an invalid Date.")
    }

    private companion object {
        val BASE_UNIVERSE_KEYS = setOf(
            "nifty_100",
            "nifty_midcap_150",
            "nifty_smallcap_250",
            "nifty_microcap_250",
        )
        val DASHBOARD_MONTHS = setOf(1, 2, 3, 9)
        val REQUIRED_HEADERS = linkedSetOf("Date", "Symbol", "Marketcapname", "Sector")
        val EVENT_DATE_FORMATTERS = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd-MM-uuuu"),
        )
    }
}
