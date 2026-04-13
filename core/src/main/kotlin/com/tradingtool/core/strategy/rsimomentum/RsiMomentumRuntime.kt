package com.tradingtool.core.strategy.rsimomentum

import com.tradingtool.core.candle.dao.CandleReadDao
import com.tradingtool.core.candle.dao.CandleWriteDao
import com.tradingtool.core.config.ConfigLoader
import com.tradingtool.core.config.IndicatorConfig
import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.database.RedisHandler
import com.tradingtool.core.database.RsiMomentumSnapshotJdbiHandler
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.KiteConfig
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.kite.KiteTokenReadDao
import com.tradingtool.core.kite.KiteTokenWriteDao
import com.tradingtool.core.model.DatabaseConfig
import com.tradingtool.core.stock.dao.StockReadDao
import com.tradingtool.core.stock.dao.StockWriteDao
import com.tradingtool.core.strategy.rsimomentum.dao.RsiMomentumSnapshotReadDao
import com.tradingtool.core.strategy.rsimomentum.dao.RsiMomentumSnapshotWriteDao
import java.io.Closeable

class RsiMomentumRuntime private constructor(
    val service: RsiMomentumService,
    private val redisHandler: RedisHandler,
) : Closeable {

    override fun close() {
        redisHandler.close()
    }

    companion object {
        suspend fun fromEnvironment(): RsiMomentumRuntime {
            val databaseConfig = DatabaseConfig(
                jdbcUrl = ConfigLoader.get("SUPABASE_DB_URL", "supabase.dbUrl"),
            )
            val redisHandler = RedisHandler.fromEnv()
            val kiteClient = buildAuthenticatedKiteClient(databaseConfig)
            val service = RsiMomentumService(
                configService = RsiMomentumConfigService(),
                candleHandler = buildHandler<CandleReadDao, CandleWriteDao>(databaseConfig),
                stockHandler = buildHandler<StockReadDao, StockWriteDao>(databaseConfig),
                redis = redisHandler,
                kiteClient = kiteClient,
                instrumentCache = InstrumentCache(),
                snapshotHandler = buildHandler<RsiMomentumSnapshotReadDao, RsiMomentumSnapshotWriteDao>(databaseConfig),
                indicatorConfig = IndicatorConfig.DEFAULT,
            )

            return RsiMomentumRuntime(
                service = service,
                redisHandler = redisHandler,
            )
        }

        private suspend fun buildAuthenticatedKiteClient(databaseConfig: DatabaseConfig): KiteConnectClient {
            val kiteClient = KiteConnectClient(
                KiteConfig(
                    apiKey = ConfigLoader.get("KITE_API_KEY", "kite.apiKey"),
                    apiSecret = ConfigLoader.get("KITE_API_SECRET", "kite.apiSecret"),
                ),
            )
            val tokenHandler = buildHandler<KiteTokenReadDao, KiteTokenWriteDao>(databaseConfig)
            val latestToken = tokenHandler.read { dao -> dao.getLatestToken() }
                ?.takeIf { token -> token.isNotBlank() }
                ?: error("Kite authentication required. No token found in kite_tokens table.")

            kiteClient.applyAccessToken(latestToken)
            return kiteClient
        }

        private inline fun <reified R, reified W> buildHandler(databaseConfig: DatabaseConfig): JdbiHandler<R, W> =
            JdbiHandler(databaseConfig, R::class.java, W::class.java)
    }
}
