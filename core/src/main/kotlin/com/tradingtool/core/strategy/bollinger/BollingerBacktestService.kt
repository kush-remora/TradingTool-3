package com.tradingtool.core.strategy.bollinger

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.screener.CandleDataService
import com.tradingtool.core.technical.roundTo2
import com.tradingtool.core.technical.toTa4jSeries
import com.tradingtool.core.watchlist.Ta4jIndicatorCalculator
import java.time.LocalDate

@Singleton
class BollingerBacktestService @Inject constructor(
    private val stockHandler: StockJdbiHandler,
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val candleCache: CandleCacheService,
    private val candleDataService: CandleDataService,
    private val kiteClient: KiteConnectClient,
) {
    suspend fun runBacktest(request: BollingerBacktestRequest): BollingerBacktestResponse {
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
        val warmupFrom = fromDate.minusDays(40)

        val symbolCandles = mutableMapOf<String, List<DailyCandle>>()
        val insufficientData = mutableListOf<String>()
        val missingSymbols = mutableListOf<SymbolMeta>()

        symbols.forEach { meta ->
            val candles = candleCache.getDailyCandles(meta.instrumentToken, meta.symbol, warmupFrom, toDate)
                .sortedBy { it.candleDate }
            if (candles.size < 30) {
                missingSymbols.add(meta)
            } else {
                symbolCandles[meta.symbol] = candles
            }
        }

        if (missingSymbols.isNotEmpty() && kiteClient.isAuthenticated) {
            candleDataService.sync(missingSymbols.map { it.symbol }, kiteClient)
            missingSymbols.forEach { meta ->
                val candles = candleCache.getDailyCandles(meta.instrumentToken, meta.symbol, warmupFrom, toDate).sortedBy { it.candleDate }
                if (candles.size < 30) insufficientData.add(meta.symbol) else symbolCandles[meta.symbol] = candles
            }
        } else {
            insufficientData.addAll(missingSymbols.map { it.symbol })
        }

        val statesBySymbol = symbolCandles.mapValues { (_, candles) -> buildStates(candles) }
        val stateByIndexBySymbol = statesBySymbol.mapValues { (_, states) -> states.associateBy { it.index } }
        val activeTrades = mutableMapOf<String, ActiveTrade>()
        val entrySetupBySymbol = mutableMapOf<String, EntrySetupState>()
        val exitSetupBySymbol = mutableMapOf<String, ExitSetupState>()
        val allTrades = mutableListOf<BollingerBacktestTrade>()

        var capital = config.capital
        var peakCapital = capital
        var maxDrawdown = 0.0
        val noTradeSymbols = symbolCandles.keys.toMutableSet()

        val tradingDates = symbolCandles.values
            .flatMap { candles -> candles.map { candle -> candle.candleDate } }
            .distinct()
            .filter { it >= fromDate && it <= toDate }
            .sorted()

        tradingDates.forEach { date ->
            val dayStates = statesBySymbol.mapNotNull { (symbol, states) ->
                states.find { it.candle.candleDate == date }?.let { symbol to it }
            }

            val exitedSymbols = mutableListOf<String>()
            activeTrades.values.forEach { active ->
                val symbolState = dayStates.find { it.first == active.symbol }?.second ?: return@forEach
                val previousState = stateByIndexBySymbol[active.symbol]?.get(symbolState.index - 1)
                val previous2State = stateByIndexBySymbol[active.symbol]?.get(symbolState.index - 2)
                val exitSetup = exitSetupBySymbol.getOrPut(active.symbol) { ExitSetupState() }
                val exitDecision = decideExit(active, symbolState, previousState, previous2State, exitSetup, config)
                if (exitDecision != null) {
                    val grossPnl = (exitDecision.exitPrice - active.entryPrice) * active.quantity
                    val netPnl = grossPnl
                    val netReturnPct = if (active.investedAmount > 0.0) (netPnl / active.investedAmount) * 100.0 else 0.0
                    capital += netPnl
                    peakCapital = maxOf(peakCapital, capital)
                    maxDrawdown = maxOf(maxDrawdown, peakCapital - capital)

                    val debugRows = buildDebugRows(
                        states = statesBySymbol[active.symbol].orEmpty(),
                        entryIndex = active.entryIndex,
                        exitIndex = symbolState.index,
                    )
                    allTrades.add(
                        BollingerBacktestTrade(
                            symbol = active.symbol,
                            companyName = active.companyName,
                            entryDate = active.entryDate.toString(),
                            exitDate = date.toString(),
                            holdingDays = symbolState.index - active.entryIndex,
                            quantity = active.quantity,
                            investedAmount = active.investedAmount.roundTo2(),
                            entryPrice = active.entryPrice.roundTo2(),
                            exitPrice = exitDecision.exitPrice.roundTo2(),
                            exitReason = exitDecision.reason,
                            grossPnlInr = grossPnl.roundTo2(),
                            netPnlInr = netPnl.roundTo2(),
                            netReturnPct = netReturnPct.roundTo2(),
                            entryCriteria = active.entryCriteria,
                            exitCriteria = symbolState.criteria,
                            debugRows = debugRows,
                        )
                    )
                    exitedSymbols.add(active.symbol)
                }
            }
            exitedSymbols.forEach {
                activeTrades.remove(it)
                exitSetupBySymbol.remove(it)
            }

            dayStates.forEach { (symbol, state) ->
                if (activeTrades.containsKey(symbol)) return@forEach
                if (activeTrades.size >= config.maxOpenPositions) return@forEach

                val close = state.candle.close
                val rsi = state.rsi14
                val setup = entrySetupBySymbol.getOrPut(symbol) { EntrySetupState() }
                if (close < state.bbLower) setup.lastLowerTouchIndex = state.index
                if (rsi != null && rsi <= config.entryRsiMax) setup.lastRsiOversoldIndex = state.index

                val isArmed = isWindowArmed(
                    currentIndex = state.index,
                    firstIndex = setup.lastLowerTouchIndex,
                    secondIndex = setup.lastRsiOversoldIndex,
                    windowDays = config.signalWindowDays,
                )
                if (!isArmed) return@forEach

                val previous = stateByIndexBySymbol[symbol]?.get(state.index - 1) ?: return@forEach
                val previous2 = stateByIndexBySymbol[symbol]?.get(state.index - 2) ?: return@forEach
                val prevRsi = previous.rsi14
                val hasTwoDayBullishCloses = close > previous.candle.close &&
                    previous.candle.close > previous2.candle.close
                val isTriggerDay = hasTwoDayBullishCloses &&
                    close > state.bbLower &&
                    rsi != null &&
                    prevRsi != null &&
                    rsi > prevRsi
                if (!isTriggerDay) return@forEach

                val meta = symbolMetaBySymbol[symbol] ?: return@forEach
                val slotCapital = capital / config.maxOpenPositions
                val entryPrice = close
                val quantity = (slotCapital / entryPrice).toInt()
                if (quantity > 0) {
                    val invested = quantity * entryPrice
                    activeTrades[symbol] = ActiveTrade(
                        symbol = symbol,
                        companyName = meta.companyName,
                        entryDate = date,
                        entryIndex = state.index,
                        entryPrice = entryPrice,
                        quantity = quantity,
                        investedAmount = invested,
                        entryCriteria = state.criteria,
                        stopPrice = entryPrice * (1.0 - config.stopLossPct / 100.0),
                    )
                    entrySetupBySymbol.remove(symbol)
                    exitSetupBySymbol[symbol] = ExitSetupState()
                    noTradeSymbols.remove(symbol)
                }
            }
        }

        val summary = summarize(config.capital, capital, allTrades, maxDrawdown)
        return BollingerBacktestResponse(
            config = BollingerBacktestConfigSnapshot(
                universe = universe,
                capital = config.capital,
                maxOpenPositions = config.maxOpenPositions,
                fromDate = config.fromDate,
                toDate = config.toDate,
                signalWindowDays = config.signalWindowDays,
                entryRsiMax = config.entryRsiMax,
                takeProfitPct = config.takeProfitPct,
                stopLossPct = config.stopLossPct,
                maxHoldDays = config.maxHoldDays,
            ),
            summary = summary,
            diagnostics = BollingerBacktestDiagnostics(
                symbolsConsidered = symbols.size,
                symbolsWithInsufficientData = insufficientData.sorted(),
                symbolsWithNoTrades = noTradeSymbols.sorted(),
            ),
            trades = allTrades.sortedBy { it.entryDate },
        )
    }

    private fun summarize(
        initialCapital: Double,
        finalCapital: Double,
        trades: List<BollingerBacktestTrade>,
        maxDrawdown: Double,
    ): BollingerBacktestSummary {
        val grossPnl = trades.sumOf { it.grossPnlInr }
        val netPnl = trades.sumOf { it.netPnlInr }
        val totalBrokerage = 0.0
        val winners = trades.count { it.netPnlInr > 0.0 }
        val losers = trades.count { it.netPnlInr <= 0.0 }
        val winRate = if (trades.isEmpty()) 0.0 else (winners.toDouble() / trades.size) * 100.0
        val avgReturn = if (trades.isEmpty()) 0.0 else trades.map { it.netReturnPct }.average()
        val totalReturn = if (initialCapital <= 0.0) 0.0 else (netPnl / initialCapital) * 100.0

        return BollingerBacktestSummary(
            totalTrades = trades.size,
            winningTrades = winners,
            losingTrades = losers,
            winRatePct = winRate.roundTo2(),
            grossPnlInr = grossPnl.roundTo2(),
            totalBrokerageInr = totalBrokerage.roundTo2(),
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
        previous2State: SymbolState?,
        setup: ExitSetupState,
        config: BollingerBacktestConfig,
    ): ExitDecision? {
        val close = state.candle.close
        val high = state.candle.high
        if (close <= active.stopPrice) return ExitDecision(close, "STOP_LOSS")

        val takeProfitPrice = active.entryPrice * (1.0 + config.takeProfitPct / 100.0)
        if (high >= takeProfitPrice) return ExitDecision(takeProfitPrice, "TAKE_PROFIT")

        if (high >= state.bbUpper) setup.lastUpperTouchIndex = state.index
        if (state.rsi14 != null && state.rsi14 >= EXIT_RSI_THRESHOLD) setup.lastRsiOverboughtIndex = state.index

        val isArmed = isWindowArmed(
            currentIndex = state.index,
            firstIndex = setup.lastUpperTouchIndex,
            secondIndex = setup.lastRsiOverboughtIndex,
            windowDays = config.signalWindowDays,
        )
        if (isArmed && previousState != null && previous2State != null && state.rsi14 != null && previousState.rsi14 != null) {
            val hasTwoDayBearishCloses = close < previousState.candle.close &&
                previousState.candle.close < previous2State.candle.close
            val isExitTrigger = hasTwoDayBearishCloses && state.rsi14 < previousState.rsi14
            if (isExitTrigger) return ExitDecision(close, "REVERSAL_EXIT")
        }

        val holdBars = state.index - active.entryIndex
        return if (holdBars >= config.maxHoldDays) ExitDecision(close, "MAX_HOLD") else null
    }

    private fun isWindowArmed(
        currentIndex: Int,
        firstIndex: Int?,
        secondIndex: Int?,
        windowDays: Int,
    ): Boolean {
        return isWithinWindow(currentIndex, firstIndex, windowDays) &&
            isWithinWindow(currentIndex, secondIndex, windowDays)
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
                    .forEach { row -> result[row.symbol.uppercase()] = SymbolMeta(row.symbol.uppercase(), row.companyName, row.instrumentToken) }
            } else {
                indexConstituentHandler.read { it.listActiveByIndex(key) }
                    .forEach { row -> result[row.symbol.uppercase()] = SymbolMeta(row.symbol.uppercase(), row.companyName, row.instrumentToken) }
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
            SymbolMeta(symbol = row.symbol.trim().uppercase(), companyName = row.companyName, instrumentToken = row.instrumentToken)
        }
    }

    private fun buildStates(candles: List<DailyCandle>): List<SymbolState> {
        val states = mutableListOf<SymbolState>()
        for (i in candles.indices) {
            if (i < 20) continue
            val series = candles.subList(0, i + 1).toTa4jSeries("BT")
            val indicators = Ta4jIndicatorCalculator.calculate(series)
            val close = candles[i].close
            val percentB = indicators.bbPercentB ?: continue
            val bbMiddle = indicators.bbMiddle ?: close
            val bandwidthRaw = indicators.bbBandwidth ?: 0.0
            val bandwidthPct = if (kotlin.math.abs(bandwidthRaw) <= 1.0) bandwidthRaw * 100.0 else bandwidthRaw
            val rsi = indicators.rsi14
            val isSqueeze = indicators.bbSqueeze

            var signal = "NORMAL"
            var score = 50
            val reasons = mutableListOf<String>()

            if (isSqueeze) {
                signal = "SQUEEZE"
                score += 30
                reasons.add("Volatility Squeeze")
            }
            if (percentB <= 0.05) {
                signal = "OVERSOLD"
                score += 20
                reasons.add("Price near lower band")
                if (rsi != null && rsi < 35.0) {
                    score += 20
                    reasons.add("RSI < 35")
                }
            } else if (percentB >= 0.95) {
                signal = "OVERBOUGHT"
                score -= 10
                reasons.add("Price near upper band")
                if (rsi != null && rsi > 70.0) {
                    score -= 10
                    reasons.add("RSI > 70")
                }
            }

            states.add(
                SymbolState(
                    candle = candles[i],
                    index = i,
                    percentB = percentB,
                    bbMiddle = bbMiddle,
                    bbUpper = indicators.bbUpper ?: close,
                    bbLower = indicators.bbLower ?: close,
                    rsi14 = rsi,
                    isSqueeze = isSqueeze,
                    setupScore = score.coerceIn(0, 100),
                    criteria = BollingerCriteriaSnapshot(
                        percentB = percentB.roundTo2(),
                        rsi14 = rsi?.roundTo2(),
                        bandwidthPct = bandwidthPct.roundTo2(),
                        setupScore = score.coerceIn(0, 100),
                        signal = signal,
                        reasoning = reasons.joinToString("; "),
                    ),
                )
            )
        }
        return states
    }

    private fun emptyResponse(
        universe: String,
        config: BollingerBacktestConfig,
        symbolsConsidered: Int,
        insufficientData: List<String>,
        noTrades: List<String>,
    ): BollingerBacktestResponse {
        return BollingerBacktestResponse(
            config = BollingerBacktestConfigSnapshot(
                universe = universe,
                capital = config.capital,
                maxOpenPositions = config.maxOpenPositions,
                fromDate = config.fromDate,
                toDate = config.toDate,
                signalWindowDays = config.signalWindowDays,
                entryRsiMax = config.entryRsiMax,
                takeProfitPct = config.takeProfitPct,
                stopLossPct = config.stopLossPct,
                maxHoldDays = config.maxHoldDays,
            ),
            summary = BollingerBacktestSummary(
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
            diagnostics = BollingerBacktestDiagnostics(
                symbolsConsidered = symbolsConsidered,
                symbolsWithInsufficientData = insufficientData,
                symbolsWithNoTrades = noTrades,
            ),
            trades = emptyList(),
        )
    }

    private fun normalize(config: BollingerBacktestConfig): BollingerBacktestConfig {
        val parsedFrom = config.fromDate?.let { LocalDate.parse(it) }
        val parsedTo = config.toDate?.let { LocalDate.parse(it) }
        require(parsedFrom == null || parsedTo == null || !parsedFrom.isAfter(parsedTo)) {
            "fromDate must be on or before toDate"
        }
        return config.copy(
            capital = config.capital.coerceAtLeast(10_000.0),
            maxOpenPositions = config.maxOpenPositions.coerceAtLeast(1),
            signalWindowDays = config.signalWindowDays.coerceAtLeast(1),
            entryRsiMax = config.entryRsiMax.coerceIn(5.0, 50.0),
            takeProfitPct = config.takeProfitPct.coerceAtLeast(0.1),
            stopLossPct = config.stopLossPct.coerceAtLeast(0.1),
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
        val percentB: Double,
        val bbMiddle: Double,
        val bbUpper: Double,
        val bbLower: Double,
        val rsi14: Double?,
        val isSqueeze: Boolean,
        val setupScore: Int,
        val criteria: BollingerCriteriaSnapshot,
    )

    private data class ActiveTrade(
        val symbol: String,
        val companyName: String,
        val entryDate: LocalDate,
        val entryIndex: Int,
        val entryPrice: Double,
        val quantity: Int,
        val investedAmount: Double,
        val entryCriteria: BollingerCriteriaSnapshot,
        val stopPrice: Double,
    )

    private data class ExitDecision(
        val exitPrice: Double,
        val reason: String,
    )

    private data class EntrySetupState(
        var lastLowerTouchIndex: Int? = null,
        var lastRsiOversoldIndex: Int? = null,
    )

    private data class ExitSetupState(
        var lastUpperTouchIndex: Int? = null,
        var lastRsiOverboughtIndex: Int? = null,
    )

    private fun buildDebugRows(states: List<SymbolState>, entryIndex: Int, exitIndex: Int): List<BollingerBacktestDebugRow> {
        if (states.isEmpty()) return emptyList()
        val from = maxOf(0, entryIndex - 5)
        val to = exitIndex + 5
        val windowStates = states.filter { it.index in from..to }.sortedBy { it.index }
        return windowStates.map { state ->
            BollingerBacktestDebugRow(
                date = state.candle.candleDate.toString(),
                ltp = state.candle.close.roundTo2(),
                bbUpper = state.bbUpper.roundTo2(),
                bbMiddle = state.bbMiddle.roundTo2(),
                bbLower = state.bbLower.roundTo2(),
                percentB = state.criteria.percentB,
                bandwidthPct = state.criteria.bandwidthPct,
                rsi14 = state.criteria.rsi14,
                setupScore = state.criteria.setupScore,
                signal = state.criteria.signal,
                reasoning = state.criteria.reasoning,
            )
        }
    }

    companion object {
        private const val EXIT_RSI_THRESHOLD = 70.0
    }
}
