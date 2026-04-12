package com.tradingtool.core.model

/**
 * Database configuration for JDBI connection.
 */
data class DatabaseConfig(
    val jdbcUrl: String,
    val maxPoolSize: Int = 5,
    val minIdleConnections: Int = 0,
    val connectionTimeoutMs: Long = 10_000,
    val idleTimeoutMs: Long = 600_000,
    val maxLifetimeMs: Long = 1_800_000,
)

/**
 * JDBI Handler exceptions.
 */
open class JdbiHandlerError(override val message: String) : Exception(message)
class JdbiNotConfiguredError(message: String) : JdbiHandlerError(message)
