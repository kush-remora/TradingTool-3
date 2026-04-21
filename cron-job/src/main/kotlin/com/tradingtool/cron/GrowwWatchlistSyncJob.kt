package com.tradingtool.cron

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.tradingtool.core.config.ConfigLoader
import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.model.DatabaseConfig
import com.tradingtool.core.stock.dao.StockReadDao
import com.tradingtool.core.stock.dao.StockWriteDao
import com.tradingtool.core.watchlist.groww.FileGrowwWatchlistAdapter
import com.tradingtool.core.watchlist.groww.GrowwWatchlistSyncRequest
import com.tradingtool.core.watchlist.groww.GrowwWatchlistSyncResult
import com.tradingtool.core.watchlist.groww.GrowwWatchlistSyncService
import com.tradingtool.core.watchlist.groww.JdbiGrowwWatchlistStockGateway
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("GrowwWatchlistSyncJob")

fun main(args: Array<String>) {
    val cli = parseArgs(args)
    val runtime = GrowwWatchlistSyncRuntime.fromEnvironment(cli)

    val exitCode = runBlocking {
        runCatching {
            val result = runtime.service.sync(
                GrowwWatchlistSyncRequest(watchlistId = runtime.watchlistId),
            )
            val outputDir = writeArtifacts(result)
            log.info(
                "Groww watchlist sync completed: watchlistId={} fetched={} synced={} output={}",
                result.watchlistId,
                result.fetchedCount,
                result.syncedCount,
                outputDir.toAbsolutePath(),
            )
            0
        }.getOrElse { error ->
            log.error("Groww watchlist sync failed: {}", error.message, error)
            1
        }
    }

    exitProcess(exitCode)
}

private data class GrowwWatchlistSyncCliArgs(
    val watchlistId: String? = "GWL_1729712098800",
    val watchlistFile: String = DEFAULT_WATCHLIST_FILE,
)

private fun parseArgs(args: Array<String>): GrowwWatchlistSyncCliArgs {
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

    val watchlistId = values["watchlistId"]?.takeIf { value -> value.isNotBlank() }
    val watchlistFile = values["watchlistFile"]?.takeIf { value -> value.isNotBlank() } ?: DEFAULT_WATCHLIST_FILE
    return GrowwWatchlistSyncCliArgs(
        watchlistId = watchlistId ?: GrowwWatchlistSyncCliArgs().watchlistId,
        watchlistFile = watchlistFile,
    )
}


private data class GrowwWatchlistSyncRuntime(
    val watchlistId: String,
    val service: GrowwWatchlistSyncService,
) {
    companion object {
        fun fromEnvironment(cli: GrowwWatchlistSyncCliArgs): GrowwWatchlistSyncRuntime {
            val objectMapper = buildObjectMapper()
            val databaseConfig = DatabaseConfig(
                jdbcUrl = ConfigLoader.get("SUPABASE_DB_URL", "supabase.dbUrl"),
            )
            val stockHandler = JdbiHandler(databaseConfig, StockReadDao::class.java, StockWriteDao::class.java)

            val watchlistId = cli.watchlistId
                ?.takeIf { value -> value.isNotBlank() }
                ?: ConfigLoader.getOptional("GROWW_WATCHLIST_ID", "groww.watchlistId")
                    ?.takeIf { value -> value.isNotBlank() }
                ?: DEFAULT_WATCHLIST_ID

            val service = GrowwWatchlistSyncService(
                source = FileGrowwWatchlistAdapter(
                    filePath = Paths.get(cli.watchlistFile),
                    objectMapper = objectMapper,
                ),
                stockGateway = JdbiGrowwWatchlistStockGateway(
                    stockHandler = stockHandler,
                    objectMapper = objectMapper,
                ),
            )

            return GrowwWatchlistSyncRuntime(
                watchlistId = watchlistId,
                service = service,
            )
        }
        private const val DEFAULT_WATCHLIST_ID = "GWL_1729712098800"
    }
}

private fun writeArtifacts(result: GrowwWatchlistSyncResult): Path {
    val outputDir = Paths.get("build", "reports", "groww-watchlist-sync")
    Files.createDirectories(outputDir)

    val objectMapper = buildObjectMapper()
    Files.writeString(outputDir.resolve("latest.json"), objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result))
    Files.writeString(outputDir.resolve("latest.md"), result.toMarkdown())
    return outputDir
}

private fun GrowwWatchlistSyncResult.toMarkdown(): String {
    return buildString {
        appendLine("# Groww Watchlist Sync Report")
        appendLine()
        appendLine("- Watchlist ID: `${watchlistId}`")
        appendLine("- Fetched stocks: `${fetchedCount}`")
        appendLine("- Synced rows: `${syncedCount}`")
    }
}

private fun buildObjectMapper(): ObjectMapper {
    return ObjectMapper()
        .findAndRegisterModules()
        .registerKotlinModule()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
}

private const val DEFAULT_WATCHLIST_FILE = "manual-input/groww-watchlist.json"
