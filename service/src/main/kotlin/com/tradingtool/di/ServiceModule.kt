package com.tradingtool.di

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Singleton
import com.google.inject.name.Named
import com.tradingtool.config.AppConfig
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.database.WatchlistJdbiHandler
import com.tradingtool.core.http.HttpRequestExecutor
import com.tradingtool.core.http.JdkHttpRequestExecutor
import com.tradingtool.core.model.DatabaseConfig
import com.tradingtool.core.telegram.TelegramApiClient
import com.tradingtool.core.telegram.TelegramSender
import com.tradingtool.core.watchlist.dao.WatchlistReadDao
import com.tradingtool.core.watchlist.dao.WatchlistWriteDao
import com.tradingtool.core.watchlist.service.WatchlistReadService
import com.tradingtool.core.watchlist.service.WatchlistWriteService
import com.tradingtool.resources.health.HealthResource
import com.tradingtool.resources.telegram.TelegramResource
import com.tradingtool.resources.watchlist.WatchlistResource
import java.net.http.HttpClient
import kotlinx.serialization.json.Json

class ServiceModule(
    private val appConfig: AppConfig,
) : AbstractModule() {
    override fun configure() {
        bind(AppConfig::class.java).toInstance(appConfig)
        bind(WatchlistReadService::class.java).`in`(Singleton::class.java)
        bind(WatchlistWriteService::class.java).`in`(Singleton::class.java)
        bind(HttpRequestExecutor::class.java).to(JdkHttpRequestExecutor::class.java).`in`(Singleton::class.java)

        // Register resources for Dropwizard
        bind(HealthResource::class.java).`in`(Singleton::class.java)
        bind(TelegramResource::class.java).`in`(Singleton::class.java)
        bind(WatchlistResource::class.java).`in`(Singleton::class.java)
    }

    @Provides
    @Singleton
    fun provideKiteConnectClient(config: AppConfig): KiteConnectClient {
        return KiteConnectClient(config.kite)
    }

    @Provides
    @Singleton
    fun provideInstrumentCache(): InstrumentCache {
        return InstrumentCache()
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

    @Provides
    @Singleton
    fun provideTelegramApiClient(
        @Named("telegramBotToken") botToken: String,
        @Named("telegramChatId") chatId: String,
        httpRequestExecutor: HttpRequestExecutor,
        json: Json,
    ): TelegramApiClient {
        return TelegramApiClient(
            botToken = botToken,
            chatId = chatId,
            httpRequestExecutor = httpRequestExecutor,
            json = json,
        )
    }

    @Provides
    @Singleton
    fun provideTelegramSender(telegramApiClient: TelegramApiClient): TelegramSender {
        return TelegramSender(telegramApiClient = telegramApiClient)
    }

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient {
        return HttpClient.newBuilder().build()
    }

    @Provides
    @Singleton
    @Named("telegramBotToken")
    fun provideBotToken(config: AppConfig): String = config.telegram.botToken

    @Provides
    @Singleton
    @Named("telegramChatId")
    fun provideChatId(config: AppConfig): String = config.telegram.chatId
}
