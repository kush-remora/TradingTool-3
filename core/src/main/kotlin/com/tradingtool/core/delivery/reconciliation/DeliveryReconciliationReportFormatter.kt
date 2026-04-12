package com.tradingtool.core.delivery.reconciliation

fun DeliveryReconciliationRunReport.toMarkdown(): String {
    return buildString {
        appendLine("# Delivery Reconciliation Report")
        appendLine()
        appendLine("- Requested date: `${requestedDate ?: "latest"}`")
        appendLine("- Resolved date: `${resolvedDate}`")
        appendLine("- Expected symbols: `${expectedSymbolCount}`")
        appendLine("- Present rows: `${presentCount}`")
        appendLine("- Missing from source: `${missingFromSourceCount}`")
        appendLine("- Nullable stock_id rows: `${nullableStockIdCount}`")
        appendLine("- Watchlist-linked rows: `${watchlistLinkedCount}`")
        appendLine("- Non-watchlist rows: `${nonWatchlistCount}`")
        appendLine("- Fetched from source: `${fetchedFromSource}`")
        appendLine("- Already complete: `${alreadyComplete}`")
        appendLine()
        appendLine("## Samples")
        appendLine()
        appendLine("### Present")
        appendLine()
        appendSampleTable(presentSamples)
        appendLine()
        appendLine("### Missing From Source")
        appendLine()
        appendSampleTable(missingFromSourceSamples)
        appendLine()
        appendLine("### Nullable stock_id")
        appendLine()
        appendSampleTable(nullableStockIdSamples)
        if (unresolvedIssues.isNotEmpty()) {
            appendLine()
            appendLine("## Unresolved Issues")
            appendLine()
            unresolvedIssues.forEach { issue ->
                appendLine("- $issue")
            }
        }
    }
}

fun DeliveryReconciliationRunReport.toTelegramSummary(): String {
    val fetchStatus = if (alreadyComplete) "no (already complete)" else if (fetchedFromSource) "yes" else "no"
    return buildString {
        appendLine("reconciled date: `${resolvedDate}`")
        appendLine("expected: `${expectedSymbolCount}`")
        appendLine("present: `${presentCount}`")
        appendLine("missing from source: `${missingFromSourceCount}`")
        appendLine("watchlist-linked rows: `${watchlistLinkedCount}`")
        appendLine("non-watchlist rows: `${nonWatchlistCount}`")
        append("source fetch: `$fetchStatus`")
        if (unresolvedIssues.isNotEmpty()) {
            appendLine()
            append("issues: `${unresolvedIssues.size}`")
        }
    }
}

private fun StringBuilder.appendSampleTable(samples: List<DeliveryReconciliationSampleRow>) {
    if (samples.isEmpty()) {
        appendLine("_None_")
        return
    }

    appendLine("| Symbol | Token | stock_id | Status | Delivery % |")
    appendLine("|---|---:|---:|---|---:|")
    samples.forEach { row ->
        appendLine(
            "| `${row.symbol}` | `${row.instrumentToken}` | `${row.stockId ?: "-"}` | `${row.reconciliationStatus}` | `${row.delivPer ?: "-"}` |",
        )
    }
}
