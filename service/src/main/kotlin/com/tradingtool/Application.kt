package com.tradingtool

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.TypeLiteral
import com.tradingtool.config.AppConfig
import com.tradingtool.config.DropwizardConfig
import com.tradingtool.di.ServiceModule
import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.kite.KiteTokenReadDao
import com.tradingtool.core.kite.KiteTokenWriteDao
import com.tradingtool.core.stock.dao.StockReadDao
import com.tradingtool.core.stock.dao.StockWriteDao
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

        // Register Jackson module for Kotlin
        val objectMapper: ObjectMapper = environment.objectMapper
        objectMapper.registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())

        // Note: ResourceScope is injected as a singleton into resources.
        // Coroutines will be cleaned up automatically when the JVM shuts down.

        // Apply the latest persisted Kite token from DB.
        // This is the only source of truth for Kite auth in all environments.
        val tokenDb = injector.getInstance(
            Key.get(object : TypeLiteral<JdbiHandler<KiteTokenReadDao, KiteTokenWriteDao>>() {})
        )
        val kiteClient = injector.getInstance(com.tradingtool.core.kite.KiteConnectClient::class.java)
        requireLatestKiteTokenFromDb(tokenDb, kiteClient)

        // Fail fast if Kite is not authenticated at startup.
        // Services should only run when dependencies (like Kite auth) are ready.
        if (!kiteClient.isAuthenticated) {
            throw IllegalStateException(
                "Kite authentication required to start. " +
                    "Open the login URL and exchange the request token first: ${kiteClient.loginUrl()}"
            )
        }

        // Populate instrument cache at startup (now guaranteed to have auth).
        val instrumentCache = injector.getInstance(com.tradingtool.core.kite.InstrumentCache::class.java)
        val kiteTickerService = injector.getInstance(KiteTickerService::class.java)
        val stockHandler = injector.getInstance(
            Key.get(object : TypeLiteral<JdbiHandler<StockReadDao, StockWriteDao>>() {})
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
                    stockHandler.read { dao -> dao.listAll() }
                }.map { it.instrumentToken }.filter { it > 0 }
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
        }?.takeIf { token -> token.isNotBlank() }

        if (latestToken != null) {
            kiteClient.applyAccessToken(latestToken)
            log.info("[KiteToken] Applied latest token from kite_tokens table at startup.")
        } else {
            throw IllegalStateException(
                "Kite authentication required to start. No token found in kite_tokens table."
            )
        }
    } catch (error: Exception) {
        throw IllegalStateException(
            "Kite authentication required to start. Failed to load token from kite_tokens table.",
            error,
        )
    }
}
