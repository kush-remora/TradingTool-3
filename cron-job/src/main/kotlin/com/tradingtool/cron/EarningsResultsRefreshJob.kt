package com.tradingtool.cron

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.tradingtool.core.candle.dao.CandleReadDao
import com.tradingtool.core.candle.dao.CandleWriteDao
import com.tradingtool.core.config.ConfigLoader
import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.earnings.EarningsRefreshRequest
import com.tradingtool.core.earnings.EarningsRefreshResult
import com.tradingtool.core.earnings.EarningsResultService
import com.tradingtool.core.earnings.FileCorporateEventAdapter
import com.tradingtool.core.earnings.JdbiCandleGateway
import com.tradingtool.core.earnings.JdbiEarningsResultGateway
import com.tradingtool.core.earnings.dao.EarningsResultReadDao
import com.tradingtool.core.earnings.dao.EarningsResultWriteDao
import com.tradingtool.core.model.DatabaseConfig
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("EarningsResultsRefreshJob")

fun main(args: Array<String>) {
    val cli = parseArgs(args)
    val runtime = EarningsResultsRuntime.fromEnvironment(cli)

    val today = LocalDate.now()
    val from = cli.from ?: today
    val to = cli.to ?: from.plusMonths(1)

    val exitCode = runBlocking {
        runCatching {
            val result = runtime.service.refresh(
                EarningsRefreshRequest(
                    from = from,
                    to = to,
                    pastDays = cli.pastDays,
                    chunkDays = cli.chunkDays,
                ),
            )
            val outputDir = writeArtifacts(result)
            log.info(
                "Earnings refresh complete: from={} to={} chunks={} fetched={} upserted={} pastRows={} behaviorUpdated={} output={}",
                result.from,
                result.to,
                result.chunkCount,
                result.fetchedEvents,
                result.upsertedRows,
                result.pastRowsEvaluated,
                result.behaviorRowsUpdated,
                outputDir.toAbsolutePath(),
            )
            0
        }.getOrElse { error ->
            log.error("Earnings refresh failed: {}", error.message, error)
            1
        }
    }

    exitProcess(exitCode)
}

private data class EarningsResultsRefreshCliArgs(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val pastDays: Int = 30,
    val chunkDays: Int = 5,
    val eventsFile: String = DEFAULT_EARNINGS_EVENTS_FILE,
)

private fun parseArgs(args: Array<String>): EarningsResultsRefreshCliArgs {
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

    val from = values["from"]?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
    val to = values["to"]?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
    val pastDays = values["pastDays"]?.toIntOrNull() ?: 30
    val chunkDays = values["chunkDays"]?.toIntOrNull() ?: 5
    val eventsFile = values["eventsFile"]?.takeIf { value -> value.isNotBlank() } ?: DEFAULT_EARNINGS_EVENTS_FILE

    return EarningsResultsRefreshCliArgs(
        from = from,
        to = to,
        pastDays = pastDays,
        chunkDays = chunkDays,
        eventsFile = eventsFile,
    )
}

private data class EarningsResultsRuntime(
    val service: EarningsResultService,
) {
    companion object {
        fun fromEnvironment(cli: EarningsResultsRefreshCliArgs): EarningsResultsRuntime {
            val databaseConfig = DatabaseConfig(
                jdbcUrl = ConfigLoader.get("SUPABASE_DB_URL", "supabase.dbUrl"),
            )
            val objectMapper = buildObjectMapper()

            val earningsHandler = JdbiHandler(databaseConfig, EarningsResultReadDao::class.java, EarningsResultWriteDao::class.java)
            val candleHandler = JdbiHandler(databaseConfig, CandleReadDao::class.java, CandleWriteDao::class.java)

            val service = EarningsResultService(
                corporateEventSource = FileCorporateEventAdapter(
                    filePath = Paths.get(cli.eventsFile),
                    objectMapper = objectMapper,
                ),
                earningsGateway = JdbiEarningsResultGateway(earningsHandler),
                candleGateway = JdbiCandleGateway(candleHandler),
                objectMapper = objectMapper,
            )

            return EarningsResultsRuntime(service = service)
        }
    }
}

private fun writeArtifacts(result: EarningsRefreshResult): Path {
    val outputDir = Paths.get("build", "reports", "earnings-results-refresh")
    Files.createDirectories(outputDir)

    val objectMapper = buildObjectMapper()
    Files.writeString(outputDir.resolve("latest.json"), objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result))
    Files.writeString(outputDir.resolve("latest.md"), result.toMarkdown())
    return outputDir
}

private fun EarningsRefreshResult.toMarkdown(): String {
    return buildString {
        appendLine("# Earnings Results Refresh Report")
        appendLine()
        appendLine("- Window: `${from}` → `${to}`")
        appendLine("- Chunk days: `${chunkDays}`")
        appendLine("- Chunk count: `${chunkCount}`")
        appendLine("- Fetched events: `${fetchedEvents}`")
        appendLine("- Upserted rows: `${upsertedRows}`")
        appendLine("- Past behavior window: `${pastFrom}` → `${pastTo}`")
        appendLine("- Past rows evaluated: `${pastRowsEvaluated}`")
        appendLine("- Behavior rows updated: `${behaviorRowsUpdated}`")
        appendLine("- Stage updates: pre_result=`${preResultUpdates}`, result_day=`${resultDayUpdates}`, next_day=`${nextDayUpdates}`, plus_5d=`${plus5dUpdates}`")
    }
}

private fun buildObjectMapper(): ObjectMapper {
    return ObjectMapper()
        .findAndRegisterModules()
        .registerKotlinModule()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
}

private const val DEFAULT_EARNINGS_EVENTS_FILE = "manual-input/groww-corporate-events.json"
