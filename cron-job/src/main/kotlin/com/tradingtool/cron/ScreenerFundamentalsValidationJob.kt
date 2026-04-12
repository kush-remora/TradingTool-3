package com.tradingtool.cron

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.tradingtool.core.fundamentals.screener.ScreenerFundamentalsValidationReport
import com.tradingtool.core.fundamentals.screener.ScreenerFundamentalsValidationService
import com.tradingtool.core.http.HttpClientConfig
import com.tradingtool.core.http.JdkHttpClientImpl
import com.tradingtool.core.fundamentals.screener.ScreenerFundamentalsSourceAdapter
import org.slf4j.LoggerFactory
import java.net.http.HttpClient as JdkHttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("ScreenerFundamentalsValidationJob")

fun main(args: Array<String>) {
    val cli = parseArgs(args)
    val service = ScreenerFundamentalsValidationService(
        sourceAdapter = ScreenerFundamentalsSourceAdapter(
            httpClient = JdkHttpClientImpl(
                JdkHttpClient.newBuilder().build(),
                HttpClientConfig(),
            ),
        ),
    )

    val exitCode = kotlinx.coroutines.runBlocking {
        runCatching {
            val report = service.validate(cli.symbols)
            val outputDir = writeArtifacts(report)
            log.info(
                "Screener fundamentals validation completed: tested={} reachable={} parsed={} output={}",
                report.testedCount,
                report.reachableCount,
                report.parsedCount,
                outputDir.toAbsolutePath(),
            )
            if (report.reachableCount > 0 && report.parsedCount > 0) 0 else 1
        }.getOrElse { error ->
            log.error("Screener fundamentals validation failed: {}", error.message, error)
            1
        }
    }

    exitProcess(exitCode)
}

private data class ScreenerFundamentalsCliArgs(
    val symbols: List<String>,
)

private fun parseArgs(args: Array<String>): ScreenerFundamentalsCliArgs {
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

    val symbols = values["symbols"]
        ?.split(",")
        ?.map { symbol -> symbol.trim() }
        ?.filter { symbol -> symbol.isNotEmpty() }
        ?.ifEmpty { null }
        ?: DEFAULT_SYMBOLS

    return ScreenerFundamentalsCliArgs(symbols = symbols)
}

private fun writeArtifacts(report: ScreenerFundamentalsValidationReport): Path {
    val outputDir = Paths.get("build", "reports", "screener-fundamentals-validation")
    Files.createDirectories(outputDir)

    val objectMapper = ObjectMapper()
        .findAndRegisterModules()
        .registerKotlinModule()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    Files.writeString(outputDir.resolve("latest.json"), objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report))
    Files.writeString(outputDir.resolve("latest.md"), report.toMarkdown())
    return outputDir
}

private fun ScreenerFundamentalsValidationReport.toMarkdown(): String {
    return buildString {
        appendLine("# Screener Fundamentals Validation Report")
        appendLine()
        appendLine("- Requested symbols: `${requestedSymbols.joinToString(",")}`")
        appendLine("- Tested count: `${testedCount}`")
        appendLine("- Reachable count: `${reachableCount}`")
        appendLine("- Parsed count: `${parsedCount}`")
        appendLine("- Market cap parsed: `${marketCapCount}`")
        appendLine("- Stock P/E parsed: `${stockPeCount}`")
        appendLine("- ROCE parsed: `${roceCount}`")
        appendLine("- ROE parsed: `${roeCount}`")
        appendLine("- Promoter holding parsed: `${promoterHoldingCount}`")
        appendLine("- Broad industry parsed: `${broadIndustryCount}`")
        appendLine("- Industry parsed: `${industryCount}`")
        appendLine("- Debt to equity parsed: `${debtToEquityCount}`")
        appendLine("- Pledged percentage parsed: `${pledgedPercentCount}`")
        appendLine()
        appendLine("| Symbol | Reachable | Parsed | Market Cap | P/E | ROCE | ROE | Promoter | Broad Industry | Industry | Error |")
        appendLine("|---|---|---|---:|---:|---:|---:|---:|---|---|---|")
        rows.forEach { row ->
            appendLine(
                "| `${row.symbol}` | ${row.reachable} | ${row.parsed} | ${row.snapshot?.marketCapCr ?: "-"} | " +
                    "${row.snapshot?.stockPe ?: "-"} | ${row.snapshot?.rocePercent ?: "-"} | ${row.snapshot?.roePercent ?: "-"} | " +
                    "${row.snapshot?.promoterHoldingPercent ?: "-"} | ${row.snapshot?.broadIndustry ?: "-"} | ${row.snapshot?.industry ?: "-"} | ${row.error ?: "-"} |",
            )
        }
    }
}

private val DEFAULT_SYMBOLS: List<String> = listOf("RELIANCE", "TCS", "INFY")
