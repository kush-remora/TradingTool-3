package com.tradingtool.core.strategy.silentbreakout

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal data class SilentBreakoutSignal(
    val symbol: String,
    val signalDate: LocalDate,
)

internal object SilentBreakoutBacktestAnalyzer {
    private const val FIFTY_TWO_WEEK_SESSIONS = 252
    private const val ROC_SESSIONS = 20
    private const val SMA_SESSIONS = 200
    private const val FORWARD_SESSIONS = 40
    private const val DIP_OBSERVATION_SESSIONS = 5
    private const val TWENTY_SESSION_RETURN_INDEX = 19
    private const val FORTY_SESSION_RETURN_INDEX = 39

    fun analyze(
        signal: SilentBreakoutSignal,
        candles: List<DailyCandle>,
        targetPct: Double,
        deliveryPctByDate: Map<LocalDate, Double>,
    ): SilentBreakoutBacktestRow {
        val sortedCandles = candles.sortedBy(DailyCandle::candleDate)
        val signalIndex = sortedCandles.indexOfFirst { candle -> candle.candleDate == signal.signalDate }
        if (signalIndex < 0) {
            return unavailableRow(signal, SilentBreakoutDataStatus.MISSING_SIGNAL_CANDLE)
        }

        val signalCandle = sortedCandles[signalIndex]
        val futureCandles = sortedCandles.drop(signalIndex + 1).take(FORWARD_SESSIONS)
        val historicalCandles = sortedCandles.subList(
            (signalIndex - FIFTY_TWO_WEEK_SESSIONS + 1).coerceAtLeast(0),
            signalIndex + 1,
        )
        val fiftyTwoWeekHigh = historicalCandles.maxOf(DailyCandle::high)
        val closeTwentySessionsAgo = sortedCandles.getOrNull(signalIndex - ROC_SESSIONS)?.close
        val sma200 = historicalCandles
            .takeLast(SMA_SESSIONS)
            .map(DailyCandle::close)
            .average()
        val distanceFromHigh = percentageDifference(signalCandle.close, fiftyTwoWeekHigh)
        val roc20 = closeTwentySessionsAgo?.let { close -> percentageDifference(signalCandle.close, close) }
        val distanceFromSma200 = percentageDifference(signalCandle.close, sma200)
        val entryCandle = futureCandles.firstOrNull()
        val targetPrice = entryCandle?.open?.let { entryPrice -> entryPrice * (1.0 + targetPct / 100.0) }
        val targetHitIndex = targetPrice?.let { price -> futureCandles.indexOfFirst { candle -> candle.high >= price } }
        val nextFiveSessions = futureCandles.take(DIP_OBSERVATION_SESSIONS)
        val nextFiveSessionsLowIndex = nextFiveSessions.indices.minByOrNull { index -> nextFiveSessions[index].low }
        val nextFiveSessionsLow = nextFiveSessionsLowIndex?.let { index -> nextFiveSessions[index].low }
        val priorFiveSessionsMaxDeliveryPct = sortedCandles
            .take(signalIndex)
            .takeLast(5)
            .mapNotNull { candle -> deliveryPctByDate[candle.candleDate] }
            .maxOrNull()

        return SilentBreakoutBacktestRow(
            symbol = signal.symbol,
            instrumentToken = signalCandle.instrumentToken,
            signalDate = signal.signalDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            dataStatus = if (historicalCandles.size < FIFTY_TWO_WEEK_SESSIONS) SilentBreakoutDataStatus.PARTIAL_HISTORY else SilentBreakoutDataStatus.AVAILABLE,
            signalClose = signalCandle.close,
            distanceFromFiftyTwoWeekHighPct = distanceFromHigh,
            roc20Pct = roc20,
            distanceFromSma200Pct = distanceFromSma200,
            lateStageRisk = roc20?.let { move -> move >= 20.0 } ?: false,
            priorFiveSessionsMaxDeliveryPct = priorFiveSessionsMaxDeliveryPct,
            entryDate = entryCandle?.candleDate?.format(DateTimeFormatter.ISO_LOCAL_DATE),
            entryPrice = entryCandle?.open,
            targetPrice = targetPrice,
            targetAchieved = targetHitIndex?.let { index -> index >= 0 },
            targetAchievedDays = targetHitIndex?.takeIf { index -> index >= 0 }?.plus(1),
            nextFiveSessionsLow = nextFiveSessionsLow,
            nextFiveSessionsLowMovePct = nextFiveSessionsLow?.let { low -> percentageDifference(low, signalCandle.close) },
            nextFiveSessionsLowDays = nextFiveSessionsLowIndex?.plus(1),
            forward20SessionReturnPct = forward20SessionReturn(signalCandle, futureCandles),
            forward40SessionReturnPct = forward40SessionReturn(signalCandle, futureCandles),
            maxGain40SessionsPct = maxGain(signalCandle, futureCandles),
            maxDrawdown40SessionsPct = maxDrawdown(signalCandle, futureCandles),
        )
    }

    private fun forward20SessionReturn(signalCandle: DailyCandle, futureCandles: List<DailyCandle>): Double? =
        futureCandles.getOrNull(TWENTY_SESSION_RETURN_INDEX)?.let { candle -> percentageDifference(candle.close, signalCandle.close) }

    private fun forward40SessionReturn(signalCandle: DailyCandle, futureCandles: List<DailyCandle>): Double? =
        futureCandles.getOrNull(FORTY_SESSION_RETURN_INDEX)?.let { candle -> percentageDifference(candle.close, signalCandle.close) }

    private fun maxGain(signalCandle: DailyCandle, futureCandles: List<DailyCandle>): Double? =
        futureCandles.maxOfOrNull(DailyCandle::high)?.let { high -> percentageDifference(high, signalCandle.close) }

    private fun maxDrawdown(signalCandle: DailyCandle, futureCandles: List<DailyCandle>): Double? =
        futureCandles.minOfOrNull(DailyCandle::low)?.let { low -> percentageDifference(low, signalCandle.close) }

    private fun unavailableRow(
        signal: SilentBreakoutSignal,
        status: SilentBreakoutDataStatus,
        signalCandle: DailyCandle? = null,
    ): SilentBreakoutBacktestRow = SilentBreakoutBacktestRow(
        symbol = signal.symbol,
        instrumentToken = signalCandle?.instrumentToken,
        signalDate = signal.signalDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
        dataStatus = status,
        signalClose = signalCandle?.close,
        distanceFromFiftyTwoWeekHighPct = null,
        roc20Pct = null,
        distanceFromSma200Pct = null,
        lateStageRisk = null,
        priorFiveSessionsMaxDeliveryPct = null,
        entryDate = null,
        entryPrice = null,
        targetPrice = null,
        targetAchieved = null,
        targetAchievedDays = null,
        nextFiveSessionsLow = null,
        nextFiveSessionsLowMovePct = null,
        nextFiveSessionsLowDays = null,
        forward20SessionReturnPct = null,
        forward40SessionReturnPct = null,
        maxGain40SessionsPct = null,
        maxDrawdown40SessionsPct = null,
    )

    private fun percentageDifference(value: Double, base: Double): Double = ((value - base) / base) * 100.0
}
