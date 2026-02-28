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
import com.tradingtool.resources.health.HealthResource
import com.tradingtool.resources.instruments.InstrumentResource
import com.tradingtool.resources.kite.KiteResource
import com.tradingtool.resources.layout.LayoutResource
import com.tradingtool.resources.notes.StockNotesResource
import com.tradingtool.resources.telegram.TelegramResource
import com.tradingtool.resources.watchlist.WatchlistResource
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
            "X-Requested-With,Content-Type,Accept,Origin,Authorization",
        )
        corsFilter.setInitParameter(CrossOriginFilter.ALLOW_CREDENTIALS_PARAM, "true")
        corsFilter.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType::class.java), true, "/*")

        // Create Guice injector
        val injector = Guice.createInjector(ServiceModule(appConfig))

        // Register Jackson module for Kotlin
        val objectMapper: ObjectMapper = environment.objectMapper
        objectMapper.registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())

        // Apply latest persisted Kite token from DB so restarts use the freshest token.
        val tokenDb = injector.getInstance(
            Key.get(object : TypeLiteral<JdbiHandler<KiteTokenReadDao, KiteTokenWriteDao>>() {})
        )
        val kiteClient = injector.getInstance(com.tradingtool.core.kite.KiteConnectClient::class.java)
        applyLatestKiteTokenFromDb(tokenDb, kiteClient)

        // Populate instrument cache at startup if Kite token is available.
        val instrumentCache = injector.getInstance(com.tradingtool.core.kite.InstrumentCache::class.java)
        if (kiteClient.isAuthenticated) {
            Thread {
                try {
                    val instruments = kiteClient.client().getInstruments("NSE")
                    instrumentCache.refresh(instruments)
                    println("[InstrumentCache] Loaded ${instrumentCache.size()} NSE instruments at startup")
                } catch (e: Exception) {
                    println("[InstrumentCache] Failed to load at startup: ${e.message}")
                }
            }.also { it.isDaemon = true }.start()
        } else {
            println("[InstrumentCache] Kite not authenticated — cache empty. Complete Kite login to enable instrument search.")
        }

        // Get resource instances from Guice
        val healthResource = injector.getInstance(HealthResource::class.java)
        val kiteResource = injector.getInstance(KiteResource::class.java)
        val telegramResource = injector.getInstance(TelegramResource::class.java)
        val watchlistResource = injector.getInstance(WatchlistResource::class.java)
        val instrumentResource = injector.getInstance(InstrumentResource::class.java)
        val stockNotesResource = injector.getInstance(StockNotesResource::class.java)
        val layoutResource = injector.getInstance(LayoutResource::class.java)

        // Register resources with Jersey
        environment.jersey().register(healthResource)
        environment.jersey().register(kiteResource)
        environment.jersey().register(telegramResource)
        environment.jersey().register(watchlistResource)
        environment.jersey().register(instrumentResource)
        environment.jersey().register(stockNotesResource)
        environment.jersey().register(layoutResource)
        environment.jersey().register(MultiPartFeature::class.java)

        // Enable RolesAllowed feature for security annotations
        environment.jersey().register(RolesAllowedDynamicFeature::class.java)
    }
}

private fun applyLatestKiteTokenFromDb(
    tokenDb: JdbiHandler<KiteTokenReadDao, KiteTokenWriteDao>,
    kiteClient: com.tradingtool.core.kite.KiteConnectClient,
) {
    try {
        val latestToken: String? = runBlocking {
            tokenDb.read { dao -> dao.getLatestToken() }
        }?.takeIf { token -> token.isNotBlank() }

        if (latestToken != null) {
            kiteClient.applyAccessToken(latestToken)
            println("[KiteToken] Applied latest token from kite_tokens table at startup.")
        } else {
            println("[KiteToken] No token found in kite_tokens table. Using configured token if present.")
        }
    } catch (error: Exception) {
        println("[KiteToken] Failed to load token from DB at startup: ${error.message}")
        println("[KiteToken] Falling back to configured token.")
    }
}
