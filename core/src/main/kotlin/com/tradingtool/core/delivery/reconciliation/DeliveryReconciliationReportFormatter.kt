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
        appendLine("- Fetched from source: `${fetchedFromSource}`")
        appendLine("- Already complete: `${alreadyComplete}`")
        appendLine("- Blocking issues: `${blockingIssues.size}`")
        appendLine("- Warnings: `${warningIssues.size}`")
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
        if (warningIssues.isNotEmpty()) {
            appendLine()
            appendLine("## Warnings")
            appendLine()
            warningIssues.forEach { issue ->
                appendLine("- $issue")
            }
        }
        if (blockingIssues.isNotEmpty()) {
            appendLine()
            appendLine("## Blocking Issues")
            appendLine()
            blockingIssues.forEach { issue ->
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
        append("source fetch: `$fetchStatus`")
        if (warningIssues.isNotEmpty()) {
            appendLine()
            append("warnings: `${warningIssues.size}`")
        }
        if (blockingIssues.isNotEmpty()) {
            appendLine()
            append("blocking issues: `${blockingIssues.size}`")
        }
    }
}

private fun StringBuilder.appendSampleTable(samples: List<DeliveryReconciliationSampleRow>) {
    if (samples.isEmpty()) {
        appendLine("_None_")
        return
    }

    appendLine("| Symbol | Token | Status | Delivery % |")
    appendLine("|---|---:|---|---:|")
    samples.forEach { row ->
        appendLine(
            "| `${row.symbol}` | `${row.instrumentToken}` | `${row.reconciliationStatus}` | `${row.delivPer ?: "-"}` |",
        )
    }
}
