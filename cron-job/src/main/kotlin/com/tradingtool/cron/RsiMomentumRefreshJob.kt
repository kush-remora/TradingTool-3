package com.tradingtool.cron

import com.tradingtool.core.config.ConfigLoader
import com.tradingtool.core.http.HttpClientConfig
import com.tradingtool.core.http.JdkHttpClientImpl
import com.tradingtool.core.http.Result
import com.tradingtool.core.telegram.TelegramApiClient
import com.tradingtool.core.telegram.TelegramNotifier
import com.tradingtool.core.telegram.TelegramSender
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.net.http.HttpClient as JdkHttpClient
import java.time.Duration
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("RsiMomentumRefreshJob")

private data class StrategyRefreshConfig(
    val renderUrl: String,
    val telegramBotToken: String?,
    val telegramChatId: String?,
)

private object StrategyRefreshConstants {
    const val HEALTH_PATH = "/health"
    const val REFRESH_PATH = "/api/strategy/rsi-momentum/refresh"
    const val REFRESH_TIMEOUT_SECONDS = 180L
}

fun main() {
    val config = StrategyRefreshConfig(
        renderUrl = ConfigLoader.get("RENDER_SERVICE_URL", "deployment.renderExternalUrl"),
        telegramBotToken = ConfigLoader.getOptional("TELEGRAM_BOT_TOKEN", "telegram.botToken"),
        telegramChatId = ConfigLoader.getOptional("TELEGRAM_CHAT_ID", "telegram.chatId"),
    )
    val httpClient = JdkHttpClientImpl(
        JdkHttpClient.newBuilder().build(),
        HttpClientConfig(
            timeout = Duration.ofSeconds(StrategyRefreshConstants.REFRESH_TIMEOUT_SECONDS),
            retryConfig = HttpClientConfig.RetryConfig(maxAttempts = 2, initialDelayMs = 500),
        ),
    )
    val notifierHttpClient = JdkHttpClientImpl(JdkHttpClient.newBuilder().build(), HttpClientConfig())
    val telegramNotifier = buildTelegramNotifier(config.telegramBotToken, config.telegramChatId, notifierHttpClient)
    val jobName = "RsiMomentumRefreshJob"

    runBlocking {
        telegramNotifier.cronStarted(jobName)
        wakeService(config.renderUrl, httpClient)

        when (val response = httpClient.post(config.renderUrl.toRefreshUrl())) {
            is Result.Success -> {
                log.info("RSI momentum refresh completed successfully: {}", response.data)
                telegramNotifier.cronCompleted(jobName)
                exitProcess(0)
            }
            is Result.Failure -> {
                log.error("RSI momentum refresh failed: {}", response.error.describe())
                val failure = RuntimeException(response.error.describe())
                telegramNotifier.cronFailed(jobName, failure)
                exitProcess(1)
            }
        }
    }
}

private fun buildTelegramNotifier(
    botToken: String?,
    chatId: String?,
    httpClient: JdkHttpClientImpl,
): TelegramNotifier {
    val apiClient = TelegramApiClient(
        botToken = botToken.orEmpty(),
        chatId = chatId.orEmpty(),
        httpClient = httpClient,
        objectMapper = ObjectMapper().registerKotlinModule(),
    )
    return TelegramNotifier(TelegramSender(apiClient))
}

private suspend fun wakeService(
    renderUrl: String,
    httpClient: JdkHttpClientImpl,
) {
    when (val response = httpClient.get(renderUrl.toHealthUrl())) {
        is Result.Success -> log.info("Wake check succeeded")
        is Result.Failure -> log.warn("Wake check failed: {}", response.error.describe())
    }
}

private fun String.toHealthUrl(): String = this.trimEnd('/') + StrategyRefreshConstants.HEALTH_PATH

private fun String.toRefreshUrl(): String = this.trimEnd('/') + StrategyRefreshConstants.REFRESH_PATH
