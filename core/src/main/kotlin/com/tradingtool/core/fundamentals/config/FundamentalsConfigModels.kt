package com.tradingtool.core.fundamentals.config

import com.fasterxml.jackson.annotation.JsonProperty

enum class FundamentalsDataSource {
    SCREENER,
}

data class FundamentalsUniverseConfig(
    @JsonProperty("preset_names")
    val presetNames: List<String> = FundamentalsConfigService.DEFAULT_PRESET_NAMES,
    @JsonProperty("include_watchlist")
    val includeWatchlist: Boolean = true,
)

data class FundamentalsConfig(
    val enabled: Boolean = true,
    val source: FundamentalsDataSource = FundamentalsDataSource.SCREENER,
    @JsonProperty("request_delay_ms")
    val requestDelayMs: Long = 1200,
    val universe: FundamentalsUniverseConfig = FundamentalsUniverseConfig(),
)
