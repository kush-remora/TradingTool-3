package com.tradingtool.di

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Singleton
import com.tradingtool.config.AppConfig
import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.database.WatchlistJdbiHandler
import com.tradingtool.core.model.DatabaseConfig
import com.tradingtool.core.watchlist.dao.WatchlistReadDao
import com.tradingtool.core.watchlist.dao.WatchlistWriteDao
import com.tradingtool.core.watchlist.service.WatchlistReadService
import com.tradingtool.core.watchlist.service.WatchlistWriteService
import kotlinx.serialization.json.Json

class ServiceModule(
    private val appConfig: AppConfig,
) : AbstractModule() {
    override fun configure() {
        bind(AppConfig::class.java).toInstance(appConfig)
        bind(WatchlistReadService::class.java).`in`(Singleton::class.java)
        bind(WatchlistWriteService::class.java).`in`(Singleton::class.java)
    }

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json { ignoreUnknownKeys = true }
    }

    @Provides
    @Singleton
    fun provideDatabaseConfig(config: AppConfig): DatabaseConfig {
        return DatabaseConfig(
            jdbcUrl = config.supabase.dbUrl,
        )
    }

    @Provides


    @Singleton
    fun provideWatchlistJdbiHandler(config: DatabaseConfig): WatchlistJdbiHandler {
        return JdbiHandler(
            config = config,
            readDaoClass = WatchlistReadDao::class.java,
            writeDaoClass = WatchlistWriteDao::class.java,
        )
    }
}
