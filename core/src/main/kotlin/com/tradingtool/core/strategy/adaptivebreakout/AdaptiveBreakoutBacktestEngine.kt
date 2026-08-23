package com.tradingtool.core.strategy.adaptivebreakout

import com.tradingtool.core.delivery.model.StockDeliveryDaily
import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate

internal object AdaptiveBreakoutBacktestEngine {
    fun run(
        candles: List<DailyCandle>,
        deliveries: List<StockDeliveryDaily>,
        testFromDate: LocalDate,
        testToDate: LocalDate,
        targetPct: Double,
        stopLossPct: Double,
        entryTrigger: AdaptiveBreakoutBacktestEntryTrigger = AdaptiveBreakoutBacktestEntryTrigger.FRESH_BREAKOUT,
        qualityFilter: AdaptiveBreakoutQualityFilter = AdaptiveBreakoutQualityFilter.NONE,
        maxEntryGapPct: Double = 2.5,
        transactionCostPct: Double = 0.3,
        slippagePct: Double = 0.2,
        marketCandles: List<DailyCandle> = emptyList(),
    ): AdaptiveBreakoutBacktestResponse {
        require(targetPct > 0.0) { "targetPct must be positive." }
        require(stopLossPct > 0.0) { "stopLossPct must be positive." }
        require(maxEntryGapPct >= 0.0) { "maxEntryGapPct must not be negative." }
        require(transactionCostPct >= 0.0) { "transactionCostPct must not be negative." }
        require(slippagePct >= 0.0) { "slippagePct must not be negative." }
        require(!testFromDate.isAfter(testToDate)) { "testFromDate must not be after testToDate." }

        val orderedCandles = candles.distinctBy(DailyCandle::candleDate).sortedBy(DailyCandle::candleDate)
        val evaluation = AdaptiveBreakoutEngine.evaluate(orderedCandles)
            ?: throw IllegalArgumentException("Not enough candle history to evaluate adaptive breakouts.")
        val breakoutSteps = evaluation.rawSteps
            .withIndex()
            .filter { (_, step) ->
                val matchTrigger = when (entryTrigger) {
                    AdaptiveBreakoutBacktestEntryTrigger.FRESH_BREAKOUT -> step.decision == AdaptiveBreakoutDecision.FRESH_BREAKOUT
                    AdaptiveBreakoutBacktestEntryTrigger.CEILING_RECLAIM -> step.decision == AdaptiveBreakoutDecision.CEILING_RECLAIM
                }
                matchTrigger && step.date >= testFromDate.toString() && step.date <= testToDate.toString()
            }

        val orderedDeliveries = deliveries.sortedBy(StockDeliveryDaily::tradingDate)
        var deliveryEndIndex = 0
        val trades = mutableListOf<AdaptiveBreakoutBacktestTrade>()
        var nextEntryIndex = 0
        breakoutSteps.forEach { indexedStep ->
            val breakoutDate = LocalDate.parse(indexedStep.value.date)
            while (deliveryEndIndex < orderedDeliveries.size &&
                !orderedDeliveries[deliveryEndIndex].tradingDate.isAfter(breakoutDate)
            ) {
                deliveryEndIndex += 1
            }
            val qualityResponse = BreakoutDayQualityAnalyzer.analyzeStep(
                symbol = orderedCandles.first().symbol,
                candles = orderedCandles.subList(0, indexedStep.index + 1),
                deliveries = orderedDeliveries.subList(0, deliveryEndIndex),
                step = indexedStep.value,
            )
            val trendAlignment = BreakoutTrendAlignmentAnalyzer.analyze(
                stockCandles = orderedCandles.subList(0, indexedStep.index + 1),
                marketCandles = marketCandles,
            )
            val nextSessionBehavior = nextSessionBehavior(orderedCandles, indexedStep.index)

            val entryIndex = orderedCandles.indices.firstOrNull { index ->
                index > indexedStep.index && index >= nextEntryIndex && orderedCandles[index].hasTradableRange()
            } ?: return@forEach
            val entryCandle = orderedCandles[entryIndex]
            if (entryCandle.candleDate.isAfter(testToDate)) return@forEach

            if (!acceptsQualityFilter(qualityFilter, qualityResponse.rules)) {
                trades += filteredTrade(
                    breakoutStep = indexedStep.value,
                    entryCandle = entryCandle,
                    qualityDecision = qualityResponse.overallDecision,
                    qualitySummary = qualityResponse.decisionSummary,
                    qualityChecks = qualityResponse.rules,
                    trendAlignment = trendAlignment,
                    nextSessionBehavior = nextSessionBehavior,
                    volumeFootprint = qualityResponse.volumeFootprint,
                )
                return@forEach
            }

            val trade = simulateTrade(
                candles = orderedCandles,
                breakoutStep = indexedStep.value,
                entryIndex = entryIndex,
                testToDate = testToDate,
                targetPct = targetPct,
                stopLossPct = stopLossPct,
                qualityDecision = qualityResponse.overallDecision,
                qualitySummary = qualityResponse.decisionSummary,
                qualityChecks = qualityResponse.rules,
                trendAlignment = trendAlignment,
                nextSessionBehavior = nextSessionBehavior,
                volumeFootprint = qualityResponse.volumeFootprint,
                forwardHorizons = FORWARD_HORIZONS,
                maxEntryGapPct = maxEntryGapPct,
                transactionCostPct = transactionCostPct,
                slippagePct = slippagePct,
            )
            trades += trade
            // Only advance position lock for trades that actually entered.
            if (trade.exitReason != AdaptiveBreakoutBacktestExitReason.SKIPPED_ENTRY_GAP) {
                nextEntryIndex = tradeExitIndex(orderedCandles, trade) + 1
            }
        }

        return AdaptiveBreakoutBacktestResponse(
            symbol = orderedCandles.first().symbol,
            testedFromDate = testFromDate.toString(),
            testedToDate = testToDate.toString(),
            entryTrigger = entryTrigger,
            qualityFilter = qualityFilter,
            targetPct = targetPct,
            stopLossPct = stopLossPct,
            transactionCostPct = transactionCostPct,
            slippagePct = slippagePct,
            entryRule = "${entryTrigger.displayName()} with ${qualityFilter.displayName()} quality filter. " +
                describeEntryGapGuard(maxEntryGapPct) + " Returns subtract ${transactionCostPct}% round-trip costs and ${slippagePct}% slippage.",
            ambiguousCandleRule = "If one daily candle touches both target and stop, stop is assumed first because daily OHLC has no intraday order.",
            summary = summarizeAdaptiveBreakoutTrades(breakoutSteps.size, trades),
            trades = trades,
        )
    }

    private fun simulateTrade(
        candles: List<DailyCandle>,
        breakoutStep: AdaptiveBreakoutRawStep,
        entryIndex: Int,
        testToDate: LocalDate,
        targetPct: Double,
        stopLossPct: Double,
        qualityDecision: BreakoutQualityDecision,
        qualitySummary: String,
        qualityChecks: List<BreakoutQualityRuleResult> = emptyList(),
        trendAlignment: BreakoutTrendAlignmentResult? = null,
        nextSessionBehavior: AdaptiveBreakoutNextSessionBehavior? = null,
        volumeFootprint: BreakoutVolumeFootprintResult? = null,
        forwardHorizons: List<Int> = FORWARD_HORIZONS,
        maxEntryGapPct: Double = 0.0,
        transactionCostPct: Double = 0.0,
        slippagePct: Double = 0.0,
    ): AdaptiveBreakoutBacktestTrade {
        val entryCandle = candles[entryIndex]
        val entryPrice = entryCandle.open
        val breakoutClose = breakoutStep.close
        val entryGapPct = if (breakoutClose > 0.0) ((entryPrice / breakoutClose) - 1.0) * 100.0 else null

        // Gap guard: skip if the first later tradable open is too extended above breakout close.
        if (maxEntryGapPct > 0.0 && entryGapPct != null && entryGapPct > maxEntryGapPct) {
            return AdaptiveBreakoutBacktestTrade(
                symbol = entryCandle.symbol,
                breakoutDate = breakoutStep.date,
                breakoutClose = breakoutClose,
                entryDate = entryCandle.candleDate.toString(),
                entryPrice = entryPrice,
                entryGapPct = entryGapPct,
                targetPrice = null,
                stopPrice = null,
                exitDate = null,
                exitPrice = null,
                exitReason = AdaptiveBreakoutBacktestExitReason.SKIPPED_ENTRY_GAP,
                holdingSessions = null,
                returnPct = null,
                ambiguousSameCandle = false,
                gapThroughExit = false,
                grossReturnPct = null,
                qualityDecision = qualityDecision,
                qualitySummary = qualitySummary,
                qualityChecks = qualityChecks,
                trendAlignment = trendAlignment,
                nextSessionBehavior = nextSessionBehavior,
                volumeFootprint = volumeFootprint,
                forwardOutcomes = emptyList(),
            )
        }

        val targetPrice = entryPrice * (1.0 + targetPct / 100.0)
        val stopPrice = entryPrice * (1.0 - stopLossPct / 100.0)
        val finalIndex = candles.indexOfLast { candle -> !candle.candleDate.isAfter(testToDate) }
        val exitSignal = (entryIndex..finalIndex)
            .mapNotNull { index -> exitSignal(candles[index], targetPrice, stopPrice)?.let { signal -> index to signal } }
            .firstOrNull()

        if (exitSignal == null) {
            val lastCandle = candles[finalIndex]
            return buildTrade(
                breakoutStep = breakoutStep,
                entryCandle = entryCandle,
                entryGapPct = entryGapPct,
                targetPrice = targetPrice,
                stopPrice = stopPrice,
                exitCandle = lastCandle,
                exitPrice = lastCandle.close,
                exitReason = AdaptiveBreakoutBacktestExitReason.END_OF_TEST,
                holdingSessions = finalIndex - entryIndex + 1,
                ambiguousSameCandle = false,
                gapThroughExit = false,
                transactionCostPct = transactionCostPct,
                slippagePct = slippagePct,
                qualityDecision = qualityDecision,
                qualitySummary = qualitySummary,
                qualityChecks = qualityChecks,
                trendAlignment = trendAlignment,
                nextSessionBehavior = nextSessionBehavior,
                volumeFootprint = volumeFootprint,
                forwardOutcomes = forwardOutcomes(candles, entryIndex, finalIndex, entryPrice, targetPrice, forwardHorizons),
            )
        }

        val (exitIndex, signal) = exitSignal
        val exitCandle = candles[exitIndex]
        return buildTrade(
            breakoutStep = breakoutStep,
            entryCandle = entryCandle,
            entryGapPct = entryGapPct,
            targetPrice = targetPrice,
            stopPrice = stopPrice,
            exitCandle = exitCandle,
            exitPrice = signal.price,
            exitReason = signal.reason,
            holdingSessions = exitIndex - entryIndex + 1,
            ambiguousSameCandle = signal.ambiguousSameCandle,
            gapThroughExit = signal.gapThrough,
            transactionCostPct = transactionCostPct,
            slippagePct = slippagePct,
            qualityDecision = qualityDecision,
            qualitySummary = qualitySummary,
            qualityChecks = qualityChecks,
            trendAlignment = trendAlignment,
            nextSessionBehavior = nextSessionBehavior,
            volumeFootprint = volumeFootprint,
            forwardOutcomes = forwardOutcomes(candles, entryIndex, finalIndex, entryCandle.open, targetPrice, forwardHorizons),
        )
    }

    private fun buildTrade(
        breakoutStep: AdaptiveBreakoutRawStep,
        entryCandle: DailyCandle,
        entryGapPct: Double?,
        targetPrice: Double,
        stopPrice: Double,
        exitCandle: DailyCandle,
        exitPrice: Double,
        exitReason: AdaptiveBreakoutBacktestExitReason,
        holdingSessions: Int,
        ambiguousSameCandle: Boolean,
        gapThroughExit: Boolean,
        transactionCostPct: Double,
        slippagePct: Double,
        qualityDecision: BreakoutQualityDecision,
        qualitySummary: String,
        qualityChecks: List<BreakoutQualityRuleResult> = emptyList(),
        trendAlignment: BreakoutTrendAlignmentResult? = null,
        nextSessionBehavior: AdaptiveBreakoutNextSessionBehavior? = null,
        volumeFootprint: BreakoutVolumeFootprintResult? = null,
        forwardOutcomes: List<AdaptiveBreakoutForwardOutcome> = emptyList(),
    ): AdaptiveBreakoutBacktestTrade {
        val grossReturnPct = (exitPrice / entryCandle.open - 1.0) * 100.0
        val netReturnPct = grossReturnPct - transactionCostPct - slippagePct
        return AdaptiveBreakoutBacktestTrade(
            symbol = entryCandle.symbol,
            breakoutDate = breakoutStep.date,
            breakoutClose = breakoutStep.close,
            entryDate = entryCandle.candleDate.toString(),
            entryPrice = entryCandle.open,
            entryGapPct = entryGapPct,
            targetPrice = targetPrice,
            stopPrice = stopPrice,
            exitDate = exitCandle.candleDate.toString(),
            exitPrice = exitPrice,
            exitReason = exitReason,
            holdingSessions = holdingSessions,
            returnPct = netReturnPct,
            ambiguousSameCandle = ambiguousSameCandle,
            gapThroughExit = gapThroughExit,
            grossReturnPct = grossReturnPct,
            qualityDecision = qualityDecision,
            qualitySummary = qualitySummary,
            qualityChecks = qualityChecks,
            trendAlignment = trendAlignment,
            nextSessionBehavior = nextSessionBehavior,
            volumeFootprint = volumeFootprint,
            forwardOutcomes = forwardOutcomes,
        )
    }

    private fun filteredTrade(
        breakoutStep: AdaptiveBreakoutRawStep,
        entryCandle: DailyCandle,
        qualityDecision: BreakoutQualityDecision,
        qualitySummary: String,
        qualityChecks: List<BreakoutQualityRuleResult>,
        trendAlignment: BreakoutTrendAlignmentResult,
        nextSessionBehavior: AdaptiveBreakoutNextSessionBehavior?,
        volumeFootprint: BreakoutVolumeFootprintResult,
    ): AdaptiveBreakoutBacktestTrade {
        val entryGapPct = if (breakoutStep.close > 0.0) {
            (entryCandle.open / breakoutStep.close - 1.0) * 100.0
        } else {
            null
        }
        return AdaptiveBreakoutBacktestTrade(
            symbol = entryCandle.symbol,
            breakoutDate = breakoutStep.date,
            breakoutClose = breakoutStep.close,
            entryDate = entryCandle.candleDate.toString(),
            entryPrice = entryCandle.open,
            entryGapPct = entryGapPct,
            targetPrice = null,
            stopPrice = null,
            exitDate = null,
            exitPrice = null,
            exitReason = AdaptiveBreakoutBacktestExitReason.FILTERED_BY_QUALITY,
            holdingSessions = null,
            returnPct = null,
            ambiguousSameCandle = false,
            gapThroughExit = false,
            grossReturnPct = null,
            qualityDecision = qualityDecision,
            qualitySummary = qualitySummary,
            qualityChecks = qualityChecks,
            trendAlignment = trendAlignment,
            nextSessionBehavior = nextSessionBehavior,
            volumeFootprint = volumeFootprint,
        )
    }

    private fun acceptsQualityFilter(
        filter: AdaptiveBreakoutQualityFilter,
        rules: List<BreakoutQualityRuleResult>,
    ): Boolean {
        val passKeys = rules.filter { rule -> rule.verdict == BreakoutQualityVerdict.PASS }
            .map(BreakoutQualityRuleResult::key)
            .toSet()
        return when (filter) {
            AdaptiveBreakoutQualityFilter.NONE -> true
            AdaptiveBreakoutQualityFilter.BASE_QUALITY -> "base-quality" in passKeys
        }
    }

    private fun nextSessionBehavior(
        candles: List<DailyCandle>,
        breakoutIndex: Int,
    ): AdaptiveBreakoutNextSessionBehavior? {
        val breakoutCandle = candles[breakoutIndex]
        val nextCandle = candles.getOrNull(breakoutIndex + 1) ?: return null
        val previousCandle = candles.getOrNull(breakoutIndex - 1)
        return AdaptiveBreakoutNextSessionBehavior(
            sessionDate = nextCandle.candleDate.toString(),
            open = nextCandle.open,
            low = nextCandle.low,
            close = nextCandle.close,
            breakoutDayMovePct = previousCandle?.let { candle ->
                percentageChange(candle.close, breakoutCandle.close)
            },
            lowVsBreakoutClosePct = percentageChange(breakoutCandle.close, nextCandle.low),
            lowVsBreakoutLowPct = percentageChange(breakoutCandle.low, nextCandle.low),
            closeVsBreakoutClosePct = percentageChange(breakoutCandle.close, nextCandle.close),
            lowVsOpenPct = percentageChange(nextCandle.open, nextCandle.low),
        )
    }

    private fun percentageChange(start: Double, end: Double): Double =
        if (start == 0.0) 0.0 else (end / start - 1.0) * 100.0

    private fun forwardOutcomes(
        candles: List<DailyCandle>,
        entryIndex: Int,
        finalIndex: Int,
        entryPrice: Double,
        targetPrice: Double,
        horizons: List<Int>,
    ): List<AdaptiveBreakoutForwardOutcome> = horizons.mapNotNull { horizon ->
        val endIndex = entryIndex + horizon - 1
        if (endIndex > finalIndex) return@mapNotNull null
        val window = candles.subList(entryIndex, endIndex + 1)
        val closeReturnPct = (window.last().close / entryPrice - 1.0) * 100.0
        val maxHighReturnPct = (window.maxOf(DailyCandle::high) / entryPrice - 1.0) * 100.0
        AdaptiveBreakoutForwardOutcome(
            sessions = horizon,
            closeReturnPct = closeReturnPct,
            maxHighReturnPct = maxHighReturnPct,
            targetReached = window.any { candle -> candle.high >= targetPrice },
        )
    }

    private fun exitSignal(
        candle: DailyCandle,
        targetPrice: Double,
        stopPrice: Double,
    ): ExitSignal? {
        if (candle.open > targetPrice) {
            return ExitSignal(candle.open, AdaptiveBreakoutBacktestExitReason.TARGET_HIT, true, false)
        }
        if (candle.open < stopPrice) {
            return ExitSignal(candle.open, AdaptiveBreakoutBacktestExitReason.STOP_LOSS, true, false)
        }

        val targetTouched = candle.high >= targetPrice
        val stopTouched = candle.low <= stopPrice
        if (!targetTouched && !stopTouched) return null
        return when {
            targetTouched && stopTouched -> ExitSignal(
                stopPrice,
                AdaptiveBreakoutBacktestExitReason.STOP_LOSS_SAME_CANDLE,
                false,
                true,
            )
            targetTouched -> ExitSignal(targetPrice, AdaptiveBreakoutBacktestExitReason.TARGET_HIT, false, false)
            else -> ExitSignal(stopPrice, AdaptiveBreakoutBacktestExitReason.STOP_LOSS, false, false)
        }
    }

    private data class ExitSignal(
        val price: Double,
        val reason: AdaptiveBreakoutBacktestExitReason,
        val gapThrough: Boolean,
        val ambiguousSameCandle: Boolean,
    )

    private fun tradeExitIndex(candles: List<DailyCandle>, trade: AdaptiveBreakoutBacktestTrade): Int =
        candles.indexOfFirst { candle -> candle.candleDate.toString() == trade.exitDate }

    private fun DailyCandle.hasTradableRange(): Boolean = high > low

    private val FORWARD_HORIZONS = listOf(5, 10, 20)
}
