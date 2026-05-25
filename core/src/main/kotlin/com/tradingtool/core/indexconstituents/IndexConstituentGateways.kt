package com.tradingtool.core.indexconstituents

import com.tradingtool.core.kite.InstrumentTokenResolution

interface IndexConstituentSource {
    suspend fun fetchRows(index: IndexDefinition): List<IndexConstituentCsvRow>
}

interface IndexConstituentTokenResolver {
    suspend fun resolveDetailed(exchange: String, symbol: String): InstrumentTokenResolution

    suspend fun resolve(exchange: String, symbol: String): Long? =
        resolveDetailed(exchange, symbol).resolvedToken
}

interface IndexConstituentGateway {
    suspend fun upsertBatch(rows: List<IndexConstituentRow>, syncedAt: java.time.OffsetDateTime): Int
    suspend fun deactivateMissing(indexKey: String, activeSymbols: Set<String>, syncedAt: java.time.OffsetDateTime): Int
}
