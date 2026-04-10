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
    private const val OVERBOUGHT_PERCENTILE = 90.0
    private const val OVERSOLD_PERCENTILE = 20.0

    /**
     * Calculates the adaptive status based on current RSI and historical bounds.
     * Overbought: RSI is near the top of the rolling range.
     * Oversold: RSI is in the bottom 20% of the rolling range.
     */
    fun getStatus(
        currentRsi: Double,
        lowestRsi: Double,
        highestRsi: Double,
        overboughtPercentile: Double = OVERBOUGHT_PERCENTILE,
    ): AdaptiveRsiStatus {
        val range = highestRsi - lowestRsi
        val percentile = if (range > 0) {
            (((currentRsi - lowestRsi) / range) * 100.0).coerceIn(0.0, 100.0)
        } else {
            50.0
        }

        return AdaptiveRsiStatus(
            isOverbought = percentile >= overboughtPercentile.coerceIn(0.0, 100.0),
            isOversold = percentile <= OVERSOLD_PERCENTILE,
            percentile = percentile.roundTo2(),
            currentRsi = currentRsi.roundTo2(),
            highestRsi = highestRsi.roundTo2(),
            lowestRsi = lowestRsi.roundTo2()
        )
    }
}
