package com.tradingtool.cron

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.tradingtool.core.config.ConfigLoader
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.indexconstituents.IndexConstituentCsvSource
import com.tradingtool.core.indexconstituents.IndexConstituentSyncService
import com.tradingtool.core.indexconstituents.IndexSyncConfigLoader
import com.tradingtool.core.indexconstituents.IndexSyncRunReport
import com.tradingtool.core.indexconstituents.JdbiIndexConstituentGateway
import com.tradingtool.core.indexconstituents.KiteIndexConstituentTokenResolver
import com.tradingtool.core.indexconstituents.dao.IndexConstituentReadDao
import com.tradingtool.core.indexconstituents.dao.IndexConstituentWriteDao
import com.tradingtool.core.http.HttpClientConfig
import com.tradingtool.core.http.JdkHttpClientImpl
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.KiteConfig
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.kite.KiteTokenReadDao
import com.tradingtool.core.kite.KiteTokenWriteDao
import com.tradingtool.core.model.DatabaseConfig
import com.tradingtool.core.telegram.TelegramApiClient
import com.tradingtool.core.telegram.TelegramNotifier
import com.tradingtool.core.telegram.TelegramSender
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.net.http.HttpClient as JdkHttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("IndexConstituentSyncJob")

fun main() {
    val runtime = IndexConstituentSyncRuntime.fromEnvironment()
    val jobName = "IndexConstituentSyncJob"

    val exitCode = runBlocking {
        runtime.telegramNotifier.cronStarted(jobName)
        runCatching {
            val config = runtime.configLoader.load()
            val report = runtime.service.sync(config)
            val outputDir = writeArtifacts(report)
            runtime.telegramNotifier.cronCompleted(jobName)

            log.info(
                "Index constituent sync completed: indices={} output={}",
                report.indexReports.size,
                outputDir.toAbsolutePath(),
            )
            0
        }.getOrElse { error ->
            log.error("Index constituent sync failed: {}", error.message, error)
            runtime.telegramNotifier.cronFailed(
                jobName,
                error as? Exception ?: RuntimeException(error.message ?: "Unknown failure", error),
            )
            1
        }
    }

    exitProcess(exitCode)
}

private data class IndexConstituentSyncRuntime(
    val service: IndexConstituentSyncService,
    val configLoader: IndexSyncConfigLoader,
    val telegramNotifier: TelegramNotifier,
) {
    companion object {
        fun fromEnvironment(): IndexConstituentSyncRuntime {
            val objectMapper = buildObjectMapper()
            val httpClient = buildHttpClient()
            val databaseConfig = DatabaseConfig(
                jdbcUrl = ConfigLoader.get("SUPABASE_DB_URL", "supabase.dbUrl"),
            )

            val indexHandler: IndexConstituentJdbiHandler =
                JdbiHandler(databaseConfig, IndexConstituentReadDao::class.java, IndexConstituentWriteDao::class.java)
            val tokenHandler = JdbiHandler(databaseConfig, KiteTokenReadDao::class.java, KiteTokenWriteDao::class.java)

            val kiteClient = buildAuthenticatedKiteClient(tokenHandler)
            val service = IndexConstituentSyncService(
                source = IndexConstituentCsvSource(),
                tokenResolver = KiteIndexConstituentTokenResolver(
                    kiteClient = kiteClient,
                    instrumentCache = InstrumentCache(),
                ),
                gateway = JdbiIndexConstituentGateway(indexHandler),
            )

            return IndexConstituentSyncRuntime(
                service = service,
                configLoader = IndexSyncConfigLoader(objectMapper),
                telegramNotifier = buildTelegramNotifier(httpClient, objectMapper),
            )
        }
    }
}

private fun buildHttpClient(): JdkHttpClientImpl {
    return JdkHttpClientImpl(
        JdkHttpClient.newBuilder().build(),
        HttpClientConfig(),
    )
}

private fun buildAuthenticatedKiteClient(
    tokenHandler: JdbiHandler<KiteTokenReadDao, KiteTokenWriteDao>,
): KiteConnectClient {
    val kiteClient = KiteConnectClient(
        KiteConfig(
            apiKey = ConfigLoader.get("KITE_API_KEY", "kite.apiKey"),
            apiSecret = ConfigLoader.get("KITE_API_SECRET", "kite.apiSecret"),
        ),
    )

    val latestToken = runBlocking {
        tokenHandler.read { dao -> dao.getLatestToken() }
    }?.takeIf { token -> token.isNotBlank() }
        ?: error("Kite authentication required. No token found in kite_tokens table.")

    kiteClient.applyAccessToken(latestToken)
    return kiteClient
}

private fun writeArtifacts(report: IndexSyncRunReport): Path {
    val outputDir = Paths.get("build", "reports", "index-constituent-sync")
    Files.createDirectories(outputDir)

    val objectMapper = buildObjectMapper()
    Files.writeString(outputDir.resolve("latest.json"), objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report))
    Files.writeString(outputDir.resolve("latest.md"), report.toMarkdown())
    return outputDir
}

private fun buildObjectMapper(): ObjectMapper {
    return ObjectMapper()
        .findAndRegisterModules()
        .registerKotlinModule()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
}

private fun buildTelegramNotifier(
    httpClient: JdkHttpClientImpl,
    objectMapper: ObjectMapper,
): TelegramNotifier {
    val botToken = ConfigLoader.getOptional("TELEGRAM_BOT_TOKEN", "telegram.botToken").orEmpty()
    val chatId = ConfigLoader.getOptional("TELEGRAM_CHAT_ID", "telegram.chatId").orEmpty()
    val apiClient = TelegramApiClient(
        botToken = botToken,
        chatId = chatId,
        httpClient = httpClient,
        objectMapper = objectMapper,
    )
    return TelegramNotifier(TelegramSender(apiClient))
}

private fun IndexSyncRunReport.toMarkdown(): String {
    val totalFetched = indexReports.sumOf { row -> row.fetchedCount }
    val totalParsed = indexReports.sumOf { row -> row.parsedCount }
    val totalUpserted = indexReports.sumOf { row -> row.upsertedCount }
    val totalDeactivated = indexReports.sumOf { row -> row.deactivatedCount }
    val totalUnresolved = indexReports.sumOf { row -> row.unresolvedSymbols.size }

    return buildString {
        appendLine("# Index Constituent Sync Report")
        appendLine()
        appendLine("- Started at: `${startedAt}`")
        appendLine("- Finished at: `${finishedAt}`")
        appendLine("- Total fetched: `${totalFetched}`")
        appendLine("- Total parsed: `${totalParsed}`")
        appendLine("- Total upserted: `${totalUpserted}`")
        appendLine("- Total deactivated: `${totalDeactivated}`")
        appendLine("- Total unresolved symbols: `${totalUnresolved}`")
        appendLine()
        indexReports.forEach { row ->
            appendLine("## ${row.indexKey}")
            appendLine("- Source: `${row.sourceUrl}`")
            appendLine("- Fetched: `${row.fetchedCount}`")
            appendLine("- Parsed: `${row.parsedCount}`")
            appendLine("- Upserted: `${row.upsertedCount}`")
            appendLine("- Deactivated: `${row.deactivatedCount}`")
            appendLine("- Unresolved: `${row.unresolvedSymbols.size}`")
        }
    }
}
