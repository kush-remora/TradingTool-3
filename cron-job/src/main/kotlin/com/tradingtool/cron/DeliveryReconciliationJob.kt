package com.tradingtool.cron

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.tradingtool.core.config.ConfigLoader
import com.tradingtool.core.database.JdbiHandler
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
import com.tradingtool.core.indexconstituents.dao.IndexConstituentReadDao
import com.tradingtool.core.indexconstituents.dao.IndexConstituentWriteDao
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
            val report = executeDeliveryReconciliation(
                service = runtime.service,
                requestedDate = cli.requestedDate,
                backfillSettings = runtime.backfillSettings,
            )
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
    val backfillSettings: DeliveryBackfillSettings,
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
            val indexConstituentHandler =
                JdbiHandler(databaseConfig, IndexConstituentReadDao::class.java, IndexConstituentWriteDao::class.java)
            val tokenHandler = JdbiHandler(databaseConfig, KiteTokenReadDao::class.java, KiteTokenWriteDao::class.java)
            val service = DeliveryReconciliationService(
                stockHandler = stockHandler,
                deliveryHandler = deliveryHandler,
                indexConstituentHandler = indexConstituentHandler,
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
                backfillSettings = loadBackfillSettings(),
            )
        }
    }
}

private data class DeliveryBackfillSettings(
    val requiredTradingDays: Int,
    val parallelBatchSize: Int,
    val extraCandidateTradingDays: Int,
    val maxFailedDates: Int,
)

private suspend fun executeDeliveryReconciliation(
    service: DeliveryReconciliationService,
    requestedDate: LocalDate?,
    backfillSettings: DeliveryBackfillSettings,
): DeliveryReconciliationRunReport {
    val latestAvailableDate = service.latestAvailableTradingDate()
    val anchorDate = latestAvailableDate ?: LocalDate.now().minusDays(1)

    if (requestedDate == null) {
        val backfillSummary = backfillRecentTradingDays(
            service = service,
            anchorDate = anchorDate,
            requiredTradingDays = backfillSettings.requiredTradingDays,
            parallelBatchSize = backfillSettings.parallelBatchSize,
            extraCandidateTradingDays = backfillSettings.extraCandidateTradingDays,
        )
        log.info(
            "Delivery backfill summary: covered={} candidates={} skippedUnavailable={} reconciled={} alreadyComplete={} failed={}",
            backfillSummary.checkedDates,
            backfillSummary.candidateDates,
            backfillSummary.skippedUnavailableDates,
            backfillSummary.reconciledDates,
            backfillSummary.alreadyCompleteDates,
            backfillSummary.failedDates,
        )

        require(backfillSummary.checkedDates >= backfillSettings.requiredTradingDays) {
            "Backfill coverage shortfall. required=${backfillSettings.requiredTradingDays}, covered=${backfillSummary.checkedDates}"
        }
        require(backfillSummary.failedDates <= backfillSettings.maxFailedDates) {
            "Backfill failures exceeded threshold. failed=${backfillSummary.failedDates}, maxAllowed=${backfillSettings.maxFailedDates}"
        }
    }

    val reconciliationResult = if (requestedDate != null) {
        service.reconcileDate(requestedDate)
    } else {
        service.reconcileDate(anchorDate)
    }

    return buildRunReport(service, requestedDate, reconciliationResult)
}

private data class DeliveryBackfillSummary(
    val candidateDates: Int,
    val checkedDates: Int,
    val skippedUnavailableDates: Int,
    val reconciledDates: Int,
    val alreadyCompleteDates: Int,
    val failedDates: Int,
)

private enum class DeliveryBackfillOutcome {
    SKIPPED_SOURCE_UNAVAILABLE,
    RECONCILED,
    ALREADY_COMPLETE,
    FAILED,
}

private suspend fun backfillRecentTradingDays(
    service: DeliveryReconciliationService,
    anchorDate: LocalDate,
    requiredTradingDays: Int,
    parallelBatchSize: Int,
    extraCandidateTradingDays: Int,
): DeliveryBackfillSummary = coroutineScope {
    val candidateDates = buildRecentTradingDates(
        anchorDate = anchorDate,
        requiredTradingDays = requiredTradingDays + extraCandidateTradingDays,
    )
    var coveredDates = 0
    var skippedUnavailable = 0
    var reconciled = 0
    var complete = 0
    var failed = 0

    for (tradingDateBatch in candidateDates.chunked(parallelBatchSize)) {
        if (coveredDates >= requiredTradingDays) break

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
                        if (isSourceUnavailableError(error)) {
                            log.info("Skipping unavailable delivery source date {}", tradingDate)
                        } else {
                            log.warn("Failed to reconcile delivery for {}: {}", tradingDate, error.message)
                        }
                    }
                    .map { result ->
                        if (result.alreadyComplete) {
                            DeliveryBackfillOutcome.ALREADY_COMPLETE
                        } else {
                            DeliveryBackfillOutcome.RECONCILED
                        }
                    }
                    .getOrElse { error ->
                        if (isSourceUnavailableError(error)) {
                            DeliveryBackfillOutcome.SKIPPED_SOURCE_UNAVAILABLE
                        } else {
                            DeliveryBackfillOutcome.FAILED
                        }
                    }
            }
        }
            .awaitAll()

        outcomes.forEach { outcome ->
            when (outcome) {
                DeliveryBackfillOutcome.SKIPPED_SOURCE_UNAVAILABLE -> skippedUnavailable++
                DeliveryBackfillOutcome.RECONCILED -> {
                    coveredDates++
                    reconciled++
                }
                DeliveryBackfillOutcome.ALREADY_COMPLETE -> {
                    coveredDates++
                    complete++
                }
                DeliveryBackfillOutcome.FAILED -> {
                    coveredDates++
                    failed++
                }
            }
        }
    }

    DeliveryBackfillSummary(
        candidateDates = candidateDates.size,
        checkedDates = coveredDates,
        skippedUnavailableDates = skippedUnavailable,
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
    val helpRequested = args.any { value -> value == "--help" || value == "-h" }
    if (helpRequested) {
        println("Usage: --date=YYYY-MM-DD")
        exitProcess(0)
    }

    val malformed = args.filter { arg -> !arg.startsWith("--") || !arg.contains("=") }
    require(malformed.isEmpty()) {
        "Invalid arguments: ${malformed.joinToString(", ")}. Usage: --date=YYYY-MM-DD"
    }

    val values = args
        .mapNotNull { arg ->
            val key = arg.substringAfter("--").substringBefore("=")
            val value = arg.substringAfter("=")
            key to value
        }
        .toMap()

    val allowedKeys = setOf("date")
    val unknownKeys = values.keys.filter { key -> key !in allowedKeys }
    require(unknownKeys.isEmpty()) {
        "Unknown arguments: ${unknownKeys.joinToString(", ")}. Allowed: --date=YYYY-MM-DD"
    }

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

private fun loadBackfillSettings(): DeliveryBackfillSettings {
    val requiredTradingDays = readPositiveIntConfig(
        envKey = "DELIVERY_BACKFILL_REQUIRED_TRADING_DAYS",
        yamlKey = "delivery.backfillRequiredTradingDays",
        defaultValue = 365,
    )
    val parallelBatchSize = readPositiveIntConfig(
        envKey = "DELIVERY_BACKFILL_PARALLEL_BATCH_SIZE",
        yamlKey = "delivery.backfillParallelBatchSize",
        defaultValue = 5,
    )
    val extraCandidateTradingDays = readPositiveIntConfig(
        envKey = "DELIVERY_BACKFILL_EXTRA_CANDIDATE_DAYS",
        yamlKey = "delivery.backfillExtraCandidateDays",
        defaultValue = 80,
    )
    val maxFailedDates = readNonNegativeIntConfig(
        envKey = "DELIVERY_BACKFILL_MAX_FAILED_DATES",
        yamlKey = "delivery.backfillMaxFailedDates",
        defaultValue = 2,
    )

    return DeliveryBackfillSettings(
        requiredTradingDays = requiredTradingDays,
        parallelBatchSize = parallelBatchSize,
        extraCandidateTradingDays = extraCandidateTradingDays,
        maxFailedDates = maxFailedDates,
    )
}

private fun readPositiveIntConfig(
    envKey: String,
    yamlKey: String,
    defaultValue: Int,
): Int {
    val raw = ConfigLoader.getOptional(envKey, yamlKey)?.trim().orEmpty()
    if (raw.isBlank()) return defaultValue
    return raw.toIntOrNull()?.takeIf { value -> value > 0 } ?: run {
        log.warn("Invalid positive integer config {}='{}'. Using default={}", envKey, raw, defaultValue)
        defaultValue
    }
}

private fun readNonNegativeIntConfig(
    envKey: String,
    yamlKey: String,
    defaultValue: Int,
): Int {
    val raw = ConfigLoader.getOptional(envKey, yamlKey)?.trim().orEmpty()
    if (raw.isBlank()) return defaultValue
    return raw.toIntOrNull()?.takeIf { value -> value >= 0 } ?: run {
        log.warn("Invalid non-negative integer config {}='{}'. Using default={}", envKey, raw, defaultValue)
        defaultValue
    }
}

private fun isSourceUnavailableError(error: Throwable): Boolean {
    val message = error.message?.lowercase().orEmpty()
    return message.contains("failed to download file") && message.contains("http 404")
}
