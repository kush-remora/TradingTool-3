package com.tradingtool.core.screener

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.inject.Inject
import com.google.inject.Singleton
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

data class WeeklyPatternConfig(
    val lookbackWeeks: Int = 8,
    val minTradingDaysPerWeek: Int = 3,
    val minWeeksRequired: Int = 6,
    val entryReboundPct: Double = 1.0,
    val swingTargetPct: Double = 5.0,
    val stopLossPct: Double = 3.0,
    val patternConfirmed: PatternConfirmedConfig = PatternConfirmedConfig(),
)

data class PatternConfirmedConfig(
    val minEntryRatePct: Double = 50.0,
    val minWinRatePct: Double = 50.0,
    val minAvgSwingPct: Double = 0.5,
)

@Singleton
class WeeklyPatternConfigService @Inject constructor() {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = ObjectMapper().registerKotlinModule()
    private val configFile = File("weekly_pattern_config.json")
    private val lock = ReentrantReadWriteLock()

    init {
        if (!configFile.exists()) {
            log.info("Creating weekly_pattern_config.json with default values")
            saveConfig(WeeklyPatternConfig())
        }
    }

    fun loadConfig(): WeeklyPatternConfig = lock.read {
        if (!configFile.exists()) return WeeklyPatternConfig()
        return try {
            mapper.readValue(configFile, WeeklyPatternConfig::class.java)
        } catch (e: Exception) {
            log.error("Failed to read weekly_pattern_config.json, using defaults: {}", e.message)
            WeeklyPatternConfig()
        }
    }

    private fun saveConfig(config: WeeklyPatternConfig) = lock.write {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(configFile, config)
        } catch (e: Exception) {
            log.error("Failed to create weekly_pattern_config.json: {}", e.message)
        }
    }
}
