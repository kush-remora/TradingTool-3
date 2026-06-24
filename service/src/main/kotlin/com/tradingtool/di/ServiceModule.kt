package com.tradingtool.di

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Singleton
import com.google.inject.name.Named
import com.tradingtool.config.AppConfig
import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.database.GrowwVolumeShockerJdbiHandler
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.database.KiteTokenJdbiHandler
import com.tradingtool.core.database.RedisHandler
import com.tradingtool.core.database.StockDeliveryJdbiHandler
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.core.http.CoreHttpModule
import com.tradingtool.core.http.HttpRequestExecutor
import com.tradingtool.core.http.JdkHttpRequestExecutor
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.InstrumentTokenResolverService
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.kite.KiteTokenReadDao
import com.tradingtool.core.kite.KiteTokenWriteDao
import com.tradingtool.core.kite.LiveMarketService
import com.tradingtool.core.kite.TickStore
import com.tradingtool.core.kite.TickerSubscriptions
import com.tradingtool.core.model.DatabaseConfig
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.dao.CandleReadDao
import com.tradingtool.core.candle.dao.CandleWriteDao
import com.tradingtool.core.delivery.dao.StockDeliveryReadDao
import com.tradingtool.core.delivery.dao.StockDeliveryWriteDao
import com.tradingtool.core.delivery.config.DeliveryConfigService
import com.tradingtool.core.delivery.config.DeliveryUniverseService
import com.tradingtool.core.delivery.reconciliation.DeliveryReconciliationService
import com.tradingtool.core.delivery.source.NseDeliverySourceAdapter
import com.tradingtool.core.screener.CandleDataService
import com.tradingtool.core.strategy.profitlookback.ProfitLookbackService
import com.tradingtool.core.strategy.hotsma.HotSmaScannerService
import com.tradingtool.core.volumeshocker.groww.dao.GrowwVolumeShockerReadDao
import com.tradingtool.core.volumeshocker.groww.dao.GrowwVolumeShockerWriteDao
import com.tradingtool.core.telegram.TelegramApiClient
import com.tradingtool.core.telegram.TelegramNotifier
import com.tradingtool.core.telegram.TelegramSender
import com.tradingtool.core.trade.dao.TradeReadDao
import com.tradingtool.core.trade.dao.TradeWriteDao
import com.tradingtool.core.trade.service.TradeService
import com.tradingtool.core.trade.service.TradeReadinessService
import com.tradingtool.eventservice.KiteTickerService
import com.tradingtool.resources.ALL_RESOURCE_CLASSES

class ServiceModule(
    private val appConfig: AppConfig,
) : AbstractModule() {

    /** Single-line factory for any JdbiHandler — eliminates the repeated constructor call. */
    private inline fun <reified R, reified W> handler(config: DatabaseConfig): JdbiHandler<R, W> =
        JdbiHandler(config, R::class.java, W::class.java)

    override fun configure() {
        install(CoreHttpModule())

        bind(AppConfig::class.java).toInstance(appConfig)
        bind(ResourceScope::class.java).`in`(Singleton::class.java)
        bind(DeliveryConfigService::class.java).`in`(Singleton::class.java)
        bind(DeliveryUniverseService::class.java).`in`(Singleton::class.java)
        bind(DeliveryReconciliationService::class.java).`in`(Singleton::class.java)
        bind(TradeService::class.java).`in`(Singleton::class.java)
        bind(TradeReadinessService::class.java).`in`(Singleton::class.java)
        bind(HttpRequestExecutor::class.java).to(JdkHttpRequestExecutor::class.java).`in`(Singleton::class.java)

        ALL_RESOURCE_CLASSES.forEach { bind(it).`in`(Singleton::class.java) }
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
    fun provideDatabaseConfig(config: AppConfig): DatabaseConfig {
        val maxPoolSize = readPositiveIntEnv("SUPABASE_DB_MAX_POOL_SIZE", 5)
        val minIdleConnections = readNonNegativeIntEnv("SUPABASE_DB_MIN_IDLE", 0).coerceAtMost(maxPoolSize)

        return DatabaseConfig(
            jdbcUrl = config.supabase.dbUrl,
            maxPoolSize = maxPoolSize,
            minIdleConnections = minIdleConnections,
            connectionTimeoutMs = readPositiveLongEnv("SUPABASE_DB_CONNECTION_TIMEOUT_MS", 10_000),
            idleTimeoutMs = readPositiveLongEnv("SUPABASE_DB_IDLE_TIMEOUT_MS", 600_000),
            maxLifetimeMs = readPositiveLongEnv("SUPABASE_DB_MAX_LIFETIME_MS", 1_800_000),
        )
    }

    @Provides @Singleton
    fun provideKiteTokenJdbiHandler(config: DatabaseConfig): KiteTokenJdbiHandler =
        handler<KiteTokenReadDao, KiteTokenWriteDao>(config)



    @Provides @Singleton
    fun provideTradeJdbiHandler(config: DatabaseConfig): JdbiHandler<TradeReadDao, TradeWriteDao> =
        handler<TradeReadDao, TradeWriteDao>(config)

    @Provides @Singleton
    fun provideIndexConstituentJdbiHandler(config: DatabaseConfig): IndexConstituentJdbiHandler =
        handler<com.tradingtool.core.indexconstituents.dao.IndexConstituentReadDao, com.tradingtool.core.indexconstituents.dao.IndexConstituentWriteDao>(config)




    @Provides
    @Singleton
    fun provideRedisHandler(config: AppConfig): RedisHandler =
        RedisHandler(config.redis.url) // Replaced the hardcoded .fromEnv() with AppConfig











    @Provides
    @Singleton
    fun provideLiveMarketService(kiteClient: KiteConnectClient): LiveMarketService =
        LiveMarketService(kiteClient)

    @Provides
    @Singleton
    fun provideTickStore(): TickStore = TickStore()

    @Provides
    @Singleton
    fun provideKiteTickerService(
        kiteClient: KiteConnectClient,
        tickStore: TickStore,
    ): KiteTickerService = KiteTickerService(kiteClient, tickStore)

    // Task 2: expose the same KiteTickerService instance as TickerSubscriptions.
    // StockResource depends on TickerSubscriptions (in core) without knowing about event-service.
    @Provides
    @Singleton
    fun provideTickerSubscriptions(kiteTickerService: KiteTickerService): TickerSubscriptions =
        kiteTickerService


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
    fun provideTelegramNotifier(sender: TelegramSender): TelegramNotifier =
        TelegramNotifier(sender)

    @Provides
    @Singleton
    @Named("telegramBotToken")
    fun provideBotToken(config: AppConfig): String = config.telegram.botToken

    @Provides
    @Singleton
    @Named("telegramChatId")
    fun provideChatId(config: AppConfig): String = config.telegram.chatId

    @Provides @Singleton
    fun provideStockDeliveryJdbiHandler(config: DatabaseConfig): StockDeliveryJdbiHandler =
        handler<StockDeliveryReadDao, StockDeliveryWriteDao>(config)

    @Provides @Singleton
    fun provideGrowwVolumeShockerJdbiHandler(config: DatabaseConfig): GrowwVolumeShockerJdbiHandler =
        handler<GrowwVolumeShockerReadDao, GrowwVolumeShockerWriteDao>(config)

    @Provides @Singleton
    fun providePhaseCWatchlistJdbiHandler(config: DatabaseConfig): com.tradingtool.core.strategy.phasedbreakout.PhaseCWatchlistJdbiHandler =
        handler<com.tradingtool.core.strategy.phasedbreakout.dao.PhaseCWatchlistReadDao, com.tradingtool.core.strategy.phasedbreakout.dao.PhaseCWatchlistWriteDao>(config)

    @Provides @Singleton
    fun provideNseDeliverySourceAdapter(jsonHttpClient: com.tradingtool.core.http.JsonHttpClient): NseDeliverySourceAdapter =
        NseDeliverySourceAdapter(jsonHttpClient)

    @Provides @Singleton
    fun provideCandleJdbiHandler(config: DatabaseConfig): CandleJdbiHandler =
        handler<CandleReadDao, CandleWriteDao>(config)


    
    @Provides
    @Singleton
    fun provideObjectMapper(): com.fasterxml.jackson.databind.ObjectMapper {
        val mapper = com.fasterxml.jackson.databind.ObjectMapper()
        mapper.registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())
        mapper.registerModule(com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        return mapper
    }

    @Provides
    @Singleton
    fun provideCandleCacheService(
        candleHandler: CandleJdbiHandler,
        redis: RedisHandler,
        objectMapper: com.fasterxml.jackson.databind.ObjectMapper,
    ): CandleCacheService = CandleCacheService(candleHandler, redis, objectMapper)

    @Provides @Singleton
    fun provideCandleDataService(
        candleHandler: CandleJdbiHandler,
        instrumentCache: InstrumentCache,
        kiteClient: KiteConnectClient,
    ): CandleDataService = CandleDataService(
        candleHandler = candleHandler,
        instrumentCache = instrumentCache,
        tokenResolver = InstrumentTokenResolverService(kiteClient, instrumentCache),
    )

    @Provides @Singleton
    fun provideHotSmaScannerService(
        indexConstituentHandler: IndexConstituentJdbiHandler,
        candleCacheService: CandleCacheService,
        candleDataService: CandleDataService,
        kiteClient: KiteConnectClient,
    ): HotSmaScannerService = HotSmaScannerService(
        indexConstituentHandler = indexConstituentHandler,
        candleCacheService = candleCacheService,
        candleDataService = candleDataService,
        kiteClient = kiteClient,
    )

    @Provides @Singleton
    fun providePhaseCWatchlistService(
        jdbiHandler: com.tradingtool.core.strategy.phasedbreakout.PhaseCWatchlistJdbiHandler,
        stockDeliveryHandler: StockDeliveryJdbiHandler,
        candleHandler: CandleJdbiHandler,
        candleDataService: CandleDataService,
        kiteClient: KiteConnectClient,
        instrumentTokenResolver: InstrumentTokenResolverService
    ): com.tradingtool.core.strategy.phasedbreakout.PhaseCWatchlistService =
        com.tradingtool.core.strategy.phasedbreakout.PhaseCWatchlistService(
            watchlistHandler = jdbiHandler,
            stockDeliveryHandler = stockDeliveryHandler,
            candleHandler = candleHandler,
            candleDataService = candleDataService,
            kiteClient = kiteClient,
            instrumentTokenResolver = instrumentTokenResolver
        )




    private companion object {
        fun readPositiveIntEnv(envName: String, defaultValue: Int): Int {
            val rawValue = System.getenv(envName)?.trim().orEmpty()
            if (rawValue.isEmpty()) {
                return defaultValue
            }

            return rawValue.toIntOrNull()?.takeIf { value -> value > 0 } ?: defaultValue
        }

        fun readNonNegativeIntEnv(envName: String, defaultValue: Int): Int {
            val rawValue = System.getenv(envName)?.trim().orEmpty()
            if (rawValue.isEmpty()) {
                return defaultValue
            }

            return rawValue.toIntOrNull()?.takeIf { value -> value >= 0 } ?: defaultValue
        }

        fun readPositiveLongEnv(envName: String, defaultValue: Long): Long {
            val rawValue = System.getenv(envName)?.trim().orEmpty()
            if (rawValue.isEmpty()) {
                return defaultValue
            }

            return rawValue.toLongOrNull()?.takeIf { value -> value > 0 } ?: defaultValue
        }
    }
}
