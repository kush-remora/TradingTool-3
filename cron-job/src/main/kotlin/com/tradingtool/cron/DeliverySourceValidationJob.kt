package com.tradingtool.cron

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.tradingtool.core.config.ConfigLoader
import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.delivery.config.DeliveryConfigService
import com.tradingtool.core.delivery.config.DeliveryUniverseService
import com.tradingtool.core.delivery.source.NseDeliverySourceAdapter
import com.tradingtool.core.delivery.validation.DeliveryRecommendation
import com.tradingtool.core.delivery.validation.DeliverySourceValidationReport
import com.tradingtool.core.delivery.validation.DeliverySourceValidationService
import com.tradingtool.core.http.HttpClientConfig
import com.tradingtool.core.http.JdkHttpClientImpl
import com.tradingtool.core.http.JsonHttpClient
import com.tradingtool.core.model.DatabaseConfig
import com.tradingtool.core.stock.dao.StockReadDao
import com.tradingtool.core.stock.dao.StockWriteDao
import org.slf4j.LoggerFactory
import java.net.http.HttpClient as JdkHttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("DeliverySourceValidationJob")

fun main(args: Array<String>) {
    val cli = parseArgs(args)
    val databaseConfig = DatabaseConfig(
        jdbcUrl = ConfigLoader.get("SUPABASE_DB_URL", "supabase.dbUrl"),
    )
    val stockHandler = JdbiHandler(databaseConfig, StockReadDao::class.java, StockWriteDao::class.java)
    val httpClient = JdkHttpClientImpl(
        JdkHttpClient.newBuilder().build(),
        HttpClientConfig(),
    )
    val deliveryConfigService = DeliveryConfigService()
    val validationService = DeliverySourceValidationService(
        deliveryUniverseService = DeliveryUniverseService(
            configService = deliveryConfigService,
            stockHandler = stockHandler,
        ),
        sourceAdapter = NseDeliverySourceAdapter(
            JsonHttpClient(
                httpClient = httpClient,
                objectMapper = ObjectMapper()
                    .findAndRegisterModules()
                    .registerKotlinModule()
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),
            ),
        ),
    )

    val exitCode = kotlinx.coroutines.runBlocking {
        runCatching {
            val report = validationService.validate(cli.date)
            val outputDir = writeArtifacts(report)
            log.info("Delivery validation completed: resolvedDate={} recommendedSource={} verdict={}", report.resolvedTradingDate, report.recommendedSource, report.verdict)
            log.info("Bhavcopy summary: available={} parseSucceeded={} matchedTargets={}/{}", report.bhavDataFull.available, report.bhavDataFull.parseSucceeded, report.bhavDataFull.matchedTargetCount, report.bhavDataFull.targetSymbolCount)
            log.info("MTO summary: available={} parseSucceeded={} matchedTargets={}/{}", report.mto.available, report.mto.parseSucceeded, report.mto.matchedTargetCount, report.mto.targetSymbolCount)
            log.info("Artifacts written to {}", outputDir.toAbsolutePath())
            if (report.recommendedSource == DeliveryRecommendation.PIVOT_REQUIRED) 1 else 0
        }.getOrElse { error ->
            log.error("Delivery source validation failed: {}", error.message, error)
            1
        }
    }

    exitProcess(exitCode)
}

private data class DeliveryValidationCliArgs(
    val date: LocalDate? = null,
)

private fun parseArgs(args: Array<String>): DeliveryValidationCliArgs {
    val values = args
        .mapNotNull { arg ->
            if (!arg.startsWith("--") || !arg.contains("=")) {
                null
            } else {
                val key = arg.substringAfter("--").substringBefore("=")
                val value = arg.substringAfter("=")
                key to value
            }
        }
        .toMap()

    val date = values["date"]?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
    return DeliveryValidationCliArgs(date = date)
}

private fun writeArtifacts(report: DeliverySourceValidationReport): Path {
    val outputDir = Paths.get("build", "reports", "delivery-source-validation")
    Files.createDirectories(outputDir)

    val objectMapper = ObjectMapper()
        .findAndRegisterModules()
        .registerKotlinModule()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    Files.writeString(outputDir.resolve("latest.json"), objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report))
    Files.writeString(outputDir.resolve("latest.md"), report.toMarkdown())
    return outputDir
}

private fun DeliverySourceValidationReport.toMarkdown(): String {
    return buildString {
        appendLine("# Delivery Source Validation Report")
        appendLine()
        appendLine("- Requested date: `${requestedDate ?: "latest"}`")
        appendLine("- Resolved date: `${resolvedTradingDate ?: "unknown"}`")
        appendLine("- Discovery worked: `${discoveryWorked}`")
        appendLine("- Discovery bucket: `${discoveryBucket ?: "-"}`")
        appendLine("- Recommended source: `${recommendedSource}`")
        appendLine("- Verdict: ${verdict}")
        if (!discoveryError.isNullOrBlank()) {
            appendLine("- Discovery error: $discoveryError")
        }
        appendLine()
        appendLine("## Source Summary")
        appendLine()
        appendLine("| Source | Available | Parsed | Rows | Target Coverage | Missing Targets | Error |")
        appendLine("|---|---|---|---:|---:|---:|---|")
        appendLine(
            "| BHAVDATA_FULL | ${bhavDataFull.available} | ${bhavDataFull.parseSucceeded} | ${bhavDataFull.rowCount} | " +
                "${formatPercent(bhavDataFull.coverageRatio)} | ${bhavDataFull.missingTargetSymbols.size} | ${bhavDataFull.error ?: "-"} |",
        )
        appendLine(
            "| MTO | ${mto.available} | ${mto.parseSucceeded} | ${mto.rowCount} | " +
                "${formatPercent(mto.coverageRatio)} | ${mto.missingTargetSymbols.size} | ${mto.error ?: "-"} |",
        )
        appendLine()
        appendLine("## Comparison")
        appendLine()
        appendLine("- Compared symbols: `${comparison.comparedSymbolCount}`")
        appendLine("- Material mismatches: `${comparison.materialMismatches.size}`")
        appendLine("- Material consistency: `${comparison.materiallyConsistent}`")
        appendLine("- Bhav-only symbols: `${comparison.bhavOnlySymbols.size}`")
        appendLine("- MTO-only symbols: `${comparison.mtoOnlySymbols.size}`")
        if (comparison.materialMismatches.isNotEmpty()) {
            appendLine()
            appendLine("| Symbol | Bhav % | MTO % | Abs Diff |")
            appendLine("|---|---:|---:|---:|")
            comparison.materialMismatches.take(20).forEach { mismatch ->
                appendLine("| `${mismatch.symbol}` | ${mismatch.bhavDeliveryPercent} | ${mismatch.mtoDeliveryPercent} | ${mismatch.absoluteDifference} |")
            }
        }
    }
}

private fun formatPercent(value: Double): String {
    return "${"%.2f".format(value * 100.0)}%"
}
