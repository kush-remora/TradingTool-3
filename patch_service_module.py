import re

file_path = "service/src/main/kotlin/com/tradingtool/di/ServiceModule.kt"

with open(file_path, "r") as f:
    content = f.read()

# 1. Remove provideRsiMomentumBackfillService
content = re.sub(r"    @Provides @Singleton\n    fun provideRsiMomentumBackfillService.*?\(.*?\)\n", "", content, flags=re.DOTALL)

# 2. Remove provideRsiMomentumBacktestService
content = re.sub(r"    @Provides @Singleton\n    fun provideRsiMomentumBacktestService.*?\n        \)\n", "", content, flags=re.DOTALL)

# 3. Remove provideS4Service
content = re.sub(r"    @Provides\n    @Singleton\n    fun provideS4Service.*?\)\n", "", content, flags=re.DOTALL)

# 4. Remove provideStockDetailService
content = re.sub(r"    @Provides\n    @Singleton\n    fun provideStockDetailService.*?\n", "", content, flags=re.DOTALL)

# 5. Remove provideWatchlistService
content = re.sub(r"    @Provides\n    @Singleton\n    fun provideWatchlistService.*?\)\n", "", content, flags=re.DOTALL)

# 6. Remove provideEarningsResultJdbiHandler
content = re.sub(r"    @Provides @Singleton\n    fun provideEarningsResultJdbiHandler.*?\n", "", content, flags=re.DOTALL)

# 7. Remove provideWeeklyPatternService
content = re.sub(r"    @Provides @Singleton\n    fun provideWeeklyPatternService.*?\n", "", content, flags=re.DOTALL)

# 8. Remove provideWeeklyCycleSuccessService
content = re.sub(r"    @Provides @Singleton\n    fun provideWeeklyCycleSuccessService.*?\n", "", content, flags=re.DOTALL)

# 9. Remove provideTechnicalContextService
content = re.sub(r"    @Provides @Singleton\n    fun provideTechnicalContextService.*?\n        \)\n", "", content, flags=re.DOTALL)

# 10. Remove provideSwingService
content = re.sub(r"    @Provides @Singleton\n    fun provideSwingService.*?\n        com\.tradingtool\.core\.analysis\.swing\.SwingService\(stockHandler, candleCache\)\n", "", content, flags=re.DOTALL)

# 11. Fix RemoraService
remora_orig = """    @Provides
    @Singleton
    fun provideRemoraService(
        stockHandler: StockJdbiHandler,
        remoraHandler: RemoraJdbiHandler,
        deliveryHandler: StockDeliveryJdbiHandler,
        deliveryReconciliationService: DeliveryReconciliationService,
        telegramSender: TelegramSender,
        kiteClient: KiteConnectClient,
    ): RemoraService = RemoraService(
        stockHandler = stockHandler,
        remoraHandler = remoraHandler,
        deliveryHandler = deliveryHandler,
        deliveryReconciliationService = deliveryReconciliationService,
        telegramSender = telegramSender,
        kiteClient = kiteClient,
    )"""

remora_new = """    @Provides
    @Singleton
    fun provideRemoraService(
        instrumentResolver: InstrumentTokenResolverService,
        indexConstituentHandler: IndexConstituentJdbiHandler,
        remoraHandler: RemoraJdbiHandler,
        deliveryHandler: StockDeliveryJdbiHandler,
        deliveryReconciliationService: DeliveryReconciliationService,
        telegramSender: TelegramSender,
        kiteClient: KiteConnectClient,
    ): RemoraService = RemoraService(
        instrumentResolver = instrumentResolver,
        indexConstituentHandler = indexConstituentHandler,
        remoraHandler = remoraHandler,
        deliveryHandler = deliveryHandler,
        deliveryReconciliationService = deliveryReconciliationService,
        telegramSender = telegramSender,
        kiteClient = kiteClient,
    )"""

content = content.replace(remora_orig, remora_new)


# 12. Fix CandleDataService
candle_orig = """    @Provides @Singleton
    fun provideCandleDataService(
        stockHandler: StockJdbiHandler,
        candleHandler: CandleJdbiHandler,
        instrumentCache: InstrumentCache,
        kiteClient: KiteConnectClient,
    ): CandleDataService = CandleDataService(
        stockHandler = stockHandler,
        candleHandler = candleHandler,
        instrumentCache = instrumentCache,
        tokenResolver = InstrumentTokenResolverService(kiteClient, instrumentCache),
    )"""

candle_new = """    @Provides @Singleton
    fun provideCandleDataService(
        candleHandler: CandleJdbiHandler,
        instrumentCache: InstrumentCache,
        kiteClient: KiteConnectClient,
    ): CandleDataService = CandleDataService(
        candleHandler = candleHandler,
        instrumentCache = instrumentCache,
        tokenResolver = InstrumentTokenResolverService(kiteClient, instrumentCache),
    )"""

content = content.replace(candle_orig, candle_new)

with open(file_path, "w") as f:
    f.write(content)

print("Patched ServiceModule.kt")
