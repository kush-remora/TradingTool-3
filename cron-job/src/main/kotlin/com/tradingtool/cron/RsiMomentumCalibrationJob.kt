package com.tradingtool.cron

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.tradingtool.core.strategy.rsimomentum.RsiMomentumCalibrationOptions
import com.tradingtool.core.strategy.rsimomentum.RsiMomentumCalibrationReport
import com.tradingtool.core.strategy.rsimomentum.RsiMomentumRuntime
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("RsiMomentumCalibrationJob")

fun main(args: Array<String>) {
    val cli = parseArgs(args)

    runBlocking {
        val exitCode = runCatching {
            RsiMomentumRuntime.fromEnvironment().use { runtime ->
                val options = RsiMomentumCalibrationOptions(
                    profileIds = cli.profileIds,
                    fromDate = cli.fromDate,
                    toDate = cli.toDate,
                    transactionCostBps = cli.transactionCostBps,
                    largeMidcapPeriodSets = cli.largeMidcapPeriodSets,
                    smallcapPeriodSets = cli.smallcapPeriodSets,
                )
                val report = runtime.service.calibrateAndApplyRsiPeriods(options)
                val outputDir = writeCalibrationArtifacts(report)

                report.profileResults.forEach { profile ->
                    log.info(
                        "RSI calibration: profile={} preset={} selectedPeriods={} reason={}",
                        profile.profileId,
                        profile.baseUniversePreset,
                        profile.selectedRsiPeriods,
                        profile.selectionReason,
                    )
                }
                log.info(
                    "RSI calibration completed: profiles={} outputDir={}",
                    report.profileResults.size,
                    outputDir.toAbsolutePath(),
                )
                0
            }
        }.getOrElse { error ->
            log.error("RSI calibration failed: {}", error.message, error)
            1
        }

        exitProcess(exitCode)
    }
}

private data class CalibrationCliArgs(
    val profileIds: Set<String> = emptySet(),
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
    val transactionCostBps: Double = RsiMomentumCalibrationOptions.DEFAULT_TRANSACTION_COST_BPS,
    val largeMidcapPeriodSets: List<List<Int>> = RsiMomentumCalibrationOptions.DEFAULT_LARGEMIDCAP_PERIOD_SETS,
    val smallcapPeriodSets: List<List<Int>> = RsiMomentumCalibrationOptions.DEFAULT_SMALLCAP_PERIOD_SETS,
)

private fun parseArgs(args: Array<String>): CalibrationCliArgs {
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

    val profileIds = values["profile"]
        ?.split(",")
        ?.map { value -> value.trim() }
        ?.filter { value -> value.isNotBlank() }
        ?.toSet()
        ?: emptySet()

    val fromDate = values["from"]?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
    val toDate = values["to"]?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
    val transactionCostBps = values["tx-bps"]
        ?.toDoubleOrNull()
        ?.coerceAtLeast(0.0)
        ?: RsiMomentumCalibrationOptions.DEFAULT_TRANSACTION_COST_BPS

    val largeSets = parsePeriodSets(values["large-sets"]) ?: RsiMomentumCalibrationOptions.DEFAULT_LARGEMIDCAP_PERIOD_SETS
    val smallSets = parsePeriodSets(values["small-sets"]) ?: RsiMomentumCalibrationOptions.DEFAULT_SMALLCAP_PERIOD_SETS

    return CalibrationCliArgs(
        profileIds = profileIds,
        fromDate = fromDate,
        toDate = toDate,
        transactionCostBps = transactionCostBps,
        largeMidcapPeriodSets = largeSets,
        smallcapPeriodSets = smallSets,
    )
}

private fun parsePeriodSets(raw: String?): List<List<Int>>? {
    if (raw.isNullOrBlank()) {
        return null
    }

    val sets = raw.split(";")
        .mapNotNull { chunk ->
            val periods = chunk
                .split(",")
                .mapNotNull { token -> token.trim().toIntOrNull() }
                .filter { period -> period >= 2 }
                .distinct()
                .sorted()
            if (periods.size < 3) null else periods
        }
        .distinct()

    return sets.ifEmpty { null }
}

private fun writeCalibrationArtifacts(report: RsiMomentumCalibrationReport): Path {
    val outputDir = Paths.get("build", "reports", "rsi-momentum-calibration")
    Files.createDirectories(outputDir)

    val objectMapper = ObjectMapper().registerKotlinModule()
    val jsonPath = outputDir.resolve("latest.json")
    val markdownPath = outputDir.resolve("latest.md")

    Files.writeString(jsonPath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report))
    Files.writeString(markdownPath, report.toMarkdown())

    return outputDir
}

private fun RsiMomentumCalibrationReport.toMarkdown(): String {
    val header = buildString {
        appendLine("# RSI Momentum Calibration Report")
        appendLine()
        appendLine("- Run at: `$runAt`")
        appendLine("- Method: `$method`")
        appendLine("- Sample range: `$sampleRange`")
        appendLine("- Transaction cost: `${transactionCostBps} bps`")
        appendLine()
    }

    val body = profileResults.joinToString("\n") { profile ->
        buildString {
            appendLine("## ${profile.profileLabel} (`${profile.profileId}`)")
            appendLine("- Universe: `${profile.baseUniversePreset}`")
            appendLine("- Selected RSI periods: `${profile.selectedRsiPeriods.joinToString("/")}`")
            appendLine("- Selection reason: ${profile.selectionReason}")
            appendLine()
            appendLine("| RSI Periods | Sortino | Sharpe | CAGR % | Max DD % | Turnover % | Stable | Rejection |")
            appendLine("|---|---:|---:|---:|---:|---:|---|---|")
            profile.candidates.forEach { candidate ->
                appendLine(
                    "| `${candidate.rsiPeriods.joinToString("/")}` | " +
                        "${candidate.annualizedSortino} | ${candidate.annualizedSharpe} | " +
                        "${candidate.cagrPct} | ${candidate.maxDrawdownPct} | ${candidate.averageTurnoverPct} | " +
                        "${if (candidate.isStable) "YES" else "NO"} | " +
                        "${candidate.rejectionReasons.joinToString(", ").ifBlank { "-" }} |",
                )
            }
            appendLine()
        }
    }

    return header + body
}
