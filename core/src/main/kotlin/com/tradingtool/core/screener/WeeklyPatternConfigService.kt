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
    val lookbackWeeks: Int = 15,
    val buyZoneLookbackWeeks: Int = 15,
    val recentSessionsLookbackDays: Int = 10,
    val minTradingDaysPerWeek: Int = 3,
    val minWeeksRequired: Int = 6,
    val entryReboundPct: Double = 1.0,
    val swingTargetPct: Double = 5.0,
    val stopLossPct: Double = 3.0,
    val rsiEntry: RsiEntryConfig = RsiEntryConfig(),
    val patternConfirmed: PatternConfirmedConfig = PatternConfirmedConfig(),
    val targetRecommendation: TargetRecommendationConfig = TargetRecommendationConfig(),
)

data class RsiEntryConfig(
    val lookbackDays: Int = 50,
    val overboughtPercentile: Double = 90.0,
)

data class PatternConfirmedConfig(
    val minEntryRatePct: Double = 50.0,
    val minWinRatePct: Double = 50.0,
    val minAvgSwingPct: Double = 0.5,
)

data class TargetRecommendationConfig(
    val candidateTargetsPct: List<Double> = listOf(5.0, 6.0, 7.0),
    val minSamples: Int = 4,
    val minWinRatePct: Double = 50.0,
    val maxStopLossRatePct: Double = 45.0,
    val fallbackTargetPct: Double = 5.0,
)

@Singleton
class WeeklyPatternConfigService @Inject constructor() {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = ObjectMapper().registerKotlinModule()
    private val configFile = File("weekly_pattern_config.json")
    private val lock = ReentrantReadWriteLock()
    private val minLookbackWeeks = 7
    private val maxLookbackWeeks = 20

    init {
        if (!configFile.exists()) {
            log.info("Creating weekly_pattern_config.json with default values")
            saveConfig(WeeklyPatternConfig())
        }
    }

    fun loadConfig(): WeeklyPatternConfig = lock.read {
        val loaded = if (!configFile.exists()) {
            WeeklyPatternConfig()
        } else {
            try {
                mapper.readValue(configFile, WeeklyPatternConfig::class.java)
            } catch (e: Exception) {
                log.error("Failed to read weekly_pattern_config.json, using defaults: {}", e.message)
                WeeklyPatternConfig()
            }
        }
        sanitizeConfig(loaded)
    }

    private fun sanitizeConfig(config: WeeklyPatternConfig): WeeklyPatternConfig {
        val effectiveLookback = config.lookbackWeeks.coerceIn(minLookbackWeeks, maxLookbackWeeks)
        val effectiveBuyZoneLookback = config.buyZoneLookbackWeeks.coerceIn(minLookbackWeeks, maxLookbackWeeks)
        val effectiveMinWeeks = config.minWeeksRequired.coerceAtMost(effectiveLookback).coerceAtLeast(1)

        return config.copy(
            lookbackWeeks = effectiveLookback,
            buyZoneLookbackWeeks = effectiveBuyZoneLookback,
            minWeeksRequired = effectiveMinWeeks,
        )
    }

    private fun saveConfig(config: WeeklyPatternConfig) = lock.write {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(configFile, config)
        } catch (e: Exception) {
            log.error("Failed to create weekly_pattern_config.json: {}", e.message)
        }
    }
}
