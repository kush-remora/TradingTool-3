package com.tradingtool.core.database

import com.tradingtool.core.config.ConfigLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.io.Closeable
import java.net.URI

/**
 * Manages a Redis connection pool via Jedis.
 *
 * Implements [Closeable] so it can be registered with Dropwizard's lifecycle manager:
 *   environment.lifecycle().manage(object : Managed {
 *       override fun start() = Unit
 *       override fun stop() = redis.close()
 *   })
 *
 * For the cron job (standalone process), use [RedisHandler.fromEnv] — the pool is
 * automatically cleaned up when the JVM exits.
 */
class RedisHandler(redisUrl: String) : Closeable {

    private val pool: JedisPool = buildPool(redisUrl)

    fun <T> withJedis(block: (Jedis) -> T): T = pool.resource.use(block)

    suspend fun set(key: String, value: String, ttlSeconds: Long) {
        withContext(Dispatchers.IO) { withJedis { it.setex(key, ttlSeconds, value) } }
    }

    suspend fun get(key: String): String? =
        withContext(Dispatchers.IO) { withJedis { it.get(key) } }

    suspend fun delete(key: String) {
        withContext(Dispatchers.IO) { withJedis { it.del(key) } }
    }

    override fun close() {
        pool.close()
    }

    private fun buildPool(redisUrl: String): JedisPool {
        val uri = URI(redisUrl)
        val config = JedisPoolConfig().apply {
            maxTotal = 20
            maxIdle = 10
            minIdle = 2
            testOnBorrow = true
        }
        return JedisPool(config, uri)
    }

    companion object {
        /**
         * Creates a [RedisHandler] from configuration files/env variables,
         * falling back to localhost:6379 for local development.
         */
        fun fromEnv(): RedisHandler {
            val url = runCatching { ConfigLoader.get("REDIS_URL", "redis.url") }.getOrNull()
                ?: System.getenv("REDIS_URL")?.takeIf { it.isNotBlank() }
                ?: "redis://localhost:6379"
            return RedisHandler(url)
        }
    }
}
