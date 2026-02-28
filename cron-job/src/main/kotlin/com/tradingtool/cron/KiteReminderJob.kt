package com.tradingtool.cron

import com.tradingtool.core.config.ConfigLoader
import com.tradingtool.core.http.HttpRequestData
import com.tradingtool.core.http.JdkHttpRequestExecutor
import com.tradingtool.core.model.telegram.TelegramSendTextRequest
import com.tradingtool.core.telegram.TelegramApiClient
import com.tradingtool.core.telegram.TelegramSender
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration
import kotlin.system.exitProcess

/**
 * Standalone job: sends the daily Kite login URL to Telegram and wakes Render.
 *
 * Run by GitHub Actions every weekday at 6:05 AM IST via:
 *   mvn -pl cron-job -am compile exec:java
 *
 * Config is resolved by [ConfigLoader]: env vars in CI/prod, localconfig.yaml locally.
 */
fun main() {
    println("Environment: ${ConfigLoader.detect()}")

    val apiKey    = ConfigLoader.get("KITE_API_KEY",         "kite.apiKey")
    val botToken  = ConfigLoader.get("TELEGRAM_BOT_TOKEN",   "telegram.botToken")
    val chatId    = ConfigLoader.get("TELEGRAM_CHAT_ID",     "telegram.chatId")
    val renderUrl = ConfigLoader.get("RENDER_SERVICE_URL",   "deployment.renderExternalUrl")

    val loginUrl = "https://kite.zerodha.com/connect/login?v=3&api_key=$apiKey"
    val wakeUrl  = "$renderUrl/health"

    val executor       = buildExecutor()
    val telegramSender = buildTelegramSender(botToken, chatId, executor)

    // Telegram auto-links bare URLs — no parse_mode needed
    val message = """
        Good morning! Kite authentication required for today.

        Login: $loginUrl

        Wake server anytime: $wakeUrl
    """.trimIndent()

    runBlocking {
        // Wake Render first — token callback will hit a live server
        wakeRenderService(wakeUrl, executor)
        println("Render wake-up ping sent.")

        // Only notify after the server is confirmed awake
        telegramSender.sendText(TelegramSendTextRequest(message))
        println("Telegram reminder sent.")
    }
    exitProcess(0)
}

// Render cold starts can take ~60s — generous timeout + existing retry logic handles it
private suspend fun wakeRenderService(wakeUrl: String, executor: JdkHttpRequestExecutor) {
    val response = executor.execute(
        HttpRequestData(
            method = "GET",
            uri = URI.create(wakeUrl),
            timeout = Duration.ofSeconds(90),
        )
    )
    println("Render responded with status: ${response.statusCode}")
}

private fun buildExecutor(): JdkHttpRequestExecutor =
    JdkHttpRequestExecutor(HttpClient.newBuilder().build())

private fun buildTelegramSender(botToken: String, chatId: String, executor: JdkHttpRequestExecutor): TelegramSender {
    val json = Json { ignoreUnknownKeys = true }
    val apiClient = TelegramApiClient(
        botToken = botToken,
        chatId = chatId,
        httpRequestExecutor = executor,
        json = json,
    )
    return TelegramSender(telegramApiClient = apiClient)
}
