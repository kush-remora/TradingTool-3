package com.tradingtool.core.model

/**
 * Database configuration for JDBI connection.
 */
data class DatabaseConfig(
    val jdbcUrl: String,
)

/**
 * JDBI Handler exceptions.
 */
open class JdbiHandlerError(override val message: String) : Exception(message)
class JdbiNotConfiguredError(message: String) : JdbiHandlerError(message)
