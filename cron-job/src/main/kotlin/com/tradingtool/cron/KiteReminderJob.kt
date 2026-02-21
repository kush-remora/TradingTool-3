package com.tradingtool.cron

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

/**
 * Standalone job: sends the daily Kite login URL to Telegram and wakes Render.
 *
 * Run by GitHub Actions every weekday at 6:05 AM IST via:
 *   mvn -pl cron-job -am compile exec:java
 *
 * Required env vars: KITE_API_KEY, TELEGRAM_BOT_TOKEN, TELEGRAM_CHAT_ID, RENDER_SERVICE_URL
 */
fun main() {
    val apiKey = requireEnv("KITE_API_KEY")
    val botToken = requireEnv("TELEGRAM_BOT_TOKEN")
    val chatId = requireEnv("TELEGRAM_CHAT_ID")
    val renderUrl = requireEnv("RENDER_SERVICE_URL")

    val loginUrl = "https://kite.zerodha.com/connect/login?v=3&api_key=$apiKey"
    val wakeUrl = "$renderUrl/health"

    val executor = buildExecutor()
    val telegramSender = buildTelegramSender(botToken, chatId, executor)

    // Telegram auto-links bare URLs — no parse_mode needed
    val message = """
        Good morning! Kite authentication required for today.

        Login: $loginUrl

        Wake server anytime: $wakeUrl
    """.trimIndent()

    runBlocking {
        telegramSender.sendText(TelegramSendTextRequest(message))
        println("Telegram reminder sent.")

        wakeRenderService(wakeUrl, executor)
        println("Render wake-up ping sent.")
    }
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

private fun buildExecutor(): JdkHttpRequestExecutor {
    return JdkHttpRequestExecutor(HttpClient.newBuilder().build())
}

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

private fun requireEnv(name: String): String {
    return System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: error("Required environment variable '$name' is not set.")
}
