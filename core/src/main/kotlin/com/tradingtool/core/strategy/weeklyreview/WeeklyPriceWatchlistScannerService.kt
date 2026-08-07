package com.tradingtool.core.strategy.weeklyreview

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.database.StockDeliveryJdbiHandler
import com.tradingtool.core.indexconstituents.dao.IndexConstituentUpsertRow
import com.tradingtool.core.model.screener.UniverseOption
import com.tradingtool.core.model.screener.UniverseOptionsResponse
import com.tradingtool.core.strategy.momentum.PARTICIPATION_DELIVERY_HISTORY_SESSIONS
import com.tradingtool.core.strategy.momentum.calculateMomentumEvidence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate

@Singleton
class WeeklyPriceWatchlistScannerService @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val candleCacheService: CandleCacheService,
    private val deliveryHandler: StockDeliveryJdbiHandler,
) {
    suspend fun listWatchlists(): UniverseOptionsResponse {
        val options = indexConstituentHandler.read { dao -> dao.listUniqueIndices() }
            .map { summary -> UniverseOption(summary.indexKey, summary.indexKey, summary.count) }
            .sortedBy(UniverseOption::label)
        return UniverseOptionsResponse(options)
    }

    suspend fun scan(watchlistKey: String, toDate: LocalDate = LocalDate.now()): WeeklyPriceWatchlistScannerResponse {
        val normalizedKey = watchlistKey.trim()
        require(normalizedKey.isNotEmpty()) { "watchlist is required." }

        val resolvedKey = indexConstituentHandler.read { dao ->
            dao.listUniqueIndices()
                .firstOrNull { summary -> summary.indexKey.equals(normalizedKey, ignoreCase = true) }
                ?.indexKey
        } ?: throw IllegalArgumentException("Unknown watchlist: $watchlistKey")
        val members = indexConstituentHandler.read { dao -> dao.listActiveByIndex(resolvedKey) }
            .distinctBy(IndexConstituentUpsertRow::symbol)

        val rows = coroutineScope {
            val semaphore = Semaphore(MAX_PARALLEL_CANDLE_READS)
            members.map { member ->
                async(Dispatchers.IO) {
                    semaphore.withPermit { buildRow(member, toDate) }
                }
            }.awaitAll()
        }
        return WeeklyPriceWatchlistScannerResponse(resolvedKey, rows.sortedBy(WeeklyPriceWatchlistRow::symbol))
    }

    private suspend fun buildRow(member: IndexConstituentUpsertRow, toDate: LocalDate): WeeklyPriceWatchlistRow {
        val deliveryByDate = deliveryHandler.read { dao -> dao.findRecentByInstrumentToken(member.instrumentToken, toDate.plusDays(1), MOMENTUM_DELIVERY_HISTORY_SESSIONS) }
            .associate { delivery -> delivery.tradingDate to delivery.delivPer }
        val allCandles = candleCacheService.getDailyCandles(
            token = member.instrumentToken,
            symbol = member.symbol,
            from = toDate.minusDays(MOMENTUM_HISTORY_CALENDAR_DAYS),
            to = toDate,
        ).sortedBy { candle -> candle.candleDate }
        val momentumEvidence = calculateMomentumEvidence(
            candles = allCandles,
            asOfDate = toDate,
            deliveryPercentageByDate = deliveryByDate,
        )
        val days = allCandles
            .filter { candle -> !candle.candleDate.isBefore(toDate.minusDays(DISPLAY_HISTORY_CALENDAR_DAYS)) }
            .map { candle ->
                WeeklyPriceWatchlistDay(
                    date = candle.candleDate.toString(),
                    open = candle.open,
                    high = candle.high,
                    low = candle.low,
                    close = candle.close,
                    volume = candle.volume,
                    deliveryPercentage = deliveryByDate[candle.candleDate],
                )
            }
        return WeeklyPriceWatchlistRow(
            symbol = member.symbol,
            companyName = member.companyName,
            instrumentToken = member.instrumentToken,
            days = days,
            momentumEvidence = momentumEvidence,
        )
    }

    private companion object {
        const val DISPLAY_HISTORY_CALENDAR_DAYS = 60L
        const val MOMENTUM_HISTORY_CALENDAR_DAYS = 400L
        const val MOMENTUM_DELIVERY_HISTORY_SESSIONS = PARTICIPATION_DELIVERY_HISTORY_SESSIONS
        const val MAX_PARALLEL_CANDLE_READS = 12
    }
}
