package com.tradingtool.core.model.screener

import kotlinx.serialization.Serializable

@Serializable
data class UniverseOption(
    val label: String,
    val value: String,
    val count: Int
)

@Serializable
data class UniverseOptionsResponse(
    val options: List<UniverseOption>
)
