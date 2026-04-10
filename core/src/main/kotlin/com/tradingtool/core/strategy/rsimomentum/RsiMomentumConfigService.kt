package com.tradingtool.core.strategy.rsimomentum

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
            mapper.readValue(configFile, RsiMomentumConfig::class.java).normalize()
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

    private fun saveConfig(config: RsiMomentumConfig) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(configFile, config.normalize())
        } catch (error: Exception) {
            log.error("Failed to save $CONFIG_FILE_NAME: ${error.message}")
        }
    }

    private fun RsiMomentumConfig.normalize(): RsiMomentumConfig {
        val normalizedHoldingCount = holdingCount.coerceAtLeast(1)
        val normalizedCandidateCount = candidateCount.coerceAtLeast(normalizedHoldingCount)

        return copy(
            candidateCount = normalizedCandidateCount,
            holdingCount = normalizedHoldingCount,
            rsiPeriods = RsiMomentumConfig.DEFAULT_RSI_PERIODS,
            minAverageTradedValue = minAverageTradedValue.coerceAtLeast(0.0),
        )
    }

    companion object {
        private const val CONFIG_FILE_NAME: String = "rsi_momentum_config.json"
        private val PRESET_RESOURCES: Map<String, String> = mapOf(
            RsiMomentumConfig.DEFAULT_BASE_UNIVERSE_PRESET to "strategy-universes/nifty_largemidcap_250.csv",
        )
    }
}
