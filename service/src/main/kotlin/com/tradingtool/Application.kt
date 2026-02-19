package com.tradingtool

import com.tradingtool.config.AppConfig
import com.tradingtool.config.loadAppConfig
import com.tradingtool.core.telegram.TelegramSender
import com.tradingtool.core.watchlist.dal.JdbiWatchlistDal
import com.tradingtool.core.watchlist.dal.WatchlistDatabaseConfig
import com.tradingtool.core.watchlist.service.WatchlistService
import com.tradingtool.telegram.registerTelegramResource
import com.tradingtool.watchlist.registerWatchlistResource
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.http.ContentType
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.net.URI


fun main() {
    val appConfig: AppConfig = loadAppConfig()
    embeddedServer(
        factory = Netty,
        port = appConfig.server.port,
        host = appConfig.server.host,
        module = {
            module(appConfig)
        },
    ).start(wait = true)
}

fun Application.module(appConfig: AppConfig = loadAppConfig()) {
    val telegramSender = TelegramSender(
        botToken = appConfig.telegram.botToken,
        chatId = appConfig.telegram.chatId,
    )
    val watchlistService = WatchlistService(
        dao = JdbiWatchlistDal(
            config = WatchlistDatabaseConfig(
                jdbcUrl = appConfig.supabase.dbUrl,
                user = appConfig.supabase.dbUser,
                password = appConfig.supabase.dbPassword,
            ),
        ),
    )

    monitor.subscribe(ApplicationStopped) {
        telegramSender.close()
    }

    install(ContentNegotiation) {
        json()
    }
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Accept)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowCredentials = true
        allowNonSimpleContentTypes = true

        appConfig.cors.allowedOrigins.forEach { origin ->
            val uri = runCatching { URI(origin) }.getOrNull() ?: return@forEach
            val scheme = uri.scheme ?: return@forEach
            val host = uri.host ?: return@forEach
            val hostWithPort = if (uri.port == -1) host else "$host:${uri.port}"
            allowHost(host = hostWithPort, schemes = listOf(scheme))
        }
    }

    routing {
        get("/") {
            call.respond(mapOf("service" to appConfig.service.name, "status" to "ok"))
        }
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }
        get("/health/config") {
            call.respondText(
                text = """
                    {
                      "status":"ok",
                      "telegramBotTokenConfigured":${appConfig.telegram.botToken.isNotBlank()},
                      "telegramWebhookSecretConfigured":${appConfig.telegram.webhookSecret.isNotBlank()},
                      "supabaseUrlConfigured":${appConfig.supabase.url.isNotBlank()},
                      "supabaseKeyConfigured":${appConfig.supabase.key.isNotBlank()},
                      "supabaseDbUrlConfigured":${appConfig.supabase.dbUrl.isNotBlank()},
                      "supabaseDbUserConfigured":${appConfig.supabase.dbUser.isNotBlank()},
                      "supabaseDbPasswordConfigured":${appConfig.supabase.dbPassword.isNotBlank()},
                      "renderExternalUrlConfigured":${appConfig.deployment.renderExternalUrl.isNotBlank()},
                      "githubPagesUrlConfigured":${appConfig.deployment.githubPagesUrl.isNotBlank()},
                      "corsOriginsCount":${appConfig.cors.allowedOrigins.size}
                    }
                """.trimIndent(),
                contentType = ContentType.Application.Json,
            )
        }

        registerTelegramResource(telegramSender = telegramSender)
        registerWatchlistResource(watchlistService = watchlistService)
    }
}
