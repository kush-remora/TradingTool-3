package com.tradingtool.core.screener

import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.model.stock.Stock
import com.tradingtool.core.technical.AdaptiveRsi
import com.tradingtool.core.technical.StrategyTechnicalSignals
import com.tradingtool.core.technical.roundTo2
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import kotlin.math.abs

/**
 * Reads raw daily candle data from the database and dynamically computes
 * stock-specific weekly buy/sell day pairs.
 */
class WeeklyPatternService(
    private val stockHandler: StockJdbiHandler,
    private val candleCache: CandleCacheService,
    private val patternConfigService: WeeklyPatternConfigService,
) {
    private val log = LoggerFactory.getLogger(WeeklyPatternService::class.java)
    private val ist = ZoneId.of("Asia/Kolkata")
    private val df = DateTimeFormatter.ofPattern("MMM dd")
    private val candidateBuyDays = 1..3

    private val dayNames = listOf("", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    // Limit peak concurrency to protect database and Redis connection pools
    private val analysisSemaphore = Semaphore(15)

    data class DayPairEval(
        val buyDay: Int,
        val sellDay: Int,
        val effectiveSellDay: Int,
        val avgDipPct: Double,
        val reboundConsistency: Int,
        val avgSwingPct: Double,
        val swingConsistency: Int,
        val compositeScore: Int,
        val weeksCount: Int,
        val eligibleWeeksCount: Int,
        val allWeeks: List<WeekInstance>,
    )

    private data class SeasonalityAnalysis(
        val eval: DayPairEval,
        val targetScenarios: List<TargetScenario>,
        val targetRecommendation: TargetRecommendation?,
    )

    data class WeekInstance(
        val isoYear: Int,
        val isoWeek: Int,
        val startDate: LocalDate?,
        val endDate: LocalDate?,
        val dailyCandles: Map<Int, DailyCandle>,
        var buyDayDipPct: Double? = null,
        var swingPct: Double? = null,
        var maxPotentialPct: Double? = null,
        var buyDayLow: Double? = null,
        var entryTriggered: Boolean = false,
        var swingTargetHit: Boolean = false,
        var buyPriceActual: Double? = null,
        var sellPriceActual: Double? = null,
        var exitDay: Int? = null,
        var buyRsi: Double? = null,
        var reasoning: String? = null,
    )

    fun lookbackWeeks(): Int = patternConfigService.loadConfig().lookbackWeeks
    fun buyZoneLookbackWeeks(): Int = patternConfigService.loadConfig().buyZoneLookbackWeeks

    suspend fun analyze(symbols: List<String>): List<WeeklyPatternResult> = coroutineScope {
        val startTime = System.currentTimeMillis()
        val config = patternConfigService.loadConfig()
        log.info("Starting weekly pattern analysis for {} symbols", symbols.size)

        val allStocks = stockHandler.read { dao ->
            dao.listBySymbols(symbols, "NSE")
        }.associateBy { it.symbol }

        val results = symbols.map { symbol ->
            async {
                analysisSemaphore.withPermit {
                    val stock = allStocks[symbol]
                    if (stock == null) {
                        noData(symbol, "Unknown", "symbol_not_in_watchlist")
                    } else {
                        analyzeSymbol(stock, config)
                    }
                }
            }
        }.awaitAll()

        log.info(
            "Completed weekly pattern analysis for {} symbols in {}ms",
            symbols.size,
            System.currentTimeMillis() - startTime
        )
        results
    }

    private suspend fun analyzeSymbol(stock: Stock, config: WeeklyPatternConfig): WeeklyPatternResult {
        val symbol = stock.symbol
        val token = stock.instrumentToken
        val today = LocalDate.now(ist)
        val from = today.minusYears(5)

        val allYearCandles = candleCache.getDailyCandles(token, symbol, from, today)
        if (allYearCandles.isEmpty()) {
            return noData(symbol, stock.companyName, "no_candle_data")
        }

        val rsiMap = StrategyTechnicalSignals.buildRollingRsiBoundsMap(
            candles = allYearCandles,
            windowSize = config.rsiEntry.lookbackDays,
        )
        val completedWeeks = selectCompletedWeeks(
            candles = allYearCandles,
            today = today,
            minTradingDaysPerWeek = config.minTradingDaysPerWeek,
        )
        val analysisWeeks = completedWeeks.takeLast(config.lookbackWeeks)

        val analysis = analyzeSeasonality(analysisWeeks, rsiMap, config)
        val bestPair = analysis?.eval
        if (bestPair == null || bestPair.eligibleWeeksCount < config.minWeeksRequired) {
            return noData(symbol, stock.companyName, "insufficient_data")
        }

        val (minLow, maxLow) = calculateBuyDayLowRange(
            completedWeeks = completedWeeks,
            buyDay = bestPair.buyDay,
            buyZoneLookbackWeeks = config.buyZoneLookbackWeeks,
        )
        val avgPotential = bestPair.allWeeks.mapNotNull { it.maxPotentialPct }.average().roundTo2()
        val targetRecommendation = analysis.targetRecommendation

        val vcpTightness = if (analysisWeeks.size >= 4) {
            analysisWeeks.takeLast(4).map { week ->
                val low = week.dailyCandles.values.minOf { it.low }
                val high = week.dailyCandles.values.maxOf { it.high }
                if (low > 0) ((high - low) / low) * 100.0 else 0.0
            }.average().roundTo2()
        } else null

        val upVol = analysisWeeks.flatMap { it.dailyCandles.values }.filter { it.close > it.open }.sumOf { it.volume }.toDouble()
        val downVol = analysisWeeks.flatMap { it.dailyCandles.values }.filter { it.close < it.open }.sumOf { it.volume }.toDouble()
        val volumeSignature = if (downVol > 0) (upVol / downVol).roundTo2() else 1.0

        val mondayWeeks = analysisWeeks.filter { it.dailyCandles.containsKey(DayOfWeek.MONDAY.value) }
        val mondayStrikeRate = if (mondayWeeks.isNotEmpty()) {
            val successes = mondayWeeks.count { week ->
                val entry = week.dailyCandles[DayOfWeek.MONDAY.value]!!.open
                val weekHigh = week.dailyCandles.values.maxOf { it.high }
                weekHigh >= (entry * 1.05)
            }
            (successes.toDouble() / mondayWeeks.size * 100.0).roundTo2()
        } else 0.0

        val latestDate = allYearCandles.last().candleDate
        val latestRsi = rsiMap[latestDate] ?: rsiMap.values.lastOrNull()
        val currentRsiStatus = if (latestRsi != null) {
            AdaptiveRsi.getStatus(
                currentRsi = latestRsi.current,
                lowestRsi = latestRsi.lowest,
                highestRsi = latestRsi.highest,
                overboughtPercentile = config.rsiEntry.overboughtPercentile,
            )
        } else {
            null
        }

        return WeeklyPatternResult(
            symbol = symbol,
            exchange = stock.exchange,
            instrumentToken = token,
            companyName = stock.companyName,
            weeksAnalyzed = bestPair.weeksCount,
            buyDay = dayNames[bestPair.buyDay],
            entryReboundPct = config.entryReboundPct.roundTo2(),
            rsiLookbackDays = config.rsiEntry.lookbackDays,
            rsiOverboughtPercentile = config.rsiEntry.overboughtPercentile.roundTo2(),
            stopLossPct = config.stopLossPct.roundTo2(),
            buyDayAvgDipPct = bestPair.avgDipPct,
            reboundConsistency = bestPair.reboundConsistency,
            sellDay = dayNames[bestPair.effectiveSellDay],
            swingAvgPct = bestPair.avgSwingPct,
            avgPotentialPct = avgPotential,
            swingConsistency = bestPair.swingConsistency,
            compositeScore = bestPair.compositeScore,
            patternConfirmed = isPatternConfirmed(bestPair, config),
            cycleType = "Weekly",
            buyDayLowMin = minLow.roundTo2(),
            buyDayLowMax = maxLow.roundTo2(),
            currentRsiStatus = currentRsiStatus,
            targetRecommendation = targetRecommendation,
            vcpTightnessPct = vcpTightness,
            volumeSignatureRatio = volumeSignature,
            mondayStrikeRatePct = mondayStrikeRate,
        )
    }

    private fun selectCompletedWeeks(
        candles: List<DailyCandle>,
        today: LocalDate,
        minTradingDaysPerWeek: Int,
    ): List<WeekInstance> {
        val currentWeekStart = today.minusDays((today.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())

        val parsedWeeks = candles.groupBy { candle ->
            Pair(
                candle.candleDate.get(WeekFields.ISO.weekBasedYear()),
                candle.candleDate.get(WeekFields.ISO.weekOfWeekBasedYear())
            )
        }.map { (key, weekCandles) ->
            WeekInstance(
                isoYear = key.first,
                isoWeek = key.second,
                startDate = weekCandles.minOfOrNull { it.candleDate },
                endDate = weekCandles.maxOfOrNull { it.candleDate },
                dailyCandles = weekCandles.associateBy { it.candleDate.get(WeekFields.ISO.dayOfWeek()) }
            )
        }
            .sortedWith(compareBy({ it.isoYear }, { it.isoWeek }))
            .filter { week ->
                val weekEnd = week.endDate ?: return@filter false
                weekEnd.isBefore(currentWeekStart) && week.dailyCandles.size >= minTradingDaysPerWeek
            }
        return parsedWeeks
    }

    private fun calculateBuyDayLowRange(
        completedWeeks: List<WeekInstance>,
        buyDay: Int,
        buyZoneLookbackWeeks: Int,
    ): Pair<Double, Double> {
        val lowSamples = completedWeeks
            .takeLast(buyZoneLookbackWeeks)
            .mapNotNull { week -> week.dailyCandles[buyDay]?.low }

        if (lowSamples.isEmpty()) return Pair(0.0, 0.0)
        return Pair(lowSamples.minOrNull() ?: 0.0, lowSamples.maxOrNull() ?: 0.0)
    }

    private fun analyzeSeasonality(
        parsedWeeks: List<WeekInstance>,
        rsiMap: Map<LocalDate, com.tradingtool.core.technical.RollingRsiBounds>,
        config: WeeklyPatternConfig,
    ): SeasonalityAnalysis? {
        val totalWeeks = parsedWeeks.size
        if (totalWeeks == 0) return null

        return candidateBuyDays
            .mapNotNull { buyDay ->
                analyzeBuyDayCandidate(
                    parsedWeeks = parsedWeeks,
                    rsiMap = rsiMap,
                    config = config,
                    totalWeeks = totalWeeks,
                    buyDay = buyDay,
                )
            }
            .maxWithOrNull(
                compareBy<SeasonalityAnalysis> { it.eval.compositeScore }
                    .thenBy { it.targetRecommendation?.expectedWinRatePct ?: 0.0 }
                    .thenBy { it.eval.reboundConsistency }
                    .thenBy { it.eval.buyDay }
            )
    }

    private fun analyzeBuyDayCandidate(
        parsedWeeks: List<WeekInstance>,
        rsiMap: Map<LocalDate, com.tradingtool.core.technical.RollingRsiBounds>,
        config: WeeklyPatternConfig,
        totalWeeks: Int,
        buyDay: Int,
    ): SeasonalityAnalysis? {
        val eligibleWeeks = parsedWeeks.count { week ->
            week.dailyCandles[buyDay] != null && week.dailyCandles[5] != null
        }
        if (eligibleWeeks == 0) return null

        val fallbackSellDay = resolveObservedSellDay(parsedWeeks, buyDay)
        val targetScenarios = evaluateTargetScenarios(
            parsedWeeks = parsedWeeks,
            buyDay = buyDay,
            sellDay = 5,
            rsiMap = rsiMap,
            config = config,
            requireObservedLowDay = false,
        )
        val targetRecommendation = buildTargetRecommendation(targetScenarios, config)
        val recommendedTargetPct = targetRecommendation?.recommendedTargetPct ?: config.swingTargetPct
        val simulatedWeeks = simulateWeeksForTarget(
            parsedWeeks = parsedWeeks,
            buyDay = buyDay,
            sellDay = 5,
            targetPct = recommendedTargetPct,
            rsiMap = rsiMap,
            config = config,
            requireObservedLowDay = false,
        )

        val entryWeeks = simulatedWeeks.filter { it.entryTriggered }
        val swingConsistency = entryWeeks.count { it.swingTargetHit }
        val effectiveSellDay = resolveEffectiveSellDay(simulatedWeeks, fallbackSellDay)
        val avgObservedSwingPct = calculateAverageObservedSwing(parsedWeeks, buyDay, effectiveSellDay)
        val avgRealizedSwingPct = entryWeeks.mapNotNull { it.swingPct }.averageOrZero().roundTo2()
        val compositeScore = calculateCompositeScore(
            eligibleWeeks = eligibleWeeks,
            entries = entryWeeks.size,
            wins = swingConsistency,
            avgRealizedSwingPct = avgRealizedSwingPct,
            referenceTargetPct = recommendedTargetPct,
        )
        val avgDipPct = parsedWeeks.mapNotNull { calculateBuyDayDipPct(it, buyDay) }.averageOrZero().roundTo2()

        return SeasonalityAnalysis(
            eval = DayPairEval(
                buyDay = buyDay,
                sellDay = 5,
                effectiveSellDay = effectiveSellDay,
                avgDipPct = avgDipPct,
                reboundConsistency = entryWeeks.size,
                avgSwingPct = avgObservedSwingPct.roundTo2(),
                swingConsistency = swingConsistency,
                compositeScore = compositeScore,
                weeksCount = totalWeeks,
                eligibleWeeksCount = eligibleWeeks,
                allWeeks = simulatedWeeks,
            ),
            targetScenarios = targetScenarios,
            targetRecommendation = targetRecommendation,
        )
    }

    private fun resolveEffectiveSellDay(weeks: List<WeekInstance>, fallbackSellDay: Int): Int {
        val targetHitDays = weeks
            .filter { it.entryTriggered && it.swingTargetHit }
            .mapNotNull { it.exitDay }
        val exitDays = if (targetHitDays.isNotEmpty()) {
            targetHitDays
        } else {
            weeks
            .filter { it.entryTriggered }
            .mapNotNull { it.exitDay }
        }
        if (exitDays.isEmpty()) return fallbackSellDay

        return exitDays
            .groupingBy { it }
            .eachCount()
            .maxWithOrNull(compareBy<Map.Entry<Int, Int>> { it.value }.thenBy { -it.key })
            ?.key
            ?: fallbackSellDay
    }

    private fun evaluateWeekForPair(
        week: WeekInstance,
        buyDay: Int,
        sellDay: Int,
        prevWeekEnd: LocalDate?,
        rsiMap: Map<LocalDate, com.tradingtool.core.technical.RollingRsiBounds>,
        config: WeeklyPatternConfig,
        targetPct: Double,
        requireObservedLowDay: Boolean = false,
    ): WeekInstance {
        val wCopy = week.copy()
        wCopy.maxPotentialPct = calculateWeekPotentialPct(wCopy)

        if (requireObservedLowDay) {
            val observedLowDay = resolveWeekLowDay(wCopy)
            if (observedLowDay == null) {
                wCopy.reasoning = "Missing week low data"
                return wCopy
            }
            if (observedLowDay != buyDay) {
                wCopy.reasoning = "Weekly low on ${dayNames[observedLowDay]}"
                return wCopy
            }
        }

        val buyCandle = wCopy.dailyCandles[buyDay]
        val sellCandle = wCopy.dailyCandles[sellDay]

        if (buyCandle == null || sellCandle == null) {
            wCopy.reasoning = "Missing buy/sell day data"
            return wCopy
        }

        wCopy.buyDayLow = buyCandle.low
        if (buyCandle.open > 0.0) {
            wCopy.buyDayDipPct = ((buyCandle.open - buyCandle.low) / buyCandle.open * 100.0).roundTo2()
        }

        val potentialEntryPrice = buyCandle.low * (1 + (config.entryReboundPct / 100.0))
        wCopy.buyPriceActual = potentialEntryPrice.roundTo2()
        wCopy.sellPriceActual = sellCandle.close.roundTo2()

        if (buyCandle.high < potentialEntryPrice) {
            wCopy.reasoning = "No ${config.entryReboundPct.roundTo2()}% rebound"
            return wCopy
        }

        val rsiDate = prevWeekEnd ?: buyCandle.candleDate
        val bounds = rsiMap[rsiDate]
        val entryRsi = bounds?.current ?: 50.0
        val minRsi = bounds?.lowest ?: 30.0
        val maxRsi = bounds?.highest ?: 70.0
        wCopy.buyRsi = entryRsi.roundTo2()

        val adaptiveRsi = AdaptiveRsi.getStatus(
            currentRsi = entryRsi,
            lowestRsi = minRsi,
            highestRsi = maxRsi,
            overboughtPercentile = config.rsiEntry.overboughtPercentile,
        )
        if (adaptiveRsi.isOverbought) {
            wCopy.reasoning =
                "RSI overbought (${adaptiveRsi.percentile}% of ${config.rsiEntry.lookbackDays}D range)"
            return wCopy
        }

        wCopy.entryTriggered = true
        val targetPrice = potentialEntryPrice * (1 + (targetPct / 100.0))
        val stopLossPrice = potentialEntryPrice * (1 - (config.stopLossPct / 100.0))

        var exitFound = false
        for (day in (buyDay + 1)..sellDay) {
            val dayCandle = wCopy.dailyCandles[day] ?: continue

            // Conservative sequencing assumption when both levels are touched in one candle.
            if (dayCandle.low <= stopLossPrice) {
                wCopy.sellPriceActual = stopLossPrice.roundTo2()
                wCopy.swingPct = -config.stopLossPct.roundTo2()
                wCopy.exitDay = day
                wCopy.reasoning =
                    "Stop Loss Hit on ${dayNames[day]} (L ${dayCandle.low.roundTo2()} <= ${stopLossPrice.roundTo2()})"
                exitFound = true
                break
            }

            if (dayCandle.high >= targetPrice) {
                wCopy.sellPriceActual = targetPrice.roundTo2()
                wCopy.swingPct = targetPct.roundTo2()
                wCopy.swingTargetHit = true
                wCopy.exitDay = day
                wCopy.reasoning =
                    "Target Hit (+${targetPct.roundTo2()}%) on ${dayNames[day]} " +
                        "(H ${dayCandle.high.roundTo2()} >= ${targetPrice.roundTo2()})"
                exitFound = true
                break
            }
        }

        if (!exitFound) {
            val exitPrice = sellCandle.close
            wCopy.sellPriceActual = exitPrice.roundTo2()
            wCopy.swingPct = ((exitPrice - potentialEntryPrice) / potentialEntryPrice * 100.0).roundTo2()
            wCopy.exitDay = sellDay
            wCopy.reasoning = "${dayNames[sellDay]} Hard Exit"
            if ((wCopy.swingPct ?: 0.0) >= targetPct) {
                wCopy.swingTargetHit = true
            }
        }

        return wCopy
    }

    private fun calculateWeekPotentialPct(week: WeekInstance): Double? {
        val weekLow = week.dailyCandles.values.minOfOrNull { it.low } ?: return null
        val weekHigh = week.dailyCandles.values.maxOfOrNull { it.high } ?: return null
        if (weekLow <= 0.0) return null
        return ((weekHigh - weekLow) / weekLow * 100.0).roundTo2()
    }

    private fun resolveObservedSellDay(parsedWeeks: List<WeekInstance>, buyDay: Int): Int {
        return ((buyDay + 1)..5)
            .mapNotNull { sellDay ->
                val swings = parsedWeeks
                    .filter { resolveWeekLowDay(it) == buyDay }
                    .mapNotNull { calculateObservedSwingPct(it, buyDay, sellDay) }
                if (swings.isEmpty()) {
                    null
                } else {
                    Triple(sellDay, swings.size, swings.average())
                }
            }
            .maxWithOrNull(
                compareBy<Triple<Int, Int, Double>> { it.second }
                    .thenBy { it.third }
                    .thenBy { -it.first }
            )
            ?.first
            ?: 5
    }

    private fun calculateCompositeScore(
        eligibleWeeks: Int,
        entries: Int,
        wins: Int,
        avgRealizedSwingPct: Double,
        referenceTargetPct: Double,
    ): Int {
        val entryRate = if (eligibleWeeks > 0) entries.toDouble() / eligibleWeeks else 0.0
        val winRate = if (entries > 0) wins.toDouble() / entries else 0.0
        val magnitudeScale = (referenceTargetPct * 1.5).coerceAtLeast(1.0)
        val magnitudeRate = avgRealizedSwingPct.coerceIn(0.0, magnitudeScale) / magnitudeScale

        val entryScore = (entryRate * 35).toInt()
        val winScore = (winRate * 45).toInt()
        val magnitudeScore = (magnitudeRate * 20).toInt()
        return entryScore + winScore + magnitudeScore
    }

    private fun simulateWeeksForTarget(
        parsedWeeks: List<WeekInstance>,
        buyDay: Int,
        sellDay: Int,
        targetPct: Double,
        rsiMap: Map<LocalDate, com.tradingtool.core.technical.RollingRsiBounds>,
        config: WeeklyPatternConfig,
        requireObservedLowDay: Boolean,
    ): List<WeekInstance> {
        return parsedWeeks.mapIndexed { idx, week ->
            val prevWeekEnd = parsedWeeks.getOrNull(idx - 1)?.endDate
            evaluateWeekForPair(
                week = week,
                buyDay = buyDay,
                sellDay = sellDay,
                prevWeekEnd = prevWeekEnd,
                rsiMap = rsiMap,
                config = config,
                targetPct = targetPct,
                requireObservedLowDay = requireObservedLowDay,
            )
        }
    }

    private fun calculateAverageObservedSwing(
        parsedWeeks: List<WeekInstance>,
        buyDay: Int,
        sellDay: Int,
    ): Double {
        return parsedWeeks
            .filter { resolveWeekLowDay(it) == buyDay }
            .mapNotNull { calculateObservedSwingPct(it, buyDay, sellDay) }
            .averageOrZero()
            .roundTo2()
    }

    private fun resolveWeekLowDay(week: WeekInstance): Int? {
        return week.dailyCandles.entries
            .minWithOrNull(compareBy<Map.Entry<Int, DailyCandle>> { it.value.low }.thenBy { it.key })
            ?.key
    }

    private fun calculateBuyDayDipPct(week: WeekInstance, buyDay: Int): Double? {
        val candle = week.dailyCandles[buyDay] ?: return null
        if (candle.open <= 0.0) return null
        return ((candle.open - candle.low) / candle.open * 100.0).roundTo2()
    }

    private fun calculateObservedSwingPct(week: WeekInstance, buyDay: Int, sellDay: Int): Double? {
        val buyCandle = week.dailyCandles[buyDay] ?: return null
        val sellCandle = week.dailyCandles[sellDay] ?: return null
        if (buyCandle.low <= 0.0) return null
        return ((sellCandle.high - buyCandle.low) / buyCandle.low * 100.0).roundTo2()
    }

    private fun List<Double>.averageOrZero(): Double {
        return if (isEmpty()) 0.0 else average()
    }

    private fun isPatternConfirmed(eval: DayPairEval, config: WeeklyPatternConfig): Boolean {
        if (eval.weeksCount < config.minWeeksRequired) {
            return false
        }
        val entryRatePct = if (eval.eligibleWeeksCount > 0) {
            (eval.reboundConsistency.toDouble() / eval.eligibleWeeksCount) * 100.0
        } else {
            0.0
        }
        val winRatePct = if (eval.reboundConsistency > 0) {
            (eval.swingConsistency.toDouble() / eval.reboundConsistency) * 100.0
        } else {
            0.0
        }

        return entryRatePct >= config.patternConfirmed.minEntryRatePct &&
            winRatePct >= config.patternConfirmed.minWinRatePct &&
            eval.avgSwingPct >= config.patternConfirmed.minAvgSwingPct
    }

    private fun evaluateTargetScenarios(
        parsedWeeks: List<WeekInstance>,
        buyDay: Int,
        sellDay: Int,
        rsiMap: Map<LocalDate, com.tradingtool.core.technical.RollingRsiBounds>,
        config: WeeklyPatternConfig,
        requireObservedLowDay: Boolean = false,
    ): List<TargetScenario> {
        val targetConfig = config.targetRecommendation
        val candidateTargets = targetConfig.candidateTargetsPct
            .map { it.roundTo2() }
            .filter { it > 0.0 }
            .distinct()
            .sorted()
        if (candidateTargets.isEmpty()) return emptyList()

        return candidateTargets.map { targetPct ->
            val simulatedWeeks = simulateWeeksForTarget(
                parsedWeeks = parsedWeeks,
                buyDay = buyDay,
                sellDay = sellDay,
                targetPct = targetPct,
                rsiMap = rsiMap,
                config = config,
                requireObservedLowDay = requireObservedLowDay,
            )

            val entryWeeks = simulatedWeeks.filter { it.entryTriggered }
            val entries = entryWeeks.size
            val wins = entryWeeks.count { it.swingTargetHit }
            val stopLossHits = entryWeeks.count { it.reasoning?.startsWith("Stop Loss Hit") == true }
            val winRatePct = if (entries > 0) (wins.toDouble() / entries * 100.0).roundTo2() else 0.0
            val stopLossRatePct = if (entries > 0) (stopLossHits.toDouble() / entries * 100.0).roundTo2() else 0.0
            val avgSwingPct = if (entryWeeks.isNotEmpty()) {
                entryWeeks.mapNotNull { it.swingPct }.average().roundTo2()
            } else {
                0.0
            }
            val avgPotentialPct = if (entryWeeks.isNotEmpty()) {
                entryWeeks.mapNotNull { it.maxPotentialPct }.average()
            } else {
                0.0
            }
            val captureRatioPct = if (avgPotentialPct > 0.0) {
                (avgSwingPct / avgPotentialPct * 100.0).roundTo2()
            } else {
                0.0
            }
            val feasible = entries >= targetConfig.minSamples &&
                winRatePct >= targetConfig.minWinRatePct &&
                stopLossRatePct <= targetConfig.maxStopLossRatePct

            TargetScenario(
                targetPct = targetPct,
                entries = entries,
                winRatePct = winRatePct,
                stopLossRatePct = stopLossRatePct,
                avgSwingPct = avgSwingPct,
                captureRatioPct = captureRatioPct,
                feasible = feasible,
            )
        }
    }

    private fun buildTargetRecommendation(
        scenarios: List<TargetScenario>,
        config: WeeklyPatternConfig,
    ): TargetRecommendation? {
        if (scenarios.isEmpty()) return null
        val targetConfig = config.targetRecommendation
        val feasibleScenarios = scenarios.filter { it.feasible }
        val fallback = scenarios.minByOrNull { abs(it.targetPct - targetConfig.fallbackTargetPct) } ?: scenarios.first()

        val recommended = if (feasibleScenarios.isNotEmpty()) {
            feasibleScenarios.maxByOrNull { recommendationScore(it) } ?: fallback
        } else {
            fallback
        }

        val safeTarget = (if (feasibleScenarios.isNotEmpty()) feasibleScenarios else scenarios)
            .maxWithOrNull(compareBy<TargetScenario> { it.winRatePct }.thenBy { it.avgSwingPct })
            ?: recommended
        val aggressiveTarget = (if (feasibleScenarios.isNotEmpty()) feasibleScenarios else scenarios)
            .maxByOrNull { it.targetPct }
            ?: recommended

        return TargetRecommendation(
            recommendedTargetPct = recommended.targetPct.roundTo2(),
            safeTargetPct = safeTarget.targetPct.roundTo2(),
            aggressiveTargetPct = aggressiveTarget.targetPct.roundTo2(),
            confidence = recommendationConfidence(recommended, targetConfig),
            expectedSwingPct = recommended.avgSwingPct.roundTo2(),
            expectedWinRatePct = recommended.winRatePct.roundTo2(),
            expectedStopLossRatePct = recommended.stopLossRatePct.roundTo2(),
            captureRatioPct = recommended.captureRatioPct.roundTo2(),
        )
    }

    private fun recommendationScore(scenario: TargetScenario): Double {
        return (scenario.avgSwingPct * 0.60) +
            (scenario.winRatePct * 0.03) -
            (scenario.stopLossRatePct * 0.02) +
            (scenario.captureRatioPct * 0.01)
    }

    private fun recommendationConfidence(
        scenario: TargetScenario,
        config: TargetRecommendationConfig,
    ): String {
        return when {
            scenario.entries >= (config.minSamples + 2) &&
                scenario.winRatePct >= (config.minWinRatePct + 15.0) &&
                scenario.stopLossRatePct <= (config.maxStopLossRatePct - 15.0) -> "HIGH"
            scenario.entries >= config.minSamples &&
                scenario.winRatePct >= config.minWinRatePct &&
                scenario.stopLossRatePct <= config.maxStopLossRatePct -> "MEDIUM"
            else -> "LOW"
        }
    }

    suspend fun analyzeDetail(symbol: String): WeeklyPatternDetail? {
        val config = patternConfigService.loadConfig()
        val stock = stockHandler.read { it.getBySymbol(symbol, "NSE") } ?: return null
        val token = stock.instrumentToken
        val today = LocalDate.now(ist)
        val from = today.minusYears(5)

        val allYearCandles = candleCache.getDailyCandles(token, symbol, from, today)
        if (allYearCandles.isEmpty()) return null

        val rsiMap = StrategyTechnicalSignals.buildRollingRsiBoundsMap(
            candles = allYearCandles,
            windowSize = config.rsiEntry.lookbackDays,
        )
        val completedWeeks = selectCompletedWeeks(
            candles = allYearCandles,
            today = today,
            minTradingDaysPerWeek = config.minTradingDaysPerWeek,
        )
        val analysisWeeks = completedWeeks.takeLast(config.lookbackWeeks)

        val analysis = analyzeSeasonality(analysisWeeks, rsiMap, config) ?: return null
        val bestPair = analysis.eval
        val targetScenarios = analysis.targetScenarios
        val targetRecommendation = analysis.targetRecommendation

        val (minLow, maxLow) = calculateBuyDayLowRange(
            completedWeeks = completedWeeks,
            buyDay = bestPair.buyDay,
            buyZoneLookbackWeeks = config.buyZoneLookbackWeeks,
        )

        val selectedCandles = bestPair.allWeeks
            .flatMap { it.dailyCandles.values }
            .sortedBy { it.candleDate }

        val dailyReturns = mutableListOf<Double>()
        for (i in 1 until selectedCandles.size) {
            val prev = selectedCandles[i - 1]
            val curr = selectedCandles[i]
            if (prev.close > 0.0) {
                dailyReturns.add((curr.close - prev.close) / prev.close * 100.0)
            }
        }

        fun getAvgOpenToCloseForDay(dayOfWeek: Int): Double {
            val rets = bestPair.allWeeks.mapNotNull { week ->
                val candle = week.dailyCandles[dayOfWeek] ?: return@mapNotNull null
                if (candle.open <= 0.0) return@mapNotNull null
                ((candle.close - candle.open) / candle.open * 100.0)
            }
            return if (rets.isNotEmpty()) rets.average().roundTo2() else 0.0
        }

        val profile = (1..5).map { d ->
            val status = when {
                d == bestPair.buyDay -> "Buy day"
                d == bestPair.effectiveSellDay -> "Typical hit"
                d in (bestPair.buyDay + 1) until bestPair.effectiveSellDay -> "Wait"
                else -> "Watch"
            }
            DayProfile(dayNames[d], status, getAvgOpenToCloseForDay(d))
        }

        val autocorrel = AutocorrelationResult(
            lag5 = pearsonCorrel(dailyReturns, 5).roundTo2(),
            lag10 = pearsonCorrel(dailyReturns, 10).roundTo2(),
            lag21 = pearsonCorrel(dailyReturns, 21).roundTo2()
        )

        val confirmed = isPatternConfirmed(bestPair, config)
        val recommendedTargetPct = targetRecommendation?.recommendedTargetPct ?: config.swingTargetPct
        val summary = if (confirmed) {
            "Buy day is ${dayNames[bestPair.buyDay]}. Only enter if price rebounds ${config.entryReboundPct.roundTo2()}% " +
                "from that day’s low. Skip only if RSI is near the ${config.rsiEntry.lookbackDays}D high " +
                "(>${config.rsiEntry.overboughtPercentile.roundTo2()}% of the ${config.rsiEntry.lookbackDays}D range). " +
                "Recommended target: +${recommendedTargetPct.roundTo2()}% " +
                "(safe ${targetRecommendation?.safeTargetPct?.roundTo2() ?: config.swingTargetPct.roundTo2()}% / " +
                "aggressive ${targetRecommendation?.aggressiveTargetPct?.roundTo2() ?: config.swingTargetPct.roundTo2()}%). " +
                "Set a hard stop at -${config.stopLossPct.roundTo2()}%. Most target hits happen by ${dayNames[bestPair.effectiveSellDay]}."
        } else {
            "No reliable weekly cycle found for current thresholds."
        }

        val avgPotential = bestPair.allWeeks.mapNotNull { it.maxPotentialPct }.average().roundTo2()

        val vcpTightness = if (analysisWeeks.size >= 4) {
            analysisWeeks.takeLast(4).map { week ->
                val low = week.dailyCandles.values.minOf { it.low }
                val high = week.dailyCandles.values.maxOf { it.high }
                if (low > 0) ((high - low) / low) * 100.0 else 0.0
            }.average().roundTo2()
        } else null

        val upVol = analysisWeeks.flatMap { it.dailyCandles.values }.filter { it.close > it.open }.sumOf { it.volume }.toDouble()
        val downVol = analysisWeeks.flatMap { it.dailyCandles.values }.filter { it.close < it.open }.sumOf { it.volume }.toDouble()
        val volumeSignature = if (downVol > 0) (upVol / downVol).roundTo2() else 1.0

        val mondayWeeks = analysisWeeks.filter { it.dailyCandles.containsKey(DayOfWeek.MONDAY.value) }
        val mondayStrikeRate = if (mondayWeeks.isNotEmpty()) {
            val successes = mondayWeeks.count { week ->
                val entry = week.dailyCandles[DayOfWeek.MONDAY.value]!!.open
                val weekHigh = week.dailyCandles.values.maxOf { it.high }
                weekHigh >= (entry * 1.05)
            }
            (successes.toDouble() / mondayWeeks.size * 100.0).roundTo2()
        } else 0.0

        val heatmap = bestPair.allWeeks.mapIndexed { idx, w ->
            fun getOpenClosePct(day: Int): Double? {
                val candle = w.dailyCandles[day] ?: return null
                if (candle.open <= 0.0) return null
                return ((candle.close - candle.open) / candle.open * 100.0).roundTo2()
            }

            WeekHeatmapRow(
                weekLabel = "W-${bestPair.allWeeks.size - idx}",
                startDate = w.startDate?.format(df) ?: "",
                endDate = w.endDate?.format(df) ?: "",
                mondayChangePct = getOpenClosePct(1),
                tuesdayChangePct = getOpenClosePct(2),
                wednesdayChangePct = getOpenClosePct(3),
                thursdayChangePct = getOpenClosePct(4),
                fridayChangePct = getOpenClosePct(5),
                entryTriggered = w.entryTriggered,
                swingTargetHit = w.swingTargetHit,
                buyPriceActual = w.buyPriceActual?.roundTo2(),
                sellPriceActual = w.sellPriceActual?.roundTo2(),
                buyRsi = w.buyRsi?.roundTo2(),
                netSwingPct = w.swingPct?.roundTo2(),
                maxPotentialPct = w.maxPotentialPct?.roundTo2(),
                reasoning = when {
                    w.reasoning != null -> w.reasoning
                    !w.entryTriggered -> "Entry not triggered"
                    else -> "No data"
                }
            )
        }

        return WeeklyPatternDetail(
            symbol = symbol,
            exchange = stock.exchange,
            instrumentToken = token,
            companyName = stock.companyName,
            weeksAnalyzed = bestPair.weeksCount,
            buyDay = dayNames[bestPair.buyDay],
            entryReboundPct = config.entryReboundPct.roundTo2(),
            rsiLookbackDays = config.rsiEntry.lookbackDays,
            rsiOverboughtPercentile = config.rsiEntry.overboughtPercentile.roundTo2(),
            stopLossPct = config.stopLossPct.roundTo2(),
            buyDayAvgDipPct = bestPair.avgDipPct,
            reboundConsistency = bestPair.reboundConsistency,
            sellDay = dayNames[bestPair.effectiveSellDay],
            swingAvgPct = bestPair.avgSwingPct,
            avgPotentialPct = avgPotential,
            swingConsistency = bestPair.swingConsistency,
            compositeScore = bestPair.compositeScore,
            patternConfirmed = confirmed,
            cycleType = "Weekly",
            reason = null,
            buyDayLowMin = minLow.roundTo2(),
            buyDayLowMax = maxLow.roundTo2(),
            vcpTightnessPct = vcpTightness,
            volumeSignatureRatio = volumeSignature,
            mondayStrikeRatePct = mondayStrikeRate,
            dayOfWeekProfile = profile,
            autocorrelation = autocorrel,
            patternSummary = summary,
            weeklyHeatmap = heatmap.reversed(),
            targetRecommendation = targetRecommendation,
            targetScenarios = targetScenarios,
        )
    }

    private fun noData(symbol: String, companyName: String, reason: String) = WeeklyPatternResult(
        symbol = symbol,
        exchange = "NSE",
        instrumentToken = 0L,
        companyName = companyName,
        weeksAnalyzed = 0,
        buyDay = "",
        entryReboundPct = 0.0,
        rsiLookbackDays = 0,
        rsiOverboughtPercentile = 0.0,
        stopLossPct = 0.0,
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
