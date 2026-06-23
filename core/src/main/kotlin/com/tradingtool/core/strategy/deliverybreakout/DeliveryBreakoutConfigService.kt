package com.tradingtool.core.strategy.deliverybreakout

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
class DeliveryBreakoutConfigService @Inject constructor() {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = ObjectMapper().registerKotlinModule()
    private val lock = ReentrantReadWriteLock()

    private val configFile = File(CONFIG_FILE)

    init {
        if (!configFile.exists()) {
            saveConfig(DeliveryBreakoutConfig())
        }
    }

    fun loadConfig(): DeliveryBreakoutConfig = lock.read {
        if (!configFile.exists()) {
            return DeliveryBreakoutConfig()
        }
        return try {
            mapper.readValue(configFile, DeliveryBreakoutConfig::class.java)
        } catch (error: Exception) {
            log.error("Failed to read {}: {}", CONFIG_FILE, error.message)
            DeliveryBreakoutConfig()
        }
    }

    private fun saveConfig(config: DeliveryBreakoutConfig) = lock.write {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(configFile, config)
        } catch (error: Exception) {
            log.error("Failed to write {}: {}", CONFIG_FILE, error.message)
        }
    }

    companion object {
        private const val CONFIG_FILE = "delivery_breakout_config.json"
    }
}
