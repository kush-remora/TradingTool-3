package com.tradingtool.core.strategy.phasedbreakout

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.round

object PhaseCDeliveryValidationAnalyzer {
    private const val PASSED = "PASSED"
    private const val WATCH = "WATCH"
    private const val NOT_PASSED = "NOT_PASSED"
    private const val DATA_MISSING = "DATA_MISSING"

    fun evaluate(
        evaluationDate: LocalDate,
        deliveries: List<StockDeliveryDaily>,
        candles: List<DailyCandle>,
        config: Phase2DeliveryConfig = Phase2DeliveryConfig(),
    ): Phase2DeliveryMetrics {
        val recentDeliveries = deliveries
            .filter { row -> row.tradingDate <= evaluationDate }
            .sortedBy { row -> row.tradingDate }
        if (recentDeliveries.size < 20 || candles.size < config.baselineLookbackDays) {
            return missingMetrics(evaluationDate, "insufficient_delivery_history")
        }

        val baselineWindowCandles = candles
            .filter { candle -> candle.candleDate <= evaluationDate }
            .takeLast(config.baselineLookbackDays)
        if (baselineWindowCandles.size < config.baselineLookbackDays) {
            return missingMetrics(evaluationDate, "insufficient_delivery_history")
        }

        val basementThreshold = resolveBasementThreshold(
            candles = baselineWindowCandles,
            basementPercentile = config.basementPercentile,
        )
        val candleByDate = baselineWindowCandles.associateBy { candle -> candle.candleDate }
        val basementDeliveries = recentDeliveries.filter { row ->
            val close = candleByDate[row.tradingDate]?.close ?: return@filter false
            val deliveryQuantity = row.delivQty ?: return@filter false
            close <= basementThreshold && deliveryQuantity > 0
        }
        val wholesaleBaseDq = basementDeliveries
            .mapNotNull { row -> row.delivQty }
            .takeIf { values -> values.isNotEmpty() }
            ?.average()
            ?.takeIf { value -> value > 0.0 }
        if (wholesaleBaseDq == null) {
            return missingMetrics(evaluationDate, "zero_baseline")
        }

        val latestRow = recentDeliveries.last()
        val recentDeliveries10d = recentDeliveries.takeLast(10)
        val recentDeliveries20d = recentDeliveries.takeLast(20)

        val deliverySpikeDays10d = recentDeliveries10d.count { row -> isSpikeDay(row, wholesaleBaseDq, config) }
        val deliverySpikeDays20d = recentDeliveries20d.count { row -> isSpikeDay(row, wholesaleBaseDq, config) }
        val deliverySupportDays10d = recentDeliveries10d.count { row -> isSupportDay(row, config) }
        val deliverySupportDays20d = recentDeliveries20d.count { row -> isSupportDay(row, config) }

        val todaySpikeRatio = latestRow.delivQty?.let { quantity -> quantity / wholesaleBaseDq }
        
        val passCondition10d = deliverySpikeDays10d >= config.passCount10d && deliverySupportDays10d >= config.passCount10d
        val passCondition20d = deliverySpikeDays20d >= config.passCount20d && deliverySupportDays20d >= config.passCount20d
        val watchCondition10d = deliverySpikeDays10d >= config.watchCount10d && deliverySupportDays10d >= config.watchCount10d
        val watchCondition20d = deliverySpikeDays20d >= config.watchCount20d && deliverySupportDays20d >= config.watchCount20d

        val status = when {
            passCondition10d || passCondition20d -> PASSED
            watchCondition10d || watchCondition20d -> WATCH
            else -> NOT_PASSED
        }
        val reason = when (status) {
            PASSED -> "strong_delivery_support"
            WATCH -> "emerging_delivery_support"
            else -> "no_delivery_confirmation"
        }

        return Phase2DeliveryMetrics(
            status = status,
            reason = reason,
            evaluatedOn = latestRow.tradingDate,
            deliveryQuantityToday = latestRow.delivQty,
            deliveryPctToday = latestRow.delivPer,
            wholesaleBaseDq = wholesaleBaseDq.toLong(),
            deliverySpikeRatio = todaySpikeRatio?.roundTo2(),
            deliverySpikeDays10d = deliverySpikeDays10d,
            deliverySpikeDays20d = deliverySpikeDays20d,
            deliverySupportDays10d = deliverySupportDays10d,
            deliverySupportDays20d = deliverySupportDays20d,
        )
    }

    private fun resolveBasementThreshold(
        candles: List<DailyCandle>,
        basementPercentile: Double,
    ): Double {
        val sortedCloses = candles.map { candle -> candle.close }.sorted()
        val percentileSize = ceil(sortedCloses.size * basementPercentile)
            .toInt()
            .coerceAtLeast(1)
        return sortedCloses[percentileSize - 1]
    }

    private fun isSpikeDay(
        row: StockDeliveryDaily,
        wholesaleBaseDq: Double,
        config: Phase2DeliveryConfig,
    ): Boolean {
        val deliveryQuantity = row.delivQty ?: return false
        return deliveryQuantity / wholesaleBaseDq >= config.deliverySpikeThreshold
    }

    private fun isSupportDay(
        row: StockDeliveryDaily,
        config: Phase2DeliveryConfig,
    ): Boolean {
        val deliveryPct = row.delivPer ?: return false
        return deliveryPct >= config.deliveryPctSupportThreshold
    }

    private fun missingMetrics(
        evaluatedOn: LocalDate,
        reason: String,
    ): Phase2DeliveryMetrics {
        return Phase2DeliveryMetrics(
            status = DATA_MISSING,
            reason = reason,
            evaluatedOn = evaluatedOn,
            deliveryQuantityToday = null,
            deliveryPctToday = null,
            wholesaleBaseDq = null,
            deliverySpikeRatio = null,
            deliverySpikeDays10d = null,
            deliverySpikeDays20d = null,
            deliverySupportDays10d = null,
            deliverySupportDays20d = null,
        )
    }

    private fun Double.roundTo2(): Double = round(this * 100.0) / 100.0
}
