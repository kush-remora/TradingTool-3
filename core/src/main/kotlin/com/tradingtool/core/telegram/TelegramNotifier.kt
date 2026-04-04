package com.tradingtool.core.telegram

import com.google.inject.Singleton
import com.tradingtool.core.model.telegram.TelegramSendTextRequest
import org.slf4j.LoggerFactory
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val log = LoggerFactory.getLogger(TelegramNotifier::class.java)

/**
 * Plug-and-play Telegram alert wrapper for cron job lifecycle notifications and critical errors.
 *
 * Usage:
 *   notifier.cronStarted("KiteReminderJob")
 *   notifier.cronCompleted("KiteReminderJob", "Sent 15 reminders")
 *   notifier.cronFailed("KiteReminderJob", exception)
 *   notifier.criticalError("KiteConnectClient", exception)
 *
 * Silently no-ops if Telegram is not configured. Never throws.
 */
@Singleton
class TelegramNotifier(private val sender: TelegramSender) {

    suspend fun cronStarted(jobName: String) =
        notify("▶ [$jobName] started at ${now()}")

    suspend fun cronCompleted(jobName: String, summary: String = "") {
        val msg = buildString {
            append("✅ [$jobName] completed at ${now()}")
            if (summary.isNotBlank()) append("\n$summary")
        }
        notify(msg)
    }

    suspend fun cronFailed(jobName: String, error: Throwable) =
        notify("❌ [$jobName] FAILED at ${now()}\n${error.message ?: "Unknown error"}")

    suspend fun criticalError(context: String, error: Throwable) =
        notify("🚨 CRITICAL in [$context] at ${now()}\n${error.message ?: "Unknown error"}")

    private suspend fun notify(text: String) {
        if (!sender.isConfigured()) return
        runCatching { sender.sendText(TelegramSendTextRequest(text)) }
            .onFailure { log.warn("TelegramNotifier failed to send: {}", it.message) }
    }

    private fun now(): String = ZonedDateTime.now(IST_ZONE).format(TIME_FORMATTER)

    companion object {
        private val IST_ZONE = ZoneId.of("Asia/Kolkata")
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MMM HH:mm z")
    }
}
