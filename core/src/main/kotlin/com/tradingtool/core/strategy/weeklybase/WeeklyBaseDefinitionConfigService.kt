package com.tradingtool.core.strategy.weeklybase

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.inject.Singleton
import java.io.File

@Singleton
class WeeklyBaseDefinitionConfigService {
    private val mapper = ObjectMapper().registerKotlinModule()
    private val configFile = File(CONFIG_FILE)

    fun loadConfig(): WeeklyBaseDefinitionConfig {
        val config = try {
            mapper.readValue(configFile, WeeklyBaseDefinitionConfig::class.java)
        } catch (error: Exception) {
            throw IllegalArgumentException("Unable to read $CONFIG_FILE: ${error.message}")
        }
        require(config.smaWindowTradingDays >= 1) { "smaWindowTradingDays must be at least 1." }
        require(config.minimumSmaDistancePct <= config.maximumSmaDistancePct) {
            "minimumSmaDistancePct must not exceed maximumSmaDistancePct."
        }
        require(config.maximumZoneWidthPct > 0) { "maximumZoneWidthPct must be greater than zero." }
        return config
    }

    private companion object {
        const val CONFIG_FILE = "weekly_base_definition_config.json"
    }
}
