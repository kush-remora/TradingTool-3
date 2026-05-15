package com.tradingtool.core.indexconstituents

import java.time.OffsetDateTime

class IndexConstituentSyncService(
    private val source: IndexConstituentSource,
    private val tokenResolver: IndexConstituentTokenResolver,
    private val gateway: IndexConstituentGateway,
) {
    suspend fun sync(config: IndexSyncConfig): IndexSyncRunReport {
        require(config.batchSize > 0) { "batchSize must be > 0" }

        val startedAt = OffsetDateTime.now()
        val syncedAt = OffsetDateTime.now()
        val reports = config.indices
            .filter { index -> index.enabled }
            .map { index ->
                syncIndex(index = index, batchSize = config.batchSize, syncedAt = syncedAt)
            }

        return IndexSyncRunReport(
            startedAt = startedAt,
            finishedAt = OffsetDateTime.now(),
            indexReports = reports,
        )
    }

    private suspend fun syncIndex(
        index: IndexDefinition,
        batchSize: Int,
        syncedAt: OffsetDateTime,
    ): IndexSyncSingleReport {
        val rawRows = source.fetchRows(index)
        val normalizedRows = rawRows
            .asSequence()
            .map { row ->
                row.copy(
                    symbol = row.symbol.trim().uppercase(),
                    companyName = row.companyName.trim(),
                    industry = row.industry.trim(),
                    series = row.series.trim(),
                    isinCode = row.isinCode.trim(),
                )
            }
            .filter { row -> row.symbol.isNotEmpty() }
            .distinctBy { row -> row.symbol }
            .toList()

        val unresolved = mutableListOf<String>()
        val rows = mutableListOf<IndexConstituentRow>()

        for (csvRow in normalizedRows) {
            val symbol = csvRow.symbol
            val token = tokenResolver.resolve(exchange = NSE_EXCHANGE, symbol = symbol)
            if (token == null || token <= 0L) {
                unresolved += symbol
                continue
            }

            rows += IndexConstituentRow(
                indexKey = index.key,
                symbol = symbol,
                instrumentToken = token,
                companyName = csvRow.companyName,
                industry = csvRow.industry,
                series = csvRow.series,
                isinCode = csvRow.isinCode,
                sourceUrl = index.csvUrl,
            )
        }

        var upsertedCount = 0
        val activeSymbols = rows.map { row -> row.symbol }.toSet()
        rows.chunked(batchSize).forEach { batch ->
            if (batch.isNotEmpty()) {
                upsertedCount += gateway.upsertBatch(batch, syncedAt)
            }
        }

        val deactivatedCount = gateway.deactivateMissing(
            indexKey = index.key,
            activeSymbols = activeSymbols,
            syncedAt = syncedAt,
        )

        return IndexSyncSingleReport(
            indexKey = index.key,
            sourceUrl = index.csvUrl,
            fetchedCount = rawRows.size,
            parsedCount = normalizedRows.size,
            upsertedCount = upsertedCount,
            deactivatedCount = deactivatedCount,
            unresolvedSymbols = unresolved.sorted(),
        )
    }

    private companion object {
        const val NSE_EXCHANGE: String = "NSE"
    }
}
