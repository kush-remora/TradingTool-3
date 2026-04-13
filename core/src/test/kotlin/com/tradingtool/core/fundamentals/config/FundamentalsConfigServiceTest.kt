package com.tradingtool.core.fundamentals.config

import com.tradingtool.core.delivery.model.DeliveryUniverse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

class FundamentalsConfigServiceTest {

    @Test
    fun `loadConfig creates default config file`() {
        withTempConfigService { service, configPath ->
            val config = service.loadConfig()

            assertTrue(configPath.exists())
            assertTrue(config.enabled)
            assertEquals(FundamentalsDataSource.SCREENER, config.source)
            assertEquals(FundamentalsConfigService.DEFAULT_PRESET_NAMES, config.universe.presetNames)
            assertTrue(config.universe.includeWatchlist)
            assertEquals(1_200L, config.requestDelayMs)
        }
    }

    @Test
    fun `resolveConfiguredUniverseAssignments unions presets with watchlist symbols`() {
        withTempConfigService { service, _ ->
            service.writeConfig(
                FundamentalsConfig(
                    universe = FundamentalsUniverseConfig(
                        presetNames = listOf(
                            FundamentalsConfigService.PRESET_LARGE_MIDCAP_250,
                            FundamentalsConfigService.PRESET_SMALLCAP_250,
                        ),
                        includeWatchlist = true,
                    ),
                ),
            )

            val assignments = service.resolveConfiguredUniverseAssignments(
                watchlistSymbols = listOf("custom", "RELIANCE"),
            )

            assertEquals(DeliveryUniverse.LARGEMIDCAP_250, assignments["RELIANCE"])
            assertEquals(DeliveryUniverse.WATCHLIST, assignments["CUSTOM"])
        }
    }

    @Test
    fun `resolveConfiguredUniverseAssignments honors include watchlist flag`() {
        withTempConfigService { service, _ ->
            service.writeConfig(
                FundamentalsConfig(
                    universe = FundamentalsUniverseConfig(
                        presetNames = listOf(FundamentalsConfigService.PRESET_LARGE_MIDCAP_250),
                        includeWatchlist = false,
                    ),
                ),
            )

            val assignments = service.resolveConfiguredUniverseAssignments(
                watchlistSymbols = listOf("CUSTOM"),
            )

            assertFalse(assignments.containsKey("CUSTOM"))
            assertEquals(
                DeliveryUniverse.LARGEMIDCAP_250,
                assignments["RELIANCE"],
            )
        }
    }

    private fun withTempConfigService(block: (FundamentalsConfigService, java.nio.file.Path) -> Unit) {
        val tempDir = Files.createTempDirectory("fundamentals-config-test")
        val configPath = tempDir.resolve(FundamentalsConfigService.CONFIG_FILE_NAME)
        val previous = System.getProperty(FundamentalsConfigService.CONFIG_FILE_PATH_PROPERTY)

        System.setProperty(FundamentalsConfigService.CONFIG_FILE_PATH_PROPERTY, configPath.toString())
        try {
            block(FundamentalsConfigService(), configPath)
        } finally {
            if (previous == null) {
                System.clearProperty(FundamentalsConfigService.CONFIG_FILE_PATH_PROPERTY)
            } else {
                System.setProperty(FundamentalsConfigService.CONFIG_FILE_PATH_PROPERTY, previous)
            }
            configPath.deleteIfExists()
            tempDir.toFile().deleteRecursively()
        }
    }
}
