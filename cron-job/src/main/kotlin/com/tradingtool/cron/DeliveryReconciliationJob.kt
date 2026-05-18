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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.net.http.HttpClient as JdkHttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.DayOfWeek
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
            if (report.blockingIssues.isEmpty()) 0 else 1
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
    val latestAvailableDate = service.latestAvailableTradingDate()
    val anchorDate = latestAvailableDate ?: LocalDate.now().minusDays(1)

    if (requestedDate == null) {
        val backfillSummary = backfillRecentTradingDays(
            service = service,
            anchorDate = anchorDate,
            requiredTradingDays = RECENT_TRADING_DAYS_BACKFILL,
        )
        log.info(
            "Delivery backfill summary: checked={} reconciled={} alreadyComplete={} failed={}",
            backfillSummary.checkedDates,
            backfillSummary.reconciledDates,
            backfillSummary.alreadyCompleteDates,
            backfillSummary.failedDates,
        )
    }

    val reconciliationResult = if (requestedDate != null) {
        service.reconcileDate(requestedDate)
    } else {
        service.reconcileDate(anchorDate)
    }

    return buildRunReport(service, requestedDate, reconciliationResult)
}

private data class DeliveryBackfillSummary(
    val checkedDates: Int,
    val reconciledDates: Int,
    val alreadyCompleteDates: Int,
    val failedDates: Int,
)

private enum class DeliveryBackfillOutcome {
    RECONCILED,
    ALREADY_COMPLETE,
    FAILED,
}

private suspend fun backfillRecentTradingDays(
    service: DeliveryReconciliationService,
    anchorDate: LocalDate,
    requiredTradingDays: Int,
): DeliveryBackfillSummary = coroutineScope {
    val targetDates = buildRecentTradingDates(anchorDate, requiredTradingDays)
    var reconciled = 0
    var complete = 0
    var failed = 0

    targetDates.chunked(BACKFILL_PARALLEL_BATCH_SIZE).forEach { tradingDateBatch ->
        val outcomes = tradingDateBatch.map { tradingDate ->
            async {
                val alreadyReconciled = runCatching { service.isDateReconciled(tradingDate) }
                    .onFailure { error ->
                        log.warn("Failed to check reconciliation status for {}: {}", tradingDate, error.message)
                    }
                    .getOrDefault(false)

                if (alreadyReconciled) {
                    return@async DeliveryBackfillOutcome.ALREADY_COMPLETE
                }

                runCatching { service.reconcileDate(tradingDate) }
                    .onFailure { error ->
                        log.warn("Failed to reconcile delivery for {}: {}", tradingDate, error.message)
                    }
                    .map { result ->
                        if (result.alreadyComplete) {
                            DeliveryBackfillOutcome.ALREADY_COMPLETE
                        } else {
                            DeliveryBackfillOutcome.RECONCILED
                        }
                    }
                    .getOrElse { DeliveryBackfillOutcome.FAILED }
            }
        }
            .awaitAll()

        outcomes.forEach { outcome ->
            when (outcome) {
                DeliveryBackfillOutcome.RECONCILED -> reconciled++
                DeliveryBackfillOutcome.ALREADY_COMPLETE -> complete++
                DeliveryBackfillOutcome.FAILED -> failed++
            }
        }
    }

    DeliveryBackfillSummary(
        checkedDates = targetDates.size,
        reconciledDates = reconciled,
        alreadyCompleteDates = complete,
        failedDates = failed,
    )
}

private fun buildRecentTradingDates(anchorDate: LocalDate, requiredTradingDays: Int): List<LocalDate> {
    val dates = mutableListOf<LocalDate>()
    var cursor = anchorDate
    while (dates.size < requiredTradingDays) {
        val day = cursor.dayOfWeek
        if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
            dates += cursor
        }
        cursor = cursor.minusDays(1)
    }
    return dates
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

private const val BACKFILL_PARALLEL_BATCH_SIZE: Int = 5
private const val RECENT_TRADING_DAYS_BACKFILL: Int = 365
