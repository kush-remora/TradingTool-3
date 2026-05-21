package com.tradingtool.core.strategy.wyckoff.deliverythreshold

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.inject.Inject
import com.google.inject.Singleton
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Singleton
class DeliveryThresholdBacktestConfigService @Inject constructor() {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = ObjectMapper().registerKotlinModule()
    private val lock = ReentrantReadWriteLock()
    private val configFile = File("delivery_threshold_backtest_config.json")

    init {
        if (!configFile.exists()) {
            saveConfig(DeliveryThresholdBacktestConfig())
        }
    }

    fun loadConfig(): DeliveryThresholdBacktestConfig = lock.read {
        if (!configFile.exists()) {
            return DeliveryThresholdBacktestConfig()
        }
        return try {
            mapper.readValue(configFile, DeliveryThresholdBacktestConfig::class.java)
        } catch (error: Exception) {
            log.error("Failed to read delivery_threshold_backtest_config.json: {}", error.message)
            DeliveryThresholdBacktestConfig()
        }
    }

    private fun saveConfig(config: DeliveryThresholdBacktestConfig) = lock.write {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(configFile, config)
        } catch (error: Exception) {
            log.error("Failed to write delivery_threshold_backtest_config.json: {}", error.message)
        }
    }
}
