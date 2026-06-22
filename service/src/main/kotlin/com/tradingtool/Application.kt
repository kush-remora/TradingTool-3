package com.tradingtool

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.TypeLiteral
import com.tradingtool.config.AppConfig
import com.tradingtool.config.DropwizardConfig
import com.tradingtool.core.http.Result
import com.tradingtool.di.ServiceModule
import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.kite.KiteTokenReadDao
import com.tradingtool.core.kite.KiteTokenWriteDao
import com.tradingtool.core.indexconstituents.dao.IndexConstituentReadDao
import com.tradingtool.core.indexconstituents.dao.IndexConstituentWriteDao
import com.tradingtool.resources.ALL_RESOURCE_CLASSES
import com.tradingtool.eventservice.KiteTickerService
import io.dropwizard.core.Application
import io.dropwizard.configuration.EnvironmentVariableSubstitutor
import io.dropwizard.configuration.SubstitutingSourceProvider
import io.dropwizard.core.setup.Bootstrap
import io.dropwizard.core.setup.Environment
import jakarta.servlet.DispatcherType
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.EnumSet
import kotlinx.coroutines.runBlocking
import org.eclipse.jetty.servlets.CrossOriginFilter
import org.glassfish.jersey.media.multipart.MultiPartFeature
import org.glassfish.jersey.media.sse.SseFeature
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(DropwizardApplication::class.java)

fun main(args: Array<String>) {
    val effectiveArgs: Array<String> = resolveDropwizardArgs(args)
    DropwizardApplication().run(*effectiveArgs)
}

private const val LOCAL_CONFIG_ABSOLUTE_PATH =
    "/Users/kushbhardwaj/Documents/github/TradingTool-3/service/src/main/resources/localconfig.yaml"

private fun resolveDropwizardArgs(args: Array<String>): Array<String> {
    if (args.isNotEmpty()) {
        return args
    }

    val configPath: String = if (isRenderEnvironment()) {
        firstExistingPath(
            "/app/serverConfig.yml",
            "service/src/main/resources/serverConfig.yml",
            "serverConfig.yml",
            "service/src/main/resources/localconfig.yaml",
            LOCAL_CONFIG_ABSOLUTE_PATH,
        )
    } else {
        firstExistingPath(
            LOCAL_CONFIG_ABSOLUTE_PATH,
            "service/src/main/resources/localconfig.yaml",
            "localconfig.yaml",
            "service/src/main/resources/serverConfig.yml",
            "serverConfig.yml",
            "/app/serverConfig.yml",
        )
    }

    return arrayOf("server", configPath)
}

private fun isRenderEnvironment(): Boolean {
    val renderEnv: String = System.getenv("RENDER")?.trim()?.lowercase() ?: ""
    return renderEnv == "true" || renderEnv == "1"
}

private fun firstExistingPath(vararg candidates: String): String {
    candidates.forEach { candidate ->
        val path: Path = Paths.get(candidate)
        if (Files.exists(path)) {
            return candidate
        }
    }

    return candidates.first()
}

class DropwizardApplication : Application<DropwizardConfig>() {

    override fun getName(): String = "TradingTool-3"

    override fun initialize(bootstrap: Bootstrap<DropwizardConfig>) {
        bootstrap.setConfigurationSourceProvider(
            SubstitutingSourceProvider(
                bootstrap.configurationSourceProvider,
                EnvironmentVariableSubstitutor(false),
            ),
        )
    }

    override fun run(config: DropwizardConfig, environment: Environment) {
        // Convert Dropwizard config to AppConfig
        val appConfig = config.toAppConfig()

        val corsFilter = environment.servlets().addFilter("CORS", CrossOriginFilter::class.java)
        corsFilter.setInitParameter(
            CrossOriginFilter.ALLOWED_ORIGINS_PARAM,
            appConfig.cors.allowedOrigins.joinToString(","),
        )
        corsFilter.setInitParameter(
            CrossOriginFilter.ALLOWED_METHODS_PARAM,
            "GET,POST,PUT,PATCH,DELETE,OPTIONS,HEAD",
        )
        corsFilter.setInitParameter(
            CrossOriginFilter.ALLOWED_HEADERS_PARAM,
            "X-Requested-With,Content-Type,Accept,Origin,Authorization,Last-Event-ID,Cache-Control",
        )
        corsFilter.setInitParameter(CrossOriginFilter.ALLOW_CREDENTIALS_PARAM, "true")
        corsFilter.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType::class.java), true, "/*")

        // Create Guice injector
        val injector = Guice.createInjector(ServiceModule(appConfig))

        // Sync Dropwizard's ObjectMapper with the one from Guice (which has JavaTimeModule)
        val guiceObjectMapper = injector.getInstance(ObjectMapper::class.java)
        environment.objectMapper.apply {
            registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())
            registerModule(com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }

        // Note: ResourceScope is injected as a singleton into resources.
        // Coroutines will be cleaned up automatically when the JVM shuts down.

        // Apply the latest persisted Kite token from DB.
        // This is the only source of truth for Kite auth in all environments.
        val tokenDb = injector.getInstance(
            Key.get(object : TypeLiteral<JdbiHandler<KiteTokenReadDao, KiteTokenWriteDao>>() {})
        )
        val kiteClient = injector.getInstance(com.tradingtool.core.kite.KiteConnectClient::class.java)
        requireLatestKiteTokenFromDb(tokenDb, kiteClient)

        if (!kiteClient.isAuthenticated) {
            log.warn(
                "Kite authentication required. " +
                    "Open the login URL and exchange the request token first: ${kiteClient.loginUrl()}"
            )
        }

        // Populate instrument cache at startup (now guaranteed to have auth).
        val instrumentCache = injector.getInstance(com.tradingtool.core.kite.InstrumentCache::class.java)
        val kiteTickerService = injector.getInstance(KiteTickerService::class.java)
        val indexConstituentDb = injector.getInstance(
            Key.get(object : TypeLiteral<JdbiHandler<IndexConstituentReadDao, IndexConstituentWriteDao>>() {})
        )
        // Task 1: when the cron-job refreshes the daily Kite token, restart the ticker.
        kiteClient.setTokenRefreshCallback { newToken ->
            kiteTickerService.restart(newToken)
        }

        Thread {
            try {
                val instruments = kiteClient.client().getInstruments("NSE")
                instrumentCache.refresh(instruments)
                log.info("[InstrumentCache] Loaded {} NSE instruments at startup", instrumentCache.size())
            } catch (e: Exception) {
                log.error("[InstrumentCache] Failed to load at startup: {}", e.message)
            }

            // Task 4: start ticker with market-hours schedule (start 9:14 AM IST, stop 3:31 PM IST).
            try {
                val tokens = runBlocking {
                    indexConstituentDb.read { dao -> dao.listAllActive() }
                }.map { row -> row.instrumentToken }
                    .filter { token -> token > 0L }
                    .distinct()
                kiteTickerService.startWithMarketSchedule(tokens)
                log.info("[KiteTicker] Market schedule registered — {} instruments queued", tokens.size)
            } catch (e: Exception) {
                log.error("[KiteTicker] Failed to register market schedule: {}", e.message)
            }
        }.also { it.isDaemon = true }.start()

        // Register all resources with Jersey (source of truth: ResourceRegistry)
        ALL_RESOURCE_CLASSES.forEach { clazz ->
            environment.jersey().register(injector.getInstance(clazz))
        }
        environment.jersey().register(MultiPartFeature::class.java)
        environment.jersey().register(SseFeature::class.java)

        // Manage coroutine scope + Redis lifecycle — shut down in order on server stop.
        environment.lifecycle().manage(object : io.dropwizard.lifecycle.Managed {
            override fun start() {}
            override fun stop() {
                injector.getInstance(com.tradingtool.core.di.ResourceScope::class.java).shutdown()
                injector.getInstance(com.tradingtool.core.database.RedisHandler::class.java).close()
            }
        })

        // Enable RolesAllowed feature for security annotations
        environment.jersey().register(RolesAllowedDynamicFeature::class.java)
    }
}

private fun requireLatestKiteTokenFromDb(
    tokenDb: JdbiHandler<KiteTokenReadDao, KiteTokenWriteDao>,
    kiteClient: com.tradingtool.core.kite.KiteConnectClient,
) {
    try {
        val latestToken: String? = runBlocking {
            tokenDb.read { dao -> dao.getLatestToken() }
        }
        requireValidKiteStartupToken(
            latestToken = latestToken,
            applyAccessToken = kiteClient::applyAccessToken,
            validateSession = kiteClient::validateSession,
        )
    } catch (error: Exception) {
        if (error is IllegalStateException) {
            log.warn(error.message)
            return
        }
        log.warn("Kite authentication missing. Failed to load token from kite_tokens table: ${error.message}")
    }
}

internal fun requireValidKiteStartupToken(
    latestToken: String?,
    applyAccessToken: (String) -> Unit,
    validateSession: () -> Result<Unit>,
) {
    val normalizedToken = latestToken?.trim()?.takeIf { token -> token.isNotEmpty() }
    if (normalizedToken == null) {
        log.warn("Kite authentication missing. No token found in kite_tokens table.")
        return
    }

    applyAccessToken(normalizedToken)

    when (val validationResult = validateSession()) {
        is Result.Success -> {
            log.info("[KiteToken] Applied and validated latest token from kite_tokens table at startup.")
        }
        is Result.Failure -> {
            log.warn(
                "Kite authentication required. " +
                    "The latest token in kite_tokens is expired or invalid. " +
                    "Refresh Kite login from the frontend. ${validationResult.error.describe()}"
            )
        }
    }
}
