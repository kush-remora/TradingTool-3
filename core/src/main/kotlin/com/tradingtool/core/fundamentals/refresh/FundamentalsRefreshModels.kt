package com.tradingtool.core.fundamentals.refresh

import com.tradingtool.core.delivery.model.DeliveryUniverse
import com.tradingtool.core.fundamentals.model.StockFundamentalsDaily
import java.time.LocalDate

data class FundamentalsRefreshFailure(
    val symbol: String,
    val reason: String,
)

data class FundamentalsRefreshResult(
    val snapshotDate: LocalDate,
    val requestedSymbolsOverride: List<String>?,
    val expectedSymbolCount: Int,
    val successfulCount: Int,
    val resolvedInstrumentTokens: List<Long>,
    val failures: List<FundamentalsRefreshFailure>,
)

data class FundamentalsRefreshRunReport(
    val requestedSymbolsOverride: List<String>?,
    val snapshotDate: LocalDate,
    val expectedSymbolCount: Int,
    val storedRowCount: Int,
    val successfulCount: Int,
    val failedCount: Int,
    val nullableStockIdCount: Int,
    val watchlistLinkedCount: Int,
    val nonWatchlistCount: Int,
    val blockingIssues: List<String>,
    val warningIssues: List<String>,
    val storedSamples: List<FundamentalsRefreshSampleRow>,
    val failureSamples: List<FundamentalsRefreshFailure>,
)

data class FundamentalsRefreshSampleRow(
    val instrumentToken: Long,
    val symbol: String,
    val stockId: Long?,
    val universe: DeliveryUniverse,
    val marketCapCr: Double?,
    val stockPe: Double?,
    val promoterHoldingPercent: Double?,
)

object FundamentalsRefreshRunReportFactory {
    fun create(
        result: FundamentalsRefreshResult,
        rows: List<StockFundamentalsDaily>,
    ): FundamentalsRefreshRunReport {
        val nullableStockRows = rows.filter { row -> row.stockId == null }
        val toleratedAvailabilityGap = isToleratedAvailabilityGap(
            expectedCount = result.expectedSymbolCount,
            failedCount = result.failures.size,
        )
        val warningIssues = mutableListOf<String>()
        if (toleratedAvailabilityGap && result.failures.isNotEmpty()) {
            warningIssues +=
                "Ignoring ${result.failures.size} failed symbol(s) because availability gap is under 1% of the requested universe: ${result.failures.joinToString(", ") { failure -> failure.symbol }}."
        }

        val blockingIssues = buildList {
            result.failures.forEach { failure ->
                if (!toleratedAvailabilityGap) {
                    add("${failure.symbol}: ${failure.reason}")
                }
            }
        }

        return FundamentalsRefreshRunReport(
            requestedSymbolsOverride = result.requestedSymbolsOverride,
            snapshotDate = result.snapshotDate,
            expectedSymbolCount = result.expectedSymbolCount,
            storedRowCount = rows.size,
            successfulCount = result.successfulCount,
            failedCount = result.failures.size,
            nullableStockIdCount = nullableStockRows.size,
            watchlistLinkedCount = rows.size - nullableStockRows.size,
            nonWatchlistCount = nullableStockRows.size,
            blockingIssues = blockingIssues,
            warningIssues = warningIssues.toList(),
            storedSamples = rows.take(5).map(::toSampleRow),
            failureSamples = result.failures.take(10),
        )
    }

    private fun isToleratedAvailabilityGap(
        expectedCount: Int,
        failedCount: Int,
    ): Boolean {
        if (expectedCount <= 0 || failedCount <= 0) {
            return false
        }
        return failedCount.toDouble() / expectedCount.toDouble() < MAX_TOLERATED_UNAVAILABLE_RATIO
    }

    private fun toSampleRow(row: StockFundamentalsDaily): FundamentalsRefreshSampleRow {
        return FundamentalsRefreshSampleRow(
            instrumentToken = row.instrumentToken,
            symbol = row.symbol,
            stockId = row.stockId,
            universe = row.universe,
            marketCapCr = row.marketCapCr,
            stockPe = row.stockPe,
            promoterHoldingPercent = row.promoterHoldingPercent,
        )
    }

    private const val MAX_TOLERATED_UNAVAILABLE_RATIO: Double = 0.01
}

fun FundamentalsRefreshRunReport.toMarkdown(): String {
    return buildString {
        appendLine("# Fundamentals Refresh Report")
        appendLine()
        appendLine("- Snapshot date: `${snapshotDate}`")
        appendLine("- Requested symbol count: `${expectedSymbolCount}`")
        appendLine("- Stored row count: `${storedRowCount}`")
        appendLine("- Successful count: `${successfulCount}`")
        appendLine("- Failed count: `${failedCount}`")
        appendLine("- Nullable stock_id rows: `${nullableStockIdCount}`")
        appendLine("- Watchlist-linked rows: `${watchlistLinkedCount}`")
        appendLine("- Non-watchlist rows: `${nonWatchlistCount}`")
        appendLine("- Requested symbols override: `${requestedSymbolsOverride?.joinToString(",") ?: "none"}`")
        appendLine()
        appendLine("## Blocking Issues")
        if (blockingIssues.isEmpty()) {
            appendLine("- None")
        } else {
            blockingIssues.forEach { issue -> appendLine("- $issue") }
        }
        appendLine()
        appendLine("## Warning Issues")
        if (warningIssues.isEmpty()) {
            appendLine("- None")
        } else {
            warningIssues.forEach { issue -> appendLine("- $issue") }
        }
        appendLine()
        appendLine("## Stored Samples")
        appendLine("| Symbol | Instrument Token | Universe | Stock ID | Market Cap (Cr) | Stock P/E | Promoter Holding % |")
        appendLine("|---|---:|---|---:|---:|---:|---:|")
        storedSamples.forEach { row ->
            appendLine(
                "| `${row.symbol}` | `${row.instrumentToken}` | `${row.universe.storageValue}` | " +
                    "${row.stockId ?: "-"} | ${row.marketCapCr ?: "-"} | ${row.stockPe ?: "-"} | ${row.promoterHoldingPercent ?: "-"} |",
            )
        }
        appendLine()
        appendLine("## Failure Samples")
        if (failureSamples.isEmpty()) {
            appendLine("- None")
        } else {
            failureSamples.forEach { failure ->
                appendLine("- `${failure.symbol}`: ${failure.reason}")
            }
        }
    }
}

fun FundamentalsRefreshRunReport.toTelegramSummary(): String {
    return buildString {
        append("snapshot date: `$snapshotDate`")
        append("\nexpected: `$expectedSymbolCount`")
        append("\nstored: `$storedRowCount`")
        append("\nsuccessful: `$successfulCount`")
        append("\nfailed: `$failedCount`")
        append("\nwatchlist-linked rows: `$watchlistLinkedCount`")
        append("\nnon-watchlist rows: `$nonWatchlistCount`")
        if (warningIssues.isNotEmpty()) {
            append("\nwarnings: `${warningIssues.size}`")
        }
    }
}
