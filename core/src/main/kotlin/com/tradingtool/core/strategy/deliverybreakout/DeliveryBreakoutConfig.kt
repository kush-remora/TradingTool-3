package com.tradingtool.core.strategy.deliverybreakout

data class DeliveryBreakoutConfig(
    val volumeMultiplier: Double = 1.0,
    val deliveryMultiplier: Double = 1.0,
    val minCurrentVolume: Long = 10_000L
)
