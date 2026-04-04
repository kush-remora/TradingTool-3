package com.tradingtool.core.technical

data class AdaptiveRsiStatus(
    val isOverbought: Boolean,
    val isOversold: Boolean,
    val percentile: Double,
    val currentRsi: Double,
    val highestRsi: Double,
    val lowestRsi: Double
)

object AdaptiveRsi {
    /**
     * Calculates the adaptive status based on current RSI and historical bounds.
     * Overbought: RSI >= highest_rsi * 0.95
     * Oversold: RSI <= lowest_rsi * 1.05
     */
    fun getStatus(currentRsi: Double, lowestRsi: Double, highestRsi: Double): AdaptiveRsiStatus {
        val range = highestRsi - lowestRsi
        val percentile = if (range > 0) ((currentRsi - lowestRsi) / range) * 100.0 else 50.0

        return AdaptiveRsiStatus(
            isOverbought = currentRsi >= (highestRsi * 0.95),
            isOversold = currentRsi <= (lowestRsi * 1.05),
            percentile = percentile.roundTo2(),
            currentRsi = currentRsi.roundTo2(),
            highestRsi = highestRsi.roundTo2(),
            lowestRsi = lowestRsi.roundTo2()
        )
    }
}
