package com.tradingtool.core.fundamentals.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.http.JsonHttpClient
import org.slf4j.LoggerFactory

@Singleton
class NseIndexConstituentsService @Inject constructor(
    private val jsonHttpClient: JsonHttpClient,
) {
    private val log = LoggerFactory.getLogger(NseIndexConstituentsService::class.java)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class IndexConstituentsResponse(
        @JsonProperty("data")
        val data: List<IndexRow> = emptyList(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class IndexRow(
        @JsonProperty("symbol")
        val symbol: String? = null,
        @JsonProperty("series")
        val series: String? = null,
    )

    suspend fun fetchSymbols(indexName: String): List<String> {
        val encodedIndex = java.net.URLEncoder.encode(indexName, Charsets.UTF_8)
        val url = "$NSE_INDEX_CONSTITUENTS_URL?index=$encodedIndex"
        val response = jsonHttpClient.get<IndexConstituentsResponse>(url, defaultHeaders())

        val payload = response.getOrNull()
        if (payload == null) {
            log.warn("Failed to fetch NSE constituents for {}: {}", indexName, response.errorOrNull())
            return emptyList()
        }

        return payload.data.asSequence()
            .filter { row -> row.series.equals("EQ", ignoreCase = true) }
            .mapNotNull { row -> row.symbol?.trim()?.uppercase() }
            .filter { symbol -> symbol.isNotEmpty() && symbol != indexName.uppercase() }
            .distinct()
            .toList()
    }

    private fun defaultHeaders(): Map<String, String> {
        return mapOf(
            "User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
            "Accept" to "application/json",
            "Referer" to "https://www.nseindia.com/market-data/live-equity-market",
        )
    }

    companion object {
        private const val NSE_INDEX_CONSTITUENTS_URL: String = "https://www.nseindia.com/api/equity-stockIndices"
    }
}
