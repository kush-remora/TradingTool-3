import re

file_path = "service/src/main/kotlin/com/tradingtool/di/ServiceModule.kt"

with open(file_path, "r") as f:
    content = f.read()

# Remove provideBaseSwingService
content = re.sub(r"    @Provides @Singleton\n    fun provideBaseSwingService.*?\n        com\.tradingtool\.core\.screener\.BaseSwingService\(stockHandler, indexConstituentHandler, candleCache\)\n", "", content, flags=re.DOTALL)

# Remove provideBollingerScreenerService
content = re.sub(r"    @Provides @Singleton\n    fun provideBollingerScreenerService.*?\n        \)\n", "", content, flags=re.DOTALL)

# Remove provideBollingerSqueezeService
content = re.sub(r"    @Provides @Singleton\n    fun provideBollingerSqueezeService.*?\n        \)\n", "", content, flags=re.DOTALL)

# Remove provideIndicatorService
content = re.sub(r"    @Provides\n    @Singleton\n    fun provideIndicatorService.*?\)\n", "", content, flags=re.DOTALL)

# Remove provideRsiMomentumConfigService
content = re.sub(r"    @Provides\n    @Singleton\n    fun provideRsiMomentumConfigService.*?\n", "", content, flags=re.DOTALL)

# Remove provideS4ConfigService
content = re.sub(r"    @Provides\n    @Singleton\n    fun provideS4ConfigService.*?\n", "", content, flags=re.DOTALL)

# Remove provideRsiMomentumService
content = re.sub(r"    @Provides\n    @Singleton\n    fun provideRsiMomentumService.*?\)\n", "", content, flags=re.DOTALL)

# Remove provideRsiMomentumSnapshotJdbiHandler
content = re.sub(r"    @Provides @Singleton\n    fun provideRsiMomentumSnapshotJdbiHandler.*?\n", "", content, flags=re.DOTALL)

# Remove provideRsiMomentumHistoryService
content = re.sub(r"    @Provides @Singleton\n    fun provideRsiMomentumHistoryService.*?\n", "", content, flags=re.DOTALL)

# Remove StockDetailService orphan if present
content = re.sub(r"        StockDetailService\(stockHandler\)\n", "", content)

# Remove provideStockJdbiHandler
content = re.sub(r"    @Provides @Singleton\n    fun provideStockJdbiHandler.*?\n", "", content, flags=re.DOTALL)

with open(file_path, "w") as f:
    f.write(content)

print("Patched ServiceModule 2")
