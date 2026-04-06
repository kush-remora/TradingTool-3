package com.tradingtool.core.model.watchlist

import com.fasterxml.jackson.annotation.JsonProperty

data class WatchlistTagDefinition(
    val name: String,
    val color: String
)

data class WatchlistConfig(
    @JsonProperty("tag_definitions")
    val tagDefinitions: List<WatchlistTagDefinition> = emptyList()
)
