package com.tradingtool.cron

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.tradingtool.core.config.ConfigLoader
import com.tradingtool.core.config.IndicatorConfig
import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.database.RedisHandler
import com.tradingtool.core.database.RemoraJdbiHandler
import com.tradingtool.core.database.StockIndicatorsJdbiHandler
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.http.HttpClientConfig
import com.tradingtool.core.http.JdkHttpClientImpl
import com.tradingtool.core.kite.KiteConfig
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.kite.KiteTokenReadDao
import com.tradingtool.core.kite.KiteTokenWriteDao
import com.tradingtool.core.model.DatabaseConfig
import com.tradingtool.core.stock.dao.StockIndicatorsReadDao
import com.tradingtool.core.stock.dao.StockIndicatorsWriteDao
import com.tradingtool.core.stock.dao.StockReadDao
import com.tradingtool.core.stock.dao.StockWriteDao
import com.tradingtool.core.strategy.SignalScanner
import com.tradingtool.core.strategy.remora.RemoraSignalReadDao
import com.tradingtool.core.strategy.remora.RemoraSignalWriteDao
import com.tradingtool.core.strategy.remora.RemoraService
import com.tradingtool.core.telegram.TelegramApiClient
import com.tradingtool.core.telegram.TelegramNotifier
import com.tradingtool.core.telegram.TelegramSender
import com.tradingtool.core.watchlist.IndicatorService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.net.http.HttpClient as JdkHttpClient

private val log = LoggerFactory.getLogger("IndicatorsSyncJob")

/**
 * Daily indicator sync — runs at 9:15 AM IST after market open.
 *
 * Scheduled via GitHub Actions (see .github/workflows/). The access token is
 * read from the [kite_tokens] DB table (written there by [KiteReminderJob] after
 * the user completes the Kite OAuth flow). This avoids storing a short-lived daily
 * token in environment variables or config files.
 *
 * To run locally:
 *   mvn -f cron-job/pom.xml clean compile exec:java -Dexec.mainClass=com.tradingtool.cron.IndicatorsSyncJobKt
 */
fun main() {
    val env = ConfigLoader.detect()
    log.info("IndicatorsSyncJob starting in environment: {}", env)

    val dbConfig = DatabaseConfig(resolveDatabaseUrl())
    val redis = RedisHandler.fromEnv()

    val kiteTokenHandler = JdbiHandler(dbConfig, KiteTokenReadDao::class.java, KiteTokenWriteDao::class.java)
    val stockHandler: StockJdbiHandler = JdbiHandler(dbConfig, StockReadDao::class.java, StockWriteDao::class.java)
    val stockIndicatorsHandler: StockIndicatorsJdbiHandler = JdbiHandler(dbConfig, StockIndicatorsReadDao::class.java, StockIndicatorsWriteDao::class.java)
    val remoraHandler: RemoraJdbiHandler = JdbiHandler(dbConfig, RemoraSignalReadDao::class.java, RemoraSignalWriteDao::class.java)

    val indicatorService = IndicatorService(
        stockIndicatorsHandler = stockIndicatorsHandler,
        stockHandler = stockHandler,
        redis = redis,
        config = IndicatorConfig.DEFAULT,
    )

    val telegramSender = buildTelegramSender()
    val notifier = TelegramNotifier(telegramSender)

    val remoraService = RemoraService(
        stockHandler = stockHandler,
        remoraHandler = remoraHandler,
        telegramSender = telegramSender,
        config = IndicatorConfig.DEFAULT,
    )

    // All signal scanners — add new strategies here to include them in the daily run.
    val scanners: List<SignalScanner> = listOf(remoraService)

    try {
        runBlocking {
            notifier.cronStarted("IndicatorsSyncJob")
            try {
                val kiteClient = buildAuthenticatedKiteClient(kiteTokenHandler)
                indicatorService.refreshAll(kiteClient, onlyNeedsRefresh = false)
                scanners.forEach { scanner ->
                    log.info("Running scanner: {}", scanner.name)
                    scanner.scan(kiteClient)
                }
                notifier.cronCompleted("IndicatorsSyncJob", "${scanners.size} scanner(s) run")
            } catch (e: Exception) {
                notifier.cronFailed("IndicatorsSyncJob", e)
                throw e
            }
        }
    } finally {
        redis.close()
        log.info("IndicatorsSyncJob finished.")
    }
}

private fun resolveDatabaseUrl(): String {
    val supabaseUrl = runCatching { ConfigLoader.get("SUPABASE_DB_URL", "supabase.dbUrl") }.getOrNull()
    if (!supabaseUrl.isNullOrBlank()) return supabaseUrl

    // Backward compatibility for deployment environments still using DATABASE_URL.
    return ConfigLoader.get("DATABASE_URL", "database.url")
}

/**
 * Builds a [KiteConnectClient] authenticated with the latest token from the DB.
 *
 * The token is written to [kite_tokens] by the auth flow after the user clicks the
 * Kite login link sent by [KiteReminderJob]. Reading from the DB (not from env vars)
 * keeps short-lived daily tokens out of CI secrets and config files.
 */
private suspend fun buildAuthenticatedKiteClient(
    kiteTokenHandler: JdbiHandler<KiteTokenReadDao, KiteTokenWriteDao>
): KiteConnectClient {
    val kiteConfig = KiteConfig(
        apiKey = ConfigLoader.get("KITE_API_KEY", "kite.apiKey"),
        apiSecret = ConfigLoader.get("KITE_API_SECRET", "kite.apiSecret"),
    )
    val kiteClient = KiteConnectClient(kiteConfig)

    val accessToken = kiteTokenHandler.read { it.getLatestToken() }
        ?: error("No Kite access token found in kite_tokens table. Complete the Kite login flow first.")

    kiteClient.applyAccessToken(accessToken)
    log.info("Kite client authenticated (token from DB).")
    return kiteClient
}

/**
 * Constructs a [TelegramSender] from env vars / localconfig.yaml.
 * If TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID are absent, returns a no-op sender
 * that silently skips notifications rather than crashing the cron job.
 */
private fun buildTelegramSender(): TelegramSender {
    return try {
        val botToken = ConfigLoader.get("TELEGRAM_BOT_TOKEN", "telegram.botToken")
        val chatId = ConfigLoader.get("TELEGRAM_CHAT_ID", "telegram.chatId")
        val httpClient = JdkHttpClientImpl(JdkHttpClient.newBuilder().build(), HttpClientConfig())
        val objectMapper = ObjectMapper().registerKotlinModule()
        val apiClient = TelegramApiClient(
            botToken = botToken,
            chatId = chatId,
            httpClient = httpClient,
            objectMapper = objectMapper,
        )
        TelegramSender(apiClient)
    } catch (e: Exception) {
        log.warn("Telegram not configured ({}) — signals will not be sent.", e.message)
        val apiClient = TelegramApiClient(
            botToken = "",
            chatId = "",
            httpClient = JdkHttpClientImpl(JdkHttpClient.newBuilder().build(), HttpClientConfig()),
            objectMapper = ObjectMapper().registerKotlinModule(),
        )
        TelegramSender(apiClient)
    }
}
