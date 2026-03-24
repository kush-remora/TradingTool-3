package com.tradingtool.core.database

import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
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

    fun set(key: String, value: String, ttlSeconds: Long) {
        withJedis { it.setex(key, ttlSeconds, value) }
    }

    fun get(key: String): String? = withJedis { it.get(key) }

    fun delete(key: String) {
        withJedis { it.del(key) }
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
         * Creates a [RedisHandler] from the REDIS_URL environment variable,
         * falling back to localhost:6379 for local development.
         */
        fun fromEnv(): RedisHandler {
            val url = System.getenv("REDIS_URL")?.takeIf { it.isNotBlank() }
                ?: "redis://localhost:6379"
            return RedisHandler(url)
        }
    }
}
