package com.tradingtool.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.tradingtool.core.kite.KiteConfig
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

    @JsonProperty("redis")
    @Valid
    @NotNull
    var redis: DropwizardRedisConfig = DropwizardRedisConfig()

    @JsonProperty("gemini")
    @Valid
    @NotNull
    var gemini: DropwizardGeminiConfig = DropwizardGeminiConfig()

    fun toAppConfig(): AppConfig {
        val defaultAllowedOrigins = "https://kush-remora.github.io,http://localhost:5173,http://127.0.0.1:5173"
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
            supabase = SupabaseConfig(
                dbUrl = System.getenv("SUPABASE_DB_URL")?.trim()?.takeIf { it.isNotEmpty() } ?: supabase.dbUrl.orEmpty(),
                url = System.getenv("SUPABASE_URL")?.trim()?.takeIf { it.isNotEmpty() } ?: supabase.url.orEmpty(),
                serviceRoleKey = System.getenv("SUPABASE_SERVICE_ROLE_KEY")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: supabase.serviceRoleKey?.trim()?.takeIf { it.isNotEmpty() }
                    ?: supabase.legacyServiceRoleKey.orEmpty(),
            ),
            deployment = DeploymentConfig(
                renderExternalUrl = deployment.renderExternalUrl ?: "",
                githubPagesUrl = deployment.githubPagesUrl ?: "https://kush-remora.github.io/TradingTool-3/",
            ),
            kite = KiteConfig(
                apiKey = kite.apiKey ?: "",
                apiSecret = kite.apiSecret ?: "",
            ),
            redis = RedisAppConfig(
                url = normalizeRedisUrl(redis.url),
            ),
            gemini = GeminiConfig(
                apiKey = System.getenv("GEMINI_API_KEY")
                    ?.trim()
                    ?.takeIf { apiKey -> apiKey.isNotEmpty() }
                    ?: gemini.apiKey?.trim()?.takeIf { it.isNotEmpty() }
                    ?: try { loadAppConfig().gemini.apiKey } catch (_: Exception) { "" },
            ),
        )
    }

    private fun parseAllowedOrigins(rawValue: String): List<String> {
        if (rawValue.isBlank()) {
            return listOf(
                "https://kush-remora.github.io",
                "http://localhost:5173",
                "http://127.0.0.1:5173",
            )
        }

        return rawValue.split(",")
            .map { item -> item.trim() }
            .filter { item -> item.isNotEmpty() }
    }

    private fun normalizeRedisUrl(rawValue: String?): String {
        val value = rawValue?.trim().orEmpty()
        if (value.isEmpty()) {
            return "redis://localhost:6379"
        }

        val isTemplateWrapped = value.startsWith("\${") && value.endsWith("}")
        val unwrappedValue = if (isTemplateWrapped) value.substring(2, value.length - 1) else value
        return if (unwrappedValue.startsWith("redis://") || unwrappedValue.startsWith("rediss://")) {
            unwrappedValue
        } else {
            value
        }
    }
}

class DropwizardServiceConfig {
    @JsonProperty("name")
    var name: String? = "TradingTool-3"
}

class DropwizardCorsConfig {
    @JsonProperty("allowedOrigins")
    var allowedOrigins: String? =
        "https://kush-remora.github.io,http://localhost:5173,http://127.0.0.1:5173"
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

    @JsonProperty("url")
    var url: String? = ""

    @JsonProperty("serviceRoleKey")
    var serviceRoleKey: String? = ""

    @JsonProperty("SUPABASE_SERVICE_ROLE_KEY")
    var legacyServiceRoleKey: String? = ""
}

class DropwizardDeploymentConfig {
    @JsonProperty("renderExternalUrl")
    var renderExternalUrl: String? = ""

    @JsonProperty("githubPagesUrl")
    var githubPagesUrl: String? = "https://kush-remora.github.io/TradingTool-3/"
}

@JsonIgnoreProperties(ignoreUnknown = true)
class DropwizardKiteConfig {
    @JsonProperty("apiKey")
    var apiKey: String? = ""

    @JsonProperty("apiSecret")
    var apiSecret: String? = ""
}

class DropwizardRedisConfig {
    @JsonProperty("url")
    var url: String? = "redis://localhost:6379"
}

class DropwizardGeminiConfig {
    @JsonProperty("apiKey")
    var apiKey: String? = ""
}
