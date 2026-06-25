package com.tradingtool.core.strategy.fiftytwohigh

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.technical.roundTo2
import java.time.LocalDate
import java.time.OffsetDateTime

class ChartinkFiftyTwoWeekHighBacktestEngine {

    fun run(
        inputFile: String,
        priceDataToDate: LocalDate,
        strategies: List<ChartinkFiftyTwoWeekHighBacktestStrategy>,
        contexts: List<ChartinkFiftyTwoWeekHighSymbolContext>,
    ): ChartinkFiftyTwoWeekHighBacktestReport {
        val tradeRows = contexts
            .flatMap { context -> evaluateSignal(context, strategies) }
            .sortedWith(
                compareBy<ChartinkFiftyTwoWeekHighTradeRow> { it.signalDate }
                    .thenBy { it.symbol }
                    .thenBy { it.strategyName },
            )

        val summaries = strategies.map { strategy ->
            summarizeStrategy(
                strategyName = strategy.name,
                contexts = contexts,
                strategyRows = tradeRows.filter { row -> row.strategyName == strategy.name },
            )
        }

        return ChartinkFiftyTwoWeekHighBacktestReport(
            generatedAt = OffsetDateTime.now().toString(),
            inputFile = inputFile,
            priceDataToDate = priceDataToDate.toString(),
            strategies = strategies,
            signalCount = contexts.size,
            uniqueSymbolCount = contexts.map { context -> context.signal.symbol }.distinct().size,
            summaries = summaries,
            trades = tradeRows,
        )
    }

    private fun evaluateSignal(
        context: ChartinkFiftyTwoWeekHighSymbolContext,
        strategies: List<ChartinkFiftyTwoWeekHighBacktestStrategy>,
    ): List<ChartinkFiftyTwoWeekHighTradeRow> {
        return strategies.map { strategy ->
            evaluateStrategy(context, strategy)
        }
    }

    private fun evaluateStrategy(
        context: ChartinkFiftyTwoWeekHighSymbolContext,
        strategy: ChartinkFiftyTwoWeekHighBacktestStrategy,
    ): ChartinkFiftyTwoWeekHighTradeRow {
        val signal = context.signal
        val candles = context.candles.sortedBy { candle -> candle.candleDate }
        val latestCandle = candles.lastOrNull()
        val marketCapBucket = normalizeMarketCapBucket(signal.marketCapName)

        if (candles.isEmpty()) {
            return ChartinkFiftyTwoWeekHighTradeRow(
                strategyName = strategy.name,
                symbol = signal.symbol,
                marketCapName = signal.marketCapName,
                marketCapBucket = marketCapBucket,
                sector = signal.sector,
                signalDate = signal.signalDate.toString(),
                entryDate = null,
                exitDate = null,
                entryPrice = null,
                exitPrice = null,
                targetPrice = null,
                stopPrice = null,
                outcome = OUTCOME_NO_PRICE_DATA,
                success = false,
                holdingTradingDays = null,
                holdingCalendarDays = null,
                returnPct = null,
                maxFavorableExcursionPct = null,
                maxAdverseExcursionPct = null,
                forward5dReturnPct = null,
                forward10dReturnPct = null,
                forward20dReturnPct = null,
                forward60dReturnPct = null,
                exitWasAmbiguous = false,
                latestAvailableDate = null,
            )
        }

        val entryIndex = candles.indexOfFirst { candle -> candle.candleDate > signal.signalDate }
        if (entryIndex < 0) {
            return ChartinkFiftyTwoWeekHighTradeRow(
                strategyName = strategy.name,
                symbol = signal.symbol,
                marketCapName = signal.marketCapName,
                marketCapBucket = marketCapBucket,
                sector = signal.sector,
                signalDate = signal.signalDate.toString(),
                entryDate = null,
                exitDate = null,
                entryPrice = null,
                exitPrice = null,
                targetPrice = null,
                stopPrice = null,
                outcome = OUTCOME_NO_NEXT_TRADING_DAY,
                success = false,
                holdingTradingDays = null,
                holdingCalendarDays = null,
                returnPct = null,
                maxFavorableExcursionPct = null,
                maxAdverseExcursionPct = null,
                forward5dReturnPct = null,
                forward10dReturnPct = null,
                forward20dReturnPct = null,
                forward60dReturnPct = null,
                exitWasAmbiguous = false,
                latestAvailableDate = latestCandle?.candleDate?.toString(),
            )
        }

        val entryCandle = candles[entryIndex]
        val entryPrice = entryCandle.open
        if (!entryPrice.isFinite() || entryPrice <= 0.0) {
            return ChartinkFiftyTwoWeekHighTradeRow(
                strategyName = strategy.name,
                symbol = signal.symbol,
                marketCapName = signal.marketCapName,
                marketCapBucket = marketCapBucket,
                sector = signal.sector,
                signalDate = signal.signalDate.toString(),
                entryDate = null,
                exitDate = null,
                entryPrice = null,
                exitPrice = null,
                targetPrice = null,
                stopPrice = null,
                outcome = OUTCOME_INVALID_ENTRY_PRICE,
                success = false,
                holdingTradingDays = null,
                holdingCalendarDays = null,
                returnPct = null,
                maxFavorableExcursionPct = null,
                maxAdverseExcursionPct = null,
                forward5dReturnPct = null,
                forward10dReturnPct = null,
                forward20dReturnPct = null,
                forward60dReturnPct = null,
                exitWasAmbiguous = false,
                latestAvailableDate = latestCandle?.candleDate?.toString(),
            )
        }

        val targetPrice = entryPrice * (1.0 + (strategy.profitTargetPct / 100.0))
        val stopPrice = entryPrice * (1.0 - (strategy.stopLossPct / 100.0))
        val exit = findExit(
            candles = candles,
            entryIndex = entryIndex,
            targetPrice = targetPrice,
            stopPrice = stopPrice,
        )

        val window = candles.subList(entryIndex, exit.exitIndex + 1)
        val maxFavorableExcursionPct = window
            .mapNotNull { candle -> pctChange(fromPrice = entryPrice, toPrice = candle.high) }
            .maxOrNull()
        val maxAdverseExcursionPct = window
            .mapNotNull { candle -> pctChange(fromPrice = entryPrice, toPrice = candle.low) }
            .minOrNull()
        val holdingTradingDays = (exit.exitIndex - entryIndex).coerceAtLeast(0)
        val holdingCalendarDays = java.time.temporal.ChronoUnit.DAYS.between(
            entryCandle.candleDate,
            exit.exitDate,
        )
        val forward5dReturnPct = computeForwardReturnPct(candles, entryIndex, 5, entryPrice)
        val forward10dReturnPct = computeForwardReturnPct(candles, entryIndex, 10, entryPrice)
        val forward20dReturnPct = computeForwardReturnPct(candles, entryIndex, 20, entryPrice)
        val forward60dReturnPct = computeForwardReturnPct(candles, entryIndex, 60, entryPrice)

        return ChartinkFiftyTwoWeekHighTradeRow(
            strategyName = strategy.name,
            symbol = signal.symbol,
            marketCapName = signal.marketCapName,
            marketCapBucket = marketCapBucket,
            sector = signal.sector,
            signalDate = signal.signalDate.toString(),
            entryDate = entryCandle.candleDate.toString(),
            exitDate = exit.exitDate.toString(),
            entryPrice = entryPrice.roundTo2(),
            exitPrice = exit.exitPrice.roundTo2(),
            targetPrice = targetPrice.roundTo2(),
            stopPrice = stopPrice.roundTo2(),
            outcome = exit.outcome,
            success = exit.outcome == OUTCOME_TARGET_HIT,
            holdingTradingDays = holdingTradingDays,
            holdingCalendarDays = holdingCalendarDays,
            returnPct = pctChange(entryPrice, exit.exitPrice)?.roundTo2(),
            maxFavorableExcursionPct = maxFavorableExcursionPct?.roundTo2(),
            maxAdverseExcursionPct = maxAdverseExcursionPct?.roundTo2(),
            forward5dReturnPct = forward5dReturnPct?.roundTo2(),
            forward10dReturnPct = forward10dReturnPct?.roundTo2(),
            forward20dReturnPct = forward20dReturnPct?.roundTo2(),
            forward60dReturnPct = forward60dReturnPct?.roundTo2(),
            exitWasAmbiguous = exit.exitWasAmbiguous,
            latestAvailableDate = latestCandle?.candleDate?.toString(),
        )
    }

    private fun findExit(
        candles: List<DailyCandle>,
        entryIndex: Int,
        targetPrice: Double,
        stopPrice: Double,
    ): StrategyExit {
        for (index in entryIndex until candles.size) {
            val candle = candles[index]
            val hitStop = candle.low <= stopPrice
            val hitTarget = candle.high >= targetPrice

            if (hitStop && hitTarget) {
                return StrategyExit(
                    exitIndex = index,
                    exitDate = candle.candleDate,
                    exitPrice = stopPrice,
                    outcome = OUTCOME_STOP_LOSS,
                    exitWasAmbiguous = true,
                )
            }

            if (hitStop) {
                return StrategyExit(
                    exitIndex = index,
                    exitDate = candle.candleDate,
                    exitPrice = stopPrice,
                    outcome = OUTCOME_STOP_LOSS,
                    exitWasAmbiguous = false,
                )
            }

            if (hitTarget) {
                return StrategyExit(
                    exitIndex = index,
                    exitDate = candle.candleDate,
                    exitPrice = targetPrice,
                    outcome = OUTCOME_TARGET_HIT,
                    exitWasAmbiguous = false,
                )
            }
        }

        val lastCandle = candles.last()
        return StrategyExit(
            exitIndex = candles.lastIndex,
            exitDate = lastCandle.candleDate,
            exitPrice = lastCandle.close,
            outcome = OUTCOME_EXIT_AT_END,
            exitWasAmbiguous = false,
        )
    }

    private fun summarizeStrategy(
        strategyName: String,
        contexts: List<ChartinkFiftyTwoWeekHighSymbolContext>,
        strategyRows: List<ChartinkFiftyTwoWeekHighTradeRow>,
    ): ChartinkFiftyTwoWeekHighStrategySummary {
        val bucketOrder = listOf("Largecap", "Midcap", "Smallcap", "Other")
        val bucketSummaries = bucketOrder.mapNotNull { bucket ->
            val bucketSignals = contexts.filter { context ->
                normalizeMarketCapBucket(context.signal.marketCapName) == bucket
            }
            if (bucketSignals.isEmpty()) {
                return@mapNotNull null
            }

            val bucketRows = strategyRows.filter { row -> row.marketCapBucket == bucket }
            buildBucketSummary(
                strategyName = strategyName,
                marketCapBucket = bucket,
                totalSignals = bucketSignals.size,
                rows = bucketRows,
            )
        }

        return buildStrategySummary(
            strategyName = strategyName,
            totalSignals = contexts.size,
            rows = strategyRows,
            buckets = bucketSummaries,
        )
    }

    private fun buildStrategySummary(
        strategyName: String,
        totalSignals: Int,
        rows: List<ChartinkFiftyTwoWeekHighTradeRow>,
        buckets: List<ChartinkFiftyTwoWeekHighBucketSummary>,
    ): ChartinkFiftyTwoWeekHighStrategySummary {
        val enteredTrades = rows.filter { row -> row.entryDate != null }
        val successCount = enteredTrades.count { row -> row.success }
        val stopLossCount = enteredTrades.count { row -> row.outcome == OUTCOME_STOP_LOSS }
        val endExitCount = enteredTrades.count { row -> row.outcome == OUTCOME_EXIT_AT_END }
        val noEntryCount = rows.count { row ->
            row.outcome == OUTCOME_NO_PRICE_DATA ||
                row.outcome == OUTCOME_NO_NEXT_TRADING_DAY ||
                row.outcome == OUTCOME_INVALID_ENTRY_PRICE
        }
        val holdingDays = enteredTrades.mapNotNull { row -> row.holdingTradingDays }

        return ChartinkFiftyTwoWeekHighStrategySummary(
            strategyName = strategyName,
            totalSignals = totalSignals,
            enteredTrades = enteredTrades.size,
            successCount = successCount,
            stopLossCount = stopLossCount,
            endExitCount = endExitCount,
            noEntryCount = noEntryCount,
            successRatePct = successRate(successCount, enteredTrades.size),
            avgHoldingTradingDays = holdingDays.averageOrNull()?.roundTo2(),
            medianHoldingTradingDays = holdingDays.medianOrNull()?.roundTo2(),
            buckets = buckets,
        )
    }

    private fun buildBucketSummary(
        strategyName: String,
        marketCapBucket: String,
        totalSignals: Int,
        rows: List<ChartinkFiftyTwoWeekHighTradeRow>,
    ): ChartinkFiftyTwoWeekHighBucketSummary {
        val enteredTrades = rows.filter { row -> row.entryDate != null }
        val successCount = enteredTrades.count { row -> row.success }
        val stopLossCount = enteredTrades.count { row -> row.outcome == OUTCOME_STOP_LOSS }
        val endExitCount = enteredTrades.count { row -> row.outcome == OUTCOME_EXIT_AT_END }
        val noEntryCount = rows.count { row ->
            row.outcome == OUTCOME_NO_PRICE_DATA ||
                row.outcome == OUTCOME_NO_NEXT_TRADING_DAY ||
                row.outcome == OUTCOME_INVALID_ENTRY_PRICE
        }
        val holdingDays = enteredTrades.mapNotNull { row -> row.holdingTradingDays }

        return ChartinkFiftyTwoWeekHighBucketSummary(
            strategyName = strategyName,
            marketCapBucket = marketCapBucket,
            totalSignals = totalSignals,
            enteredTrades = enteredTrades.size,
            successCount = successCount,
            stopLossCount = stopLossCount,
            endExitCount = endExitCount,
            noEntryCount = noEntryCount,
            successRatePct = successRate(successCount, enteredTrades.size),
            avgHoldingTradingDays = holdingDays.averageOrNull()?.roundTo2(),
            medianHoldingTradingDays = holdingDays.medianOrNull()?.roundTo2(),
        )
    }

    private fun computeForwardReturnPct(
        candles: List<DailyCandle>,
        entryIndex: Int,
        forwardTradingDays: Int,
        entryPrice: Double,
    ): Double? {
        val forwardIndex = entryIndex + forwardTradingDays
        if (forwardIndex > candles.lastIndex) {
            return null
        }
        return pctChange(entryPrice, candles[forwardIndex].close)
    }

    private fun successRate(successCount: Int, enteredTrades: Int): Double {
        if (enteredTrades <= 0) {
            return 0.0
        }
        return ((successCount.toDouble() / enteredTrades.toDouble()) * 100.0).roundTo2()
    }

    private fun pctChange(fromPrice: Double, toPrice: Double): Double? {
        if (fromPrice <= 0.0 || !fromPrice.isFinite() || !toPrice.isFinite()) {
            return null
        }
        return ((toPrice - fromPrice) / fromPrice) * 100.0
    }

    private fun normalizeMarketCapBucket(marketCapName: String): String {
        return when (marketCapName.trim().lowercase()) {
            "largecap" -> "Largecap"
            "midcap" -> "Midcap"
            "smallcap" -> "Smallcap"
            else -> "Other"
        }
    }

    private fun List<Int>.averageOrNull(): Double? {
        if (isEmpty()) {
            return null
        }
        return average()
    }

    private fun List<Int>.medianOrNull(): Double? {
        if (isEmpty()) {
            return null
        }
        val sorted = sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]).toDouble() / 2.0
        } else {
            sorted[middle].toDouble()
        }
    }

    private data class StrategyExit(
        val exitIndex: Int,
        val exitDate: LocalDate,
        val exitPrice: Double,
        val outcome: String,
        val exitWasAmbiguous: Boolean,
    )

    private companion object {
        const val OUTCOME_TARGET_HIT: String = "TARGET_HIT"
        const val OUTCOME_STOP_LOSS: String = "STOP_LOSS"
        const val OUTCOME_EXIT_AT_END: String = "EXIT_AT_END"
        const val OUTCOME_NO_PRICE_DATA: String = "NO_PRICE_DATA"
        const val OUTCOME_NO_NEXT_TRADING_DAY: String = "NO_NEXT_TRADING_DAY"
        const val OUTCOME_INVALID_ENTRY_PRICE: String = "INVALID_ENTRY_PRICE"
    }
}
