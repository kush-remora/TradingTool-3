package com.tradingtool.core.fundamentals.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.delivery.model.DeliveryUniverse
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read

@Singleton
class FundamentalsConfigService @Inject constructor() {
    private val log = LoggerFactory.getLogger(FundamentalsConfigService::class.java)
    private val mapper = ObjectMapper().registerKotlinModule()
    private val configFile = File(System.getProperty(CONFIG_FILE_PATH_PROPERTY, CONFIG_FILE_NAME))
    private val lock = ReentrantReadWriteLock()

    init {
        if (!configFile.exists()) {
            saveConfig(FundamentalsConfig())
        }
    }

    fun loadConfig(): FundamentalsConfig = lock.read {
        if (!configFile.exists()) {
            return FundamentalsConfig()
        }

        return try {
            mapper.readValue(configFile, FundamentalsConfig::class.java).normalize()
        } catch (error: Exception) {
            log.error("Failed to read {}: {}", configFile.name, error.message)
            FundamentalsConfig()
        }
    }

    fun writeConfig(config: FundamentalsConfig) {
        saveConfig(config.normalize())
    }

    fun loadPresetSymbols(presetName: String): List<String> {
        val resourceName = PRESET_RESOURCES[presetName]
        if (resourceName == null) {
            log.warn("Unknown fundamentals universe preset: {}", presetName)
            return emptyList()
        }

        val stream: InputStream = javaClass.classLoader.getResourceAsStream(resourceName)
            ?: run {
                log.warn("Fundamentals universe resource not found: {}", resourceName)
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

    fun resolveConfiguredUniverseAssignments(
        watchlistSymbols: List<String> = emptyList(),
        symbolsOverride: List<String>? = null,
    ): Map<String, DeliveryUniverse> {
        val config = loadConfig()
        val configuredAssignments = linkedMapOf<String, DeliveryUniverse>()
        config.universe.presetNames.forEach { presetName ->
            val universe = PRESET_UNIVERSE_VALUES[presetName]
            if (universe == null) {
                log.warn("No fundamentals universe mapping configured for preset: {}", presetName)
                return@forEach
            }
            loadPresetSymbols(presetName).forEach { symbol ->
                configuredAssignments.putIfAbsent(symbol, universe)
            }
        }

        if (config.universe.includeWatchlist) {
            watchlistSymbols.asSequence()
                .map { symbol -> symbol.trim().uppercase() }
                .filter { symbol -> symbol.isNotEmpty() }
                .forEach { symbol ->
                    configuredAssignments.putIfAbsent(symbol, DeliveryUniverse.WATCHLIST)
                }
        }

        val overrideSymbols = symbolsOverride
            ?.map { symbol -> symbol.trim().uppercase() }
            ?.filter { symbol -> symbol.isNotEmpty() }
            ?.distinct()

        return if (overrideSymbols == null) {
            configuredAssignments.toSortedMap()
        } else {
            overrideSymbols.associateWith { symbol ->
                configuredAssignments[symbol] ?: DeliveryUniverse.WATCHLIST
            }.toSortedMap()
        }
    }

    private fun saveConfig(config: FundamentalsConfig) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(configFile, config)
        } catch (error: Exception) {
            log.error("Failed to save {}: {}", configFile.name, error.message)
        }
    }

    private fun FundamentalsConfig.normalize(): FundamentalsConfig {
        val normalizedPresetNames = universe.presetNames
            .map { preset -> preset.trim().uppercase() }
            .filter { preset -> preset.isNotEmpty() }
            .distinct()
            .ifEmpty { DEFAULT_PRESET_NAMES }

        return copy(
            requestDelayMs = requestDelayMs.coerceAtLeast(0),
            universe = universe.copy(
                presetNames = normalizedPresetNames,
            ),
        )
    }

    companion object {
        const val CONFIG_FILE_NAME: String = "fundamentals_config.json"
        const val CONFIG_FILE_PATH_PROPERTY: String = "fundamentals.config.file"
        const val PRESET_LARGE_MIDCAP_250: String = "NIFTY_LARGEMIDCAP_250"
        const val PRESET_SMALLCAP_250: String = "NIFTY_SMALLCAP_250"
        val DEFAULT_PRESET_NAMES: List<String> = listOf(
            PRESET_LARGE_MIDCAP_250,
            PRESET_SMALLCAP_250,
        )

        private val PRESET_RESOURCES: Map<String, String> = mapOf(
            PRESET_LARGE_MIDCAP_250 to "strategy-universes/nifty_largemidcap_250.csv",
            PRESET_SMALLCAP_250 to "strategy-universes/nifty_smallcap_250.csv",
        )
        private val PRESET_UNIVERSE_VALUES: Map<String, DeliveryUniverse> = mapOf(
            PRESET_LARGE_MIDCAP_250 to DeliveryUniverse.LARGEMIDCAP_250,
            PRESET_SMALLCAP_250 to DeliveryUniverse.SMALLCAP_250,
        )
    }
}
