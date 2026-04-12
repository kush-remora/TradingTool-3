package com.tradingtool.core.delivery.config

import com.fasterxml.jackson.annotation.JsonProperty

enum class DeliveryDataSource {
    CM_BHAVDATA_FULL,
    MTO,
}

data class DeliveryUniverseConfig(
    @JsonProperty("preset_names")
    val presetNames: List<String> = DeliveryConfigService.DEFAULT_PRESET_NAMES,
    @JsonProperty("include_watchlist")
    val includeWatchlist: Boolean = true,
)

data class DeliveryConfig(
    val enabled: Boolean = true,
    val source: DeliveryDataSource = DeliveryDataSource.CM_BHAVDATA_FULL,
    val universe: DeliveryUniverseConfig = DeliveryUniverseConfig(),
)
