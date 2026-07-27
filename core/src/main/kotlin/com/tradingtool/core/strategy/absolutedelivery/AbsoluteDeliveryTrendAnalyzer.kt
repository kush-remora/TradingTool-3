package com.tradingtool.core.strategy.absolutedelivery

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.technical.calculateSma
import com.tradingtool.core.technical.getNullableDouble
import com.tradingtool.core.technical.toTa4jSeries
import org.ta4j.core.indicators.SMAIndicator
import java.time.LocalDate

internal data class AbsoluteDeliveryTrendContext(
    val candles: List<DailyCandle>,
    val indexByDate: Map<LocalDate, Int>,
    val sma50: SMAIndicator,
    val sma200: SMAIndicator,
)

internal data class AbsoluteDeliveryTrendEvaluation(
    val closePrice: Double? = null,
    val sma50: Double? = null,
    val sma200: Double? = null,
    val sma50TwentySessionsAgo: Double? = null,
    val priceAboveSma50Passed: Boolean = false,
    val sma50AboveSma200Passed: Boolean = false,
    val sma50RisingPassed: Boolean = false,
    val matched: Boolean = false,
    val dataStatus: AbsoluteDeliveryTrendDataStatus,
)

internal fun buildAbsoluteDeliveryTrendContexts(
    candles: List<DailyCandle>,
    criteria: AbsoluteDeliveryCriteria,
): Map<Long, AbsoluteDeliveryTrendContext> =
    candles
        .groupBy { candle -> candle.instrumentToken }
        .mapValues { (instrumentToken, tokenCandles) ->
            val sortedCandles = tokenCandles
                .distinctBy { candle -> candle.candleDate }
                .sortedBy { candle -> candle.candleDate }
            val series = sortedCandles.toTa4jSeries("absolute-delivery-$instrumentToken")
            AbsoluteDeliveryTrendContext(
                candles = sortedCandles,
                indexByDate = sortedCandles
                    .mapIndexed { index, candle -> candle.candleDate to index }
                    .toMap(),
                sma50 = series.calculateSma(criteria.shortSmaPeriod),
                sma200 = series.calculateSma(criteria.longSmaPeriod),
            )
        }

internal fun evaluateAbsoluteDeliveryTrend(
    context: AbsoluteDeliveryTrendContext?,
    tradingDate: LocalDate,
    criteria: AbsoluteDeliveryCriteria,
): AbsoluteDeliveryTrendEvaluation {
    val index = context?.indexByDate?.get(tradingDate)
        ?: return AbsoluteDeliveryTrendEvaluation(dataStatus = AbsoluteDeliveryTrendDataStatus.NO_CANDLE)
    val previousSmaIndex = index - criteria.shortSmaSlopeLookbackSessions
    val hasRequiredHistory =
        index >= criteria.longSmaPeriod - 1 &&
            previousSmaIndex >= criteria.shortSmaPeriod - 1
    if (!hasRequiredHistory) {
        return AbsoluteDeliveryTrendEvaluation(
            closePrice = context.candles[index].close,
            dataStatus = AbsoluteDeliveryTrendDataStatus.INSUFFICIENT_HISTORY,
        )
    }

    val closePrice = context.candles[index].close
    val sma50 = context.sma50.getNullableDouble(index)
    val sma200 = context.sma200.getNullableDouble(index)
    val previousSma50 = context.sma50.getNullableDouble(previousSmaIndex)
    if (!closePrice.isFinite() || sma50 == null || sma200 == null || previousSma50 == null) {
        return AbsoluteDeliveryTrendEvaluation(
            closePrice = closePrice.takeIf(Double::isFinite),
            dataStatus = AbsoluteDeliveryTrendDataStatus.INSUFFICIENT_HISTORY,
        )
    }

    val priceAboveSma50Passed = closePrice > sma50
    val sma50AboveSma200Passed = sma50 > sma200
    val sma50RisingPassed = sma50 > previousSma50
    return AbsoluteDeliveryTrendEvaluation(
        closePrice = closePrice,
        sma50 = sma50,
        sma200 = sma200,
        sma50TwentySessionsAgo = previousSma50,
        priceAboveSma50Passed = priceAboveSma50Passed,
        sma50AboveSma200Passed = sma50AboveSma200Passed,
        sma50RisingPassed = sma50RisingPassed,
        matched = priceAboveSma50Passed && sma50AboveSma200Passed && sma50RisingPassed,
        dataStatus = AbsoluteDeliveryTrendDataStatus.AVAILABLE,
    )
}
