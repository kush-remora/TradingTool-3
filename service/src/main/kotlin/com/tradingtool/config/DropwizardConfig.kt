package com.tradingtool.config

import com.fasterxml.jackson.annotation.JsonProperty
import io.dropwizard.core.Configuration
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull

class DropwizardConfig : Configuration() {
    @JsonProperty("service")
    @Valid
    @NotNull
    var service: DropwizardServiceConfig = DropwizardServiceConfig()

    @JsonProperty("cors")
    @Valid
    @NotNull
    var cors: DropwizardCorsConfig = DropwizardCorsConfig()

    @JsonProperty("telegram")
    @Valid
    @NotNull
    var telegram: DropwizardTelegramConfig = DropwizardTelegramConfig()

    @JsonProperty("supabase")
    @Valid
    @NotNull
    var supabase: DropwizardSupabaseConfig = DropwizardSupabaseConfig()

    @JsonProperty("deployment")
    @Valid
    @NotNull
    var deployment: DropwizardDeploymentConfig = DropwizardDeploymentConfig()

    @JsonProperty("kite")
    @Valid
    @NotNull
    var kite: DropwizardKiteConfig = DropwizardKiteConfig()

    fun toAppConfig(): AppConfig {
        val defaultAllowedOrigins = "https://kushb2.github.io,http://localhost:5173,http://127.0.0.1:5173"
        return AppConfig(
            server = ServerConfig(
                host = "0.0.0.0",
                port = 8080,
            ),
            service = ServiceConfig(name = service.name ?: "TradingTool-3"),
            cors = CorsConfig(
                allowedOrigins = parseAllowedOrigins(cors.allowedOrigins ?: defaultAllowedOrigins),
            ),
            telegram = TelegramConfig(
                botToken = telegram.botToken ?: "",
                chatId = telegram.chatId ?: "",
                webhookSecret = telegram.webhookSecret ?: "",
                downloadDir = telegram.downloadDir ?: "data/telegram_downloads",
                pollTimeoutSeconds = telegram.pollTimeoutSeconds,
                requestTimeoutSeconds = telegram.requestTimeoutSeconds,
                errorRetrySleepSeconds = telegram.errorRetrySleepSeconds,
                maxRetrySleepSeconds = telegram.maxRetrySleepSeconds,
            ),
            supabase = SupabaseConfig(dbUrl = supabase.dbUrl ?: ""),
            deployment = DeploymentConfig(
                renderExternalUrl = deployment.renderExternalUrl ?: "",
                githubPagesUrl = deployment.githubPagesUrl ?: "https://kushb2.github.io/TradingTool-3/",
            ),
            kite = KiteConfig(
                apiKey = kite.apiKey ?: "",
                apiSecret = kite.apiSecret ?: "",
                accessToken = kite.accessToken ?: "",
            ),
        )
    }

    private fun parseAllowedOrigins(rawValue: String): List<String> {
        if (rawValue.isBlank()) {
            return listOf(
                "https://kushb2.github.io",
                "http://localhost:5173",
                "http://127.0.0.1:5173",
            )
        }

        return rawValue.split(",")
            .map { item -> item.trim() }
            .filter { item -> item.isNotEmpty() }
    }
}

class DropwizardServiceConfig {
    @JsonProperty("name")
    var name: String? = "TradingTool-3"
}

class DropwizardCorsConfig {
    @JsonProperty("allowedOrigins")
    var allowedOrigins: String? =
        "https://kushb2.github.io,http://localhost:5173,http://127.0.0.1:5173"
}

class DropwizardTelegramConfig {
    @JsonProperty("botToken")
    var botToken: String? = ""

    @JsonProperty("chatId")
    var chatId: String? = ""

    @JsonProperty("webhookSecret")
    var webhookSecret: String? = ""

    @JsonProperty("downloadDir")
    var downloadDir: String? = "data/telegram_downloads"

    @JsonProperty("pollTimeoutSeconds")
    var pollTimeoutSeconds: Int = 60

    @JsonProperty("requestTimeoutSeconds")
    var requestTimeoutSeconds: Int = 75

    @JsonProperty("errorRetrySleepSeconds")
    var errorRetrySleepSeconds: Int = 1

    @JsonProperty("maxRetrySleepSeconds")
    var maxRetrySleepSeconds: Int = 30
}

class DropwizardSupabaseConfig {
    @JsonProperty("dbUrl")
    var dbUrl: String? = ""
}

class DropwizardDeploymentConfig {
    @JsonProperty("renderExternalUrl")
    var renderExternalUrl: String? = ""

    @JsonProperty("githubPagesUrl")
    var githubPagesUrl: String? = "https://kushb2.github.io/TradingTool-3/"
}

class DropwizardKiteConfig {
    @JsonProperty("apiKey")
    var apiKey: String? = ""

    @JsonProperty("apiSecret")
    var apiSecret: String? = ""

    @JsonProperty("accessToken")
    var accessToken: String? = ""
}
