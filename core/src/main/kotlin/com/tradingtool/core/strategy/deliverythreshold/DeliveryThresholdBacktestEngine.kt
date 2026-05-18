package com.tradingtool.core.strategy.deliverythreshold

import com.tradingtool.core.delivery.model.StockDeliveryDaily
import com.tradingtool.core.technical.calculateRsi
import com.tradingtool.core.technical.getNullableDouble
import com.tradingtool.core.technical.roundTo2
import com.tradingtool.core.technical.toTa4jSeries
import java.time.LocalDate

class DeliveryThresholdBacktestEngine {

    fun run(
        config: DeliveryThresholdBacktestRunConfig,
        contexts: List<DeliveryThresholdSymbolContext>,
    ): DeliveryThresholdBacktestResponse {
        val rows = contexts
            .flatMap { context -> evaluateSymbol(config, context) }
            .sortedWith(compareBy<DeliveryThresholdBacktestRow> { it.entryDate }.thenBy { it.symbol })

        val hitRows = rows.filter { row -> row.status == STATUS_HIT }
        val hitHoldingDays = hitRows.map { row -> row.holdingDays }
        val summary = DeliveryThresholdBacktestSummary(
            totalBuys = rows.size,
            hitCount = hitRows.size,
            hitRatePct = if (rows.isEmpty()) 0.0 else ((hitRows.size.toDouble() / rows.size.toDouble()) * 100.0).roundTo2(),
            daysToHitAvg = hitHoldingDays.averageOrNull()?.roundTo2(),
            daysToHitMedian = hitHoldingDays.medianOrNull()?.roundTo2(),
            daysToHitMin = hitHoldingDays.minOrNull(),
            daysToHitMax = hitHoldingDays.maxOrNull(),
            openCount = rows.count { row -> row.status == STATUS_OPEN },
        )

        return DeliveryThresholdBacktestResponse(
            config = DeliveryThresholdBacktestConfigSnapshot(
                indexKeys = config.indexKeys,
                symbols = config.symbols,
                thresholds = config.thresholdsByIndex,
                profitPct = config.profitPct.roundTo2(),
                fromDate = config.fromDate.toString(),
                toDate = config.toDate.toString(),
            ),
            summary = summary,
            rows = rows,
        )
    }

    private fun evaluateSymbol(
        config: DeliveryThresholdBacktestRunConfig,
        context: DeliveryThresholdSymbolContext,
    ): List<DeliveryThresholdBacktestRow> {
        val candles = context.candles.sortedBy { candle -> candle.candleDate }
        if (candles.size < MIN_REQUIRED_CANDLES) {
            return emptyList()
        }

        val rsiByDate = computeRsiByDate(candles)
        val deliveries = context.deliveries
            .asSequence()
            .filter { row -> row.tradingDate >= config.fromDate && row.tradingDate <= config.toDate }
            .sortedBy { row -> row.tradingDate }
            .toList()

        if (deliveries.isEmpty()) {
            return emptyList()
        }

        val latestCandle = candles.lastOrNull() ?: return emptyList()
        val rows = mutableListOf<DeliveryThresholdBacktestRow>()

        deliveries.forEach { delivery ->
            val deliveryPct = delivery.delivPer ?: return@forEach
            if (deliveryPct < context.threshold) {
                return@forEach
            }

            val entryIndex = candles.indexOfFirst { candle -> candle.candleDate > delivery.tradingDate }
            if (entryIndex < 0) {
                return@forEach
            }

            val entryCandle = candles[entryIndex]
            val entryPrice = entryCandle.open
            if (!entryPrice.isFinite() || entryPrice <= 0.0) {
                return@forEach
            }
            val triggerCandleIndex = candles.indexOfFirst { candle -> candle.candleDate == delivery.tradingDate }
            val avg20dVolumeAtSignal = computeAvg20dVolumeBeforeIndex(candles, triggerCandleIndex)
            val triggerVolume = delivery.ttlTrdQnty?.toDouble()
                ?: triggerCandleIndex.takeIf { index -> index >= 0 }?.let { index -> candles[index].volume.toDouble() }
            val signalVolumeVs20dPct = if (
                triggerVolume != null &&
                avg20dVolumeAtSignal != null &&
                avg20dVolumeAtSignal > 0.0
            ) {
                (triggerVolume / avg20dVolumeAtSignal) * 100.0
            } else {
                null
            }

            val targetPrice = entryPrice * (1.0 + (config.profitPct / 100.0))
            val hitIndex = findTargetHitIndex(candles, entryIndex, targetPrice)
            val status = if (hitIndex == null) STATUS_OPEN else STATUS_HIT
            val currentPrice = latestCandle.close
            val entryRange = computeEntryRange(candles, entryIndex)
            val maxDrawdownAtBuyPct = computeEntryDrawdownPct(entryRange.high, entryPrice)
            val pctFrom52WeekHighAtBuy = computePctFromReference(entryPrice, entryRange.high)
            val pctFrom52WeekLowAtBuy = computePctFromReference(entryPrice, entryRange.low)
            val holdingDays = if (hitIndex == null) {
                (candles.size - 1 - entryIndex).coerceAtLeast(0)
            } else {
                (hitIndex - entryIndex).coerceAtLeast(0)
            }

            val exitDate = hitIndex?.let { index -> candles[index].candleDate.toString() }
            val exitPrice = hitIndex?.let { _ -> targetPrice.roundTo2() }
            val floatingPnlPct = if (hitIndex == null) {
                (((currentPrice - entryPrice) / entryPrice) * 100.0).roundTo2()
            } else {
                null
            }

            rows.add(
                DeliveryThresholdBacktestRow(
                    symbol = context.symbol,
                    index = context.resolvedIndexKey,
                    entryDate = entryCandle.candleDate.toString(),
                    entryPrice = entryPrice.roundTo2(),
                    entryDeliveryPct = deliveryPct.roundTo2(),
                    totalVolumeCount = delivery.ttlTrdQnty,
                    avg20dVolumeAtSignal = avg20dVolumeAtSignal?.roundTo2(),
                    signalVolumeVs20dPct = signalVolumeVs20dPct?.roundTo2(),
                    targetPrice = targetPrice.roundTo2(),
                    fiftyTwoWeekHighAtBuy = entryRange.high.takeIf { value -> value > 0.0 }?.roundTo2(),
                    fiftyTwoWeekLowAtBuy = entryRange.low.takeIf { value -> value > 0.0 }?.roundTo2(),
                    pctFrom52WeekHighAtBuy = pctFrom52WeekHighAtBuy?.roundTo2(),
                    pctFrom52WeekLowAtBuy = pctFrom52WeekLowAtBuy?.roundTo2(),
                    buyDayOfWeek = entryCandle.candleDate.dayOfWeek.name.lowercase()
                        .replaceFirstChar { ch -> ch.uppercase() }
                        .take(3),
                    exitDate = exitDate,
                    exitPrice = exitPrice,
                    holdingDays = holdingDays,
                    rsiBuy = rsiByDate[entryCandle.candleDate]?.roundTo2(),
                    rsiSell = hitIndex?.let { index -> rsiByDate[candles[index].candleDate]?.roundTo2() },
                    maxDrawdownAtBuyPct = maxDrawdownAtBuyPct?.roundTo2(),
                    status = status,
                    currentPrice = currentPrice.roundTo2(),
                    floatingPnlPct = floatingPnlPct,
                    thresholdUsed = context.threshold.roundTo2(),
                ),
            )
        }

        return rows
    }

    private fun findTargetHitIndex(candles: List<com.tradingtool.core.candle.DailyCandle>, entryIndex: Int, targetPrice: Double): Int? {
        for (index in entryIndex until candles.size) {
            if (candles[index].high >= targetPrice) {
                return index
            }
        }
        return null
    }

    private fun computeRsiByDate(candles: List<com.tradingtool.core.candle.DailyCandle>): Map<LocalDate, Double> {
        if (candles.isEmpty()) {
            return emptyMap()
        }

        val series = candles.toTa4jSeries("delivery-threshold-rsi")
        val rsiIndicator = series.calculateRsi(14)
        return candles.mapIndexed { index, candle ->
            candle.candleDate to (rsiIndicator.getNullableDouble(index) ?: 50.0)
        }.toMap()
    }

    private fun computeEntryDrawdownPct(peakHigh: Double, entryPrice: Double): Double? {
        if (entryPrice <= 0.0 || peakHigh <= 0.0 || !peakHigh.isFinite()) {
            return null
        }
        return ((entryPrice / peakHigh) - 1.0) * 100.0
    }

    private fun computePctFromReference(entryPrice: Double, referencePrice: Double): Double? {
        if (entryPrice <= 0.0 || referencePrice <= 0.0 || !referencePrice.isFinite()) {
            return null
        }
        return ((entryPrice / referencePrice) - 1.0) * 100.0
    }

    private fun computeEntryRange(
        candles: List<com.tradingtool.core.candle.DailyCandle>,
        entryIndex: Int,
    ): EntryRange {
        if (entryIndex < 0 || entryIndex >= candles.size) {
            val fallback = candles.lastOrNull()
            val fallbackHigh = fallback?.high ?: 0.0
            val fallbackLow = fallback?.low ?: 0.0
            return EntryRange(high = fallbackHigh, low = fallbackLow)
        }
        val lookbackStart = (entryIndex - DRAWDOWN_LOOKBACK_DAYS + 1).coerceAtLeast(0)
        val window = candles.subList(lookbackStart, entryIndex + 1)
        val high = window.maxOfOrNull { candle -> candle.high } ?: 0.0
        val low = window.minOfOrNull { candle -> candle.low } ?: 0.0
        return EntryRange(high = high, low = low)
    }

    private fun computeAvg20dVolumeBeforeIndex(
        candles: List<com.tradingtool.core.candle.DailyCandle>,
        triggerIndex: Int,
    ): Double? {
        if (triggerIndex <= 0 || triggerIndex > candles.lastIndex) {
            return null
        }
        val start = (triggerIndex - AVG_VOLUME_LOOKBACK_DAYS).coerceAtLeast(0)
        val history = candles.subList(start, triggerIndex)
        if (history.isEmpty()) {
            return null
        }
        return history.map { candle -> candle.volume.toDouble() }.average()
    }

    private data class EntryRange(
        val high: Double,
        val low: Double,
    )

    private fun List<Int>.medianOrNull(): Double? {
        if (isEmpty()) return null
        val sorted = sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle].toDouble()
        } else {
            (sorted[middle - 1].toDouble() + sorted[middle].toDouble()) / 2.0
        }
    }

    private fun Iterable<Int>.averageOrNull(): Double? {
        val values = toList()
        if (values.isEmpty()) return null
        return values.average()
    }

    companion object {
        private const val STATUS_HIT = "HIT"
        private const val STATUS_OPEN = "OPEN"
        private const val MIN_REQUIRED_CANDLES = 20
        private const val DRAWDOWN_LOOKBACK_DAYS = 252
        private const val AVG_VOLUME_LOOKBACK_DAYS = 20
    }
}
