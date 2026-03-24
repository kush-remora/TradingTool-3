package com.tradingtool.cron

import com.tradingtool.core.config.ConfigLoader
import com.tradingtool.core.config.IndicatorConfig
import com.tradingtool.core.database.RedisHandler
import com.tradingtool.core.database.StockIndicatorsJdbiHandler
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.kite.KiteConfig
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.kite.KiteTokenReadDao
import com.tradingtool.core.kite.KiteTokenWriteDao
import com.tradingtool.core.model.DatabaseConfig
import com.tradingtool.core.stock.dao.StockIndicatorsReadDao
import com.tradingtool.core.stock.dao.StockIndicatorsWriteDao
import com.tradingtool.core.stock.dao.StockReadDao
import com.tradingtool.core.stock.dao.StockWriteDao
import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.watchlist.IndicatorService
import kotlinx.coroutines.runBlocking

/**
 * Daily indicator sync — runs at 9:15 AM IST after market open.
 *
 * Scheduled via GitHub Actions (see .github/workflows/). The access token is
 * read from the [kite_tokens] DB table (written there by [KiteReminderJob] after
 * the user completes the Kite OAuth flow). This avoids storing a short-lived daily
 * token in environment variables or config files.
 *
 * To run locally:
 *   mvn -pl cron-job -am compile exec:java -Dexec.mainClass=com.tradingtool.cron.IndicatorsSyncJobKt
 */
fun main() {
    val env = ConfigLoader.detect()
    println("IndicatorsSyncJob starting in environment: $env")

    val dbConfig = DatabaseConfig(ConfigLoader.get("DATABASE_URL", "database.url"))
    val redis = RedisHandler.fromEnv()

    val kiteTokenHandler = JdbiHandler(dbConfig, KiteTokenReadDao::class.java, KiteTokenWriteDao::class.java)
    val stockHandler = JdbiHandler(dbConfig, StockReadDao::class.java, StockWriteDao::class.java)
    val stockIndicatorsHandler = JdbiHandler(dbConfig, StockIndicatorsReadDao::class.java, StockIndicatorsWriteDao::class.java)

    val kiteClient = buildAuthenticatedKiteClient(kiteTokenHandler)
    val indicatorService = IndicatorService(
        stockIndicatorsHandler = stockIndicatorsHandler,
        stockHandler = stockHandler,
        redis = redis,
        config = IndicatorConfig.DEFAULT,
    )

    runBlocking {
        indicatorService.refreshAll(kiteClient, onlyNeedsRefresh = false)
    }

    redis.close()
    println("IndicatorsSyncJob finished.")
}

/**
 * Builds a [KiteConnectClient] authenticated with the latest token from the DB.
 *
 * The token is written to [kite_tokens] by the auth flow after the user clicks the
 * Kite login link sent by [KiteReminderJob]. Reading from the DB (not from env vars)
 * keeps short-lived daily tokens out of CI secrets and config files.
 */
private fun buildAuthenticatedKiteClient(
    kiteTokenHandler: JdbiHandler<KiteTokenReadDao, KiteTokenWriteDao>
): KiteConnectClient {
    val kiteConfig = KiteConfig(
        apiKey = ConfigLoader.get("KITE_API_KEY", "kite.apiKey"),
        apiSecret = ConfigLoader.get("KITE_API_SECRET", "kite.apiSecret"),
    )
    val kiteClient = KiteConnectClient(kiteConfig)

    val accessToken = runBlocking {
        kiteTokenHandler.read { it.getLatestToken() }
    } ?: error("No Kite access token found in kite_tokens table. Complete the Kite login flow first.")

    kiteClient.applyAccessToken(accessToken)
    println("Kite client authenticated (token from DB).")
    return kiteClient
}
