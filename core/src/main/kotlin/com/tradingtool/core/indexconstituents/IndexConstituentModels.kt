package com.tradingtool.core.indexconstituents

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.OffsetDateTime

object IndexConstituentKeys {
    const val GROWW_WATCHLIST: String = "groww_HIGH_QUALITY"
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class IndexSyncConfig(
    @JsonProperty("batchSize")
    val batchSize: Int,
    @JsonProperty("indices")
    val indices: List<IndexDefinition>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class IndexDefinition(
    @JsonProperty("key")
    val key: String,
    @JsonProperty("enabled")
    val enabled: Boolean,
    @JsonProperty("csvUrl")
    val csvUrl: String,
)

data class IndexSyncRunReport(
    val startedAt: OffsetDateTime,
    val finishedAt: OffsetDateTime,
    val indexReports: List<IndexSyncSingleReport>,
)

data class IndexSyncSingleReport(
    val indexKey: String,
    val sourceUrl: String,
    val fetchedCount: Int,
    val parsedCount: Int,
    val upsertedCount: Int,
    val deactivatedCount: Int,
    val unresolvedSymbols: List<String>,
)

data class IndexConstituentRow(
    val indexKey: String,
    val symbol: String,
    val instrumentToken: Long,
    val companyName: String,
    val industry: String,
    val series: String,
    val isinCode: String,
    val sourceUrl: String,
)

data class IndexConstituentCsvRow(
    val symbol: String,
    val companyName: String,
    val industry: String,
    val series: String,
    val isinCode: String,
)
