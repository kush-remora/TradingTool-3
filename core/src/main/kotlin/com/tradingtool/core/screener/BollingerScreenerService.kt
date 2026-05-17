package com.tradingtool.core.screener

import com.google.inject.Inject
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.technical.toTa4jSeries
import com.tradingtool.core.technical.roundTo2
import com.tradingtool.core.watchlist.Ta4jIndicatorCalculator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.ZoneId

class BollingerScreenerService(
    private val stockHandler: StockJdbiHandler,
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val candleCache: CandleCacheService,
    private val candleDataService: CandleDataService,
    private val kiteClient: KiteConnectClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ist = ZoneId.of("Asia/Kolkata")
    private val analysisSemaphore = Semaphore(15)
    private companion object {
        // Accept either ratio form (0.04) or percent form (4.0) and normalize for UI display.
        private const val BANDWIDTH_RATIO_THRESHOLD = 1.0
    }

    suspend fun analyze(indexKeys: List<String>): List<BollingerScanResult> = coroutineScope {
        val startTime = System.currentTimeMillis()
        log.info("Starting Bollinger analysis for indices: {}", indexKeys)

        val symbolsToAnalyze = mutableSetOf<SymbolMetadata>()

        indexKeys.forEach { key ->
            when (key) {
                "WATCHLIST", "ALL_STOCKS" -> {
                    val stocks = stockHandler.read { it.listAll() }
                    symbolsToAnalyze.addAll(stocks.map { SymbolMetadata(it.symbol, it.companyName, it.instrumentToken) })
                }
                else -> {
                    val constituents = indexConstituentHandler.read { it.listActiveByIndex(key) }
                    symbolsToAnalyze.addAll(constituents.map { SymbolMetadata(it.symbol, it.companyName, it.instrumentToken) })
                }
            }
        }

        log.info("Analyzing {} unique symbols", symbolsToAnalyze.size)

        // Pre-fetch missing data if Kite is authenticated
        if (kiteClient.isAuthenticated) {
            val today = LocalDate.now(ist)
            val symbolsToSync = mutableListOf<String>()

            symbolsToAnalyze.forEach { metadata ->
                val cached = candleCache.getDailyCandles(metadata.instrumentToken, metadata.symbol, today.minusDays(7), today)
                if (cached.size < 2) { // Allow for weekend/holiday gap, but 0 or 1 usually means missing
                    symbolsToSync.add(metadata.symbol)
                }
            }

            if (symbolsToSync.isNotEmpty()) {
                log.info("Syncing missing data for {} symbols before analysis", symbolsToSync.size)
                // Batch sync in chunks to avoid massive delay but ensure data is there
                symbolsToSync.chunked(20).forEach { chunk ->
                    try {
                        candleDataService.sync(chunk, kiteClient)
                    } catch (e: Exception) {
                        log.warn("Auto-sync failed for chunk {}: {}", chunk, e.message)
                    }
                }
            }
        } else {
            log.info("Kite not authenticated, skipping auto-sync of missing data")
        }

        val results = symbolsToAnalyze.map { metadata ->

            async {
                analysisSemaphore.withPermit {
                    analyzeSymbol(metadata)
                }
            }
        }.awaitAll().filterNotNull()

        log.info("Completed Bollinger analysis in {}ms", System.currentTimeMillis() - startTime)
        results.sortedByDescending { it.setupScore }
    }

    private suspend fun analyzeSymbol(metadata: SymbolMetadata): BollingerScanResult? {
        val today = LocalDate.now(ist)
        val from = today.minusYears(1) 

        val candles = try {
            candleCache.getDailyCandles(metadata.instrumentToken, metadata.symbol, from, today)
        } catch (e: Exception) {
            log.warn("Failed to fetch candles for {}: {}", metadata.symbol, e.message)
            return null
        }
        
        if (candles.size < 20) return null 

        val series = candles.toTa4jSeries(metadata.symbol)
        val indicators = Ta4jIndicatorCalculator.calculate(series)
        
        val ltp = indicators.lastClose ?: 0.0
        val bbUpper = indicators.bbUpper ?: 0.0
        val bbLower = indicators.bbLower ?: 0.0
        val bbMiddle = indicators.bbMiddle ?: 0.0
        val percentB = indicators.bbPercentB ?: 0.0
        val bandwidthRaw = indicators.bbBandwidth ?: 0.0
        val bandwidthPercent = toBandwidthPercent(bandwidthRaw)
        val isSqueeze = indicators.bbSqueeze
        val rsi = indicators.rsi14

        var signal = "NORMAL"
        var score = 50
        val reasons = mutableListOf<String>()

        if (isSqueeze) {
            signal = "SQUEEZE"
            score += 30
            reasons.add("Volatility Squeeze (today bandwidth = lowest in last 60 trading days)")
        }

        if (percentB <= 0.05) {
            signal = "OVERSOLD"
            score += 20
            reasons.add("Price hitting lower band (%B=${percentB.roundTo2()})")
            if (rsi != null && rsi < 35) {
                score += 20
                reasons.add("RSI Oversold ($rsi)")
            }
        } else if (percentB >= 0.95) {
            signal = "OVERBOUGHT"
            score -= 10
            reasons.add("Price hitting upper band (%B=${percentB.roundTo2()})")
            if (rsi != null && rsi > 70) {
                score -= 10
                reasons.add("RSI Overbought ($rsi)")
            }
        }

        return BollingerScanResult(
            symbol = metadata.symbol,
            companyName = metadata.companyName,
            instrumentToken = metadata.instrumentToken,
            ltp = ltp.roundTo2(),
            bbUpper = bbUpper.roundTo2(),
            bbLower = bbLower.roundTo2(),
            bbMiddle = bbMiddle.roundTo2(),
            percentB = percentB.roundTo2(),
            bandwidth = bandwidthPercent.roundTo2(),
            isSqueeze = isSqueeze,
            rsi14 = rsi?.roundTo2(),
            signal = signal,
            setupScore = score.coerceIn(0, 100),
            reasoning = reasons.joinToString("; ")
        )
    }

    private fun toBandwidthPercent(rawBandwidth: Double): Double {
        if (!rawBandwidth.isFinite()) return 0.0
        return if (kotlin.math.abs(rawBandwidth) <= BANDWIDTH_RATIO_THRESHOLD) {
            rawBandwidth * 100.0
        } else {
            rawBandwidth
        }
    }

    private data class SymbolMetadata(
        val symbol: String,
        val companyName: String,
        val instrumentToken: Long,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SymbolMetadata) return false
            return symbol == other.symbol
        }
        override fun hashCode(): Int = symbol.hashCode()
    }
}
