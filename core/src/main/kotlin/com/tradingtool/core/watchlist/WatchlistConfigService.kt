package com.tradingtool.core.watchlist

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.model.watchlist.WatchlistConfig
import com.tradingtool.core.model.watchlist.WatchlistTagDefinition
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read

@Singleton
class WatchlistConfigService @Inject constructor() {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = ObjectMapper().registerKotlinModule()
    private val configFile = File("watchlist_config.json")
    private val lock = ReentrantReadWriteLock()

    init {
        if (!configFile.exists()) {
            log.info("Creating initial watchlist_config.json with default definitions")
            val defaultConfig = WatchlistConfig(
                tagDefinitions = listOf(
                    WatchlistTagDefinition("weekly", "#4287f5"),
                    WatchlistTagDefinition("Remora", "#f54242"),
                    WatchlistTagDefinition("Momentum", "#ff3d5a")
                )
            )
            saveConfig(defaultConfig)
        }
    }

    fun loadConfig(): WatchlistConfig = lock.read {
        if (!configFile.exists()) return WatchlistConfig()
        try {
            mapper.readValue(configFile, WatchlistConfig::class.java)
        } catch (e: Exception) {
            log.error("Failed to read watchlist_config.json: ${e.message}")
            WatchlistConfig()
        }
    }

    private fun saveConfig(config: WatchlistConfig) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(configFile, config)
        } catch (e: Exception) {
            log.error("Failed to save watchlist_config.json: ${e.message}")
        }
    }

    fun listAllTags(): List<WatchlistTagDefinition> {
        return loadConfig().tagDefinitions
    }

    fun getTagDefinition(name: String): WatchlistTagDefinition? {
        return loadConfig().tagDefinitions.find { it.name.equals(name, ignoreCase = true) }
    }
}
