package com.tradingtool.cron

import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Provides
import com.google.inject.Singleton
import com.google.inject.name.Named
import com.tradingtool.core.config.ConfigLoader
import com.tradingtool.core.http.CoreHttpModule
import com.tradingtool.core.model.telegram.TelegramSendStatus
import com.tradingtool.core.model.telegram.TelegramSendTextRequest
import com.tradingtool.core.telegram.TelegramSender
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("KiteCronSmokeTestJob")

private object SmokeConstants {
    val IST_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
    val UTC_ZONE: ZoneId = ZoneId.of("UTC")
    val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
}

fun main() {
    val runEnvironment: ConfigLoader.RunEnvironment = ConfigLoader.detect()
    log.info("Starting KiteCronSmokeTestJob in environment: {}", runEnvironment)

    val injector = Guice.createInjector(
        CoreHttpModule(),
        SmokeTelegramModule(
            botToken = ConfigLoader.get("TELEGRAM_BOT_TOKEN", "telegram.botToken"),
            chatId = ConfigLoader.get("TELEGRAM_CHAT_ID", "telegram.chatId"),
        ),
    )
    val telegramSender: TelegramSender = injector.getInstance(TelegramSender::class.java)

    val nowIst: ZonedDateTime = ZonedDateTime.now(SmokeConstants.IST_ZONE)
    val nowUtc: ZonedDateTime = ZonedDateTime.now(SmokeConstants.UTC_ZONE)
    val messageSummary = buildSmokeSummary(nowIst, nowUtc, runEnvironment)

    runBlocking {
        val sendResult = telegramSender.sendText(TelegramSendTextRequest(messageSummary))
        if (sendResult.status == TelegramSendStatus.SUCCESS) {
            log.info("Kite cron smoke test completed successfully")
            exitProcess(0)
        }
        log.error(
            "Smoke test message failed. status={}, description={}",
            sendResult.status,
            sendResult.response.telegramDescription ?: sendResult.response.message,
        )
        exitProcess(1)
    }
}

private fun buildSmokeSummary(
    nowIst: ZonedDateTime,
    nowUtc: ZonedDateTime,
    runEnvironment: ConfigLoader.RunEnvironment,
): String =
    """
        Smoke test passed.
        Kotlin main invocation: OK
        Core dependency injection: OK
        Environment: $runEnvironment
        Time IST: ${nowIst.format(SmokeConstants.DATE_FORMATTER)}
        Time UTC: ${nowUtc.format(SmokeConstants.DATE_FORMATTER)}
    """.trimIndent()

private class SmokeTelegramModule(
    private val botToken: String,
    private val chatId: String,
) : AbstractModule() {
    @Provides
    @Singleton
    @Named("telegramBotToken")
    fun provideTelegramBotToken(): String = botToken

    @Provides
    @Singleton
    @Named("telegramChatId")
    fun provideTelegramChatId(): String = chatId
}
