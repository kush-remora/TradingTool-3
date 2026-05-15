package com.tradingtool.core.indexconstituents

interface IndexConstituentSource {
    suspend fun fetchRows(index: IndexDefinition): List<IndexConstituentCsvRow>
}

interface IndexConstituentTokenResolver {
    suspend fun resolve(exchange: String, symbol: String): Long?
}

interface IndexConstituentGateway {
    suspend fun upsertBatch(rows: List<IndexConstituentRow>, syncedAt: java.time.OffsetDateTime): Int
    suspend fun deactivateMissing(indexKey: String, activeSymbols: Set<String>, syncedAt: java.time.OffsetDateTime): Int
}
