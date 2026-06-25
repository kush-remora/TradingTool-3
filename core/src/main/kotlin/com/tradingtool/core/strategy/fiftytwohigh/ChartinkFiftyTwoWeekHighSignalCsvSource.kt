package com.tradingtool.core.strategy.fiftytwohigh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ChartinkFiftyTwoWeekHighSignalCsvSource {

    suspend fun load(inputFile: Path): List<ChartinkFiftyTwoWeekHighSignal> = withContext(Dispatchers.IO) {
        Files.newBufferedReader(inputFile).use { reader ->
            parse(reader)
        }
    }

    internal fun parse(reader: Reader): List<ChartinkFiftyTwoWeekHighSignal> {
        val parser = CSVParser(
            reader,
            CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build(),
        )

        if (!parser.headerMap.keys.containsAll(REQUIRED_HEADERS)) {
            val foundHeaders = parser.headerMap.keys.sorted().joinToString(", ")
            error("Signal CSV is missing required headers. expected=$REQUIRED_HEADERS found=$foundHeaders")
        }

        return parser.asSequence()
            .map { row ->
                ChartinkFiftyTwoWeekHighSignal(
                    signalDate = LocalDate.parse(row.get("date").trim(), DATE_FORMATTER),
                    symbol = row.get("symbol").trim().uppercase(),
                    marketCapName = row.get("marketcapname").trim(),
                    sector = row.get("sector").trim(),
                )
            }
            .filter { signal -> signal.symbol.isNotBlank() }
            .toList()
    }

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        val REQUIRED_HEADERS: Set<String> = setOf("date", "symbol", "marketcapname", "sector")
    }
}
