package com.tradingtool.core.strategy.bollinger

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.screener.CandleDataService
import com.tradingtool.core.technical.calculateEma
import com.tradingtool.core.technical.getNullableDouble
import com.tradingtool.core.technical.roundTo2
import com.tradingtool.core.technical.toTa4jSeries
import com.tradingtool.core.watchlist.Ta4jIndicatorCalculator
import org.slf4j.LoggerFactory
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max

@Singleton
class BollingerMeanReversionBacktestService @Inject constructor(
    private val stockHandler: StockJdbiHandler,
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val candleCache: CandleCacheService,
    private val candleDataService: CandleDataService,
    private val kiteClient: KiteConnectClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun runBacktest(request: BollingerMeanReversionBacktestRequest): BollingerMeanReversionBacktestResponse {
        val config = normalize(request.config)
        val universe = request.universe.trim().uppercase().ifBlank { "WATCHLIST" }
        val symbols = resolveSymbols(request.symbols, universe)
        val symbolMetaBySymbol = symbols.associateBy { it.symbol }

        if (symbols.isEmpty()) {
            return emptyResponse(universe, config, 0, emptyList(), emptyList())
        }

        val today = LocalDate.now()
        val fromDate = config.fromDate?.let { LocalDate.parse(it) } ?: today.minusMonths(1)
        val toDate = config.toDate?.let { LocalDate.parse(it) } ?: today
        val warmupDays = max(220, config.signalWindowDays + 90)
        val warmupFrom = fromDate.minusDays(warmupDays.toLong())

        val symbolCandles = mutableMapOf<String, List<DailyCandle>>()
        val insufficientData = mutableListOf<String>()
        val missingSymbols = mutableListOf<SymbolMeta>()

        symbols.forEach { meta ->
            val candles = candleCache.getDailyCandles(meta.instrumentToken, meta.symbol, warmupFrom, toDate)
                .sortedBy { it.candleDate }
            if (candles.size < MIN_REQUIRED_CANDLES) {
                missingSymbols.add(meta)
            } else {
                symbolCandles[meta.symbol] = candles
            }
        }

        if (missingSymbols.isNotEmpty() && kiteClient.isAuthenticated) {
            runCatching {
                candleDataService.sync(missingSymbols.map { it.symbol }, kiteClient)
            }.onFailure { error ->
                log.warn(
                    "Mean reversion backtest candle sync failed for {} symbols: {}",
                    missingSymbols.size,
                    error.message,
                )
            }
            missingSymbols.forEach { meta ->
                val candles = candleCache.getDailyCandles(meta.instrumentToken, meta.symbol, warmupFrom, toDate)
                    .sortedBy { it.candleDate }
                if (candles.size < MIN_REQUIRED_CANDLES) {
                    insufficientData.add(meta.symbol)
                } else {
                    symbolCandles[meta.symbol] = candles
                }
            }
        } else {
            insufficientData.addAll(missingSymbols.map { it.symbol })
        }

        val statesBySymbol = mutableMapOf<String, List<SymbolState>>()
        symbolCandles.forEach { (symbol, candles) ->
            runCatching { buildStates(candles) }
                .onSuccess { states ->
                    if (states.isEmpty()) {
                        insufficientData.add(symbol)
                    } else {
                        statesBySymbol[symbol] = states
                    }
                }
                .onFailure { error ->
                    log.warn("Skipping {} in mean reversion backtest due indicator/state error: {}", symbol, error.message)
                    insufficientData.add(symbol)
                }
        }

        val stateByIndexBySymbol = statesBySymbol.mapValues { (_, states) -> states.associateBy { it.index } }
        val stateByDateBySymbol = statesBySymbol.mapValues { (_, states) -> states.associateBy { it.candle.candleDate } }

        val activeTrades = mutableMapOf<String, ActiveTrade>()
        val setupBySymbol = mutableMapOf<String, EntrySetupState>()
        val allTrades = mutableListOf<BollingerMeanReversionBacktestTrade>()

        var capital = config.capital
        var peakCapital = capital
        var maxDrawdown = 0.0
        val noTradeSymbols = statesBySymbol.keys.toMutableSet()

        val tradingDates = statesBySymbol.values
            .flatMap { states -> states.map { it.candle.candleDate } }
            .distinct()
            .filter { it >= fromDate && it <= toDate }
            .sorted()

        tradingDates.forEach { date ->
            val exitedSymbols = mutableListOf<String>()
            activeTrades.values.forEach { active ->
                val state = stateByDateBySymbol[active.symbol]?.get(date) ?: return@forEach
                val previousState = stateByIndexBySymbol[active.symbol]?.get(state.index - 1)
                val exitDecision = decideExit(active, state, previousState, config)
                if (exitDecision != null) {
                    val grossPnl = (exitDecision.exitPrice - active.entryPrice) * active.quantity
                    val netPnl = grossPnl
                    val netReturnPct = if (active.investedAmount > 0.0) (netPnl / active.investedAmount) * 100.0 else 0.0
                    capital += netPnl
                    peakCapital = maxOf(peakCapital, capital)
                    maxDrawdown = maxOf(maxDrawdown, peakCapital - capital)

                    allTrades.add(
                        BollingerMeanReversionBacktestTrade(
                            symbol = active.symbol,
                            companyName = active.companyName,
                            entryDate = active.entryDate.toString(),
                            exitDate = date.toString(),
                            holdingDays = state.index - active.entryIndex,
                            quantity = active.quantity,
                            investedAmount = active.investedAmount.roundTo2(),
                            entryPrice = active.entryPrice.roundTo2(),
                            exitPrice = exitDecision.exitPrice.roundTo2(),
                            exitReason = exitDecision.reason,
                            grossPnlInr = grossPnl.roundTo2(),
                            netPnlInr = netPnl.roundTo2(),
                            netReturnPct = netReturnPct.roundTo2(),
                            entryCriteria = active.entryCriteria,
                            exitCriteria = state.criteria,
                            debugRows = buildDebugRows(statesBySymbol[active.symbol].orEmpty(), active.entryIndex, state.index),
                        )
                    )
                    exitedSymbols.add(active.symbol)
                }
            }
            exitedSymbols.forEach { symbol -> activeTrades.remove(symbol) }

            statesBySymbol.forEach { (symbol, states) ->
                if (activeTrades.containsKey(symbol)) return@forEach
                if (activeTrades.size >= config.maxOpenPositions) return@forEach

                val state = stateByDateBySymbol[symbol]?.get(date) ?: return@forEach
                val setup = setupBySymbol.getOrPut(symbol) { EntrySetupState() }
                if (state.closeBelowLowerBand) {
                    setup.lastLowerBandStressIndex = state.index
                }

                val isArmed = isWithinWindow(state.index, setup.lastLowerBandStressIndex, config.signalWindowDays)
                if (!isArmed) return@forEach

                val previousState = stateByIndexBySymbol[symbol]?.get(state.index - 1) ?: return@forEach
                val previous2State = stateByIndexBySymbol[symbol]?.get(state.index - 2) ?: return@forEach

                val isDoubleGreen = state.candle.close > previousState.candle.close && state.candle.close > state.candle.open
                if (!isDoubleGreen) return@forEach

                val fastEntryA = state.candle.close > state.ema5
                val fastEntryB = state.volumeRatio20 >= config.volumeMultiplier
                val safeEntryC = previousState.candle.close > previous2State.candle.close
                val triggerType = when {
                    fastEntryA -> "FAST_ENTRY_A_EMA5_BREAK"
                    fastEntryB -> "FAST_ENTRY_B_VOLUME_SPIKE"
                    safeEntryC -> "SAFE_ENTRY_C_2_GREEN"
                    else -> null
                } ?: return@forEach

                val aboveLowerBand = state.candle.close > state.bbLower
                if (!aboveLowerBand) return@forEach

                val entryPrice = state.candle.close
                val slotCapital = capital / config.maxOpenPositions
                val quantity = (slotCapital / entryPrice).toInt()
                if (quantity <= 0) return@forEach

                val setupWindowStart = maxOf(0, state.index - config.signalWindowDays)
                val structuralLow = states.asSequence()
                    .filter { it.index in setupWindowStart..state.index }
                    .minOfOrNull { it.candle.low }
                    ?: state.candle.low

                val channelWidth = state.bbUpper - state.bbLower
                val bandwidthRecovery = if (channelWidth > 0.0) {
                    (entryPrice - state.bbLower) / channelWidth
                } else {
                    0.0
                }

                val initialStop = if (bandwidthRecovery >= config.bandwidthRecoveryThreshold) {
                    previousState.candle.low
                } else {
                    structuralLow
                }

                val stressDate = setup.lastLowerBandStressIndex?.let { idx ->
                    stateByIndexBySymbol[symbol]?.get(idx)?.candle?.candleDate?.toString()
                } ?: "unknown"

                val meta = symbolMetaBySymbol[symbol] ?: return@forEach
                val invested = quantity * entryPrice
                val entryReasoning = buildEntryReasoning(
                    state = state,
                    triggerType = triggerType,
                    stressDate = stressDate,
                    bandwidthRecovery = bandwidthRecovery,
                    threshold = config.bandwidthRecoveryThreshold,
                    volumeMultiplier = config.volumeMultiplier,
                )

                activeTrades[symbol] = ActiveTrade(
                    symbol = symbol,
                    companyName = meta.companyName,
                    entryDate = date,
                    entryIndex = state.index,
                    entryPrice = entryPrice,
                    quantity = quantity,
                    investedAmount = invested,
                    activeStopPrice = initialStop,
                    phase = ExitPhase.SAFETY,
                    entryCriteria = state.criteria.copy(
                        signal = "MEAN_REVERSION_ENTRY",
                        reasoning = entryReasoning,
                    ),
                )
                noTradeSymbols.remove(symbol)
            }
        }

        if (activeTrades.isNotEmpty()) {
            activeTrades.values.forEach { active ->
                val states = statesBySymbol[active.symbol].orEmpty()
                val finalState = states.lastOrNull {
                    it.candle.candleDate <= toDate && it.index >= active.entryIndex
                } ?: return@forEach

                val grossPnl = (finalState.candle.close - active.entryPrice) * active.quantity
                val netPnl = grossPnl
                val netReturnPct = if (active.investedAmount > 0.0) (netPnl / active.investedAmount) * 100.0 else 0.0
                capital += netPnl
                peakCapital = maxOf(peakCapital, capital)
                maxDrawdown = maxOf(maxDrawdown, peakCapital - capital)

                allTrades.add(
                    BollingerMeanReversionBacktestTrade(
                        symbol = active.symbol,
                        companyName = active.companyName,
                        entryDate = active.entryDate.toString(),
                        exitDate = finalState.candle.candleDate.toString(),
                        holdingDays = finalState.index - active.entryIndex,
                        quantity = active.quantity,
                        investedAmount = active.investedAmount.roundTo2(),
                        entryPrice = active.entryPrice.roundTo2(),
                        exitPrice = finalState.candle.close.roundTo2(),
                        exitReason = "BACKTEST_END",
                        grossPnlInr = grossPnl.roundTo2(),
                        netPnlInr = netPnl.roundTo2(),
                        netReturnPct = netReturnPct.roundTo2(),
                        entryCriteria = active.entryCriteria,
                        exitCriteria = finalState.criteria.copy(
                            signal = "BACKTEST_END",
                            reasoning = "Position marked to market on final backtest day.",
                        ),
                        debugRows = buildDebugRows(states, active.entryIndex, finalState.index),
                    )
                )
            }
            activeTrades.clear()
        }

        return BollingerMeanReversionBacktestResponse(
            config = buildConfigSnapshot(universe, config),
            summary = summarize(config.capital, capital, allTrades, maxDrawdown),
            diagnostics = BollingerMeanReversionBacktestDiagnostics(
                symbolsConsidered = symbols.size,
                symbolsWithInsufficientData = insufficientData.distinct().sorted(),
                symbolsWithNoTrades = noTradeSymbols.sorted(),
            ),
            trades = allTrades.sortedBy { it.entryDate },
        )
    }

    private fun summarize(
        initialCapital: Double,
        finalCapital: Double,
        trades: List<BollingerMeanReversionBacktestTrade>,
        maxDrawdown: Double,
    ): BollingerMeanReversionBacktestSummary {
        val grossPnl = trades.sumOf { it.grossPnlInr }
        val netPnl = trades.sumOf { it.netPnlInr }
        val winners = trades.count { it.netPnlInr > 0.0 }
        val losers = trades.count { it.netPnlInr <= 0.0 }
        val winRate = if (trades.isEmpty()) 0.0 else (winners.toDouble() / trades.size) * 100.0
        val avgReturn = if (trades.isEmpty()) 0.0 else trades.map { it.netReturnPct }.average()
        val totalReturn = if (initialCapital <= 0.0) 0.0 else (netPnl / initialCapital) * 100.0

        return BollingerMeanReversionBacktestSummary(
            totalTrades = trades.size,
            winningTrades = winners,
            losingTrades = losers,
            winRatePct = winRate.roundTo2(),
            grossPnlInr = grossPnl.roundTo2(),
            totalBrokerageInr = 0.0,
            netPnlInr = netPnl.roundTo2(),
            totalReturnPct = totalReturn.roundTo2(),
            avgReturnPerTradePct = avgReturn.roundTo2(),
            maxDrawdownInr = maxDrawdown.roundTo2(),
            finalCapital = finalCapital.roundTo2(),
        )
    }

    private fun decideExit(
        active: ActiveTrade,
        state: SymbolState,
        previousState: SymbolState?,
        config: BollingerMeanReversionBacktestConfig,
    ): ExitDecision? {
        val stopHitPrice = detectStopHitPrice(state.candle, active.activeStopPrice)
        if (stopHitPrice != null) {
            val reason = when (active.phase) {
                ExitPhase.SAFETY -> "STOP_LOSS_PHASE_1"
                ExitPhase.PROTECTION -> "STOP_LOSS_PHASE_2"
                ExitPhase.PROFIT -> "STOP_LOSS_PHASE_3"
            }
            return ExitDecision(stopHitPrice, reason)
        }

        if (state.candle.close > state.bbMiddle && active.phase == ExitPhase.SAFETY) {
            active.phase = ExitPhase.PROTECTION
            active.activeStopPrice = maxOf(active.activeStopPrice, active.entryPrice)
        }

        if (state.candle.high >= state.bbUpper) {
            active.phase = ExitPhase.PROFIT
            val previousLow = previousState?.candle?.low
            if (previousLow != null) {
                active.activeStopPrice = maxOf(active.activeStopPrice, previousLow)
            }
        }

        val holdBars = state.index - active.entryIndex
        return if (holdBars >= config.maxHoldDays) {
            ExitDecision(state.candle.close, "MAX_HOLD")
        } else {
            null
        }
    }

    private fun detectStopHitPrice(candle: DailyCandle, stopPrice: Double): Double? {
        return when {
            candle.open <= stopPrice -> candle.open
            candle.low <= stopPrice -> stopPrice
            else -> null
        }
    }

    private fun isWithinWindow(currentIndex: Int, signalIndex: Int?, windowDays: Int): Boolean {
        return signalIndex != null && currentIndex - signalIndex <= windowDays
    }

    private suspend fun resolveUniverseSymbols(universe: String): List<SymbolMeta> {
        val keys = universe.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val result = linkedMapOf<String, SymbolMeta>()
        keys.forEach { key ->
            if (key == "WATCHLIST" || key == "ALL_STOCKS") {
                stockHandler.read { it.listAll() }
                    .forEach { row ->
                        result[row.symbol.uppercase()] = SymbolMeta(
                            symbol = row.symbol.uppercase(),
                            companyName = row.companyName,
                            instrumentToken = row.instrumentToken,
                        )
                    }
            } else {
                indexConstituentHandler.read { it.listActiveByIndex(key) }
                    .forEach { row ->
                        result[row.symbol.uppercase()] = SymbolMeta(
                            symbol = row.symbol.uppercase(),
                            companyName = row.companyName,
                            instrumentToken = row.instrumentToken,
                        )
                    }
            }
        }
        return result.values.toList().sortedBy { it.symbol }
    }

    private suspend fun resolveSymbols(selectedSymbols: List<String>, universe: String): List<SymbolMeta> {
        val normalizedSelected = selectedSymbols
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .distinct()

        if (normalizedSelected.isEmpty()) {
            return resolveUniverseSymbols(universe)
        }

        val stockBySymbol = stockHandler.read { it.listAll() }
            .associateBy { row -> row.symbol.trim().uppercase() }

        return normalizedSelected.mapNotNull { symbol ->
            val row = stockBySymbol[symbol] ?: return@mapNotNull null
            SymbolMeta(
                symbol = row.symbol.trim().uppercase(),
                companyName = row.companyName,
                instrumentToken = row.instrumentToken,
            )
        }
    }

    private fun buildStates(candles: List<DailyCandle>): List<SymbolState> {
        val states = mutableListOf<SymbolState>()
        for (index in candles.indices) {
            if (index < BB_LOOKBACK_DAYS - 1) continue

            val series = candles.subList(0, index + 1).toTa4jSeries("BT")
            val indicators = Ta4jIndicatorCalculator.calculate(series)

            val close = candles[index].close
            val bbUpper = indicators.bbUpper ?: continue
            val bbMiddle = indicators.bbMiddle ?: close
            val bbLower = indicators.bbLower ?: continue
            val percentB = indicators.bbPercentB ?: continue
            val bandwidthRaw = indicators.bbBandwidth ?: 0.0
            val bandwidthPct = if (abs(bandwidthRaw) <= 1.0) bandwidthRaw * 100.0 else bandwidthRaw
            val rsi14 = indicators.rsi14
            val sma200 = indicators.sma200
            val avgVolume20 = indicators.avgVol20d
            val volumeRatio20 = indicators.volumeVsAvg20d ?: 0.0
            val ema5Maybe = if (series.barCount >= EMA_FAST_PERIOD) {
                series.calculateEma(EMA_FAST_PERIOD).getNullableDouble(series.endIndex)
            } else {
                null
            }
            val ema5 = ema5Maybe ?: continue
            val closeAboveSma200 = sma200?.let { close >= it }

            val closeBelowLowerBand = close < bbLower
            val reasons = mutableListOf<String>()
            if (closeBelowLowerBand) reasons.add("close < lower band (stress)")
            if (close > bbLower) reasons.add("close > lower band")
            if (close > ema5) reasons.add("close > EMA5")
            if (avgVolume20 != null && avgVolume20 > 0.0) reasons.add("vol ratio ${volumeRatio20.roundTo2()}x")

            val signal = when {
                closeBelowLowerBand -> "LOWER_BAND_STRESS"
                close > bbUpper -> "UPPER_BAND_TEST"
                else -> "NORMAL"
            }

            states.add(
                SymbolState(
                    candle = candles[index],
                    index = index,
                    bbUpper = bbUpper,
                    bbMiddle = bbMiddle,
                    bbLower = bbLower,
                    ema5 = ema5,
                    volumeRatio20 = volumeRatio20,
                    closeBelowLowerBand = closeBelowLowerBand,
                    criteria = BollingerMeanReversionCriteriaSnapshot(
                        percentB = percentB.roundTo2(),
                        rsi14 = rsi14?.roundTo2(),
                        bandwidthPct = bandwidthPct.roundTo2(),
                        volumeRatio20 = volumeRatio20.roundTo2(),
                        closeAboveSma200 = closeAboveSma200,
                        signal = signal,
                        reasoning = reasons.joinToString("; "),
                    ),
                )
            )
        }

        return states
    }

    private fun buildEntryReasoning(
        state: SymbolState,
        triggerType: String,
        stressDate: String,
        bandwidthRecovery: Double,
        threshold: Double,
        volumeMultiplier: Double,
    ): String {
        val recoveryPct = (bandwidthRecovery * 100.0).roundTo2()
        val thresholdPct = (threshold * 100.0).roundTo2()
        val volumeRatio = state.volumeRatio20.roundTo2()
        return "Armed from lower-band stress on $stressDate; trigger=$triggerType on double-green day; close ${state.candle.close.roundTo2()} > lower ${state.bbLower.roundTo2()}; vol ${volumeRatio}x vs ${volumeMultiplier.roundTo2()}x; recovery ${recoveryPct}% (threshold ${thresholdPct}%)."
    }

    private fun buildDebugRows(
        states: List<SymbolState>,
        entryIndex: Int,
        exitIndex: Int,
    ): List<BollingerMeanReversionBacktestDebugRow> {
        if (states.isEmpty()) return emptyList()
        val from = maxOf(0, entryIndex - 5)
        val to = exitIndex + 5
        return states.asSequence()
            .filter { it.index in from..to }
            .sortedBy { it.index }
            .map { state ->
                BollingerMeanReversionBacktestDebugRow(
                    date = state.candle.candleDate.toString(),
                    ltp = state.candle.close.roundTo2(),
                    bbUpper = state.bbUpper.roundTo2(),
                    bbMiddle = state.bbMiddle.roundTo2(),
                    bbLower = state.bbLower.roundTo2(),
                    percentB = state.criteria.percentB,
                    bandwidthPct = state.criteria.bandwidthPct,
                    rsi14 = state.criteria.rsi14,
                    volumeRatio20 = state.criteria.volumeRatio20,
                    closeAboveSma200 = state.criteria.closeAboveSma200,
                    signal = state.criteria.signal,
                    reasoning = state.criteria.reasoning,
                )
            }
            .toList()
    }

    private fun emptyResponse(
        universe: String,
        config: BollingerMeanReversionBacktestConfig,
        symbolsConsidered: Int,
        insufficientData: List<String>,
        noTrades: List<String>,
    ): BollingerMeanReversionBacktestResponse {
        return BollingerMeanReversionBacktestResponse(
            config = buildConfigSnapshot(universe, config),
            summary = BollingerMeanReversionBacktestSummary(
                totalTrades = 0,
                winningTrades = 0,
                losingTrades = 0,
                winRatePct = 0.0,
                grossPnlInr = 0.0,
                totalBrokerageInr = 0.0,
                netPnlInr = 0.0,
                totalReturnPct = 0.0,
                avgReturnPerTradePct = 0.0,
                maxDrawdownInr = 0.0,
                finalCapital = config.capital,
            ),
            diagnostics = BollingerMeanReversionBacktestDiagnostics(
                symbolsConsidered = symbolsConsidered,
                symbolsWithInsufficientData = insufficientData,
                symbolsWithNoTrades = noTrades,
            ),
            trades = emptyList(),
        )
    }

    private fun buildConfigSnapshot(
        universe: String,
        config: BollingerMeanReversionBacktestConfig,
    ): BollingerMeanReversionBacktestConfigSnapshot {
        return BollingerMeanReversionBacktestConfigSnapshot(
            universe = universe,
            capital = config.capital,
            maxOpenPositions = config.maxOpenPositions,
            fromDate = config.fromDate,
            toDate = config.toDate,
            signalWindowDays = config.signalWindowDays,
            volumeMultiplier = config.volumeMultiplier,
            bandwidthRecoveryThreshold = config.bandwidthRecoveryThreshold,
            maxHoldDays = config.maxHoldDays,
        )
    }

    private fun normalize(config: BollingerMeanReversionBacktestConfig): BollingerMeanReversionBacktestConfig {
        val parsedFrom = config.fromDate?.let { LocalDate.parse(it) }
        val parsedTo = config.toDate?.let { LocalDate.parse(it) }
        require(parsedFrom == null || parsedTo == null || !parsedFrom.isAfter(parsedTo)) {
            "fromDate must be on or before toDate"
        }

        return config.copy(
            capital = config.capital.coerceAtLeast(10_000.0),
            maxOpenPositions = config.maxOpenPositions.coerceAtLeast(1),
            signalWindowDays = config.signalWindowDays.coerceAtLeast(1),
            volumeMultiplier = config.volumeMultiplier.coerceAtLeast(1.0),
            bandwidthRecoveryThreshold = config.bandwidthRecoveryThreshold.coerceIn(0.1, 1.0),
            maxHoldDays = config.maxHoldDays.coerceAtLeast(1),
        )
    }

    private data class SymbolMeta(
        val symbol: String,
        val companyName: String,
        val instrumentToken: Long,
    )

    private data class SymbolState(
        val candle: DailyCandle,
        val index: Int,
        val bbUpper: Double,
        val bbMiddle: Double,
        val bbLower: Double,
        val ema5: Double,
        val volumeRatio20: Double,
        val closeBelowLowerBand: Boolean,
        val criteria: BollingerMeanReversionCriteriaSnapshot,
    )

    private data class ActiveTrade(
        val symbol: String,
        val companyName: String,
        val entryDate: LocalDate,
        val entryIndex: Int,
        val entryPrice: Double,
        val quantity: Int,
        val investedAmount: Double,
        var activeStopPrice: Double,
        var phase: ExitPhase,
        val entryCriteria: BollingerMeanReversionCriteriaSnapshot,
    )

    private data class ExitDecision(
        val exitPrice: Double,
        val reason: String,
    )

    private data class EntrySetupState(
        var lastLowerBandStressIndex: Int? = null,
    )

    private enum class ExitPhase {
        SAFETY,
        PROTECTION,
        PROFIT,
    }

    companion object {
        private const val BB_LOOKBACK_DAYS = 20
        private const val MIN_REQUIRED_CANDLES = 80
        private const val EMA_FAST_PERIOD = 5
    }
}
