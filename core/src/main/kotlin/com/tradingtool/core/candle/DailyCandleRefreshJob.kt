package com.tradingtool.core.candle

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.screener.CandleDataService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.LocalTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Singleton
class DailyCandleRefreshJob @Inject constructor(
    private val indexHandler: IndexConstituentJdbiHandler,
    private val candleDataService: CandleDataService,
    private val candleCacheService: CandleCacheService,
    private val kiteClient: KiteConnectClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "daily-candle-refresh").apply { isDaemon = true } }
    private val zone = ZoneId.of("Asia/Kolkata")

    fun start(): Unit = scheduleNext()

    private fun scheduleNext(): Unit {
        val now = ZonedDateTime.now(zone)
        val next = listOf(LocalTime.of(9, 30), LocalTime.of(17, 0))
            .map { time -> now.toLocalDate().atTime(time).atZone(zone) }
            .firstOrNull { it.isAfter(now) }
            ?: now.plusDays(1).toLocalDate().atTime(9, 30).atZone(zone)
        executor.schedule({ run(); scheduleNext() }, Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
        log.info("Daily candle refresh scheduled for {}", next)
    }

    private fun run(): Unit = runBlocking {
        if (!kiteClient.isAuthenticated) return@runBlocking
        val symbols = indexHandler.read { dao -> dao.listUniqueIndices().flatMap { dao.listActiveByIndex(it.indexKey) }.map { it.symbol }.distinct() }
        if (symbols.isEmpty()) return@runBlocking
        val today = ZonedDateTime.now(zone).toLocalDate()
        candleDataService.syncDailyRange(symbols, today.minusDays(5), today, kiteClient)
        symbols.forEach { candleCacheService.invalidateDailyCandles(it) }
        log.info("Daily candle refresh completed for {} symbols", symbols.size)
    }
}
