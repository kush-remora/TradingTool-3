package com.tradingtool.core.strategy.netwebcycle

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.inject.Singleton
import java.io.File

@Singleton
class NetwebCycleConfigService {
    private val mapper = ObjectMapper().registerKotlinModule()
    private val configFile = File(CONFIG_FILE)

    fun loadConfig(): NetwebCycleConfig {
        val config = try {
            mapper.readValue(configFile, NetwebCycleConfig::class.java)
        } catch (error: Exception) {
            throw IllegalArgumentException("Unable to read $CONFIG_FILE: ${error.message}")
        }

        require(config.baseLookbackTradingDays >= config.minimumBaseHistoryTradingDays) {
            "baseLookbackTradingDays must be at least minimumBaseHistoryTradingDays."
        }
        require(config.maximumBaseWidthPct > 0.0) { "maximumBaseWidthPct must be positive." }
        require(config.maximumBaseDriftPct >= 0.0) { "maximumBaseDriftPct must not be negative." }
        require(config.breakoutBufferPct >= 0.0) { "breakoutBufferPct must not be negative." }
        require(config.strongBreakoutMovePct > 0.0) { "strongBreakoutMovePct must be positive." }
        require(config.drawdownTriggerPct > 0.0) { "drawdownTriggerPct must be positive." }
        require(config.minimumNewBaseTradingDays >= 1) { "minimumNewBaseTradingDays must be at least one." }
        require(config.newBaseLookbackTradingDays >= config.minimumNewBaseTradingDays) {
            "newBaseLookbackTradingDays must be at least minimumNewBaseTradingDays."
        }
        require(config.minimumHistoryTradingDays >= config.baseLookbackTradingDays) {
            "minimumHistoryTradingDays must be at least baseLookbackTradingDays."
        }
        require(config.rotationMoveTargetPct > 0.0) { "rotationMoveTargetPct must be positive." }
        return config
    }

    private companion object {
        const val CONFIG_FILE = "netweb_cycle_config.json"
    }
}
