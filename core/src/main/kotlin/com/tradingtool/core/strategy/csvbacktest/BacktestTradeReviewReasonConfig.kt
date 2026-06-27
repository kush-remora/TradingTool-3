package com.tradingtool.core.strategy.csvbacktest

data class ReviewReason(
    val id: String,
    val label: String,
    val description: String
)

data class ReviewReasonsResponse(
    val acceptanceReasons: List<ReviewReason>,
    val rejectionReasons: List<ReviewReason>
)

object BacktestTradeReviewReasonConfig {
    val reasons = ReviewReasonsResponse(
        acceptanceReasons = listOf(
//            ReviewReason("GOOD_VOL_CONTRACTION", "Good Vol Contraction", "Volume has dried up significantly indicating lack of supply."),
//            ReviewReason("STRONG_SECTOR", "Strong Sector Momentum", "The overall sector is in a strong uptrend."),
//            ReviewReason("CLEAN_TRENDLINE_BREAK", "Clean Trendline Break", "Price broke out of a well-defined trendline cleanly."),
//            ReviewReason("HIGH_RELATIVE_STRENGTH", "High Relative Strength", "Stock is outperforming the broader market significantly."),
//            ReviewReason("EARNINGS_CATALYST", "Earnings Catalyst", "Positive earnings surprise or guidance driving the momentum."),
//            ReviewReason("MA_SUPPORT", "Moving Average Support", "Bouncing perfectly off a key moving average (e.g. 50 SMA / 200 SMA)."),
            ReviewReason("WYCKOFF_ACCUMULATION", "Wyckoff Accumulation Phase C/D", "Clear spring or sign of strength in a defined trading range."),
//            ReviewReason("INSTITUTIONAL_BUYING", "Institutional Buying Imprint", "Massive volume spikes on up days, low volume on down days.")
        ),
        rejectionReasons = listOf(
//            ReviewReason("POOR_VOL_STRUCTURE", "Poor Volume Structure", "Volume does not support the price action; lacks institutional footprint."),
//            ReviewReason("RESISTANCE_OVERHEAD", "Resistance Overhead", "Too close to major overhead supply or resistance levels."),
            ReviewReason("TOO_MUCH_STRETCH", "Extended from breakout", "Price is too far extended from initial breakout, high risk of mean reversion."),

//            ReviewReason("EXTENDED_FROM_MA", "Extended from Moving Average", "Price is too far extended from the moving average, high risk of mean reversion."),
//            ReviewReason("EARNINGS_IMMINENT", "Earnings Imminent", "Earnings report is too close, increasing gap-down risk."),
            ReviewReason("WEAK_SECTOR", "Weak Sector", "Sector is in a downtrend or underperforming."),
//            ReviewReason("WICKS_SUPPLY", "Wicks indicating Supply", "Large upper wicks show sellers are aggressively rejecting higher prices."),
//            ReviewReason("WYCKOFF_DISTRIBUTION", "Wyckoff Distribution Phase B/C", "Looks more like an upthrust or distribution than accumulation."),
//            ReviewReason("LOW_LIQUIDITY", "Low Liquidity", "Stock does not trade enough daily volume to safely enter/exit size."),
            ReviewReason("CHOPPY_BASE", "Too choppy / Base flawed", "The base is too loose or volatile, lacking tight closes."),
            ReviewReason("RECENT_52_WEEK_HIGH", "Second time high is bad ", "The recent 52 week high not able to break it on 2nd time .")
        )
    )
}
