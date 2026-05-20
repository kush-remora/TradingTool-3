package com.tradingtool.core.strategy.fiftytwohigh

data class FiftyTwoWeekHighLiveRequest(
    val universeKeys: List<String> = emptyList(),
    val symbols: List<String> = emptyList(),
)

data class FiftyTwoWeekHighLiveTelegramRequest(
    val symbol: String,
    val bucket: String,
    val breakoutLevel: Double,
    val latestHigh: Double,
    val latestClose: Double,
    val gapToBreakoutPct: Double,
    val latestDate: String,
    val lastHitDate: String?,
)

data class FiftyTwoWeekHighLiveUniverseOption(
    val value: String,
    val count: Int,
)

data class FiftyTwoWeekHighLiveRow(
    val symbol: String,
    val indexBucket: String,
    val latestDate: String,
    val breakoutLevel: Double,
    val latestHigh: Double,
    val latestClose: Double,
    val gapToBreakoutPct: Double,
    val lastHitDate: String?,
    val cooldownActive: Boolean,
)

data class FiftyTwoWeekHighLiveSummary(
    val nearBreakout: Int,
    val hitInLast2Weeks: Int,
    val hitToday: Int,
)

data class FiftyTwoWeekHighLiveConfigSnapshot(
    val nearThresholdPct: Double,
    val breakoutLookbackDays: Int,
    val hitLookbackTradingDays: Int,
    val hitTodayTradingDays: Int,
    val cooldownTradingDays: Int,
)

data class FiftyTwoWeekHighLiveResponse(
    val config: FiftyTwoWeekHighLiveConfigSnapshot,
    val summary: FiftyTwoWeekHighLiveSummary,
    val nearBreakout: List<FiftyTwoWeekHighLiveRow>,
    val hitInLast2Weeks: List<FiftyTwoWeekHighLiveRow>,
    val hitToday: List<FiftyTwoWeekHighLiveRow>,
)

data class FiftyTwoWeekHighLiveRunConfig(
    val universeKeys: List<String>,
    val symbols: List<String>,
)
