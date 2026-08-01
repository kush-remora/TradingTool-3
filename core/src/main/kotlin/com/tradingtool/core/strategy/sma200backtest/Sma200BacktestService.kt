package com.tradingtool.core.strategy.sma200backtest

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.technical.calculateRsiValues
import java.time.LocalDate
import kotlin.math.abs

@Singleton
class Sma200BacktestService @Inject constructor(
    private val candleCacheService: CandleCacheService,
) {
    suspend fun run(request: Sma200BacktestRequest): Sma200BacktestResponse {
        require(request.symbol.isNotBlank()) { "symbol is required." }
        require(request.instrumentToken > 0) { "instrumentToken must be positive." }
        validateEntrySmaPeriod(request.entrySmaPeriod)

        val symbol = request.symbol.trim().uppercase()
        val toDate = LocalDate.now()
        val candles = loadCandles(symbol, request.instrumentToken, toDate)
        require(candles.isNotEmpty()) { "No daily candle data is available for $symbol." }

        val testedCandles = candles.filter { candle -> candle.candleDate >= toDate.minusYears(TEST_WINDOW_YEARS) }
        require(testedCandles.isNotEmpty()) { "No daily candle data is available in the last year for $symbol." }

        val sma50Values = calculateRollingSmaValues(candles, SMA50_WINDOW)
        val sma100Values = calculateRollingSmaValues(candles, SMA100_WINDOW)
        val sma200Values = calculateRollingSmaValues(candles, SMA200_WINDOW)
        val entrySmaValues = resolveEntrySmaValues(request.entrySmaPeriod, sma50Values, sma100Values, sma200Values)
        val rsiValues = candles.calculateRsiValues(period = RSI_PERIOD, fallback = RSI_FALLBACK)
        val firstTestIndex = candles.indexOfFirst { candle -> candle.candleDate >= testedCandles.first().candleDate }
        val trades = mutableListOf<Sma200BacktestTrade>()
        val (entryIndices, touchCount) = selectSmaEntryIndices(candles, entrySmaValues, firstTestIndex)
        entryIndices.forEach { index ->
            trades += buildTrade(candles, index, sma100Values[index], sma200Values[index], entrySmaValues[index], rsiValues[index])
        }

        val tradeRows = trades.toList()
        return Sma200BacktestResponse(
            symbol = symbol,
            entrySmaPeriod = request.entrySmaPeriod,
            testedFromDate = testedCandles.first().candleDate.toString(),
            testedToDate = testedCandles.last().candleDate.toString(),
            summary = Sma200BacktestSummary(
                smaTouchCount = touchCount,
                tradeCount = tradeRows.size,
                ignoredTouchCount = touchCount - tradeRows.size,
                completed10dCount = tradeRows.count { trade -> trade.return10dPct != null },
                completed20dCount = tradeRows.count { trade -> trade.return20dPct != null },
                completed40dCount = tradeRows.count { trade -> trade.return40dPct != null },
            ),
            trades = tradeRows,
        )
    }

    private suspend fun loadCandles(symbol: String, instrumentToken: Long, toDate: LocalDate): List<DailyCandle> {
        val fromDate = toDate.minusDays(HISTORY_DAYS)
        return candleCacheService.getDailyCandles(instrumentToken, symbol, fromDate, toDate)
            .sortedBy(DailyCandle::candleDate)
    }

    private fun buildTrade(
        candles: List<DailyCandle>,
        entryIndex: Int,
        sma100: Double,
        sma200: Double,
        entrySma: Double,
        rsi14: Double,
    ): Sma200BacktestTrade {
        val entryCandle = candles[entryIndex]
        return Sma200BacktestTrade(
            entryDate = entryCandle.candleDate.toString(),
            entryPrice = entrySma,
            entryClose = entryCandle.close,
            sma100 = sma100,
            pctToSma100 = percentageDistance(entryCandle.close, sma100),
            sma200 = sma200,
            pctToSma200 = percentageDistance(entryCandle.close, sma200),
            distanceToSma200AbsPct = abs(percentageDistance(entryCandle.close, sma200)),
            rsi14 = rsi14,
            drawdownFromHigh20Pct = drawdownFromRecentHigh(candles, entryIndex, DRAWDOWN_WINDOW_20),
            drawdownFromHigh60Pct = drawdownFromRecentHigh(candles, entryIndex, DRAWDOWN_WINDOW_60),
            consecutiveRedDays = countConsecutiveRedDays(candles, entryIndex),
            move3dPct = move3d(candles, entryIndex),
            return10dPct = forwardReturn(candles, entryIndex, entrySma, RETURN_10D),
            return20dPct = forwardReturn(candles, entryIndex, entrySma, RETURN_20D),
            return40dPct = forwardReturn(candles, entryIndex, entrySma, RETURN_40D),
            return10dDate = forwardDate(candles, entryIndex, RETURN_10D),
            return20dDate = forwardDate(candles, entryIndex, RETURN_20D),
            return40dDate = forwardDate(candles, entryIndex, RETURN_40D),
        )
    }

    private companion object {
        const val TEST_WINDOW_YEARS: Long = 1
        const val HISTORY_DAYS: Long = 800
        const val SMA50_WINDOW: Int = 50
        const val SMA100_WINDOW: Int = 100
        const val SMA200_WINDOW: Int = 200
        const val RSI_PERIOD: Int = 14
        const val RSI_FALLBACK: Double = 50.0
        const val RETURN_10D: Int = 10
        const val RETURN_20D: Int = 20
        const val RETURN_40D: Int = 40
        const val DRAWDOWN_WINDOW_20: Int = 20
        const val DRAWDOWN_WINDOW_60: Int = 60
    }
}

internal fun calculateRollingSmaValues(candles: List<DailyCandle>, window: Int): List<Double> {
    if (candles.isEmpty()) return emptyList()
    val values = DoubleArray(candles.size)
    var rollingSum = 0.0
    for (index in candles.indices) {
        rollingSum += candles[index].close
        if (index >= window) rollingSum -= candles[index - window].close
        values[index] = rollingSum / minOf(window, index + 1)
    }
    return values.toList()
}

private fun percentageDistance(price: Double, average: Double): Double = ((price / average) - 1.0) * 100.0

private fun forwardReturn(candles: List<DailyCandle>, entryIndex: Int, entryPrice: Double, days: Int): Double? {
    val exitClose = candles.getOrNull(entryIndex + days)?.close ?: return null
    return ((exitClose / entryPrice) - 1.0) * 100.0
}

private fun forwardDate(candles: List<DailyCandle>, entryIndex: Int, days: Int): String? =
    candles.getOrNull(entryIndex + days)?.candleDate?.toString()

private fun drawdownFromRecentHigh(candles: List<DailyCandle>, index: Int, window: Int): Double {
    val start = (index - window + 1).coerceAtLeast(0)
    val high = candles.subList(start, index + 1).maxOf { it.high }
    return ((candles[index].close / high) - 1.0) * 100.0
}

private fun countConsecutiveRedDays(candles: List<DailyCandle>, index: Int): Int {
    var count = 0
    for (currentIndex in index downTo 1) {
        if (candles[currentIndex].close >= candles[currentIndex - 1].close) break
        count += 1
    }
    return count
}

private fun move3d(candles: List<DailyCandle>, index: Int): Double {
    val baseClose = candles.getOrNull(index - 3)?.close ?: return 0.0
    return ((candles[index].close / baseClose) - 1.0) * 100.0
}

internal fun selectSmaEntryIndices(
    candles: List<DailyCandle>,
    entrySmaValues: List<Double>,
    firstTestIndex: Int,
): Pair<List<Int>, Int> {
    val entries = mutableListOf<Int>()
    var touchCount = 0
    var nextEligibleIndex = firstTestIndex

    for (index in firstTestIndex..candles.lastIndex) {
        if (candles[index].low > entrySmaValues[index]) continue
        touchCount += 1
        if (index < nextEligibleIndex) continue
        entries += index
        nextEligibleIndex = index + HOLDING_DAYS + 1
    }
    return entries to touchCount
}

internal fun validateEntrySmaPeriod(entrySmaPeriod: Int) {
    require(entrySmaPeriod in ENTRY_SMA_PERIODS) { "entrySmaPeriod must be 50, 100, or 200." }
}

internal fun resolveEntrySmaValues(
    entrySmaPeriod: Int,
    sma50Values: List<Double>,
    sma100Values: List<Double>,
    sma200Values: List<Double>,
): List<Double> = when (entrySmaPeriod) {
    50 -> sma50Values
    100 -> sma100Values
    200 -> sma200Values
    else -> throw IllegalArgumentException("entrySmaPeriod must be 50, 100, or 200.")
}

private const val HOLDING_DAYS: Int = 40
private val ENTRY_SMA_PERIODS: Set<Int> = setOf(50, 100, 200)
