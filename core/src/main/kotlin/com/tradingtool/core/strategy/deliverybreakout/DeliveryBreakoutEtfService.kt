package com.tradingtool.core.strategy.deliverybreakout

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.slf4j.LoggerFactory
import java.io.File
import java.io.StringReader

@Singleton
class DeliveryBreakoutEtfService @Inject constructor() {
    private val log = LoggerFactory.getLogger(javaClass)
    private val etfFile = File(System.getProperty(ETF_FILE_PATH_PROPERTY, DEFAULT_ETF_FILE_PATH))

    fun filterNonEtfRows(rows: List<StockDeliveryDaily>): List<StockDeliveryDaily> {
        val etfSymbols = loadEtfSymbols()
        if (etfSymbols.isEmpty()) {
            return rows
        }

        return rows.filterNot { row ->
            etfSymbols.contains(normalizeSymbol(row.symbol))
        }
    }

    internal fun loadEtfSymbols(): Set<String> {
        if (!etfFile.exists()) {
            log.warn("Delivery breakout ETF file not found: {}", etfFile.path)
            return emptySet()
        }

        return try {
            parseEtfSymbols(etfFile.readText())
        } catch (error: Exception) {
            log.error("Failed to read ETF file {}: {}", etfFile.path, error.message)
            emptySet()
        }
    }

    internal fun parseEtfSymbols(csvBody: String): Set<String> {
        StringReader(csvBody).use { reader ->
            val parser = CSVParser(
                reader,
                CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreHeaderCase(true)
                    .setTrim(true)
                    .build(),
            )

            if (!parser.headerMap.containsKey(SYMBOL_HEADER)) {
                log.warn("ETF CSV missing '{}' header", SYMBOL_HEADER)
                return emptySet()
            }

            return parser.asSequence()
                .mapNotNull { row -> row.get(SYMBOL_HEADER) }
                .map(::normalizeSymbol)
                .filter(String::isNotEmpty)
                .toSet()
        }
    }

    private fun normalizeSymbol(symbol: String): String {
        return symbol.trim().uppercase()
    }

    companion object {
        const val ETF_FILE_PATH_PROPERTY: String = "delivery.breakout.etf.file"
        const val DEFAULT_ETF_FILE_PATH: String = "manual-input/eq_etfseclist.csv"
        private const val SYMBOL_HEADER: String = "Symbol"
    }
}
