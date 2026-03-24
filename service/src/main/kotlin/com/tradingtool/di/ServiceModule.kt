package com.tradingtool.di

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Singleton
import com.google.inject.name.Named
import com.tradingtool.config.AppConfig
import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.database.KiteTokenJdbiHandler
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.http.CoreHttpModule
import com.tradingtool.core.http.HttpRequestExecutor
import com.tradingtool.core.http.JdkHttpRequestExecutor
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.kite.KiteTokenReadDao
import com.tradingtool.core.kite.KiteTokenWriteDao
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.core.model.DatabaseConfig
import com.tradingtool.core.stock.dao.StockReadDao
import com.tradingtool.core.stock.dao.StockWriteDao
import com.tradingtool.core.stock.service.StockService
import com.tradingtool.core.telegram.TelegramApiClient
import com.tradingtool.core.telegram.TelegramSender
import com.tradingtool.core.trade.dao.TradeReadDao
import com.tradingtool.core.trade.dao.TradeWriteDao
import com.tradingtool.core.trade.service.TradeService
import com.tradingtool.resources.health.HealthResource
import com.tradingtool.resources.instruments.InstrumentResource
import com.tradingtool.resources.kite.KiteResource
import com.tradingtool.resources.stock.StockResource
import com.tradingtool.resources.telegram.TelegramResource
import com.tradingtool.resources.trade.TradeResource
import java.net.http.HttpClient

class ServiceModule(
    private val appConfig: AppConfig,
) : AbstractModule() {
    override fun configure() {
        install(CoreHttpModule())

        bind(AppConfig::class.java).toInstance(appConfig)
        bind(ResourceScope::class.java).`in`(Singleton::class.java)
        bind(StockService::class.java).`in`(Singleton::class.java)
        bind(TradeService::class.java).`in`(Singleton::class.java)
        bind(HttpRequestExecutor::class.java).to(JdkHttpRequestExecutor::class.java).`in`(Singleton::class.java)

        bind(HealthResource::class.java).`in`(Singleton::class.java)
        bind(KiteResource::class.java).`in`(Singleton::class.java)
        bind(TelegramResource::class.java).`in`(Singleton::class.java)
        bind(StockResource::class.java).`in`(Singleton::class.java)
        bind(InstrumentResource::class.java).`in`(Singleton::class.java)
        bind(TradeResource::class.java).`in`(Singleton::class.java)
    }

    @Provides
    @Singleton
    fun provideKiteConnectClient(config: AppConfig): KiteConnectClient =
        KiteConnectClient(config.kite)

    @Provides
    @Singleton
    fun provideInstrumentCache(): InstrumentCache = InstrumentCache()

    @Provides
    @Singleton
    fun provideDatabaseConfig(config: AppConfig): DatabaseConfig =
        DatabaseConfig(jdbcUrl = config.supabase.dbUrl)

    @Provides
    @Singleton
    fun provideKiteTokenJdbiHandler(config: DatabaseConfig): KiteTokenJdbiHandler =
        JdbiHandler(config = config, readDaoClass = KiteTokenReadDao::class.java, writeDaoClass = KiteTokenWriteDao::class.java)

    @Provides
    @Singleton
    fun provideStockJdbiHandler(config: DatabaseConfig): StockJdbiHandler =
        JdbiHandler(config = config, readDaoClass = StockReadDao::class.java, writeDaoClass = StockWriteDao::class.java)

    @Provides
    @Singleton
    fun provideTradeJdbiHandler(config: DatabaseConfig): JdbiHandler<TradeReadDao, TradeWriteDao> =
        JdbiHandler(config = config, readDaoClass = TradeReadDao::class.java, writeDaoClass = TradeWriteDao::class.java)

    @Provides
    @Singleton
    fun provideTelegramApiClient(
        @Named("telegramBotToken") botToken: String,
        @Named("telegramChatId") chatId: String,
        httpClient: com.tradingtool.core.http.SuspendHttpClient,
        objectMapper: com.fasterxml.jackson.databind.ObjectMapper,
    ): TelegramApiClient = TelegramApiClient(
        botToken = botToken,
        chatId = chatId,
        httpClient = httpClient,
        objectMapper = objectMapper,
    )

    @Provides
    @Singleton
    fun provideTelegramSender(telegramApiClient: TelegramApiClient): TelegramSender =
        TelegramSender(telegramApiClient = telegramApiClient)

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient.newBuilder().build()

    @Provides
    @Singleton
    @Named("telegramBotToken")
    fun provideBotToken(config: AppConfig): String = config.telegram.botToken

    @Provides
    @Singleton
    @Named("telegramChatId")
    fun provideChatId(config: AppConfig): String = config.telegram.chatId
}
