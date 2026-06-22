package com.tradingtool.core.volumeshocker.groww

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path

class FileGrowwVolumeShockerSource(
    private val filePath: Path,
    private val objectMapper: ObjectMapper,
) : GrowwVolumeShockerSource {

    override suspend fun fetchRows(): List<GrowwVolumeShockerSourceRow> {
        val root = readPayload()
        val stocks = root.path("data").path("stocks")
        require(stocks.isArray) {
            "Groww volume-shocker input must contain a data.stocks array."
        }

        return stocks.mapIndexed { index, stock ->
            parseStock(stock, sourceRank = index + 1)
        }
    }

    private fun readPayload(): JsonNode {
        require(Files.exists(filePath)) {
            "Groww volume-shocker input file not found: $filePath"
        }

        return runCatching {
            objectMapper.readTree(Files.readString(filePath))
        }.getOrElse { error ->
            throw IllegalArgumentException("Invalid Groww volume-shocker JSON in $filePath", error)
        }
    }

    private fun parseStock(stock: JsonNode, sourceRank: Int): GrowwVolumeShockerSourceRow {
        requiredText(stock, "gsin", sourceRank)
        requiredText(stock, "isin", sourceRank)
        requiredText(stock, "searchId", sourceRank)

        val nseSymbol = optionalText(stock, "nseScriptCode")
        val bseSymbol = optionalText(stock, "bseScriptCode")
        require(nseSymbol != null || bseSymbol != null) {
            "Groww volume-shocker row $sourceRank has no NSE or BSE symbol."
        }

        val instrumentType = requiredText(stock, "type", sourceRank)
        require(instrumentType.equals("STOCKS", ignoreCase = true)) {
            "Groww volume-shocker row $sourceRank is not a stock."
        }

        val exchange = if (nseSymbol != null) "NSE" else "BSE"
        val symbol = (nseSymbol ?: bseSymbol.orEmpty()).uppercase()
        val ltp = requiredPositiveDecimal(stock, "ltp", sourceRank)
        val close = requiredPositiveDecimal(stock, "close", sourceRank)
        val marketCapCrore = requiredPositiveDecimal(stock, "marketCap", sourceRank)
        val yearLow = requiredPositiveDecimal(stock, "yearLow", sourceRank)
        val yearHigh = requiredPositiveDecimal(stock, "yearHigh", sourceRank)
        val volume = requiredPositiveLong(stock, "volume", sourceRank)
        val weeklyAverageVolume = requiredPositiveDecimal(stock, "volumeWeekAvg", sourceRank)

        require(yearHigh >= yearLow) {
            "Groww volume-shocker row $sourceRank has yearHigh below yearLow."
        }

        return GrowwVolumeShockerSourceRow(
            sourceRank = sourceRank,
            exchange = exchange,
            symbol = symbol,
            companyName = requiredText(stock, "companyName", sourceRank),
            ltp = ltp,
            close = close,
            marketCapCrore = marketCapCrore,
            yearLow = yearLow,
            yearHigh = yearHigh,
            volume = volume,
            weeklyAverageVolume = weeklyAverageVolume,
        )
    }

    private fun requiredText(node: JsonNode, field: String, sourceRank: Int): String {
        return optionalText(node, field)
            ?: throw IllegalArgumentException(
                "Groww volume-shocker row $sourceRank has empty $field.",
            )
    }

    private fun optionalText(node: JsonNode, field: String): String? {
        val value = node.get(field) ?: return null
        if (!value.isTextual) return null
        return value.asText().trim().takeIf { text -> text.isNotEmpty() }
    }

    private fun requiredPositiveDecimal(node: JsonNode, field: String, sourceRank: Int): BigDecimal {
        val value = node.get(field)
        require(value != null && value.isNumber && value.decimalValue() > BigDecimal.ZERO) {
            "Groww volume-shocker row $sourceRank has invalid $field."
        }
        return value.decimalValue()
    }

    private fun requiredPositiveLong(node: JsonNode, field: String, sourceRank: Int): Long {
        val value = node.get(field)
        require(value != null && value.isIntegralNumber && value.canConvertToLong() && value.asLong() > 0L) {
            "Groww volume-shocker row $sourceRank has invalid $field."
        }
        return value.asLong()
    }
}
