package com.tradingtool.core.config

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Reads config values with environment-aware fallback.
 *
 * Priority:
 *   1. Environment variable (always checked first — works everywhere)
 *   2. localconfig.yaml (LOCAL only — for running jobs/service without setting env vars)
 *
 * Environment detection:
 *   - GITHUB_ACTIONS=true → GitHub Actions
 *   - RENDER=true         → Render production
 *   - neither             → local dev
 */
object ConfigLoader {

    enum class RunEnvironment { LOCAL, GITHUB_ACTIONS, RENDER }

    fun detect(): RunEnvironment = when {
        System.getenv("GITHUB_ACTIONS")?.lowercase() == "true" -> RunEnvironment.GITHUB_ACTIONS
        System.getenv("RENDER")?.lowercase() == "true" -> RunEnvironment.RENDER
        else -> RunEnvironment.LOCAL
    }

    /**
     * Resolves a config value.
     * @param envVarName  env var to check (e.g. "KITE_API_KEY")
     * @param yamlKey     dot-separated key in localconfig.yaml (e.g. "kite.apiKey")
     */
    fun get(envVarName: String, yamlKey: String): String {
        // 1. Env var — works in all environments
        val envValue = System.getenv(envVarName)?.takeIf { it.isNotBlank() }
        if (envValue != null) return envValue

        // 2. localconfig.yaml — local dev only
        if (detect() == RunEnvironment.LOCAL) {
            val yamlValue = loadLocalConfig()[yamlKey]?.takeIf { it.isNotBlank() }
            if (yamlValue != null) return yamlValue
        }

        val hint = if (detect() == RunEnvironment.LOCAL) " or localconfig.yaml[$yamlKey]" else ""
        error("Required config '$envVarName' not found in env vars$hint. Environment: ${detect()}")
    }

    private fun loadLocalConfig(): Map<String, String> {
        val path = findLocalConfigPath() ?: return emptyMap()
        return parseYaml(path)
    }

    private fun findLocalConfigPath(): Path? {
        val candidates = listOf(
            // Absolute path for this machine — same pattern used in Application.kt
            Paths.get("/Users/kushbhardwaj/Documents/github/TradingTool-3/service/src/main/resources/localconfig.yaml"),
            // Relative paths for running from project root or service/
            Paths.get("service/src/main/resources/localconfig.yaml"),
            Paths.get("localconfig.yaml"),
        )
        return candidates.firstOrNull { Files.exists(it) }
    }

    // Simple line-by-line YAML parser — handles "section:\n  key: value" format only.
    // No library needed; localconfig.yaml is flat and predictable.
    private fun parseYaml(path: Path): Map<String, String> {
        val values = mutableMapOf<String, String>()
        var section: String? = null

        Files.newBufferedReader(path).useLines { lines ->
            lines.forEach { rawLine ->
                val line = rawLine.substringBefore("#").trim()
                if (line.isEmpty()) return@forEach

                val isSectionHeader = !rawLine.startsWith(" ") && line.endsWith(":")
                if (isSectionHeader) {
                    section = line.removeSuffix(":")
                    return@forEach
                }

                if (!rawLine.startsWith("  ")) return@forEach
                val activeSection = section ?: return@forEach

                val delimIdx = line.indexOf(':')
                if (delimIdx <= 0) return@forEach

                val key = line.substring(0, delimIdx).trim()
                val value = line.substring(delimIdx + 1).trim()
                    .removeSurrounding("\"")
                    .removeSurrounding("'")

                values["$activeSection.$key"] = value
            }
        }
        return values
    }
}
