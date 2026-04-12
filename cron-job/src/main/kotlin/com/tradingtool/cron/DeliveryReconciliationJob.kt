package com.tradingtool.cron

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.tradingtool.core.config.ConfigLoader
import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.delivery.config.DeliveryConfigService
import com.tradingtool.core.delivery.config.DeliveryUniverseService
import com.tradingtool.core.delivery.reconciliation.DeliveryDateReconciliationResult
import com.tradingtool.core.delivery.reconciliation.DeliveryReconciliationRunReport
import com.tradingtool.core.delivery.reconciliation.DeliveryReconciliationRunReportFactory
import com.tradingtool.core.delivery.reconciliation.DeliveryReconciliationService
import com.tradingtool.core.delivery.reconciliation.toMarkdown
import com.tradingtool.core.delivery.reconciliation.toTelegramSummary
import com.tradingtool.core.delivery.source.NseDeliverySourceAdapter
import com.tradingtool.core.http.HttpClientConfig
import com.tradingtool.core.http.JdkHttpClientImpl
import com.tradingtool.core.http.JsonHttpClient
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.KiteConfig
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.kite.KiteTokenReadDao
import com.tradingtool.core.kite.KiteTokenWriteDao
import com.tradingtool.core.model.DatabaseConfig
import com.tradingtool.core.stock.dao.StockReadDao
import com.tradingtool.core.stock.dao.StockWriteDao
import com.tradingtool.core.delivery.dao.StockDeliveryReadDao
import com.tradingtool.core.delivery.dao.StockDeliveryWriteDao
import com.tradingtool.core.telegram.TelegramApiClient
import com.tradingtool.core.telegram.TelegramNotifier
import com.tradingtool.core.telegram.TelegramSender
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.net.http.HttpClient as JdkHttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("DeliveryReconciliationJob")

fun main(args: Array<String>) {
    val cli = parseArgs(args)
    val runtime = DeliveryReconciliationRuntime.fromEnvironment()
    val jobName = "DeliveryReconciliationJob"

    runBlocking {
        runtime.telegramNotifier.cronStarted(jobName)
        val exitCode = runCatching {
            val report = executeDeliveryReconciliation(runtime.service, cli.requestedDate)
            val outputDir = writeArtifacts(report)
            runtime.telegramNotifier.cronCompleted(jobName, report.toTelegramSummary())
            log.info(
                "Delivery reconciliation completed: resolvedDate={} expected={} present={} missingFromSource={} artifacts={}",
                report.resolvedDate,
                report.expectedSymbolCount,
                report.presentCount,
                report.missingFromSourceCount,
                outputDir.toAbsolutePath(),
            )
            if (report.unresolvedIssues.isEmpty()) 0 else 1
        }.getOrElse { error ->
            log.error("Delivery reconciliation failed: {}", error.message, error)
            runtime.telegramNotifier.cronFailed(
                jobName,
                error as? Exception ?: RuntimeException(error.message ?: "Unknown failure", error),
            )
            1
        }
        exitProcess(exitCode)
    }
}

private data class DeliveryReconciliationCliArgs(
    val requestedDate: LocalDate? = null,
)

private data class DeliveryReconciliationRuntime(
    val service: DeliveryReconciliationService,
    val telegramNotifier: TelegramNotifier,
) {
    companion object {
        fun fromEnvironment(): DeliveryReconciliationRuntime {
            val databaseConfig = DatabaseConfig(
                jdbcUrl = ConfigLoader.get("SUPABASE_DB_URL", "supabase.dbUrl"),
            )
            val httpClient = JdkHttpClientImpl(
                JdkHttpClient.newBuilder().build(),
                HttpClientConfig(),
            )
            val objectMapper = buildObjectMapper()
            val stockHandler = JdbiHandler(databaseConfig, StockReadDao::class.java, StockWriteDao::class.java)
            val deliveryHandler = JdbiHandler(databaseConfig, StockDeliveryReadDao::class.java, StockDeliveryWriteDao::class.java)
            val tokenHandler = JdbiHandler(databaseConfig, KiteTokenReadDao::class.java, KiteTokenWriteDao::class.java)
            val deliveryConfigService = DeliveryConfigService()
            val service = DeliveryReconciliationService(
                configService = deliveryConfigService,
                deliveryUniverseService = DeliveryUniverseService(
                    configService = deliveryConfigService,
                    stockHandler = stockHandler,
                ),
                stockHandler = stockHandler,
                deliveryHandler = deliveryHandler,
                instrumentCache = InstrumentCache(),
                kiteClient = buildAuthenticatedKiteClient(tokenHandler),
                sourceAdapter = NseDeliverySourceAdapter(
                    JsonHttpClient(
                        httpClient = httpClient,
                        objectMapper = objectMapper,
                    ),
                ),
            )

            return DeliveryReconciliationRuntime(
                service = service,
                telegramNotifier = buildTelegramNotifier(httpClient, objectMapper),
            )
        }
    }
}

private suspend fun executeDeliveryReconciliation(
    service: DeliveryReconciliationService,
    requestedDate: LocalDate?,
): DeliveryReconciliationRunReport {
    val reconciliationResult = if (requestedDate != null) {
        service.reconcileDate(requestedDate)
    } else {
        service.reconcileLatestAvailableDate()
            ?: error("No latest NSE delivery date could be discovered.")
    }

    return buildRunReport(service, requestedDate, reconciliationResult)
}

private suspend fun buildRunReport(
    service: DeliveryReconciliationService,
    requestedDate: LocalDate?,
    reconciliationResult: DeliveryDateReconciliationResult,
): DeliveryReconciliationRunReport {
    val rows = service.findConfiguredRowsForDate(reconciliationResult.tradingDate)
    return DeliveryReconciliationRunReportFactory.create(
        requestedDate = requestedDate,
        result = reconciliationResult,
        rows = rows,
    )
}

private fun parseArgs(args: Array<String>): DeliveryReconciliationCliArgs {
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

    val requestedDate = values["date"]?.let { value ->
        runCatching { LocalDate.parse(value) }.getOrElse {
            error("Invalid --date value '$value'. Expected YYYY-MM-DD.")
        }
    }
    return DeliveryReconciliationCliArgs(requestedDate = requestedDate)
}

private fun writeArtifacts(report: DeliveryReconciliationRunReport): Path {
    val outputDir = Paths.get("build", "reports", "delivery-reconciliation")
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
