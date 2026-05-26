package com.tradingtool.core.strategy.wyckoff.phase1

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
class WyckoffPhase1ConfigService @Inject constructor() {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = ObjectMapper().registerKotlinModule()
    private val lock = ReentrantReadWriteLock()

    private val phase1ConfigFile = File(PHASE1_CONFIG_FILE)
    private val tableColumnsFile = File(TABLE_COLUMNS_CONFIG_FILE)

    init {
        if (!phase1ConfigFile.exists()) {
            savePhase1Config(WyckoffPhase1Config())
        }
        if (!tableColumnsFile.exists()) {
            saveTableColumnsConfig(defaultTableColumnsConfig())
        }
    }

    fun loadPhase1Config(): WyckoffPhase1Config = lock.read {
        if (!phase1ConfigFile.exists()) {
            return WyckoffPhase1Config()
        }
        return try {
            mapper.readValue(phase1ConfigFile, WyckoffPhase1Config::class.java)
        } catch (error: Exception) {
            log.error("Failed to read {}: {}", PHASE1_CONFIG_FILE, error.message)
            WyckoffPhase1Config()
        }
    }

    fun loadTableColumnsConfig(): WyckoffPhase1TableColumnsConfig = lock.read {
        if (!tableColumnsFile.exists()) {
            return defaultTableColumnsConfig()
        }
        return try {
            mapper.readValue(tableColumnsFile, WyckoffPhase1TableColumnsConfig::class.java)
        } catch (error: Exception) {
            log.error("Failed to read {}: {}", TABLE_COLUMNS_CONFIG_FILE, error.message)
            defaultTableColumnsConfig()
        }
    }

    private fun savePhase1Config(config: WyckoffPhase1Config) = lock.write {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(phase1ConfigFile, config)
        } catch (error: Exception) {
            log.error("Failed to write {}: {}", PHASE1_CONFIG_FILE, error.message)
        }
    }

    private fun saveTableColumnsConfig(config: WyckoffPhase1TableColumnsConfig) = lock.write {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(tableColumnsFile, config)
        } catch (error: Exception) {
            log.error("Failed to write {}: {}", TABLE_COLUMNS_CONFIG_FILE, error.message)
        }
    }

    private fun defaultTableColumnsConfig(): WyckoffPhase1TableColumnsConfig {
        return WyckoffPhase1TableColumnsConfig(
            enabled = true,
            defaultSort = listOf(
                WyckoffPhase1SortColumn(key = "signal_date", direction = "desc"),
            ),
            columns = listOf(
                WyckoffPhase1ColumnConfig(key = "symbol", enabled = true),
                WyckoffPhase1ColumnConfig(key = "signal_date", enabled = true),
                WyckoffPhase1ColumnConfig(key = "days_ago", enabled = false),
                WyckoffPhase1ColumnConfig(key = "index_key", enabled = true),
                WyckoffPhase1ColumnConfig(key = "delivery_pct", enabled = true),
                WyckoffPhase1ColumnConfig(key = "delivery_threshold_pct", enabled = false),
                WyckoffPhase1ColumnConfig(key = "delivery_pass", enabled = true),
                WyckoffPhase1ColumnConfig(key = "density_breach_count_15d", enabled = true),
                WyckoffPhase1ColumnConfig(key = "density_pass", enabled = true),
                WyckoffPhase1ColumnConfig(key = "delivery_volume_zscore_60d", enabled = true),
                WyckoffPhase1ColumnConfig(key = "zscore_pass", enabled = true),
                WyckoffPhase1ColumnConfig(key = "lvq_dq_pass", enabled = true),
                WyckoffPhase1ColumnConfig(key = "lvq_hit_count_15d", enabled = true),
                WyckoffPhase1ColumnConfig(key = "spread_pct", enabled = true),
                WyckoffPhase1ColumnConfig(key = "avg_spread_pct_20d", enabled = true),
                WyckoffPhase1ColumnConfig(key = "absorption_pass", enabled = true),
                WyckoffPhase1ColumnConfig(key = "roc20_pct", enabled = true),
                WyckoffPhase1ColumnConfig(key = "roc20_range_pass", enabled = true),
                WyckoffPhase1ColumnConfig(key = "sma200_distance_pct", enabled = true),
                WyckoffPhase1ColumnConfig(key = "sma_window_used", enabled = true),
                WyckoffPhase1ColumnConfig(key = "dma200_range_pass", enabled = true),
                WyckoffPhase1ColumnConfig(key = "low_volume_high_delivery_info", enabled = true),
                WyckoffPhase1ColumnConfig(key = "volume_vs_50d_ratio", enabled = true),
                WyckoffPhase1ColumnConfig(key = "passed_count", enabled = true),
                WyckoffPhase1ColumnConfig(key = "accumulation_run_length_days", enabled = true),
            ),
        )
    }

    companion object {
        private const val PHASE1_CONFIG_FILE = "wyckoff_phase1_config.json"
        private const val TABLE_COLUMNS_CONFIG_FILE = "wyckoff_phase1_table_columns.json"
    }
}
