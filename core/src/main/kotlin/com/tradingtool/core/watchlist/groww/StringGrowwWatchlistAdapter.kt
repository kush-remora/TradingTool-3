package com.tradingtool.core.watchlist.groww

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory

/**
 * Parses a Groww watchlist JSON payload provided as a raw string.
 *
 * This is the Console/UI counterpart of [FileGrowwWatchlistAdapter] and reuses the same
 * defensive extraction rules (NSE-only, STOCKS-only, best-effort token extraction).
 */
class StringGrowwWatchlistAdapter(
    private val json: String,
    private val objectMapper: ObjectMapper,
) : GrowwWatchlistSource {
    private val log = LoggerFactory.getLogger(StringGrowwWatchlistAdapter::class.java)

    override suspend fun fetchStocks(request: GrowwWatchlistSyncRequest): List<GrowwWatchlistStock> {
        val root = parsePayloadTree()
        val rawRows = mutableListOf<GrowwWatchlistStock>()
        collectStocks(root, rawRows)
        return rawRows.distinctBy { row -> row.symbol to row.exchange }
    }

    private fun parsePayloadTree(): JsonNode {
        if (json.isBlank()) {
            throw IllegalStateException("Empty Groww watchlist JSON payload.")
        }
        return runCatching { objectMapper.readTree(json) }.getOrElse { error ->
            log.error("Failed to parse uploaded Groww watchlist JSON: {}", error.message, error)
            throw IllegalStateException("Invalid Groww watchlist JSON.", error)
        }
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
        val symbol = firstText(node, listOf("nseSymbol", "nseScripCode", "tradingSymbol", "symbol"))
            ?.uppercase()
            ?.takeIf { value -> value.isNotBlank() }
            ?: return null

        val instrumentType = firstText(node, listOf("instrumentType", "securityType", "equityType"))
        if (instrumentType != null && instrumentType.equals("STOCKS", ignoreCase = true).not()) {
            return null
        }

        val exchange = firstText(node, listOf("exchange"))?.uppercase() ?: "NSE"
        if (exchange != "NSE") {
            return null
        }

        val instrumentToken = firstLong(node, listOf("instrumentToken", "instrument_token", "token")) ?: 0L

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
}
