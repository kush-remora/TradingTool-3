package com.tradingtool.core.screener

import com.google.inject.Inject
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.technical.*
import com.tradingtool.core.watchlist.Ta4jIndicatorCalculator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.ZoneId
import org.ta4j.core.indicators.bollinger.BollingerBandWidthIndicator
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator
import org.ta4j.core.indicators.SMAIndicator
import org.ta4j.core.indicators.RSIIndicator
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.indicators.helpers.VolumeIndicator
import org.ta4j.core.indicators.helpers.HighestValueIndicator
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator

class BollingerSqueezeService @Inject constructor(
    private val stockHandler: StockJdbiHandler,
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val candleCache: CandleCacheService,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ist = ZoneId.of("Asia/Kolkata")
    private val analysisSemaphore = Semaphore(15)

    suspend fun analyze(indexKeys: List<String>): List<BollingerSqueezeScanResult> = coroutineScope {
        val startTime = System.currentTimeMillis()
        log.info("Starting Bollinger Squeeze analysis for indices: {}", indexKeys)

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

        val results = symbolsToAnalyze.map { metadata ->
            async {
                analysisSemaphore.withPermit {
                    analyzeSymbol(metadata)
                }
            }
        }.awaitAll().filterNotNull()

        log.info("Completed Bollinger Squeeze analysis in {}ms", System.currentTimeMillis() - startTime)
        results.sortedByDescending { it.filter1Passed }
    }

    suspend fun track(positions: List<SqueezePositionInput>): SqueezeTrackResponse = coroutineScope {
        val results = positions.map { pos ->
            async {
                analysisSemaphore.withPermit {
                    trackPosition(pos)
                }
            }
        }.awaitAll().filterNotNull()
        SqueezeTrackResponse(results)
    }

    private suspend fun trackPosition(pos: SqueezePositionInput): SqueezeTrackResult? {
        val symbol = pos.symbol.trim().uppercase()
        val buyDate = try { LocalDate.parse(pos.buyDate) } catch (e: Exception) { return null }
        val buyPrice = pos.buyPrice
        if (buyPrice <= 0.0) return null

        val stock = stockHandler.read { it.getBySymbol(symbol, "NSE") } ?: return null
        
        val today = LocalDate.now(ist)
        val from = buyDate.minusYears(1).minusDays(50)

        val candles = try {
            candleCache.getDailyCandles(stock.instrumentToken, symbol, from, today)
        } catch (e: Exception) {
            return null
        }
        
        if (candles.isEmpty()) return null

        val series = candles.toTa4jSeries(symbol)
        val closePrice = ClosePriceIndicator(series)
        val rsi14 = RSIIndicator(closePrice, 14)
        val sma20 = SMAIndicator(closePrice, 20)
        val stdDev = StandardDeviationIndicator(closePrice, 20)
        val bbMiddle = BollingerBandsMiddleIndicator(sma20)
        val bbUpper = BollingerBandsUpperIndicator(bbMiddle, stdDev)
        val bbLower = BollingerBandsLowerIndicator(bbMiddle, stdDev)

        val lastIndex = series.endIndex
        val ltpValue = closePrice.getValue(lastIndex).doubleValue()
        val ltp = if (ltpValue.isFinite()) ltpValue else 0.0
        val profitPct = ((ltp - buyPrice) / buyPrice) * 100.0
        
        val buyIndex = (0..lastIndex).find { series.getBar(it).endTime.toLocalDate() == buyDate } ?: return null

        // Phase 1: Safety (Structural Low of 5 days prior to Buy Date)
        val startIndex = (buyIndex - 4).coerceAtLeast(0)
        var structuralLow = Double.POSITIVE_INFINITY
        for (i in startIndex..buyIndex) {
            val low = series.getBar(i).lowPrice.doubleValue()
            if (low < structuralLow) structuralLow = low
        }
        
        var currentPhase = "1. Safety"
        var requiredSl = structuralLow

        // Phase 2: Protection (Break Even if profit >= 2%)
        if (profitPct >= 2.0) {
            currentPhase = "2. Protection"
            requiredSl = buyPrice
        }

        // Phase 3: Profit (Staircase Trail if today's high > buy day's high)
        val buyHigh = series.getBar(buyIndex).highPrice.doubleValue()
        var madeNewHigh = false
        for (i in (buyIndex + 1)..lastIndex) {
            if (series.getBar(i).highPrice.doubleValue() > buyHigh) {
                madeNewHigh = true
                break
            }
        }

        if (madeNewHigh) {
            currentPhase = "3. Profit"
            requiredSl = if (lastIndex > 0) {
                series.getBar(lastIndex - 1).lowPrice.doubleValue()
            } else {
                buyPrice
            }
        }

        val todayRsi = rsi14.getValue(lastIndex).doubleValue()
        val maxRsi1y = HighestValueIndicator(rsi14, 252).getValue(lastIndex).doubleValue()
        val high1y = HighestValueIndicator(closePrice, 252).getValue(lastIndex).doubleValue()
        val maxDrawdownPct = if (high1y > 0.0) ((ltp - high1y) / high1y) * 100.0 else 0.0

        return SqueezeTrackResult(
            symbol = symbol,
            companyName = stock.companyName,
            buyDate = pos.buyDate,
            buyPrice = buyPrice,
            ltp = ltp.roundTo2(),
            profitPct = profitPct.roundTo2(),
            currentPhase = currentPhase,
            requiredSl = requiredSl.roundTo2(),
            todayRsi = if (todayRsi.isFinite()) todayRsi.roundTo2() else null,
            maxRsi1y = if (maxRsi1y.isFinite()) maxRsi1y.roundTo2() else null,
            maxDrawdownPct = if (maxDrawdownPct.isFinite()) maxDrawdownPct.roundTo2() else 0.0,
            bbUpper = bbUpper.getValue(lastIndex).doubleValue().let { if (it.isFinite()) it.roundTo2() else 0.0 },
            bbMiddle = bbMiddle.getValue(lastIndex).doubleValue().let { if (it.isFinite()) it.roundTo2() else 0.0 },
            bbLower = bbLower.getValue(lastIndex).doubleValue().let { if (it.isFinite()) it.roundTo2() else 0.0 }
        )
    }

    private suspend fun analyzeSymbol(metadata: SymbolMetadata): BollingerSqueezeScanResult? {
        val today = LocalDate.now(ist)
        val from = today.minusYears(1).minusDays(100) // Extra buffer for indicators

        val candles = try {
            candleCache.getDailyCandles(metadata.instrumentToken, metadata.symbol, from, today)
        } catch (e: Exception) {
            return null
        }
        
        if (candles.size < 100) return null 

        val series = candles.toTa4jSeries(metadata.symbol)
        val closePrice = ClosePriceIndicator(series)
        val volume = VolumeIndicator(series)
        val rsi14 = RSIIndicator(closePrice, 14)
        val sma20 = SMAIndicator(closePrice, 20)
        val stdDev = StandardDeviationIndicator(closePrice, 20)
        val bbMiddle = BollingerBandsMiddleIndicator(sma20)
        val bbUpper = BollingerBandsUpperIndicator(bbMiddle, stdDev)
        val bbLower = BollingerBandsLowerIndicator(bbMiddle, stdDev)
        val bandwidth = BollingerBandWidthIndicator(bbUpper, bbMiddle, bbLower)
        val sma200 = SMAIndicator(closePrice, 200)
        val volSma20 = SMAIndicator(volume, 20)

        val lastIndex = series.endIndex
        val lookback60 = 60
        val tolerance = 1.12

        // Filter 1: 3-day consecutive squeeze in last 60 days
        var filter1Passed = false
        var filter1OriginDate: LocalDate? = null
        var filter1LatestDate: LocalDate? = null
        var filter1OriginIdx: Int? = null
        
        // Find all 3-day squeeze completions in the last 60 days
        val squeezeIndices = mutableListOf<Int>()
        for (i in (lastIndex - lookback60 + 1).coerceAtLeast(80)..lastIndex) {
            if (is3DaySqueeze(bandwidth, i, tolerance)) {
                squeezeIndices.add(i)
            }
        }

        if (squeezeIndices.isNotEmpty()) {
            filter1Passed = true
            filter1OriginIdx = squeezeIndices.first()
            filter1OriginDate = series.getBar(filter1OriginIdx).endTime.toLocalDate()
            filter1LatestDate = series.getBar(squeezeIndices.last()).endTime.toLocalDate()
        }

        // Filter 2: Breakout Triggers
        var filter2Passed = false
        var filter2OriginDate: LocalDate? = null
        var filter2LatestDate: LocalDate? = null
        var filter2Type: String? = null

        val breakoutDetails = mutableListOf<Pair<Int, String>>()
        
        // Strategy Pure: Search for the FIRST breakout trigger occurring AFTER the FIRST squeeze completion
        if (filter1Passed && filter1OriginIdx != null) {
            for (i in filter1OriginIdx..lastIndex) {
                val type = checkBreakout(series, bbUpper, bbMiddle, volume, volSma20, i)
                if (type != null) {
                    breakoutDetails.add(i to type)
                }
            }
        } else {
            // Fallback for stale display: find any recent breakout in the last 30 days
            for (i in (lastIndex - 30).coerceAtLeast(20)..lastIndex) {
                val type = checkBreakout(series, bbUpper, bbMiddle, volume, volSma20, i)
                if (type != null) {
                    breakoutDetails.add(i to type)
                }
            }
        }

        if (breakoutDetails.isNotEmpty()) {
            filter2Passed = true
            val (originIdx, originType) = breakoutDetails.first()
            filter2OriginDate = series.getBar(originIdx).endTime.toLocalDate()
            filter2LatestDate = series.getBar(breakoutDetails.last().first).endTime.toLocalDate()
            filter2Type = originType
        }

        val trendSinceFilter1 = buildTrendSinceFilter1(series, filter1OriginIdx)

        // Alert Status
        var alertStatus = "NORMAL"
        val todayClose = series.getBar(lastIndex).closePrice.doubleValue()
        val todayOpen = series.getBar(lastIndex).openPrice.doubleValue()
        val todayMiddle = bbMiddle.getValue(lastIndex).doubleValue()
        val isTodayGreen = todayClose > todayOpen

        if (filter2Passed && filter2LatestDate == today) { 
             // check if it triggered today exactly
             val typeToday = checkBreakout(series, bbUpper, bbMiddle, volume, volSma20, lastIndex)
             if (typeToday != null) {
                 alertStatus = "TRIGGERED_TODAY"
             }
        }

        if (alertStatus == "NORMAL") {
            if (filter2Passed && filter1LatestDate != null && filter2OriginDate != null && filter2OriginDate.isAfter(filter1LatestDate)) {
                alertStatus = "STALE_USED"
            } else if (filter1Passed) {
                if (todayClose > todayMiddle && isTodayGreen) {
                    alertStatus = "DAY_1_ALERT"
                } else if (isTodayGreen) {
                    alertStatus = "SQUEEZING_GREEN"
                } else {
                    alertStatus = "ACTIVE_SQUEEZE"
                }
            }
        }

        val curRsi = rsi14.getValue(lastIndex).doubleValue()
        val maxRsi52w = HighestValueIndicator(rsi14, 252).getValue(lastIndex).doubleValue()
        val high1y = HighestValueIndicator(closePrice, 252).getValue(lastIndex).doubleValue()
        val maxDrawdownPct = if (high1y > 0.0) ((todayClose - high1y) / high1y) * 100.0 else 0.0

        return BollingerSqueezeScanResult(
            symbol = metadata.symbol,
            companyName = metadata.companyName,
            instrumentToken = metadata.instrumentToken,
            ltp = todayClose.roundTo2(),
            above200Sma = todayClose >= sma200.getValue(lastIndex).doubleValue(),
            filter1Passed = filter1Passed,
            filter1OriginDate = filter1OriginDate?.toString(),
            filter1LatestDate = filter1LatestDate?.toString(),
            filter2Passed = filter2Passed,
            filter2OriginDate = filter2OriginDate?.toString(),
            filter2LatestDate = filter2LatestDate?.toString(),
            filter2Type = filter2Type,
            alertStatus = alertStatus,
            trendPatternFromFilter1 = trendSinceFilter1?.pattern,
            trendOverallFromFilter1 = trendSinceFilter1?.overall,
            trendNetMovePctFromFilter1 = trendSinceFilter1?.netMovePct?.roundTo2(),
            currentRsi = if (curRsi.isFinite()) curRsi.roundTo2() else null,
            triggerRsi = if (filter2OriginDate != null) {
                 // find index of filter2OriginDate
                 val idx = (0..lastIndex).find { series.getBar(it).endTime.toLocalDate() == filter2OriginDate }
                 idx?.let { rsi14.getValue(it).doubleValue().roundTo2() }
            } else null,
            maxRsi52w = if (maxRsi52w.isFinite()) maxRsi52w.roundTo2() else null,
            maxDrawdownPct = if (maxDrawdownPct.isFinite()) maxDrawdownPct.roundTo2() else 0.0,
            bbUpper = bbUpper.getValue(lastIndex).doubleValue().roundTo2(),
            bbMiddle = todayMiddle.roundTo2(),
            bbLower = bbLower.getValue(lastIndex).doubleValue().roundTo2()
        )
    }

    private fun buildTrendSinceFilter1(
        series: org.ta4j.core.BarSeries,
        filter1OriginIdx: Int?,
    ): TrendSinceFilter1? {
        if (filter1OriginIdx == null) return null

        val endIdx = series.endIndex
        if (filter1OriginIdx >= endIdx) {
            val startClose = series.getBar(filter1OriginIdx).closePrice.doubleValue()
            return TrendSinceFilter1(
                pattern = "NA",
                overall = "SIDEWAYS",
                netMovePct = 0.0.takeIf { startClose.isFinite() }
            )
        }

        val startClose = series.getBar(filter1OriginIdx).closePrice.doubleValue()
        val endClose = series.getBar(endIdx).closePrice.doubleValue()
        val netMovePct = if (startClose.isFinite() && startClose > 0.0 && endClose.isFinite()) {
            ((endClose - startClose) / startClose) * 100.0
        } else {
            null
        }

        val trendTokens = mutableListOf<String>()
        var currentDirection: Char? = null
        var runLength = 0

        for (i in (filter1OriginIdx + 1)..endIdx) {
            val prevClose = series.getBar(i - 1).closePrice.doubleValue()
            val curClose = series.getBar(i).closePrice.doubleValue()
            if (!prevClose.isFinite() || !curClose.isFinite()) continue

            val direction = when {
                curClose > prevClose -> 'U'
                curClose < prevClose -> 'D'
                else -> 'F'
            }

            if (currentDirection == null) {
                currentDirection = direction
                runLength = 1
            } else if (currentDirection == direction) {
                runLength += 1
            } else {
                trendTokens.add("${currentDirection}${runLength}")
                currentDirection = direction
                runLength = 1
            }
        }

        if (currentDirection != null && runLength > 0) {
            trendTokens.add("${currentDirection}${runLength}")
        }

        val pattern = if (trendTokens.isEmpty()) "NA" else trendTokens.joinToString("-")
        val overall = when {
            netMovePct == null -> "UNKNOWN"
            netMovePct > 0.0 -> "UPTREND"
            netMovePct < 0.0 -> "DOWNTREND"
            else -> "SIDEWAYS"
        }

        return TrendSinceFilter1(
            pattern = pattern,
            overall = overall,
            netMovePct = netMovePct
        )
    }

    private fun is3DaySqueeze(bandwidth: BollingerBandWidthIndicator, index: Int, tolerance: Double): Boolean {
        if (index < 2) return false
        return isSqueezeDay(bandwidth, index, tolerance) && 
               isSqueezeDay(bandwidth, index - 1, tolerance) && 
               isSqueezeDay(bandwidth, index - 2, tolerance)
    }

    private fun isSqueezeDay(bandwidth: BollingerBandWidthIndicator, index: Int, tolerance: Double): Boolean {
        val currentBw = bandwidth.getValue(index).doubleValue()
        val lookback = 60
        var minBw = Double.POSITIVE_INFINITY
        for (i in (index - lookback + 1).coerceAtLeast(0)..index) {
            val bw = bandwidth.getValue(i).doubleValue()
            if (bw < minBw) minBw = bw
        }
        return currentBw <= minBw * tolerance
    }

    private fun checkBreakout(
        series: org.ta4j.core.BarSeries, 
        bbUpper: BollingerBandsUpperIndicator, 
        bbMiddle: BollingerBandsMiddleIndicator,
        volume: VolumeIndicator, 
        volSma20: SMAIndicator, 
        index: Int
    ): String? {
        val bar = series.getBar(index)
        val close = bar.closePrice.doubleValue()
        val open = bar.openPrice.doubleValue()
        val upper = bbUpper.getValue(index).doubleValue()
        val middle = bbMiddle.getValue(index).doubleValue()
        val vol = volume.getValue(index).doubleValue()
        
        // Path B: Fast Day-1
        if (index > 0) {
            val prevClose = series.getBar(index - 1).closePrice.doubleValue()
            val priceChange = (close - prevClose) / prevClose
            val volAvg = volSma20.getValue(index - 1).doubleValue() // Exclude today
            
            if (close > upper && priceChange >= 0.08 && vol >= volAvg * 10.0) {
                return "FAST_1_DAY"
            }
        }

        // Path A: Standard 2-Day (Relaxed to Middle Band)
        if (index > 0) {
            val prevBar = series.getBar(index - 1)
            val prevClose = prevBar.closePrice.doubleValue()
            val prevOpen = prevBar.openPrice.doubleValue()
            val prevMiddle = bbMiddle.getValue(index - 1).doubleValue()
            
            if (close > middle && close > open && prevClose > prevMiddle && prevClose > prevOpen) {
                return "STANDARD_2_DAY"
            }
        }

        return null
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

    private data class TrendSinceFilter1(
        val pattern: String,
        val overall: String,
        val netMovePct: Double?,
    )
}
