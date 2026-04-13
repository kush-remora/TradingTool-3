package com.tradingtool.cron

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.tradingtool.core.config.ConfigLoader
import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.fundamentals.config.FundamentalsConfigService
import com.tradingtool.core.fundamentals.config.FundamentalsUniverseService
import com.tradingtool.core.fundamentals.dao.StockFundamentalsReadDao
import com.tradingtool.core.fundamentals.dao.StockFundamentalsWriteDao
import com.tradingtool.core.fundamentals.refresh.FundamentalsRefreshRunReport
import com.tradingtool.core.fundamentals.refresh.FundamentalsRefreshRunReportFactory
import com.tradingtool.core.fundamentals.refresh.FundamentalsRefreshService
import com.tradingtool.core.fundamentals.refresh.toMarkdown
import com.tradingtool.core.fundamentals.refresh.toTelegramSummary
import com.tradingtool.core.fundamentals.screener.ScreenerFundamentalsSourceAdapter
import com.tradingtool.core.http.HttpClientConfig
import com.tradingtool.core.http.JdkHttpClientImpl
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.KiteConfig
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.kite.KiteTokenReadDao
import com.tradingtool.core.kite.KiteTokenWriteDao
import com.tradingtool.core.model.DatabaseConfig
import com.tradingtool.core.stock.dao.StockReadDao
import com.tradingtool.core.stock.dao.StockWriteDao
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

private val log = LoggerFactory.getLogger("FundamentalsRefreshJob")

fun main(args: Array<String>) {
    val cli = parseArgs(args)
    val runtime = FundamentalsRefreshRuntime.fromEnvironment()
    val jobName = "FundamentalsRefreshJob"

    runBlocking {
        runtime.telegramNotifier.cronStarted(jobName)
        val exitCode = runCatching {
            val result = runtime.service.refreshDailySnapshots(cli.symbols)
            val rows = runtime.service.findRowsForDate(result.snapshotDate, result.resolvedInstrumentTokens)
            val report = FundamentalsRefreshRunReportFactory.create(result, rows)
            val outputDir = writeArtifacts(report)
            runtime.telegramNotifier.cronCompleted(jobName, report.toTelegramSummary())
            log.info(
                "Fundamentals refresh completed: snapshotDate={} expected={} stored={} failed={} artifacts={}",
                report.snapshotDate,
                report.expectedSymbolCount,
                report.storedRowCount,
                report.failedCount,
                outputDir.toAbsolutePath(),
            )
            if (report.blockingIssues.isEmpty()) 0 else 1
        }.getOrElse { error ->
            log.error("Fundamentals refresh failed: {}", error.message, error)
            runtime.telegramNotifier.cronFailed(
                jobName,
                error as? Exception ?: RuntimeException(error.message ?: "Unknown failure", error),
            )
            1
        }
        exitProcess(exitCode)
    }
}

private data class FundamentalsRefreshCliArgs(
    val symbols: List<String>? = null,
)

private data class FundamentalsRefreshRuntime(
    val service: FundamentalsRefreshService,
    val telegramNotifier: TelegramNotifier,
) {
    companion object {
        fun fromEnvironment(): FundamentalsRefreshRuntime {
            val databaseConfig = DatabaseConfig(
                jdbcUrl = ConfigLoader.get("SUPABASE_DB_URL", "supabase.dbUrl"),
            )
            val httpClient = JdkHttpClientImpl(
                JdkHttpClient.newBuilder().build(),
                HttpClientConfig(),
            )
            val objectMapper = buildObjectMapper()
            val stockHandler = JdbiHandler(databaseConfig, StockReadDao::class.java, StockWriteDao::class.java)
            val fundamentalsHandler = JdbiHandler(databaseConfig, StockFundamentalsReadDao::class.java, StockFundamentalsWriteDao::class.java)
            val tokenHandler = JdbiHandler(databaseConfig, KiteTokenReadDao::class.java, KiteTokenWriteDao::class.java)
            val configService = FundamentalsConfigService()
            val service = FundamentalsRefreshService(
                configService = configService,
                fundamentalsUniverseService = FundamentalsUniverseService(
                    configService = configService,
                    stockHandler = stockHandler,
                ),
                stockHandler = stockHandler,
                fundamentalsHandler = fundamentalsHandler,
                instrumentCache = InstrumentCache(),
                kiteClient = buildAuthenticatedKiteClient(tokenHandler),
                sourceAdapter = ScreenerFundamentalsSourceAdapter(httpClient),
            )

            return FundamentalsRefreshRuntime(
                service = service,
                telegramNotifier = buildTelegramNotifier(httpClient, objectMapper),
            )
        }
    }
}

private fun parseArgs(args: Array<String>): FundamentalsRefreshCliArgs {
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

    return FundamentalsRefreshCliArgs(symbols = symbols)
}

private fun writeArtifacts(report: FundamentalsRefreshRunReport): Path {
    val outputDir = Paths.get("build", "reports", "fundamentals-refresh")
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
