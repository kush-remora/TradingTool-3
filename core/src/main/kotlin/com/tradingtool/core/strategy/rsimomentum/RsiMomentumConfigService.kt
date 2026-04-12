package com.tradingtool.core.strategy.rsimomentum

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.inject.Inject
import com.google.inject.Singleton
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read

@Singleton
class RsiMomentumConfigService @Inject constructor() {
    private val log = LoggerFactory.getLogger(RsiMomentumConfigService::class.java)
    private val mapper = ObjectMapper().registerKotlinModule()
    private val configFile = File(CONFIG_FILE_NAME)
    private val lock = ReentrantReadWriteLock()

    init {
        if (!configFile.exists()) {
            saveConfig(RsiMomentumConfig())
        }
    }

    fun loadConfig(): RsiMomentumConfig = lock.read {
        if (!configFile.exists()) {
            return RsiMomentumConfig()
        }

        return try {
            parseConfigNode(mapper.readTree(configFile)).normalize()
        } catch (error: Exception) {
            log.error("Failed to read $CONFIG_FILE_NAME: ${error.message}")
            RsiMomentumConfig()
        }
    }

    fun loadBaseUniverseSymbols(presetName: String): List<String> {
        val resourceName = PRESET_RESOURCES[presetName]
        if (resourceName == null) {
            log.warn("Unknown RSI momentum preset: {}", presetName)
            return emptyList()
        }

        val stream: InputStream = javaClass.classLoader.getResourceAsStream(resourceName)
            ?: run {
                log.warn("Universe resource not found: {}", resourceName)
                return emptyList()
            }

        return stream.bufferedReader().useLines { lines ->
            lines.map { line -> line.trim() }
                .filter { line -> line.isNotEmpty() && !line.startsWith("#") && line != "symbol" }
                .map { line -> line.uppercase() }
                .distinct()
                .toList()
        }
    }

    fun writeConfig(config: RsiMomentumConfig) {
        saveConfig(config)
    }

    private fun saveConfig(config: RsiMomentumConfig) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(configFile, config.normalize())
        } catch (error: Exception) {
            log.error("Failed to save $CONFIG_FILE_NAME: ${error.message}")
        }
    }

    private fun parseConfigNode(node: JsonNode): RsiMomentumConfig {
        if (node.has("profiles")) {
            return mapper.treeToValue(node, RsiMomentumConfig::class.java)
        }

        val legacy = mapper.treeToValue(node, LegacyRsiMomentumConfig::class.java)
        return RsiMomentumConfig(
            enabled = legacy.enabled,
            profiles = listOf(
                RsiMomentumProfileConfig(
                    id = "default",
                    label = "Default",
                    baseUniversePreset = legacy.baseUniversePreset,
                    candidateCount = legacy.candidateCount,
                    boardDisplayCount = legacy.boardDisplayCount,
                    replacementPoolCount = legacy.replacementPoolCount,
                    holdingCount = legacy.holdingCount,
                    rsiPeriods = legacy.rsiPeriods,
                    minAverageTradedValue = legacy.minAverageTradedValue,
                    maxExtensionAboveSma20ForNewEntry = legacy.maxExtensionAboveSma20ForNewEntry,
                    maxExtensionAboveSma20ForNewEntryPct = legacy.maxExtensionAboveSma20ForNewEntryPct,
                    maxExtensionAboveSma20ForSkipNewEntry = legacy.maxExtensionAboveSma20ForSkipNewEntry,
                    maxExtensionAboveSma20ForSkipNewEntryPct = legacy.maxExtensionAboveSma20ForSkipNewEntryPct,
                    rebalanceDay = legacy.rebalanceDay,
                    rebalanceTime = legacy.rebalanceTime,
                    rsiCalibrationRunAt = null,
                    rsiCalibrationMethod = null,
                    rsiCalibrationSampleRange = null,
                ),
            ),
        )
    }

    private fun RsiMomentumConfig.normalize(): RsiMomentumConfig {
        val baseProfiles = profiles.ifEmpty { RsiMomentumProfileConfig.defaultProfiles() }
        val normalizedProfiles = baseProfiles.mapIndexed { index, profile ->
            profile.normalize(index)
        }

        return copy(enabled = enabled, profiles = normalizedProfiles)
    }

    private fun RsiMomentumProfileConfig.normalize(index: Int): RsiMomentumProfileConfig {
        val normalizedHoldingCount = holdingCount.coerceAtLeast(1)
        val normalizedCandidateCount = candidateCount.coerceAtLeast(normalizedHoldingCount)
        val normalizedBoardDisplayCount = boardDisplayCount.coerceAtLeast(normalizedCandidateCount)
        val normalizedReplacementPoolCount = replacementPoolCount.coerceAtLeast(normalizedCandidateCount)
        val normalizedRsiPeriods = rsiPeriods
            .map { period -> period.coerceAtLeast(2) }
            .distinct()
            .sorted()
            .ifEmpty { RsiMomentumProfileConfig.DEFAULT_RSI_PERIODS }
        val normalizedExtensionFraction = (
            maxExtensionAboveSma20ForNewEntryPct?.div(100.0) ?: maxExtensionAboveSma20ForNewEntry
            ).coerceAtLeast(0.0)
        val normalizedSkipExtensionFraction = (
            maxExtensionAboveSma20ForSkipNewEntryPct?.div(100.0) ?: maxExtensionAboveSma20ForSkipNewEntry
            ).coerceAtLeast(normalizedExtensionFraction)

        return copy(
            id = id.ifBlank { "profile-${index + 1}" },
            label = label.ifBlank { baseUniversePreset },
            baseUniversePreset = baseUniversePreset.ifBlank { RsiMomentumProfileConfig.DEFAULT_BASE_UNIVERSE_PRESET },
            candidateCount = normalizedCandidateCount,
            boardDisplayCount = normalizedBoardDisplayCount,
            replacementPoolCount = normalizedReplacementPoolCount,
            holdingCount = normalizedHoldingCount,
            rsiPeriods = normalizedRsiPeriods,
            minAverageTradedValue = minAverageTradedValue.coerceAtLeast(0.0),
            maxExtensionAboveSma20ForNewEntry = normalizedExtensionFraction,
            maxExtensionAboveSma20ForNewEntryPct = normalizedExtensionFraction * 100.0,
            maxExtensionAboveSma20ForSkipNewEntry = normalizedSkipExtensionFraction,
            maxExtensionAboveSma20ForSkipNewEntryPct = normalizedSkipExtensionFraction * 100.0,
            rsiCalibrationRunAt = rsiCalibrationRunAt?.trim()?.takeIf { value -> value.isNotBlank() },
            rsiCalibrationMethod = rsiCalibrationMethod?.trim()?.takeIf { value -> value.isNotBlank() },
            rsiCalibrationSampleRange = rsiCalibrationSampleRange?.trim()?.takeIf { value -> value.isNotBlank() },
        )
    }

    companion object {
        private const val CONFIG_FILE_NAME: String = "rsi_momentum_config.json"
        private val PRESET_RESOURCES: Map<String, String> = mapOf(
            RsiMomentumProfileConfig.DEFAULT_BASE_UNIVERSE_PRESET to "strategy-universes/nifty_largemidcap_250.csv",
            RsiMomentumProfileConfig.DEFAULT_SMALLCAP_UNIVERSE_PRESET to "strategy-universes/nifty_smallcap_250.csv",
        )
    }

    private data class LegacyRsiMomentumConfig(
        val enabled: Boolean = true,
        val baseUniversePreset: String = RsiMomentumProfileConfig.DEFAULT_BASE_UNIVERSE_PRESET,
        val candidateCount: Int = 20,
        val boardDisplayCount: Int = RsiMomentumProfileConfig.DEFAULT_BOARD_DISPLAY_COUNT,
        val replacementPoolCount: Int = RsiMomentumProfileConfig.DEFAULT_REPLACEMENT_POOL_COUNT,
        val holdingCount: Int = 10,
        val rsiPeriods: List<Int> = RsiMomentumProfileConfig.DEFAULT_RSI_PERIODS,
        val minAverageTradedValue: Double = RsiMomentumProfileConfig.DEFAULT_MIN_AVERAGE_TRADED_VALUE_CR,
        val maxExtensionAboveSma20ForNewEntry: Double = RsiMomentumProfileConfig.DEFAULT_MAX_EXTENSION_ABOVE_SMA20_FOR_NEW_ENTRY,
        val maxExtensionAboveSma20ForNewEntryPct: Double? = null,
        val maxExtensionAboveSma20ForSkipNewEntry: Double = RsiMomentumProfileConfig.DEFAULT_MAX_EXTENSION_ABOVE_SMA20_FOR_SKIP_NEW_ENTRY,
        val maxExtensionAboveSma20ForSkipNewEntryPct: Double? = null,
        val rebalanceDay: String = "FRIDAY",
        val rebalanceTime: String = "15:40",
    )
}
