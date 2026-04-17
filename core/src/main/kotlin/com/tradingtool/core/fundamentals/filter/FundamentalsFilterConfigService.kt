package com.tradingtool.core.fundamentals.filter

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.inject.Inject
import com.google.inject.Singleton
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read

data class FundamentalsProfileRule(
    @JsonProperty("debt_to_equity_max")
    val debtToEquityMax: Double? = null,
    @JsonProperty("roce_min_percent")
    val roceMinPercent: Double? = null,
    @JsonProperty("promoter_pledge_max_percent")
    val promoterPledgeMaxPercent: Double? = null,
    @JsonProperty("sales_cagr_3y_min_percent")
    val salesCagr3yMinPercent: Double? = null,
    @JsonProperty("pat_cagr_3y_min_percent")
    val patCagr3yMinPercent: Double? = null,
    @JsonProperty("ocf_positive_years_min")
    val ocfPositiveYearsMin: Int? = null,
    @JsonProperty("ocf_positive_years_window")
    val ocfPositiveYearsWindow: Int? = null,
    @JsonProperty("avg_traded_value_20d_min_cr")
    val avgTradedValue20dMinCr: Double? = null,
)

data class FundamentalsBucketFilterProfiles(
    val standard: FundamentalsProfileRule = FundamentalsProfileRule(),
    val extreme: FundamentalsProfileRule = FundamentalsProfileRule(),
)

data class FundamentalsFilterConfig(
    val enabled: Boolean = true,
    val version: Int = 1,
    val notes: String? = null,
    val buckets: Map<String, FundamentalsBucketFilterProfiles> = emptyMap(),
)

@Singleton
class FundamentalsFilterConfigService @Inject constructor() {
    private val log = LoggerFactory.getLogger(FundamentalsFilterConfigService::class.java)
    private val mapper = ObjectMapper().registerKotlinModule()
    private val configFile = File(CONFIG_FILE_NAME)
    private val lock = ReentrantReadWriteLock()

    fun loadConfig(): FundamentalsFilterConfig = lock.read {
        if (!configFile.exists()) {
            return FundamentalsFilterConfig()
        }

        return try {
            val parsed = mapper.readValue(configFile, FundamentalsFilterConfig::class.java)
            parsed.copy(
                buckets = parsed.buckets.mapKeys { (key, _) -> key.trim().uppercase() },
            )
        } catch (error: Exception) {
            log.error("Failed to read {}: {}", CONFIG_FILE_NAME, error.message)
            FundamentalsFilterConfig()
        }
    }

    fun findRule(tag: String, profile: String): FundamentalsProfileRule? {
        val config = loadConfig()
        if (!config.enabled) {
            return null
        }

        val bucket = config.buckets[tag.trim().uppercase()] ?: return null
        return if (profile.trim().equals(PROFILE_EXTREME, ignoreCase = true)) {
            bucket.extreme
        } else {
            bucket.standard
        }
    }

    companion object {
        const val CONFIG_FILE_NAME: String = "fundamentals_filter_config.json"
        const val PROFILE_STANDARD: String = "standard"
        const val PROFILE_EXTREME: String = "extreme"
    }
}
