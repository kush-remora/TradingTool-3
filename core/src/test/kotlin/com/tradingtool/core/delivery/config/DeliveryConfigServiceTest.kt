package com.tradingtool.core.delivery.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

class DeliveryConfigServiceTest {

    @Test
    fun `loadConfig creates default config file`() {
        withTempConfigService { service, configPath ->
            val config = service.loadConfig()

            assertTrue(configPath.exists())
            assertTrue(config.enabled)
            assertEquals(DeliveryDataSource.CM_BHAVDATA_FULL, config.source)
            assertEquals(DeliveryConfigService.DEFAULT_PRESET_NAMES, config.universe.presetNames)
            assertTrue(config.universe.includeWatchlist)
        }
    }

    @Test
    fun `resolveConfiguredUniverseSymbols unions presets with watchlist symbols`() {
        withTempConfigService { service, _ ->
            service.writeConfig(
                DeliveryConfig(
                    universe = DeliveryUniverseConfig(
                        presetNames = listOf(
                            DeliveryConfigService.PRESET_LARGE_MIDCAP_250,
                            DeliveryConfigService.PRESET_SMALLCAP_250,
                        ),
                        includeWatchlist = true,
                    ),
                ),
            )

            val expected = (
                service.loadPresetSymbols(DeliveryConfigService.PRESET_LARGE_MIDCAP_250) +
                    service.loadPresetSymbols(DeliveryConfigService.PRESET_SMALLCAP_250) +
                    listOf("CUSTOM", "RELIANCE")
                ).toSortedSet()

            val resolved = service.resolveConfiguredUniverseSymbols(
                watchlistSymbols = listOf("custom", " reliance ", ""),
            )

            assertEquals(expected, resolved)
        }
    }

    @Test
    fun `resolveConfiguredUniverseSymbols honors include watchlist flag`() {
        withTempConfigService { service, _ ->
            service.writeConfig(
                DeliveryConfig(
                    universe = DeliveryUniverseConfig(
                        presetNames = listOf(DeliveryConfigService.PRESET_LARGE_MIDCAP_250),
                        includeWatchlist = false,
                    ),
                ),
            )

            val resolved = service.resolveConfiguredUniverseSymbols(
                watchlistSymbols = listOf("CUSTOM"),
            )

            assertFalse(resolved.contains("CUSTOM"))
            assertEquals(
                service.loadPresetSymbols(DeliveryConfigService.PRESET_LARGE_MIDCAP_250).toSortedSet(),
                resolved,
            )
        }
    }

    private fun withTempConfigService(block: (DeliveryConfigService, java.nio.file.Path) -> Unit) {
        val tempDir = Files.createTempDirectory("delivery-config-test")
        val configPath = tempDir.resolve(DeliveryConfigService.CONFIG_FILE_NAME)
        val previous = System.getProperty(DeliveryConfigService.CONFIG_FILE_PATH_PROPERTY)

        System.setProperty(DeliveryConfigService.CONFIG_FILE_PATH_PROPERTY, configPath.toString())
        try {
            block(DeliveryConfigService(), configPath)
        } finally {
            if (previous == null) {
                System.clearProperty(DeliveryConfigService.CONFIG_FILE_PATH_PROPERTY)
            } else {
                System.setProperty(DeliveryConfigService.CONFIG_FILE_PATH_PROPERTY, previous)
            }
            configPath.deleteIfExists()
            tempDir.toFile().deleteRecursively()
        }
    }
}
