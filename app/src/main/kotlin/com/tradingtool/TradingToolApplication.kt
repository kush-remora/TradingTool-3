package com.tradingtool

import com.tradingtool.core.telegram.TelegramSender
import com.tradingtool.core.watchlist.dao.WatchlistDal
import com.tradingtool.core.watchlist.dao.WatchlistDatabaseConfig
import com.tradingtool.core.watchlist.service.WatchlistService
import com.tradingtool.exception.GenericServiceExceptionMapper
import com.tradingtool.exception.ServiceUnavailableExceptionMapper
import com.tradingtool.exception.ValidationExceptionMapper
import com.tradingtool.health.DatabaseHealthCheck
import com.tradingtool.resources.health.HealthResource
import com.tradingtool.resources.telegram.TelegramResource
import com.tradingtool.resources.watchlist.WatchlistResource
import io.dropwizard.core.Application
import io.dropwizard.core.setup.Bootstrap
import io.dropwizard.core.setup.Environment
import org.eclipse.jetty.servlets.CrossOriginFilter
import java.util.EnumSet
import jakarta.servlet.DispatcherType
import jakarta.servlet.FilterRegistration

class TradingToolApplication : Application<TradingToolConfiguration>() {

    override fun getName(): String {
        return "TradingTool-3"
    }

    override fun initialize(bootstrap: Bootstrap<TradingToolConfiguration>) {
        // Add bundles, commands, etc. here if needed
    }

    override fun run(configuration: TradingToolConfiguration, environment: Environment) {
        // Initialize services
        val telegramSender = TelegramSender(
            botToken = configuration.telegram.botToken,
            chatId = configuration.telegram.chatId,
        )

        val watchlistDal = WatchlistDal(
            config = WatchlistDatabaseConfig(
                jdbcUrl = configuration.database.url,
                user = configuration.database.user,
                password = configuration.database.password,
            ),
        )

        val watchlistService = WatchlistService(dal = watchlistDal)

        // Register resources
        environment.jersey().register(HealthResource())
        environment.jersey().register(TelegramResource(telegramSender))
        environment.jersey().register(WatchlistResource(watchlistService))

        // Register exception mappers
        environment.jersey().register(ValidationExceptionMapper())
        environment.jersey().register(ServiceUnavailableExceptionMapper())
        environment.jersey().register(GenericServiceExceptionMapper())

        // Register health checks
        environment.healthChecks().register("database", DatabaseHealthCheck(watchlistDal))

        // Configure CORS
        configureCors(configuration, environment)
    }

    private fun configureCors(configuration: TradingToolConfiguration, environment: Environment) {
        val filter: FilterRegistration.Dynamic = environment.servlets().addFilter("CORS", CrossOriginFilter::class.java)

        filter.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, configuration.cors.allowedOrigins)
        filter.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,POST,PUT,PATCH,DELETE,OPTIONS")
        filter.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "Content-Type,Authorization,Accept")
        filter.setInitParameter(CrossOriginFilter.ALLOW_CREDENTIALS_PARAM, "true")

        filter.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType::class.java), true, "/*")
    }
}

fun main(args: Array<String>) {
    TradingToolApplication().run(*args)
}
