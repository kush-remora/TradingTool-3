package com.tradingtool.cron

import com.tradingtool.core.candle.dao.CandleReadDao
import com.tradingtool.core.candle.dao.CandleWriteDao
import com.tradingtool.core.config.ConfigLoader
import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.database.RedisHandler
import com.tradingtool.core.indexconstituents.dao.IndexConstituentReadDao
import com.tradingtool.core.indexconstituents.dao.IndexConstituentWriteDao
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.InstrumentTokenResolverService
import com.tradingtool.core.kite.KiteConfig
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.kite.KiteTokenReadDao
import com.tradingtool.core.kite.KiteTokenWriteDao
import com.tradingtool.core.model.DatabaseConfig
import com.tradingtool.core.screener.CandleDataService
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.system.exitProcess

fun main(): Unit = runBlocking {
    val database = DatabaseConfig(jdbcUrl = ConfigLoader.get("SUPABASE_DB_URL", "supabase.dbUrl"))
    val tokenHandler = JdbiHandler(database, KiteTokenReadDao::class.java, KiteTokenWriteDao::class.java)
    val kite = KiteConnectClient(KiteConfig(ConfigLoader.get("KITE_API_KEY", "kite.apiKey"), ConfigLoader.get("KITE_API_SECRET", "kite.apiSecret")))
    kite.applyAccessToken(tokenHandler.read { it.getLatestToken() } ?: error("Kite token is required."))
    val instruments = InstrumentCache()
    instruments.refresh(kite.client().getInstruments("NSE"))
    val candleHandler = JdbiHandler(database, CandleReadDao::class.java, CandleWriteDao::class.java)
    val indexHandler = JdbiHandler(database, IndexConstituentReadDao::class.java, IndexConstituentWriteDao::class.java)
    val symbols = indexHandler.read { dao -> dao.listUniqueIndices().flatMap { dao.listActiveByIndex(it.indexKey) }.map { it.symbol }.distinct() }
    val service = CandleDataService(candleHandler, instruments, InstrumentTokenResolverService(kite, instruments))
    service.syncDailyRange(symbols, LocalDate.now().minusDays(5), LocalDate.now(), kite)
    RedisHandler.fromEnv().use { redis -> symbols.forEach { symbol -> redis.withJedis { jedis -> jedis.keys("candles:${symbol.uppercase()}:day:*").takeIf { it.isNotEmpty() }?.let { jedis.del(*it.toTypedArray()) } } } }
    exitProcess(0)
}
