package com.tradingtool.health

import com.codahale.metrics.health.HealthCheck
import com.tradingtool.core.watchlist.dao.WatchlistDal

class DatabaseHealthCheck(
    private val watchlistDal: WatchlistDal,
) : HealthCheck() {
    override fun check(): Result {
        return try {
            val statuses = watchlistDal.checkTablesAccess()
            val allAccessible = statuses.all { it.accessible }

            if (allAccessible) {
                Result.healthy("All database tables accessible")
            } else {
                val failedTables = statuses.filter { !it.accessible }.map { it.tableName }
                Result.unhealthy("Tables not accessible: ${failedTables.joinToString()}")
            }
        } catch (e: Exception) {
            Result.unhealthy("Database check failed: ${e.message}")
        }
    }
}
