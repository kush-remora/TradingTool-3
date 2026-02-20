package com.tradingtool.config

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private const val PRODUCTION_CONFIG_FILE = "serverConfig.yml"
private const val LOCAL_CONFIG_FILE = "localconfig.yaml"

data class AppConfig(
    val server: ServerConfig,
    val service: ServiceConfig,
    val cors: CorsConfig,
    val telegram: TelegramConfig,
    val supabase: SupabaseConfig,
    val deployment: DeploymentConfig,
    val kite: KiteConfig,
)

data class ServerConfig(
    val host: String,
    val port: Int,
)

data class ServiceConfig(
    val name: String,
)

data class CorsConfig(
    val allowedOrigins: List<String>,
)

data class TelegramConfig(
    val botToken: String,
    val chatId: String,
    val webhookSecret: String,
    val downloadDir: String,
    val pollTimeoutSeconds: Int,
    val requestTimeoutSeconds: Int,
    val errorRetrySleepSeconds: Int,
    val maxRetrySleepSeconds: Int,
)

data class SupabaseConfig(
    val dbUrl: String,
)

data class DeploymentConfig(
    val renderExternalUrl: String,
    val githubPagesUrl: String,
)

data class KiteConfig(
    val apiKey: String,
    val apiSecret: String,
    // Short-lived token set via env or manual login. Empty = not yet authenticated.
    val accessToken: String,
)

fun loadAppConfig(resourceName: String = defaultConfigFileName()): AppConfig {
    val fileValues: Map<String, String> = loadYamlSectionValues(resourceName)

    val server = ServerConfig(
        host = getString(
            fileValues = fileValues,
            yamlKey = "server.host",
            envVars = listOf("HOST"),
            defaultValue = "0.0.0.0",
        ),
        port = getInt(
            fileValues = fileValues,
            yamlKey = "server.port",
            envVars = listOf("PORT", "SERVER_PORT"),
            defaultValue = 8080,
        ),
    )

    val service = ServiceConfig(
        name = getString(
            fileValues = fileValues,
            yamlKey = "service.name",
            envVars = listOf("SERVICE_NAME"),
            defaultValue = "TradingTool-3",
        ),
    )

    val corsAllowedOriginsRaw: String = getString(
        fileValues = fileValues,
        yamlKey = "cors.allowedOrigins",
        envVars = listOf("CORS_ALLOWED_ORIGINS"),
        defaultValue = "https://kushb2.github.io,http://localhost:5173,http://127.0.0.1:5173",
    )
    val cors = CorsConfig(
        allowedOrigins = parseCsv(corsAllowedOriginsRaw),
    )

    val telegram = TelegramConfig(
        botToken = getString(
            fileValues = fileValues,
            yamlKey = "telegram.botToken",
            envVars = listOf("TELEGRAM_BOT_TOKEN"),
            defaultValue = "",
        ),
        chatId = getString(
            fileValues = fileValues,
            yamlKey = "telegram.chatId",
            envVars = listOf("TELEGRAM_CHAT_ID"),
            defaultValue = "",
        ),
        webhookSecret = getString(
            fileValues = fileValues,
            yamlKey = "telegram.webhookSecret",
            envVars = listOf("TELEGRAM_WEBHOOK_SECRET"),
            defaultValue = "",
        ),
        downloadDir = getString(
            fileValues = fileValues,
            yamlKey = "telegram.downloadDir",
            envVars = listOf("TELEGRAM_DOWNLOAD_DIR"),
            defaultValue = "data/telegram_downloads",
        ),
        pollTimeoutSeconds = getInt(
            fileValues = fileValues,
            yamlKey = "telegram.pollTimeoutSeconds",
            envVars = listOf("TELEGRAM_POLL_TIMEOUT_SECONDS"),
            defaultValue = 60,
        ),
        requestTimeoutSeconds = getInt(
            fileValues = fileValues,
            yamlKey = "telegram.requestTimeoutSeconds",
            envVars = listOf("TELEGRAM_REQUEST_TIMEOUT_SECONDS"),
            defaultValue = 75,
        ),
        errorRetrySleepSeconds = getInt(
            fileValues = fileValues,
            yamlKey = "telegram.errorRetrySleepSeconds",
            envVars = listOf("TELEGRAM_ERROR_RETRY_SLEEP_SECONDS"),
            defaultValue = 1,
        ),
        maxRetrySleepSeconds = getInt(
            fileValues = fileValues,
            yamlKey = "telegram.maxRetrySleepSeconds",
            envVars = listOf("TELEGRAM_MAX_RETRY_SLEEP_SECONDS"),
            defaultValue = 30,
        ),
    )

    val supabase = SupabaseConfig(
        dbUrl = getString(
            fileValues = fileValues,
            yamlKey = "supabase.dbUrl",
            envVars = listOf("SUPABASE_DB_URL"),
            defaultValue = "",
        ),
    )

    val deployment = DeploymentConfig(
        renderExternalUrl = getString(
            fileValues = fileValues,
            yamlKey = "deployment.renderExternalUrl",
            envVars = listOf("RENDER_EXTERNAL_URL"),
            defaultValue = "",
        ),
        githubPagesUrl = getString(
            fileValues = fileValues,
            yamlKey = "deployment.githubPagesUrl",
            envVars = listOf("GITHUB_PAGES_URL"),
            defaultValue = "https://kushb2.github.io/TradingTool-3/",
        ),
    )

    val kite = KiteConfig(
        apiKey = getString(
            fileValues = fileValues,
            yamlKey = "kite.apiKey",
            envVars = listOf("KITE_API_KEY"),
            defaultValue = "",
        ),
        apiSecret = getString(
            fileValues = fileValues,
            yamlKey = "kite.apiSecret",
            envVars = listOf("KITE_API_SECRET"),
            defaultValue = "",
        ),
        accessToken = getString(
            fileValues = fileValues,
            yamlKey = "kite.accessToken",
            envVars = listOf("KITE_ACCESS_TOKEN"),
            defaultValue = "",
        ),
    )

    return AppConfig(
        server = server,
        service = service,
        cors = cors,
        telegram = telegram,
        supabase = supabase,
        deployment = deployment,
        kite = kite,
    )
}

private fun getString(
    fileValues: Map<String, String>,
    yamlKey: String,
    envVars: List<String>,
    defaultValue: String,
): String {
    val envValue: String? = firstNonBlankEnvironmentValue(envVars)
    if (envValue != null) {
        return envValue
    }

    val fileValue: String? = fileValues[yamlKey]?.trim()?.takeIf { value -> value.isNotEmpty() }
    if (fileValue != null) {
        return fileValue
    }

    return defaultValue
}

private fun getInt(
    fileValues: Map<String, String>,
    yamlKey: String,
    envVars: List<String>,
    defaultValue: Int,
): Int {
    val rawValue: String = getString(
        fileValues = fileValues,
        yamlKey = yamlKey,
        envVars = envVars,
        defaultValue = defaultValue.toString(),
    )

    return rawValue.toIntOrNull()
        ?: error("Invalid integer value for '$yamlKey': '$rawValue'")
}

private fun firstNonBlankEnvironmentValue(envVars: List<String>): String? {
    return envVars.asSequence()
        .mapNotNull { envVar -> System.getenv(envVar)?.trim() }
        .firstOrNull { value -> value.isNotEmpty() }
}

private fun parseCsv(rawValue: String): List<String> {
    if (rawValue.isBlank()) {
        return emptyList()
    }

    return rawValue.split(",")
        .map { item -> item.trim() }
        .filter { item -> item.isNotEmpty() }
}

private fun loadYamlSectionValues(resourceName: String): Map<String, String> {
    val configStream: InputStream = openConfigStream(resourceName)

    val values: MutableMap<String, String> = mutableMapOf()
    var section: String? = null

    configStream.bufferedReader().useLines { lines ->
        lines.forEach { rawLine ->
            val lineWithoutComment: String = rawLine.substringBefore("#")
            val trimmedLine: String = lineWithoutComment.trim()
            if (trimmedLine.isEmpty()) {
                return@forEach
            }

            val isSectionHeader: Boolean = !rawLine.startsWith(" ") && trimmedLine.endsWith(":")
            if (isSectionHeader) {
                section = trimmedLine.removeSuffix(":")
                return@forEach
            }

            if (!rawLine.startsWith("  ")) {
                return@forEach
            }
            val activeSection: String = section ?: return@forEach

            val delimiterIndex: Int = trimmedLine.indexOf(':')
            if (delimiterIndex <= 0) {
                return@forEach
            }

            val key: String = trimmedLine.substring(0, delimiterIndex).trim()
            val rawValue: String = trimmedLine.substring(delimiterIndex + 1).trim()
            val cleanValue: String = rawValue
                .removeSurrounding("\"")
                .removeSurrounding("'")

            values["$activeSection.$key"] = cleanValue
        }
    }

    return values
}

private fun openConfigStream(resourceName: String): InputStream {
    val explicitPath: String? = firstNonBlankEnvironmentValue(
        listOf("SERVER_CONFIG_PATH", "SERVER_CONFIG_FILE"),
    )
    if (explicitPath != null) {
        val path: Path = Paths.get(explicitPath)
        if (Files.exists(path)) {
            return Files.newInputStream(path)
        }
        error("Configured server config file does not exist: $explicitPath")
    }

    findExistingConfigPath(resourceName)?.let { path ->
        return Files.newInputStream(path)
    }

    return object {}.javaClass.classLoader.getResourceAsStream(resourceName)
        ?: error("Missing resource file: $resourceName")
}

private fun defaultConfigFileName(): String {
    if (isProductionEnvironment()) {
        return PRODUCTION_CONFIG_FILE
    }

    if (findExistingConfigPath(LOCAL_CONFIG_FILE) != null) {
        return LOCAL_CONFIG_FILE
    }

    val localConfigOnClasspath =
        object {}.javaClass.classLoader.getResource(LOCAL_CONFIG_FILE) != null
    if (localConfigOnClasspath) {
        return LOCAL_CONFIG_FILE
    }

    return PRODUCTION_CONFIG_FILE
}

private fun isProductionEnvironment(): Boolean {
    val profile: String = firstNonBlankEnvironmentValue(
        listOf("APP_ENV", "APP_PROFILE", "ENV"),
    )?.lowercase().orEmpty()
    if (profile == "prod" || profile == "production") {
        return true
    }

    val renderFlag: String = System.getenv("RENDER")?.trim()?.lowercase().orEmpty()
    return renderFlag == "true"
}

private fun findExistingConfigPath(resourceName: String): Path? {
    val localCandidates: List<Path> = listOf(
        Paths.get("service", "src", "main", "resources", resourceName),
        Paths.get("src", "main", "resources", resourceName),
        Paths.get(resourceName),
    )
    return localCandidates.firstOrNull { path -> Files.exists(path) }
}
