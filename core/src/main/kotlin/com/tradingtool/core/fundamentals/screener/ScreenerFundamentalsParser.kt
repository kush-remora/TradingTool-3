package com.tradingtool.core.fundamentals.screener

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

object ScreenerFundamentalsParser {
    fun parse(symbol: String, payloadJson: String): ScreenerFundamentalsSnapshot {
        val payload = objectMapper.readValue(payloadJson, NseFundamentalsPayload::class.java)

        val quoteRoot = objectMapper.readTree(payload.quoteJson)
        val filingsRoot = objectMapper.readTree(payload.financialResultsJson)
        val shareholdingRoot = objectMapper.readTree(payload.shareholdingJson)

        val companyName = quoteRoot.path("info").path("companyName").asCleanText()
            ?: filingsRoot.firstOrNullNode()?.path("companyName")?.asCleanText()
            ?: error("Company name not found in NSE payload for $symbol")

        val industry = quoteRoot.path("metadata").path("industry").asCleanText()
            ?: quoteRoot.path("industryInfo").path("industry").asCleanText()
            ?: filingsRoot.firstOrNullNode()?.path("industry")?.asCleanText()

        val broadIndustry = quoteRoot.path("industryInfo").path("macro").asCleanText()
            ?: quoteRoot.path("industryInfo").path("sector").asCleanText()

        val stockPe = quoteRoot.path("metadata").path("pdSymbolPe").asDoubleOrNull()

        val issuedSize = quoteRoot.path("securityInfo").path("issuedSize").asDoubleOrNull()
        val lastPrice = quoteRoot.path("priceInfo").path("lastPrice").asDoubleOrNull()
        val marketCapCr = if (issuedSize != null && lastPrice != null) {
            (issuedSize * lastPrice) / RUPEES_PER_CRORE
        } else {
            null
        }

        val promoterHoldingPercent = shareholdingRoot.firstOrNullNode()
            ?.path("pr_and_prgrp")
            ?.asDoubleOrNull()

        return ScreenerFundamentalsSnapshot(
            symbol = symbol,
            companyName = companyName,
            marketCapCr = marketCapCr,
            stockPe = stockPe,
            rocePercent = null,
            roePercent = null,
            promoterHoldingPercent = promoterHoldingPercent,
            broadIndustry = broadIndustry,
            industry = industry,
            debtToEquity = null,
            pledgedPercent = null,
        )
    }

    private fun JsonNode.asCleanText(): String? {
        if (!isTextual) {
            return null
        }
        val value = asText().trim()
        return if (value.isEmpty() || value == "-") null else value
    }

    private fun JsonNode.asDoubleOrNull(): Double? {
        return when {
            isNumber -> asDouble()
            isTextual -> parseNumber(asText())
            else -> null
        }
    }

    private fun parseNumber(raw: String): Double? {
        val normalized = raw
            .replace(",", "")
            .trim()
        if (normalized.isEmpty() || normalized == "-") {
            return null
        }
        return normalized.toDoubleOrNull()
    }

    private fun JsonNode.firstOrNullNode(): JsonNode? {
        return if (isArray && size() > 0) get(0) else null
    }

    private val objectMapper: ObjectMapper = ObjectMapper().registerKotlinModule()
    private const val RUPEES_PER_CRORE: Double = 10_000_000.0
}
