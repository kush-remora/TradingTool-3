package com.tradingtool.core.indexconstituents

import com.tradingtool.core.kite.InstrumentTokenResolution
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
        val preparedIndexes = config.indices
            .filter { index -> index.enabled }
            .map { index ->
                prepareIndex(index)
            }

        val unresolved = preparedIndexes
            .flatMap { prepared -> prepared.unresolvedResolutions.map { resolution -> prepared.index.key to resolution } }
        if (unresolved.isNotEmpty()) {
            throw IllegalStateException(buildUnresolvedTokenMessage(unresolved))
        }

        val reports = preparedIndexes.map { prepared ->
            persistIndex(prepared, batchSize = config.batchSize, syncedAt = syncedAt)
        }

        return IndexSyncRunReport(
            startedAt = startedAt,
            finishedAt = OffsetDateTime.now(),
            indexReports = reports,
        )
    }

    private suspend fun prepareIndex(
        index: IndexDefinition,
    ): PreparedIndexSync {
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

        val unresolvedResolutions = mutableListOf<InstrumentTokenResolution>()
        val rows = mutableListOf<IndexConstituentRow>()

        for (csvRow in normalizedRows) {
            val symbol = csvRow.symbol
            val resolution = tokenResolver.resolveDetailed(exchange = NSE_EXCHANGE, symbol = symbol)
            val token = resolution.resolvedToken
            if (token == null || token <= 0L) {
                unresolvedResolutions += resolution
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

        return PreparedIndexSync(
            index = index,
            sourceUrl = index.csvUrl,
            fetchedCount = rawRows.size,
            parsedCount = normalizedRows.size,
            rows = rows,
            unresolvedResolutions = unresolvedResolutions.sortedBy { resolution -> resolution.symbol },
        )
    }

    private suspend fun persistIndex(
        prepared: PreparedIndexSync,
        batchSize: Int,
        syncedAt: OffsetDateTime,
    ): IndexSyncSingleReport {
        var upsertedCount = 0
        val activeSymbols = prepared.rows.map { row -> row.symbol }.toSet()
        prepared.rows.chunked(batchSize).forEach { batch ->
            if (batch.isNotEmpty()) {
                upsertedCount += gateway.upsertBatch(batch, syncedAt)
            }
        }

        val deactivatedCount = gateway.deactivateMissing(
            indexKey = prepared.index.key,
            activeSymbols = activeSymbols,
            syncedAt = syncedAt,
        )

        return IndexSyncSingleReport(
            indexKey = prepared.index.key,
            sourceUrl = prepared.sourceUrl,
            fetchedCount = prepared.fetchedCount,
            parsedCount = prepared.parsedCount,
            upsertedCount = upsertedCount,
            deactivatedCount = deactivatedCount,
            unresolvedSymbols = emptyList(),
        )
    }

    private fun buildUnresolvedTokenMessage(
        unresolved: List<Pair<String, InstrumentTokenResolution>>,
    ): String {
        val details = unresolved
            .take(MAX_UNRESOLVED_SYMBOLS_IN_MESSAGE)
            .joinToString(separator = "; ") { (indexKey, resolution) ->
                val expected = resolution.expectedKeys.joinToString(" | ")
                val candidates = if (resolution.candidateKeys.isEmpty()) "none" else resolution.candidateKeys.joinToString(" | ")
                "${indexKey}:${resolution.symbol} (expected=$expected, candidates=$candidates)"
            }
        return "IndexConstituentSyncJob unresolved instrument tokens: count=${unresolved.size}; $details"
    }

    private data class PreparedIndexSync(
        val index: IndexDefinition,
        val sourceUrl: String,
        val fetchedCount: Int,
        val parsedCount: Int,
        val rows: List<IndexConstituentRow>,
        val unresolvedResolutions: List<InstrumentTokenResolution>,
    )

    private companion object {
        const val NSE_EXCHANGE: String = "NSE"
        const val MAX_UNRESOLVED_SYMBOLS_IN_MESSAGE: Int = 30
    }
}
