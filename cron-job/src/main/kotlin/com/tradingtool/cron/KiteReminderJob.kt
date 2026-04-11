package com.tradingtool.cron

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.tradingtool.core.config.ConfigLoader
import com.tradingtool.core.http.JdkHttpClientImpl
import com.tradingtool.core.http.HttpClientConfig
import com.tradingtool.core.http.SuspendHttpClient
import com.tradingtool.core.model.telegram.TelegramSendTextRequest
import com.tradingtool.core.telegram.TelegramApiClient
import com.tradingtool.core.telegram.TelegramNotifier
import com.tradingtool.core.telegram.TelegramSender
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.net.http.HttpClient as JdkHttpClient
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("KiteReminderJob")

/**
 * Standalone job: sends the daily Kite login URL to Telegram and wakes Render.
 *
 * Run by GitHub Actions every weekday at 6:05 AM IST via:
 *   mvn -pl cron-job -am compile exec:java
 *
 * Config is resolved by [ConfigLoader]: env vars in CI/prod, localconfig.yaml locally.
 */
fun main() {
    log.info("Environment: {}", ConfigLoader.detect())

    val config = loadKiteReminderConfig()
    val httpClient = buildHttpClient()
    val telegramNotifier = TelegramNotifier(buildTelegramSender(config.botToken, config.chatId, httpClient))

    runBlocking {
        telegramNotifier.cronStarted("KiteReminderJob")
        val result = executeKiteReminder(config)
        when (result) {
            is ReminderResult.Success -> {
                telegramNotifier.cronCompleted("KiteReminderJob")
                log.info("Kite reminder job completed successfully")
                exitProcess(0)
            }
            is ReminderResult.Failure -> {
                telegramNotifier.cronFailed("KiteReminderJob", result.error)
                log.error("Kite reminder job failed: {}", result.error.message, result.error)
                exitProcess(1)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Configuration & Result Types
// ─────────────────────────────────────────────────────────────

data class KiteReminderConfig(
    val apiKey: String,
    val botToken: String,
    val chatId: String,
    val renderUrl: String,
)

sealed class ReminderResult {
    data object Success : ReminderResult()
    data class Failure(val error: Exception) : ReminderResult()
}

private object KiteReminderConstants {
    const val KITE_LOGIN_BASE_URL = "https://kite.zerodha.com/connect/login"
    const val KITE_API_VERSION = "3"
    const val RENDER_HEALTH_ENDPOINT = "/health"
    const val RENDER_WAKE_TIMEOUT_SECONDS = 90L
}

// ─────────────────────────────────────────────────────────────
// Configuration Loading
// ─────────────────────────────────────────────────────────────

private fun loadKiteReminderConfig(): KiteReminderConfig =
    KiteReminderConfig(
        apiKey = ConfigLoader.get("KITE_API_KEY", "kite.apiKey"),
        botToken = ConfigLoader.get("TELEGRAM_BOT_TOKEN", "telegram.botToken"),
        chatId = ConfigLoader.get("TELEGRAM_CHAT_ID", "telegram.chatId"),
        renderUrl = ConfigLoader.get("RENDER_SERVICE_URL", "deployment.renderExternalUrl"),
    )

// ─────────────────────────────────────────────────────────────
// Main Execution Orchestration
// ─────────────────────────────────────────────────────────────

private suspend fun executeKiteReminder(config: KiteReminderConfig): ReminderResult =
    try {
        val httpClient = buildHttpClient()
        val telegramSender = buildTelegramSender(config.botToken, config.chatId, httpClient)

        wakeRenderService(config.renderUrl, httpClient)
        log.info("Render wake-up ping sent.")

        sendReminderMessage(telegramSender, config)
        log.info("Telegram reminder sent.")

        ReminderResult.Success
    } catch (e: Exception) {
        ReminderResult.Failure(e)
    }

// ─────────────────────────────────────────────────────────────
// Service Interactions
// ─────────────────────────────────────────────────────────────

private suspend fun wakeRenderService(renderUrl: String, httpClient: SuspendHttpClient) {
    val wakeUrl = renderUrl.toRenderWakeUrl()
    httpClient.get(wakeUrl)
        .onSuccess { _ ->
            log.info("Render responded successfully")
        }
        .onFailure { error ->
            log.warn("Render wake-up failed: {}", error.describe())
        }
}

private suspend fun sendReminderMessage(
    telegramSender: TelegramSender,
    config: KiteReminderConfig,
) {
    val loginUrl = config.apiKey.toKiteLoginUrl()
    val wakeUrl = config.renderUrl.toRenderWakeUrl()
    val message = buildReminderMessage(loginUrl, wakeUrl)

    telegramSender.sendText(TelegramSendTextRequest(message))
}

// ─────────────────────────────────────────────────────────────
// Builder Functions
// ─────────────────────────────────────────────────────────────

private fun buildHttpClient(): SuspendHttpClient =
    JdkHttpClientImpl(JdkHttpClient.newBuilder().build(), HttpClientConfig())

private fun buildTelegramSender(
    botToken: String,
    chatId: String,
    httpClient: SuspendHttpClient,
): TelegramSender =
    TelegramApiClient(
        botToken = botToken,
        chatId = chatId,
        httpClient = httpClient,
        objectMapper = ObjectMapper().registerKotlinModule(),
    ).let { apiClient ->
        TelegramSender(telegramApiClient = apiClient)
    }

// ─────────────────────────────────────────────────────────────
// Extension Functions & Helpers
// ─────────────────────────────────────────────────────────────

private fun String.toKiteLoginUrl(): String =
    "${KiteReminderConstants.KITE_LOGIN_BASE_URL}?v=${KiteReminderConstants.KITE_API_VERSION}&api_key=$this"

private fun String.toRenderWakeUrl(): String =
    this + KiteReminderConstants.RENDER_HEALTH_ENDPOINT

private fun buildReminderMessage(loginUrl: String, wakeUrl: String): String =
    """
        Good morning! Kite authentication required for today.

        Login: $loginUrl

        Wake server anytime: $wakeUrl
    """.trimIndent()
