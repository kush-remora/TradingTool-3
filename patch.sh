#!/bin/bash
set -e

# Fix App.tsx
sed -i '' -E 's/import.*(BaseSwingPage|BollingerSqueezePage|CorporateResultsPage|DrawdownScannerPage|DeliveryThresholdBacktestPage|FiftyTwoWeekHighBacktestPage|FiftyTwoWeekLowBacktestPage|FiftyTwoWeekHighLivePage|HotSmaScannerPage|MomentumDataPrepPage|RemoraRsiFloorPage|RsiMomentumBasePage|RsiMomentumLeadersDrawdownPage|RsiMomentumPage|RsiMomentumSafePage|RsiRankDriftBacktestPage|S4VolumeSpikePage|ScreenerPage|SimpleBacktestPage|SwingAnalysisPage|VolumeSpikeBacktestPage|IntradayShockBacktestPage|WeeklyCycleSuccessPage|WeeklySwingPage).*//g' frontend/src/App.tsx
sed -i '' -E 's/.*(bollinger-squeeze|delivery-threshold-backtest|fiftytwo-week-high-backtest|fiftytwo-week-low-backtest|fiftytwo-week-high-live|hot-sma-scanner|unused|swing-analysis|remora-rsi-floor|rsi-momentum|rsi-momentum-base|rsi-momentum-safe|rsi-momentum-drawdown|drawdown-scanner|simple-backtest|rsi-rank-drift|momentum-data-prep|base-swing|weekly-swing|weekly-cycle-success|s4-volume-spike|volume-spike-backtest|intraday-shock-backtest|screener|corporate-results|earnings-dashboard|v2-dashboard).*//g' frontend/src/App.tsx

# Clean up Trade.kt (remove stockId)
sed -i '' 's/val stockId: Long,/val stockId: Long = 0L,/g' core/src/main/kotlin/com/tradingtool/core/model/trade/Trade.kt

# Clean up TradeWriteDao.kt
sed -i '' 's/ON CONFLICT (${TradeColumns.STOCK_ID})/ON CONFLICT (${TradeColumns.NSE_SYMBOL})/g' core/src/main/kotlin/com/tradingtool/core/trade/dao/TradeWriteDao.kt
sed -i '' 's/@Bind("stockId") stockId: Long,/@Bind("stockId") stockId: Long = 0L,/g' core/src/main/kotlin/com/tradingtool/core/trade/dao/TradeWriteDao.kt

# Clean up TradeService.kt
sed -i '' 's/import com.tradingtool.core.stock.service.StockService//g' core/src/main/kotlin/com/tradingtool/core/trade/service/TradeService.kt
sed -i '' 's/private val stockService: StockService,//g' core/src/main/kotlin/com/tradingtool/core/trade/service/TradeService.kt
sed -i '' -e '/val stock = stockService.getBySymbol/,/)/c\
        val stockId = 0L\
' core/src/main/kotlin/com/tradingtool/core/trade/service/TradeService.kt

# Clean up TradeReadinessService.kt
sed -i '' 's/import com.tradingtool.core.database.StockJdbiHandler//g' core/src/main/kotlin/com/tradingtool/core/trade/service/TradeReadinessService.kt
sed -i '' 's/private val stockHandler: StockJdbiHandler,//g' core/src/main/kotlin/com/tradingtool/core/trade/service/TradeReadinessService.kt
sed -i '' -e '/val stocks = stockHandler/,/}/c\
        val symbolsMap = symbols.associateBy { it }\
' core/src/main/kotlin/com/tradingtool/core/trade/service/TradeReadinessService.kt
sed -i '' 's/val stock = stocks\[it.instrumentToken\]/val stock = symbolsMap\[it.symbol\]/g' core/src/main/kotlin/com/tradingtool/core/trade/service/TradeReadinessService.kt
sed -i '' 's/stock?.instrumentToken ?: 0L/it.instrumentToken/g' core/src/main/kotlin/com/tradingtool/core/trade/service/TradeReadinessService.kt
sed -i '' 's/stock?.companyName ?: ""/""/g' core/src/main/kotlin/com/tradingtool/core/trade/service/TradeReadinessService.kt

# Clean up WyckoffPhase1ScannerService.kt
sed -i '' 's/import com.tradingtool.core.database.StockJdbiHandler//g' core/src/main/kotlin/com/tradingtool/core/strategy/wyckoff/phase1/WyckoffPhase1ScannerService.kt
sed -i '' 's/private val stockHandler: StockJdbiHandler,//g' core/src/main/kotlin/com/tradingtool/core/strategy/wyckoff/phase1/WyckoffPhase1ScannerService.kt
sed -i '' -e '/val allStocks = stockHandler.read/,/}/c\
        val allStocks = emptyList<com.tradingtool.core.model.stock.Stock>()\
' core/src/main/kotlin/com/tradingtool/core/strategy/wyckoff/phase1/WyckoffPhase1ScannerService.kt
