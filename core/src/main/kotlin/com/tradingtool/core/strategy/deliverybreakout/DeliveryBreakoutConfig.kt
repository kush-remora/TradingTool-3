package com.tradingtool.core.strategy.deliverybreakout

data class DeliveryBreakoutConfig(
    val shockMultiplier: Double = 2.0,
    val baselineSessions: Int = 10,
    val scanSessions: Int = 10,
)
