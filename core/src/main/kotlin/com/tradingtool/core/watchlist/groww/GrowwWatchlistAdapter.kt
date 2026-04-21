package com.tradingtool.core.watchlist.groww

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.tradingtool.core.http.JsonHttpClient
import org.slf4j.LoggerFactory

class GrowwWatchlistAdapter(
    private val jsonHttpClient: JsonHttpClient,
    private val objectMapper: ObjectMapper,
    private val extraHeaders: Map<String, String> = emptyMap(),
) : GrowwWatchlistSource {

    private val log = LoggerFactory.getLogger(GrowwWatchlistAdapter::class.java)

    override suspend fun fetchStocks(request: GrowwWatchlistSyncRequest): List<GrowwWatchlistStock> {
        val url = "$BASE_URL/${request.watchlistId}/details?include_index_fno=true"
        val response = jsonHttpClient.getRaw(url, defaultHeaders() + extraHeaders)
        val payload = response.getOrNull() ?: run {
            val message = "Groww watchlist fetch failed for ${request.watchlistId}: ${response.errorOrNull()}"
            log.error(message)
            throw IllegalStateException(message)
        }

        val root = runCatching { objectMapper.readTree(payload) }.getOrElse { error ->
            val message = "Groww watchlist response parse failed for ${request.watchlistId}: ${error.message}"
            log.error(message, error)
            throw IllegalStateException(message, error)
        }
        val rawRows = mutableListOf<GrowwWatchlistStock>()
        collectStocks(root, rawRows)

        return rawRows
            .distinctBy { row -> row.symbol to row.exchange }
    }

    private fun collectStocks(node: JsonNode, output: MutableList<GrowwWatchlistStock>) {
        when {
            node.isObject -> {
                parseStockNode(node)?.let { output += it }
                node.fields().forEach { (_, value) -> collectStocks(value, output) }
            }
            node.isArray -> {
                node.forEach { child -> collectStocks(child, output) }
            }
        }
    }

    private fun parseStockNode(node: JsonNode): GrowwWatchlistStock? {
        val symbol = firstText(node, listOf("nseSymbol", "tradingSymbol", "symbol"))
            ?.uppercase()
            ?.takeIf { value -> value.isNotBlank() }
            ?: return null

        val instrumentType = firstText(node, listOf("instrumentType", "securityType"))
        if (instrumentType != null && instrumentType.equals("STOCKS", ignoreCase = true).not()) {
            return null
        }

        val exchange = firstText(node, listOf("exchange"))?.uppercase() ?: "NSE"
        if (exchange != "NSE") {
            return null
        }

        val instrumentToken = firstLong(node, listOf("instrumentToken", "instrument_token", "token"))
            ?: return null

        val companyName = firstText(node, listOf("companyName", "companyShortName", "name", "displayName"))
            ?.trim()
            ?.takeIf { value -> value.isNotBlank() }
            ?: symbol

        return GrowwWatchlistStock(
            symbol = symbol,
            instrumentToken = instrumentToken,
            companyName = companyName,
            exchange = exchange,
        )
    }

    private fun firstText(node: JsonNode, keys: List<String>): String? {
        for (key in keys) {
            val value = findValue(node, key)
            if (value != null && value.isTextual) {
                val text = value.asText().trim()
                if (text.isNotBlank()) {
                    return text
                }
            }
        }
        return null
    }

    private fun firstLong(node: JsonNode, keys: List<String>): Long? {
        for (key in keys) {
            val value = findValue(node, key) ?: continue
            if (value.isNumber) {
                return value.asLong()
            }
            if (value.isTextual) {
                val parsed = value.asText().trim().toLongOrNull()
                if (parsed != null) {
                    return parsed
                }
            }
        }
        return null
    }

    private fun findValue(node: JsonNode, key: String): JsonNode? {
        if (!node.isObject) {
            return null
        }
        if (node.has(key)) {
            return node.get(key)
        }

        node.fields().forEach { (_, value) ->
            if (value.isObject) {
                val nested = findValue(value, key)
                if (nested != null) {
                    return nested
                }
            }
        }
        return null
    }

    private fun defaultHeaders(): Map<String, String> {
        return mapOf(
            "Accept" to "application/json, text/plain, */*",
            "Origin" to "https://groww.in",
            "Referer" to "https://groww.in/",
            "User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
        )
    }

    companion object {
        private const val BASE_URL = "https://groww.in/v1/api/presentation/v2/watchlist"
    }
}
