package com.tradingtool.core.delivery.source

import com.fasterxml.jackson.annotation.JsonProperty
import com.tradingtool.core.http.JsonHttpClient
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

class NseDeliverySourceAdapter(
    private val jsonHttpClient: JsonHttpClient
) {
    private val log = LoggerFactory.getLogger(NseDeliverySourceAdapter::class.java)

    data class DailyReportResponse(
        @JsonProperty("CurrentDay") val currentDay: List<ReportFile>? = null,
        @JsonProperty("PreviousDay") val previousDay: List<ReportFile>? = null
    )

    data class ReportFile(
        @JsonProperty("fileKey") val fileKey: String,
        @JsonProperty("filePath") val filePath: String,
        @JsonProperty("fileActlName") val fileActlName: String
    )

    data class NseDeliveryRow(
        val symbol: String,
        val series: String,
        val tradingDate: LocalDate,
        val ttlTrdQnty: Long,
        val delivQty: Long,
        val delivPer: Double,
        val sourceFileName: String,
        val sourceUrl: String
    )

    suspend fun fetchLatestDeliveryData(): List<NseDeliveryRow> {
        val discoveryUrl = "https://www.nseindia.com/api/daily-reports?key=CM"
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
            "Accept" to "application/json"
        )

        // NSE sometimes requires hitting the homepage first to establish session/cookies, 
        // but let's try direct first as many tools do.
        val response = jsonHttpClient.get<DailyReportResponse>(discoveryUrl, headers)
        val reports = response.getOrNull() ?: run {
            log.error("Failed to fetch NSE daily reports: ${response.errorOrNull()}")
            return emptyList()
        }

        val reportFile = findBhavDataFile(reports) ?: run {
            log.warn("CM-BHAVDATA-FULL not found in NSE daily reports")
            return emptyList()
        }

        val downloadUrl = reportFile.filePath + reportFile.fileActlName
        log.info("Downloading NSE delivery data from $downloadUrl")

        val csvContent = jsonHttpClient.getRaw(downloadUrl, headers).getOrNull() ?: run {
            log.error("Failed to download CSV from $downloadUrl")
            return emptyList()
        }

        return parseCsv(csvContent, downloadUrl, reportFile.fileActlName)
    }

    private fun findBhavDataFile(response: DailyReportResponse): ReportFile? {
        val current = response.currentDay?.find { it.fileKey == "CM-BHAVDATA-FULL" }
        if (current != null) return current
        return response.previousDay?.find { it.fileKey == "CM-BHAVDATA-FULL" }
    }

    private fun parseCsv(content: String, url: String, fileName: String): List<NseDeliveryRow> {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val header = lines[0].split(",").map { it.trim().uppercase() }
        val symbolIdx = header.indexOf("SYMBOL")
        val seriesIdx = header.indexOf("SERIES")
        val dateIdx = header.indexOf("DATE1")
        val ttlTrdIdx = header.indexOf("TTL_TRD_QNTY")
        val delivQtyIdx = header.indexOf("DELIV_QTY")
        val delivPerIdx = header.indexOf("DELIV_PER")

        if (symbolIdx == -1 || seriesIdx == -1 || dateIdx == -1 || ttlTrdIdx == -1 || delivQtyIdx == -1 || delivPerIdx == -1) {
            log.error("Invalid CSV header in $fileName: $header")
            return emptyList()
        }

        val dateFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)
        val rows = mutableListOf<NseDeliveryRow>()

        for (i in 1 until lines.size) {
            val line = lines[i]
            val cols = line.split(",").map { it.trim() }
            if (cols.size <= maxOf(symbolIdx, seriesIdx, dateIdx, ttlTrdIdx, delivQtyIdx, delivPerIdx)) continue

            try {
                val symbol = cols[symbolIdx]
                val series = cols[seriesIdx]
                val dateStr = cols[dateIdx]
                val ttlTrdStr = cols[ttlTrdIdx]
                val delivQtyStr = cols[delivQtyIdx]
                val delivPerStr = cols[delivPerIdx]

                if (isMissing(delivPerStr) || isMissing(delivQtyStr) || isMissing(ttlTrdStr)) continue

                rows.add(
                    NseDeliveryRow(
                        symbol = symbol,
                        series = series,
                        tradingDate = LocalDate.parse(dateStr, dateFormatter),
                        ttlTrdQnty = ttlTrdStr.toLong(),
                        delivQty = delivQtyStr.toLong(),
                        delivPer = delivPerStr.toDouble(),
                        sourceFileName = fileName,
                        sourceUrl = url
                    )
                )
            } catch (e: Exception) {
                // Log at debug to avoid spamming if there are many malformed lines
                log.debug("Failed to parse CSV line in $fileName: $line. Error: ${e.message}")
            }
        }
        return rows
    }

    private fun isMissing(value: String): Boolean {
        return value == "-" || value.isBlank()
    }
}
