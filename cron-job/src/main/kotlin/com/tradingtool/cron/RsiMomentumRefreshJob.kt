package com.tradingtool.cron

import com.tradingtool.core.config.ConfigLoader
import com.tradingtool.core.http.SuspendHttpClient
import com.tradingtool.core.strategy.rsimomentum.RsiMomentumRuntime
import com.tradingtool.core.telegram.TelegramApiClient
import com.tradingtool.core.telegram.TelegramNotifier
import com.tradingtool.core.telegram.TelegramSender
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.net.http.HttpClient as JdkHttpClient
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("RsiMomentumRefreshJob")

private data class StrategyRefreshConfig(
    val telegramBotToken: String?,
    val telegramChatId: String?,
)

fun main() {
    val config = StrategyRefreshConfig(
        telegramBotToken = ConfigLoader.getOptional("TELEGRAM_BOT_TOKEN", "telegram.botToken"),
        telegramChatId = ConfigLoader.getOptional("TELEGRAM_CHAT_ID", "telegram.chatId"),
    )
    val notifierHttpClient = com.tradingtool.core.http.JdkHttpClientImpl(
        JdkHttpClient.newBuilder().build(),
        com.tradingtool.core.http.HttpClientConfig(),
    )
    val telegramNotifier = buildTelegramNotifier(
        config.telegramBotToken,
        config.telegramChatId,
        notifierHttpClient,
    )
    val jobName = "RsiMomentumRefreshJob"

    runBlocking {
        telegramNotifier.cronStarted(jobName)
        val exitCode = runCatching {
            RsiMomentumRuntime.fromEnvironment().use { runtime ->
                val snapshot = runtime.service.refreshLatest()
                log.info(
                    "RSI momentum refresh completed successfully: candidates={}, holdings={}, stale={}",
                    snapshot.topCandidates.size,
                    snapshot.holdings.size,
                    snapshot.stale,
                )
                telegramNotifier.cronCompleted(jobName)
                0
            }
        }.getOrElse { error ->
            log.error("RSI momentum refresh failed: {}", error.message, error)
            telegramNotifier.cronFailed(jobName, error as? Exception ?: RuntimeException(error.message ?: "Unknown failure", error))
            1
        }
        exitProcess(exitCode)
    }
}

private fun buildTelegramNotifier(
    botToken: String?,
    chatId: String?,
    httpClient: SuspendHttpClient,
): TelegramNotifier {
    val apiClient = TelegramApiClient(
        botToken = botToken.orEmpty(),
        chatId = chatId.orEmpty(),
        httpClient = httpClient,
        objectMapper = ObjectMapper().registerKotlinModule(),
    )
    return TelegramNotifier(TelegramSender(apiClient))
}
