package com.tradingtool.core.database

interface KeyValueCache {
    suspend fun get(key: String): String?

    suspend fun set(key: String, value: String, ttlSeconds: Long)
}
