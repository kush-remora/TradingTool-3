package com.tradingtool.core.screener

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.TickStore
import com.tradingtool.core.model.stock.WatchlistList
import com.tradingtool.core.model.watchlist.WatchlistRow
import com.tradingtool.core.strategy.rsimomentum.RsiMomentumConfigService
import com.tradingtool.core.watchlist.IndicatorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

@Singleton
class DrawdownScannerService @Inject constructor(
    private val indicatorService: IndicatorService,
    private val rsiMomentumConfigService: RsiMomentumConfigService,
    private val instrumentCache: InstrumentCache,
    private val stockHandler: StockJdbiHandler,
    private val tickStore: TickStore,
    private val kiteClient: com.tradingtool.core.kite.KiteConnectClient,
) {
    private val log = LoggerFactory.getLogger(DrawdownScannerService::class.java)
    private val cacheMutex = kotlinx.coroutines.sync.Mutex()

    private suspend fun ensureInstrumentCacheLoaded() {
        if (!instrumentCache.isEmpty()) return
        cacheMutex.withLock {
            if (instrumentCache.isEmpty()) {
                log.info("Instrument cache empty. Loading from Kite...")
                val instruments = withContext(Dispatchers.IO) {
                    kiteClient.client().getInstruments("NSE")
                }
                instrumentCache.refresh(instruments)
                log.info("Instrument cache loaded with ${instruments.size} NSE instruments")
            }
        }
    }

    suspend fun scanUniverse(universe: String): DrawdownScannerResult {
        log.info("Starting drawdown scan for universe: $universe")
        ensureInstrumentCacheLoaded()
        
        val symbols = when (universe.uppercase()) {
            "WATCHLIST" -> stockHandler.read { it.listAll() }.map { it.symbol }
            "NIFTY_50" -> rsiMomentumConfigService.loadBaseUniverseSymbols("NIFTY_50")
            "NIFTY_500" -> loadCustomUniverse("NIFTY_500")
            "LARGEMIDCAP_250" -> rsiMomentumConfigService.loadBaseUniverseSymbols("NIFTY_LARGEMIDCAP_250")
            "SMALLCAP_250" -> rsiMomentumConfigService.loadBaseUniverseSymbols("NIFTY_SMALLCAP_250")
            else -> rsiMomentumConfigService.loadBaseUniverseSymbols(universe)
        }.map { it.trim().uppercase() }.distinct()

        if (symbols.isEmpty()) {
            return DrawdownScannerResult(universe, 0, emptyList())
        }

        // Resolve tokens for all symbols
        val tokens = symbols.mapNotNull { symbol ->
            instrumentCache.token("NSE", symbol)
        }

        log.info("Resolved ${tokens.size} tokens for $universe (requested ${symbols.size} symbols)")

        // Fetch indicators for all tokens. 
        // indicatorService.loadIndicatorsForTokens will handle L1/L2/L3 (Redis/DB/Kite)
        val indicators = indicatorService.loadIndicatorsForTokens(tokens)
        
        val results = indicators.map { ind ->
            val inst = instrumentCache.find(ind.instrumentToken)
            val symbol = inst?.tradingsymbol ?: "UNKNOWN"
            val companyName = inst?.name ?: symbol
            val tick = tickStore.get(ind.instrumentToken)
            val effectiveLtp = tick?.ltp ?: ind.lastClose

            // Compute metrics similar to WatchlistService but simplified for scanner
            val priceVs200maPct = if (effectiveLtp != null && ind.sma200 != null && ind.sma200 != 0.0) {
                (effectiveLtp - ind.sma200) / ind.sma200 * 100.0
            } else null
            val priceVs50maPct = if (effectiveLtp != null && ind.sma50 != null && ind.sma50 != 0.0) {
                (effectiveLtp - ind.sma50) / ind.sma50 * 100.0
            } else null

            val trendState = when {
                effectiveLtp == null || ind.sma50 == null || ind.sma200 == null -> null
                effectiveLtp >= ind.sma50 && effectiveLtp >= ind.sma200 -> "ABOVE_BOTH"
                effectiveLtp >= ind.sma50 -> "ABOVE_50_ONLY"
                effectiveLtp >= ind.sma200 -> "ABOVE_200_ONLY"
                else -> "BELOW_BOTH"
            }

            WatchlistRow(
                symbol = symbol,
                instrumentToken = ind.instrumentToken,
                companyName = companyName,
                exchange = "NSE",
                watchlistList = WatchlistList.RESEARCH,
                sector = null,
                ltp = effectiveLtp,
                changePercent = tick?.changePercent,
                sma50 = ind.sma50,
                sma200 = ind.sma200,
                trendState = trendState,
                high60d = ind.high60d,
                low60d = ind.low60d,
                rangePosition60dPct = null, // Simplified
                gapTo3mLowPct = null,
                gapTo3mHighPct = null,
                rsiAtHigh60d = ind.rsiAtHigh60d,
                rsiAtLow60d = ind.rsiAtLow60d,
                volumeAtHigh60d = ind.volumeAtHigh60d,
                volumeAtLow60d = ind.volumeAtLow60d,
                priceVs50maPct = priceVs50maPct,
                priceVs200maPct = priceVs200maPct,
                rsi14 = ind.rsi14,
                atr14 = ind.atr14,
                atr14Pct = if (ind.atr14 != null && effectiveLtp != null && effectiveLtp != 0.0) (ind.atr14 / effectiveLtp) * 100.0 else null,
                roc1w = ind.roc1w,
                roc3m = ind.roc3m,
                macdSignal = ind.macdSignal,
                drawdownPct = ind.drawdownPct,
                maxDd1y = ind.maxDd1y,
                volumeVsAvg = ind.volumeVsAvg20d,
            )
        }

        return DrawdownScannerResult(
            universe = universe,
            count = results.size,
            results = results.sortedBy { it.drawdownPct }
        )
    }

    private fun loadCustomUniverse(presetName: String): List<String> {
        val resourceName = when(presetName) {
            "NIFTY_500" -> "strategy-universes/nifty_500.csv"
            else -> return emptyList()
        }

        val stream = javaClass.classLoader.getResourceAsStream(resourceName) ?: return emptyList()
        return stream.bufferedReader().useLines { lines ->
            lines.map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && it.lowercase() != "symbol" }
                .toList()
        }
    }
}
