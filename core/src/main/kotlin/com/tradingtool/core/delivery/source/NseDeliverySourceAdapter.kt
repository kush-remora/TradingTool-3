package com.tradingtool.core.delivery.source

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.tradingtool.core.delivery.model.DeliverySourceRow
import com.tradingtool.core.delivery.model.DeliverySourceType
import com.tradingtool.core.delivery.validation.DeliveryDiscoveryResult
import com.tradingtool.core.delivery.validation.DeliveryFileDescriptor
import com.tradingtool.core.http.JsonHttpClient
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

class NseDeliverySourceAdapter(
    private val jsonHttpClient: JsonHttpClient
) {
    private val log = LoggerFactory.getLogger(NseDeliverySourceAdapter::class.java)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class DailyReportResponse(
        @JsonProperty("CurrentDay") val currentDay: List<ReportFile>? = null,
        @JsonProperty("PreviousDay") val previousDay: List<ReportFile>? = null,
        @JsonProperty("FutureDay") val futureDay: List<ReportFile>? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ReportFile(
        @JsonProperty("fileKey") val fileKey: String,
        @JsonProperty("filePath") val filePath: String,
        @JsonProperty("fileActlName") val fileActlName: String,
        @JsonProperty("tradingDate") val tradingDate: String? = null,
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
        val discovery = discoverDeliveryReports() ?: run {
            log.warn("CM-BHAVDATA-FULL not found in NSE daily reports")
            return emptyList()
        }
        val reportFile = discovery.bhavDataFull ?: run {
            log.warn("CM-BHAVDATA-FULL file descriptor missing from discovery result")
            return emptyList()
        }
        return runCatching {
            fetchBhavDataRows(reportFile).map { row ->
                NseDeliveryRow(
                    symbol = row.symbol,
                    series = row.series,
                    tradingDate = row.tradingDate,
                    ttlTrdQnty = row.tradedQuantity,
                    delivQty = row.deliverableQuantity,
                    delivPer = row.deliveryPercent,
                    sourceFileName = reportFile.fileName,
                    sourceUrl = reportFile.url,
                )
            }
        }.getOrElse { error ->
            log.error("Failed to fetch latest delivery data: {}", error.message)
            emptyList()
        }
    }

    suspend fun discoverDeliveryReports(targetDate: LocalDate? = null): DeliveryDiscoveryResult? {
        val reports = fetchDailyReports() ?: return null
        val bucket = when {
            targetDate != null -> resolveBucketForTargetDate(reports, targetDate)
            containsDeliveryFiles(reports.currentDay.orEmpty()) -> "CurrentDay"
            containsDeliveryFiles(reports.previousDay.orEmpty()) -> "PreviousDay"
            else -> null
        } ?: return null

        val reportFiles = bucketFiles(reports, bucket)
        val bhav = reportFiles.firstOrNull { file -> file.fileKey == CM_BHAVDATA_FULL_FILE_KEY }
        val mto = reportFiles.firstOrNull { file -> file.fileKey == CM_MTO_FILE_KEY }
        val resolvedTradingDate = targetDate
            ?: parseTradingDate(bhav?.tradingDate)
            ?: parseTradingDate(mto?.tradingDate)
            ?: return null

        return DeliveryDiscoveryResult(
            resolvedTradingDate = resolvedTradingDate,
            bucket = bucket,
            bhavDataFull = bhav?.toDescriptor(),
            mto = mto?.toDescriptor(),
        )
    }

    suspend fun fetchBhavDataRows(reportFile: DeliveryFileDescriptor): List<DeliverySourceRow> {
        val csvContent = fetchRawContent(reportFile.url)
        return parseBhavCsv(
            content = csvContent,
            tradingDate = reportFile.tradingDate,
        )
    }

    suspend fun fetchMtoRows(reportFile: DeliveryFileDescriptor): List<DeliverySourceRow> {
        val rawContent = fetchRawContent(reportFile.url)
        return parseMtoDat(
            content = rawContent,
            tradingDate = reportFile.tradingDate,
        )
    }

    private suspend fun fetchDailyReports(): DailyReportResponse? {
        val response = jsonHttpClient.get<DailyReportResponse>(DISCOVERY_URL, defaultHeaders())
        val reports = response.getOrNull()
        if (reports == null) {
            log.error("Failed to fetch NSE daily reports: {}", response.errorOrNull())
        }
        return reports
    }

    private suspend fun fetchRawContent(url: String): String {
        log.info("Downloading NSE delivery file from {}", url)
        return jsonHttpClient.getRaw(url, defaultHeaders()).getOrNull()
            ?: error("Failed to download file from $url")
    }

    private fun resolveBucketForTargetDate(response: DailyReportResponse, targetDate: LocalDate): String? {
        return listOf("CurrentDay", "PreviousDay", "FutureDay")
            .firstOrNull { bucket ->
                bucketFiles(response, bucket)
                    .any { file -> parseTradingDate(file.tradingDate) == targetDate }
            }
    }

    private fun containsDeliveryFiles(files: List<ReportFile>): Boolean {
        return files.any { file ->
            file.fileKey == CM_BHAVDATA_FULL_FILE_KEY || file.fileKey == CM_MTO_FILE_KEY
        }
    }

    private fun bucketFiles(response: DailyReportResponse, bucket: String): List<ReportFile> {
        return when (bucket) {
            "CurrentDay" -> response.currentDay.orEmpty()
            "PreviousDay" -> response.previousDay.orEmpty()
            "FutureDay" -> response.futureDay.orEmpty()
            else -> emptyList()
        }
    }

    private fun parseBhavCsv(
        content: String,
        tradingDate: LocalDate,
    ): List<DeliverySourceRow> {
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
            error("Invalid bhavcopy header: $header")
        }

        val dateFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)
        val rows = mutableListOf<DeliverySourceRow>()

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

                val rowTradingDate = LocalDate.parse(dateStr, dateFormatter)
                if (rowTradingDate != tradingDate) continue

                rows.add(
                    DeliverySourceRow(
                        symbol = symbol.uppercase(),
                        series = series,
                        tradingDate = rowTradingDate,
                        tradedQuantity = ttlTrdStr.toLong(),
                        deliverableQuantity = delivQtyStr.toLong(),
                        deliveryPercent = delivPerStr.toDouble(),
                        source = DeliverySourceType.BHAVDATA_FULL,
                    )
                )
            } catch (e: Exception) {
                log.debug("Failed to parse bhavcopy line: {}. Error: {}", line, e.message)
            }
        }
        return rows
    }

    private fun parseMtoDat(
        content: String,
        tradingDate: LocalDate,
    ): List<DeliverySourceRow> {
        return content.lineSequence()
            .map { line -> line.trim() }
            .filter { line -> line.startsWith("20,") }
            .mapNotNull { line ->
                val cols = line.split(",").map { value -> value.trim() }
                if (cols.size < 7) {
                    return@mapNotNull null
                }

                runCatching {
                    DeliverySourceRow(
                        symbol = cols[2].uppercase(),
                        series = cols[3],
                        tradingDate = tradingDate,
                        tradedQuantity = cols[4].toLong(),
                        deliverableQuantity = cols[5].toLong(),
                        deliveryPercent = cols[6].toDouble(),
                        source = DeliverySourceType.MTO,
                    )
                }.getOrNull()
            }
            .toList()
    }

    private fun isMissing(value: String): Boolean {
        return value == "-" || value.isBlank()
    }

    private fun parseTradingDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) {
            return null
        }
        return runCatching { LocalDate.parse(raw, apiDateFormatter) }.getOrNull()
    }

    private fun ReportFile.toDescriptor(): DeliveryFileDescriptor {
        val tradingDate = parseTradingDate(tradingDate)
            ?: error("Unable to parse trading date from report file $fileActlName")
        return DeliveryFileDescriptor(
            tradingDate = tradingDate,
            url = filePath + fileActlName,
            fileName = fileActlName,
        )
    }

    private fun defaultHeaders(): Map<String, String> {
        return mapOf(
            "User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
            "Accept" to "application/json",
        )
    }

    companion object {
        private const val DISCOVERY_URL: String = "https://www.nseindia.com/api/daily-reports?key=CM"
        private const val CM_BHAVDATA_FULL_FILE_KEY: String = "CM-BHAVDATA-FULL"
        private const val CM_MTO_FILE_KEY: String = "CM-SECWISE-DELIVERY-POSITION"
        private val apiDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)
    }
}
