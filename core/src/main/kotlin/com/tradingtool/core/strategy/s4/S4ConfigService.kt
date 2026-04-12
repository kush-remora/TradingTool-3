package com.tradingtool.core.strategy.s4

import com.google.inject.Inject
import com.google.inject.Singleton
import org.slf4j.LoggerFactory
import java.io.InputStream

@Singleton
class S4ConfigService @Inject constructor() {
    private val log = LoggerFactory.getLogger(S4ConfigService::class.java)

    fun loadConfig(): S4Config = S4Config()

    fun loadBaseUniverseSymbols(presetName: String): List<String> {
        val resourceName = PRESET_RESOURCES[presetName]
        if (resourceName == null) {
            log.warn("Unknown S4 preset: {}", presetName)
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

    companion object {
        private val PRESET_RESOURCES: Map<String, String> = mapOf(
            S4ProfileConfig.DEFAULT_BASE_UNIVERSE_PRESET to "strategy-universes/nifty_largemidcap_250.csv",
            S4ProfileConfig.DEFAULT_SMALLCAP_UNIVERSE_PRESET to "strategy-universes/nifty_smallcap_250.csv",
        )
    }
}
