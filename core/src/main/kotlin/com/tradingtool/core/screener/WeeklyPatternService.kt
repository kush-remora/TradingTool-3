package com.tradingtool.core.screener

import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.technical.calculateRsiValues
import com.tradingtool.core.technical.roundTo2
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

/**
 * Reads raw daily candle data from the database and dynamically computes the 
 * optimal buy and sell days per week using the 1% Rebound Entry logic.
 */
class WeeklyPatternService(
    private val stockHandler: StockJdbiHandler,
    private val candleCache: CandleCacheService,
) {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val df = DateTimeFormatter.ofPattern("MMM dd")

    private val dayNames = listOf("", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    data class DayPairEval(
        val buyDay: Int,
        val sellDay: Int,
        val avgDipPct: Double,
        val reboundConsistency: Int,
        val avgSwingPct: Double,
        val swingConsistency: Int,
        val compositeScore: Int,
        val weeksCount: Int,
        val allWeeks: List<WeekInstance>
    )

    data class WeekInstance(
        val isoYear: Int,
        val isoWeek: Int,
        val startDate: LocalDate?,
        val endDate: LocalDate?,
        val dailyCandles: Map<Int, DailyCandle>,
        var buyDayDipPct: Double? = null,
        var swingPct: Double? = null,
        var maxPotentialPct: Double? = null, // Monday Low to Week High
        var buyDayLow: Double? = null,
        var entryTriggered: Boolean = false,
        var swingTargetHit: Boolean = false,
        var buyPriceActual: Double? = null,
        var sellPriceActual: Double? = null,
        var buyRsi: Double? = null,
        var reasoning: String? = null
    )

    suspend fun analyze(symbols: List<String>): List<WeeklyPatternResult> =
        symbols.map { analyzeSymbol(it) }

    private suspend fun analyzeSymbol(symbol: String): WeeklyPatternResult {
        val stock = stockHandler.read { it.getBySymbol(symbol, "NSE") }
            ?: return noData(symbol, "Unknown", "symbol_not_in_watchlist")

        val token = stock.instrumentToken
        val today = LocalDate.now(ist)
        val from = today.minusYears(5)
        
        val allYearCandles = candleCache.getDailyCandles(token, symbol, from, today)
        if (allYearCandles.isEmpty()) {
            return noData(symbol, stock.companyName, "no_candle_data")
        }

        val rsiMap = buildRsiBoundsMap(allYearCandles)

        val last70DaysDate = today.minusDays(70)
        val evaluationCandles = allYearCandles.filter { !it.candleDate.isBefore(last70DaysDate) }

        val bestPair = findBestDayPair(evaluationCandles, rsiMap)
        if (bestPair == null || bestPair.weeksCount < 6) {
            return noData(symbol, stock.companyName, "insufficient_data")
        }

        val minLow = bestPair.allWeeks.mapNotNull { it.buyDayLow }.minOrNull() ?: 0.0
        val maxLow = bestPair.allWeeks.mapNotNull { it.buyDayLow }.maxOrNull() ?: 0.0
        val avgPotential = bestPair.allWeeks.mapNotNull { it.maxPotentialPct }.average().roundTo2()

        return WeeklyPatternResult(
            symbol = symbol,
            instrumentToken = token,
            companyName = stock.companyName,
            weeksAnalyzed = bestPair.weeksCount,
            buyDay = dayNames[bestPair.buyDay],
            buyDayAvgDipPct = bestPair.avgDipPct,
            reboundConsistency = bestPair.reboundConsistency,
            sellDay = dayNames[bestPair.sellDay],
            swingAvgPct = bestPair.avgSwingPct,
            avgPotentialPct = avgPotential,
            swingConsistency = bestPair.swingConsistency,
            compositeScore = bestPair.compositeScore,
            patternConfirmed = bestPair.reboundConsistency >= 5 && bestPair.swingConsistency >= 3 && bestPair.avgSwingPct > 0.0,
            cycleType = "Weekly",
            buyDayLowMin = minLow.roundTo2(),
            buyDayLowMax = maxLow.roundTo2(),
        )
    }

    private fun findBestDayPair(candles: List<DailyCandle>, rsiMap: Map<LocalDate, RsiBounds>): DayPairEval? {
        val weeksMap = candles.groupBy { candle ->
            Pair(candle.candleDate.get(WeekFields.ISO.weekBasedYear()), candle.candleDate.get(WeekFields.ISO.weekOfWeekBasedYear()))
        }

        val parsedWeeks = weeksMap.map { (key, weekCandles) ->
            WeekInstance(
                isoYear = key.first,
                isoWeek = key.second,
                startDate = weekCandles.minOfOrNull { it.candleDate },
                endDate = weekCandles.maxOfOrNull { it.candleDate },
                dailyCandles = weekCandles.associateBy { it.candleDate.get(WeekFields.ISO.dayOfWeek()) }
            )
        }.sortedWith(compareBy({ it.isoYear }, { it.isoWeek }))

        val totalWeeks = parsedWeeks.size
        if (totalWeeks == 0) return null

        var bestEval: DayPairEval? = null

        // Loop through potential buy days (Mon to Wed)
        for (buy in 1..3) {
            val sell = 4 // Fixed Thursday Exit logic
                
            val evaluatedWeeks = parsedWeeks.map { w ->
                val wCopy = w.copy()
                val bCandle = wCopy.dailyCandles[buy]
                
                if (bCandle != null) {
                    if (bCandle.open > 0) {
                        wCopy.buyDayDipPct = ((bCandle.open - bCandle.low) / bCandle.open * 100.0).roundTo2()
                    }
                    wCopy.buyDayLow = bCandle.low
                    
                    // Ideal Potential: Monday Low to Thursday High
                    val weekHigh = (buy..4).mapNotNull { wCopy.dailyCandles[it]?.high }.maxOrNull() ?: bCandle.high
                    if (bCandle.low > 0) {
                        wCopy.maxPotentialPct = ((weekHigh - bCandle.low) / bCandle.low * 100.0).roundTo2()
                    }

                    // Use PREVIOUS candle's RSI to decide if we are overbought ENTERING the week
                    val prevCandleDate = parsedWeeks.getOrNull(parsedWeeks.indexOf(w) - 1)?.endDate
                    val rsiBounds = rsiMap[prevCandleDate ?: bCandle.candleDate]
                    val entryRsi = rsiBounds?.current ?: 50.0
                    val max200Rsi = rsiBounds?.max200 ?: 70.0
                    wCopy.buyRsi = entryRsi

                    // 1% Rebound Entry Trigger Logic
                    val potentialEntryPrice = bCandle.low * 1.01
                    
                    // Shadow Price: Always calculate what the entry WOULD have been for visualization
                    wCopy.buyPriceActual = potentialEntryPrice
                    val thuCandleForShadow = wCopy.dailyCandles[4] ?: wCopy.dailyCandles[3] ?: wCopy.dailyCandles[2]
                    if (thuCandleForShadow != null) {
                        wCopy.sellPriceActual = thuCandleForShadow.close
                    }

                    if (bCandle.high >= potentialEntryPrice) {
                        // Tightened Overbought: > 65 or > 90% of 200-day high
                        if (entryRsi >= 65.0 || (max200Rsi > 0 && entryRsi >= max200Rsi * 0.90)) {
                            wCopy.entryTriggered = false
                            wCopy.reasoning = "Overbought (RSI: ${entryRsi.roundTo2()})"
                        } else {
                            wCopy.entryTriggered = true
                            val entryPrice = potentialEntryPrice
                            
                            val targetPrice = entryPrice * 1.05
                            val stopLossPrice = entryPrice * 0.97

                            // Simulate day-by-day progression
                            var exitFound = false
                            for (dayIdx in (buy + 1)..4) { 
                                val dCandle = wCopy.dailyCandles[dayIdx]
                                if (dCandle != null) {
                                    if (dCandle.low <= stopLossPrice) {
                                        wCopy.sellPriceActual = stopLossPrice
                                        wCopy.swingPct = -3.0
                                        wCopy.reasoning = "Stop Loss Hit"
                                        exitFound = true
                                        break
                                    }
                                    if (dCandle.high >= targetPrice) {
                                        wCopy.sellPriceActual = targetPrice
                                        wCopy.swingPct = 5.0
                                        wCopy.swingTargetHit = true
                                        wCopy.reasoning = "Target Hit (+5%)"
                                        exitFound = true
                                        break
                                    }
                                }
                            }

                            if (!exitFound) {
                                val thuCandle = wCopy.dailyCandles[4] ?: wCopy.dailyCandles[3] ?: wCopy.dailyCandles[2]
                                if (thuCandle != null) {
                                    val exitPrice = thuCandle.close
                                    wCopy.sellPriceActual = exitPrice
                                    wCopy.swingPct = ((exitPrice - entryPrice) / entryPrice * 100.0).roundTo2()
                                    wCopy.reasoning = "Thursday Hard Exit"
                                    if (wCopy.swingPct!! >= 4.0) {
                                        wCopy.swingTargetHit = true
                                    }
                                }
                            }
                        }
                    } else {
                        wCopy.entryTriggered = false
                        wCopy.reasoning = "No 1% rebound"
                    }
                }
                wCopy
            }

            val entryWeeks = evaluatedWeeks.filter { it.entryTriggered }
            val swingWeeks = evaluatedWeeks.filter { it.swingPct != null }

            val avgDip = if (evaluatedWeeks.isNotEmpty()) evaluatedWeeks.mapNotNull { it.buyDayDipPct }.average() else 0.0
            val avgSwing = if (swingWeeks.isNotEmpty()) swingWeeks.mapNotNull { it.swingPct }.average() else 0.0

            val reboundCons = entryWeeks.size
            val swingCons = evaluatedWeeks.count { it.swingTargetHit }

            val dipScore = (reboundCons.toDouble() / totalWeeks * 30).toInt()
            val swingScore = (swingCons.toDouble() / totalWeeks * 40).toInt()
            val magnitudeScore = (avgSwing.coerceIn(0.0, 10.0) / 10.0 * 30).toInt()
            val composite = dipScore + swingScore + magnitudeScore

            val eval = DayPairEval(
                buy, sell, avgDip.roundTo2(), reboundCons, avgSwing.roundTo2(), swingCons, 
                composite, totalWeeks, evaluatedWeeks
            )
            
            if (bestEval == null || eval.compositeScore > bestEval.compositeScore) {
                bestEval = eval
            }
        }
        return bestEval
    }

    suspend fun analyzeDetail(symbol: String): WeeklyPatternDetail? {
        val stock = stockHandler.read { it.getBySymbol(symbol, "NSE") } ?: return null
        val token = stock.instrumentToken
        val today = LocalDate.now(ist)
        val from = today.minusYears(5)

        val allYearCandles = candleCache.getDailyCandles(token, symbol, from, today)
        if (allYearCandles.isEmpty()) return null

        val rsiMap = buildRsiBoundsMap(allYearCandles)

        val last70DaysDate = today.minusDays(70)
        val evaluationCandles = allYearCandles.filter { !it.candleDate.isBefore(last70DaysDate) }

        val bestPair = findBestDayPair(evaluationCandles, rsiMap) ?: return null
        
        val minLow = bestPair.allWeeks.mapNotNull { it.buyDayLow }.minOrNull() ?: 0.0
        val maxLow = bestPair.allWeeks.mapNotNull { it.buyDayLow }.maxOrNull() ?: 0.0

        val dailyReturns = mutableListOf<Double>()
        val returnsByDate = mutableMapOf<LocalDate, Double>()
        for (i in 1 until evaluationCandles.size) {
            val prev = evaluationCandles[i - 1]
            val curr = evaluationCandles[i]
            if (prev.close > 0) {
                val ret = (curr.close - prev.close) / prev.close * 100.0
                dailyReturns.add(ret)
                returnsByDate[curr.candleDate] = ret
            }
        }

        fun getAvgRetForDay(dayOfWeek: Int): Double {
            val rets = evaluationCandles.filter { it.candleDate.get(WeekFields.ISO.dayOfWeek()) == dayOfWeek }
                .mapNotNull { returnsByDate[it.candleDate] }
            return if (rets.isNotEmpty()) rets.average().roundTo2() else 0.0
        }

        val profile = (1..5).map { d ->
            val status = when {
                d == bestPair.buyDay -> "Buy zone"
                d == bestPair.sellDay -> "Sell zone"
                d in (bestPair.buyDay + 1) until bestPair.sellDay -> "Hold"
                else -> "Watch"
            }
            DayProfile(dayNames[d], status, getAvgRetForDay(d))
        }

        val autocorrel = AutocorrelationResult(
            lag5 = pearsonCorrel(dailyReturns, 5).roundTo2(),
            lag10 = pearsonCorrel(dailyReturns, 10).roundTo2(),
            lag21 = pearsonCorrel(dailyReturns, 21).roundTo2()
        )

        val confirmed = bestPair.reboundConsistency >= 5 && bestPair.swingConsistency >= 3 && bestPair.avgSwingPct > 0.0
        val summary = if (confirmed) {
            "Strong weekly rhythm detected. Entry triggered on ${dayNames[bestPair.buyDay]} via 1% rebound. Strategy: GTT Sell at +5% Target / -3% SL, or hard-exit by Thursday 2:00 PM."
        } else {
            "No consistent safe swing rhythm detected. Entry criteria (rebound/consistency) not met for a reliable 5% GTT target."
        }

        val avgPotential = bestPair.allWeeks.mapNotNull { it.maxPotentialPct }.average().roundTo2()

        val heatmap = bestPair.allWeeks.mapIndexed { idx, w ->
            fun getRet(day: Int) = w.dailyCandles[day]?.let { returnsByDate[it.candleDate]?.roundTo2() }

            WeekHeatmapRow(
                weekLabel = "W-${bestPair.allWeeks.size - idx}",
                startDate = w.startDate?.format(df) ?: "",
                endDate = w.endDate?.format(df) ?: "",
                mondayChangePct = getRet(1),
                tuesdayChangePct = getRet(2),
                wednesdayChangePct = getRet(3),
                thursdayChangePct = w.dailyCandles[4]?.let { ((it.close - it.open) / it.open * 100).roundTo2() },
                fridayChangePct = w.dailyCandles[5]?.let { ((it.close - it.open) / it.open * 100).roundTo2() },
                entryTriggered = w.entryTriggered,
                swingTargetHit = w.swingTargetHit,
                buyPriceActual = w.buyPriceActual?.roundTo2(),
                sellPriceActual = w.sellPriceActual?.roundTo2(),
                buyRsi = w.buyRsi?.roundTo2(),
                netSwingPct = w.swingPct?.roundTo2(),
                maxPotentialPct = w.maxPotentialPct?.roundTo2(),
                reasoning = when {
                    w.reasoning != null -> w.reasoning
                    !w.entryTriggered -> "No 1% rebound"
                    else -> "No data"
                }
            )
        }

        return WeeklyPatternDetail(
            symbol = symbol,
            instrumentToken = token,
            companyName = stock.companyName,
            weeksAnalyzed = bestPair.weeksCount,
            buyDay = dayNames[bestPair.buyDay],
            buyDayAvgDipPct = bestPair.avgDipPct,
            reboundConsistency = bestPair.reboundConsistency,
            sellDay = dayNames[bestPair.sellDay],
            swingAvgPct = bestPair.avgSwingPct,
            avgPotentialPct = avgPotential,
            swingConsistency = bestPair.swingConsistency,
            compositeScore = bestPair.compositeScore,
            patternConfirmed = confirmed,
            cycleType = "Weekly",
            reason = null,
            buyDayLowMin = minLow.roundTo2(),
            buyDayLowMax = maxLow.roundTo2(),
            dayOfWeekProfile = profile,
            autocorrelation = autocorrel,
            patternSummary = summary,
            weeklyHeatmap = heatmap.reversed()
        )
    }

    private fun noData(symbol: String, companyName: String, reason: String) = WeeklyPatternResult(
        symbol = symbol,
        instrumentToken = 0L,
        companyName = companyName,
        weeksAnalyzed = 0,
        buyDay = "",
        buyDayAvgDipPct = 0.0,
        reboundConsistency = 0,
        sellDay = "",
        swingAvgPct = 0.0,
        avgPotentialPct = 0.0,
        swingConsistency = 0,
        compositeScore = 0,
        patternConfirmed = false,
        cycleType = "None",
        reason = reason,
        buyDayLowMin = 0.0,
        buyDayLowMax = 0.0,
    )

    data class RsiBounds(
        val current: Double,
        val min200: Double,
        val max200: Double,
    )

    private fun buildRsiBoundsMap(candles: List<DailyCandle>): Map<LocalDate, RsiBounds> {
        val rsiValues = candles.calculateRsiValues(period = 14, fallback = 50.0)

        return candles.mapIndexed { index, candle ->
            val currentRsi = rsiValues.getOrNull(index) ?: 50.0
            val lookbackStart = maxOf(0, index - 200)

            // Ensure we have at least some values to calculate min/max
            val window = if (index > 0) rsiValues.subList(lookbackStart, index) else listOf(currentRsi)

            val max200Rsi = window.maxOrNull() ?: currentRsi
            val min200Rsi = window.minOrNull() ?: currentRsi

            candle.candleDate to RsiBounds(current = currentRsi, min200 = min200Rsi, max200 = max200Rsi)
        }.toMap()
    }
    private fun pearsonCorrel(series: List<Double>, lag: Int): Double {
        if (series.size <= lag + 1) return 0.0
        val x = series.drop(lag)
        val y = series.dropLast(lag)
        val avgX = x.average()
        val avgY = y.average()
        var num = 0.0
        var denX = 0.0
        var denY = 0.0
        for (i in x.indices) {
            val dx = x[i] - avgX
            val dy = y[i] - avgY
            num += dx * dy
            denX += dx * dx
            denY += dy * dy
        }
        return if (denX == 0.0 || denY == 0.0) 0.0 else num / Math.sqrt(denX * denY)
    }
}
