package com.tradingtool.core.indexconstituents

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class IndexSyncConfigLoader(
    private val objectMapper: ObjectMapper,
) {
    fun load(): IndexSyncConfig {
        val path = resolvePath()
        require(Files.exists(path)) { "Index sync config not found at ${path.toAbsolutePath()}" }

        val config = objectMapper.readValue(path.toFile(), IndexSyncConfig::class.java)
        require(config.batchSize > 0) { "batchSize must be > 0" }
        return config
    }

    private fun resolvePath(): Path {
        val candidates = listOf(
            Paths.get("wyckoff-market-cycle/config/index_sync_config.json"),
            Paths.get("../wyckoff-market-cycle/config/index_sync_config.json"),
        )

        return candidates.firstOrNull { candidate -> Files.exists(candidate) }
            ?: candidates.first()
    }

    companion object {
        fun defaultObjectMapper(): ObjectMapper {
            return ObjectMapper().findAndRegisterModules().registerKotlinModule()
        }
    }
}
