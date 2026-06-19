package com.tradingtool.core.indexconstituents

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.StringReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class IndexConstituentCsvSource(
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
) : IndexConstituentSource {

    override suspend fun fetchRows(index: IndexDefinition): List<IndexConstituentCsvRow> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(index.csvUrl))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/csv,application/csv,*/*")
            .GET()
            .build()

        val response = withContext(Dispatchers.IO) {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }

        if (response.statusCode() != 200) {
            throw IllegalStateException("Failed to fetch ${index.key}. status=${response.statusCode()}")
        }

        val body = response.body()
        val rows = parseRows(body)
        if (rows.isEmpty()) {
            throw IllegalStateException("CSV parse failed for ${index.key}: ${describeParseFailure(body)}")
        }

        return rows
    }

    internal fun parseRows(csvBody: String): List<IndexConstituentCsvRow> {
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

            if (!parser.headerMap.containsKey("Symbol")) {
                return emptyList()
            }

            return parser.asSequence()
                .map { row ->
                    IndexConstituentCsvRow(
                        symbol = row.get("Symbol")?.trim().orEmpty().uppercase(),
                        companyName = row.get("Company Name")?.trim().orEmpty(),
                        industry = row.get("Industry")?.trim().orEmpty(),
                        series = row.get("Series")?.trim().orEmpty(),
                        isinCode = row.get("ISIN Code")?.trim().orEmpty(),
                    )
                }
                .filter { row -> row.symbol.isNotEmpty() }
                .toList()
        }
    }

    internal fun describeParseFailure(csvBody: String): String {
        if (isLikelyHtml(csvBody)) {
            return "received HTML instead of CSV from source URL"
        }

        val headerLine = csvBody
            .lineSequence()
            .firstOrNull { line -> line.isNotBlank() }
            ?.trim()
            .orEmpty()

        return if (headerLine.isBlank()) {
            "response body was empty"
        } else {
            "Symbol column missing or empty. first line=`$headerLine`"
        }
    }

    private fun isLikelyHtml(csvBody: String): Boolean {
        val firstLine = csvBody
            .lineSequence()
            .firstOrNull { line -> line.isNotBlank() }
            ?.trim()
            ?.lowercase()
            .orEmpty()

        return firstLine.startsWith("<!doctype html") ||
            firstLine.startsWith("<html") ||
            firstLine.startsWith("<head") ||
            firstLine.startsWith("<body")
    }

    private companion object {
        const val USER_AGENT: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    }
}
