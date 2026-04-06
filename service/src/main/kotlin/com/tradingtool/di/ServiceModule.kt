package com.tradingtool.di

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Singleton
import com.google.inject.name.Named
import com.tradingtool.config.AppConfig
import com.tradingtool.core.config.IndicatorConfig
import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.database.KiteTokenJdbiHandler
import com.tradingtool.core.database.RedisHandler
import com.tradingtool.core.database.RemoraJdbiHandler
import com.tradingtool.core.database.StockDeliveryJdbiHandler
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.core.http.CoreHttpModule
import com.tradingtool.core.http.HttpRequestExecutor
import com.tradingtool.core.http.JdkHttpRequestExecutor
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.kite.KiteTokenReadDao
import com.tradingtool.core.kite.KiteTokenWriteDao
import com.tradingtool.core.kite.LiveMarketService
import com.tradingtool.core.kite.TickStore
import com.tradingtool.core.kite.TickerSubscriptions
import com.tradingtool.core.model.DatabaseConfig
import com.tradingtool.core.stock.dao.StockReadDao
import com.tradingtool.core.stock.dao.StockWriteDao
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.dao.CandleReadDao
import com.tradingtool.core.candle.dao.CandleWriteDao
import com.tradingtool.core.delivery.dao.StockDeliveryReadDao
import com.tradingtool.core.delivery.dao.StockDeliveryWriteDao
import com.tradingtool.core.delivery.source.NseDeliverySourceAdapter
import com.tradingtool.core.screener.CandleDataService
import com.tradingtool.core.screener.WeeklyPatternService
import com.tradingtool.core.strategy.remora.RemoraService
import com.tradingtool.core.strategy.remora.RemoraSignalReadDao
import com.tradingtool.core.strategy.remora.RemoraSignalWriteDao
import com.tradingtool.core.stock.service.StockDetailService
import com.tradingtool.core.stock.service.StockService
import com.tradingtool.core.telegram.TelegramApiClient
import com.tradingtool.core.telegram.TelegramNotifier
import com.tradingtool.core.telegram.TelegramSender
import com.tradingtool.core.trade.dao.TradeReadDao
import com.tradingtool.core.trade.dao.TradeWriteDao
import com.tradingtool.core.trade.service.TradeService
import com.tradingtool.core.watchlist.IndicatorService
import com.tradingtool.core.watchlist.WatchlistConfigService
import com.tradingtool.core.watchlist.WatchlistService
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
        bind(WatchlistConfigService::class.java).`in`(Singleton::class.java)
        bind(StockService::class.java).`in`(Singleton::class.java)
        bind(TradeService::class.java).`in`(Singleton::class.java)
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
    fun provideDatabaseConfig(config: AppConfig): DatabaseConfig =
        DatabaseConfig(jdbcUrl = config.supabase.dbUrl)

    @Provides @Singleton
    fun provideKiteTokenJdbiHandler(config: DatabaseConfig): KiteTokenJdbiHandler =
        handler<KiteTokenReadDao, KiteTokenWriteDao>(config)

    @Provides @Singleton
    fun provideStockJdbiHandler(config: DatabaseConfig): StockJdbiHandler =
        handler<StockReadDao, StockWriteDao>(config)

    @Provides @Singleton
    fun provideTradeJdbiHandler(config: DatabaseConfig): JdbiHandler<TradeReadDao, TradeWriteDao> =
        handler<TradeReadDao, TradeWriteDao>(config)

    @Provides
    @Singleton
    fun provideRedisHandler(config: AppConfig): RedisHandler =
        RedisHandler(config.redis.url) // Replaced the hardcoded .fromEnv() with AppConfig

    @Provides
    @Singleton
    fun provideIndicatorService(
        candleHandler: CandleJdbiHandler,
        stockHandler: StockJdbiHandler,
        redis: RedisHandler,
        kiteClient: KiteConnectClient,
    ): IndicatorService = IndicatorService(
        candleHandler = candleHandler,
        stockHandler = stockHandler,
        redis = redis,
        kiteClient = kiteClient,
        config = IndicatorConfig.DEFAULT,
    )

    @Provides
    @Singleton
    fun provideStockDetailService(stockHandler: StockJdbiHandler): StockDetailService =
        StockDetailService(stockHandler)

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
    fun provideWatchlistService(
        stockHandler: StockJdbiHandler,
        indicatorService: IndicatorService,
        liveMarketService: LiveMarketService,
    ): WatchlistService = WatchlistService(
        stockHandler = stockHandler,
        indicatorService = indicatorService,
        liveMarketService = liveMarketService,
    )

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
    fun provideRemoraJdbiHandler(config: DatabaseConfig): RemoraJdbiHandler =
        handler<RemoraSignalReadDao, RemoraSignalWriteDao>(config)

    @Provides @Singleton
    fun provideStockDeliveryJdbiHandler(config: DatabaseConfig): StockDeliveryJdbiHandler =
        handler<StockDeliveryReadDao, StockDeliveryWriteDao>(config)

    @Provides @Singleton
    fun provideNseDeliverySourceAdapter(jsonHttpClient: com.tradingtool.core.http.JsonHttpClient): NseDeliverySourceAdapter =
        NseDeliverySourceAdapter(jsonHttpClient)

    @Provides
    @Singleton
    fun provideRemoraService(
        stockHandler: StockJdbiHandler,
        remoraHandler: RemoraJdbiHandler,
        deliveryHandler: StockDeliveryJdbiHandler,
        nseAdapter: NseDeliverySourceAdapter,
        telegramSender: TelegramSender,
        kiteClient: KiteConnectClient,
    ): RemoraService = RemoraService(
        stockHandler = stockHandler,
        remoraHandler = remoraHandler,
        deliveryHandler = deliveryHandler,
        nseAdapter = nseAdapter,
        telegramSender = telegramSender,
        kiteClient = kiteClient,
    )

    @Provides @Singleton
    fun provideCandleJdbiHandler(config: DatabaseConfig): CandleJdbiHandler =
        handler<CandleReadDao, CandleWriteDao>(config)
    
    @Provides @Singleton
    fun provideCandleCacheService(
        candleHandler: CandleJdbiHandler,
        redis: RedisHandler,
        objectMapper: com.fasterxml.jackson.databind.ObjectMapper,
    ): CandleCacheService = CandleCacheService(candleHandler, redis, objectMapper)

    @Provides @Singleton
    fun provideCandleDataService(
        stockHandler: StockJdbiHandler,
        candleHandler: CandleJdbiHandler,
    ): CandleDataService = CandleDataService(stockHandler, candleHandler)

    @Provides @Singleton
    fun provideWeeklyPatternService(
        stockHandler: StockJdbiHandler,
        candleCache: CandleCacheService,
    ): WeeklyPatternService = WeeklyPatternService(stockHandler, candleCache)

    @Provides @Singleton
    fun provideTechnicalContextService(
        stockHandler: StockJdbiHandler,
        candleCache: CandleCacheService,
    ): com.tradingtool.core.technical.TechnicalContextService =
        com.tradingtool.core.technical.TechnicalContextService(stockHandler, candleCache)
}
