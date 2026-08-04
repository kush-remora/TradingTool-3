package com.tradingtool.core.strategy.deliverybreakout

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.database.StockDeliveryJdbiHandler
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import com.tradingtool.core.indexconstituents.dao.IndexConstituentUpsertRow
import com.tradingtool.core.technical.roundTo2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import java.time.LocalDate

@Singleton
class DeliveryBreakoutScannerService @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val stockDeliveryHandler: StockDeliveryJdbiHandler,
    private val candleCacheService: CandleCacheService,
    private val configService: DeliveryBreakoutConfigService,
    private val etfService: DeliveryBreakoutEtfService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun getDashboard(
        watchlistKey: String,
        requestedTradeDate: LocalDate?,
    ): DeliveryBreakoutDashboardResponse {
        val config = configService.loadConfig()
        require(config.baselineSessions > 0) { "baselineSessions must be greater than zero." }
        require(config.scanSessions > 0) { "scanSessions must be greater than zero." }
        require(config.shockMultiplier > 0.0) { "shockMultiplier must be greater than zero." }

        val resolvedWatchlist = resolveWatchlist(watchlistKey)
        val tradeDate = requestedTradeDate ?: stockDeliveryHandler.read { dao -> dao.getLatestTradingDate() }
        requireNotNull(tradeDate) { "No stock delivery data available." }

        val totalRequiredSessions = config.baselineSessions + config.scanSessions
        val availableDates = loadTradingDates(tradeDate, totalRequiredSessions)
        require(availableDates.size >= totalRequiredSessions) {
            "At least $totalRequiredSessions trading sessions are required before $tradeDate."
        }

        val baselineAndScanDates = availableDates.takeLast(totalRequiredSessions)
        val evaluationDates = baselineAndScanDates.takeLast(config.scanSessions)
        val members = resolvedWatchlist.members
        val nonEtfMembers = etfService.filterNonEtfMembers(members)
        val deliveryHistoryByToken = loadDeliveryHistoryByToken(
            tokens = nonEtfMembers.map { member -> member.instrumentToken }.distinct(),
            fromDate = baselineAndScanDates.first(),
            toDate = tradeDate,
        )

        val events = nonEtfMembers.flatMap { member ->
            DeliveryBreakoutAnalyzer.buildEvents(
                symbol = member.symbol,
                instrumentToken = member.instrumentToken,
                history = deliveryHistoryByToken[member.instrumentToken].orEmpty(),
                evaluationDates = evaluationDates,
                baselineSessions = config.baselineSessions,
                shockMultiplier = config.shockMultiplier,
            )
        }
        val eventsBySymbol = events.groupBy { event -> event.symbol }
        val candlesBySymbol = loadCandlesBySymbol(events, tradeDate)
        val dashboardRows = events.map { event ->
            buildDashboardRow(event, candlesBySymbol[event.symbol].orEmpty())
        }.sortedWith(
            compareBy<DeliveryBreakoutDashboardRow> { eventPriority(it.event_type) }
                .thenByDescending { row -> row.event_date }
                .thenByDescending { row -> maxOf(row.volume_ratio ?: 0.0, row.delivery_ratio ?: 0.0) },
        )

        val stocksWithData = deliveryHistoryByToken.count { (_, rows) -> rows.any { row -> row.tradingDate in baselineAndScanDates } }
        val bothCount = dashboardRows.count { row -> row.event_type == EVENT_BOTH }
        val deliveryOnlyCount = dashboardRows.count { row -> row.event_type == EVENT_DELIVERY_ONLY }
        val volumeOnlyCount = dashboardRows.count { row -> row.event_type == EVENT_VOLUME_ONLY }
        val stocksWithEvents = eventsBySymbol.size

        log.info(
            "Delivery breakout scanned watchlist {} through {}: {} members, {} events",
            resolvedWatchlist.key,
            tradeDate,
            nonEtfMembers.size,
            dashboardRows.size,
        )

        return DeliveryBreakoutDashboardResponse(
            meta = DeliveryBreakoutDashboardMeta(
                watchlist_key = resolvedWatchlist.key,
                trade_date = tradeDate.toString(),
                window_start_date = evaluationDates.first().toString(),
                window_end_date = evaluationDates.last().toString(),
                scanned_count = nonEtfMembers.size,
                data_available_count = stocksWithData,
                event_count = dashboardRows.size,
                both_count = bothCount,
                delivery_only_count = deliveryOnlyCount,
                volume_only_count = volumeOnlyCount,
                no_event_count = (nonEtfMembers.size - stocksWithEvents).coerceAtLeast(0),
            ),
            rows = dashboardRows,
        )
    }

    private suspend fun resolveWatchlist(requestedKey: String): ResolvedWatchlist {
        val normalizedKey = requestedKey.trim()
        require(normalizedKey.isNotEmpty()) { "watchlistKey is required." }

        val resolvedKey = indexConstituentHandler.read { dao ->
            dao.listUniqueIndices()
                .firstOrNull { summary -> summary.indexKey.equals(normalizedKey, ignoreCase = true) }
                ?.indexKey
        } ?: throw IllegalArgumentException("Unknown watchlist: $requestedKey")

        val members = indexConstituentHandler.read { dao -> dao.listActiveByIndex(resolvedKey) }
            .distinctBy { member -> member.instrumentToken }
        require(members.isNotEmpty()) { "Watchlist $resolvedKey has no active stocks." }
        return ResolvedWatchlist(resolvedKey, members)
    }

    private suspend fun loadTradingDates(
        tradeDate: LocalDate,
        requiredSessions: Int,
    ): List<LocalDate> {
        val dates = stockDeliveryHandler.read { dao ->
            dao.findTradingDatesBetween(
                fromDate = tradeDate.minusDays(TRADING_DATE_LOOKBACK_CALENDAR_DAYS),
                toDate = tradeDate,
            )
        }
        if (dates.isEmpty() || !dates.contains(tradeDate)) {
            throw IllegalArgumentException("No delivery data is available for $tradeDate.")
        }
        return dates.takeLast(requiredSessions)
    }

    private suspend fun loadDeliveryHistoryByToken(
        tokens: List<Long>,
        fromDate: LocalDate,
        toDate: LocalDate,
    ): Map<Long, List<StockDeliveryDaily>> {
        if (tokens.isEmpty()) {
            return emptyMap()
        }
        return stockDeliveryHandler.read { dao ->
            dao.findByInstrumentTokensBetweenDates(
                instrumentTokens = tokens,
                fromDate = fromDate,
                toDate = toDate,
            )
        }.groupBy { row -> row.instrumentToken }
            .mapValues { (_, rows) -> rows.sortedBy { row -> row.tradingDate } }
    }

    private suspend fun loadCandlesBySymbol(
        events: List<DeliveryBreakoutEvent>,
        tradeDate: LocalDate,
    ): Map<String, List<DailyCandle>> = coroutineScope {
        val semaphore = Semaphore(MAX_PARALLEL_SYMBOL_LOADS)
        events.map { event -> event.symbol to event.instrumentToken }
            .distinct()
            .map { (symbol, token) ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        symbol to candleCacheService.getDailyCandles(
                            token = token,
                            symbol = symbol,
                            from = tradeDate.minusDays(CANDLE_HISTORY_CALENDAR_DAYS),
                            to = tradeDate,
                        ).sortedBy { candle -> candle.candleDate }
                    }
                }
            }.awaitAll().toMap()
    }

    private fun buildDashboardRow(
        event: DeliveryBreakoutEvent,
        candles: List<DailyCandle>,
    ): DeliveryBreakoutDashboardRow {
        val eventDate = LocalDate.parse(event.eventDate)
        val candleIndex = candles.indexOfFirst { candle -> candle.candleDate == eventDate }
        val close = candles.getOrNull(candleIndex)?.close?.roundTo2()
        val prevClose = if (candleIndex > 0) candles[candleIndex - 1].close.roundTo2() else null

        return DeliveryBreakoutDashboardRow(
            symbol = event.symbol,
            instrument_token = event.instrumentToken,
            event_date = event.eventDate,
            event_type = event.eventType,
            close = close,
            prev_close = prevClose,
            close_pct_change = DeliveryBreakoutAnalyzer.calculatePctChange(candles, eventDate),
            fifty_two_week_high = candles.maxOfOrNull { candle -> candle.high }?.roundTo2(),
            fifty_two_week_low = candles.minOfOrNull { candle -> candle.low }?.roundTo2(),
            volume = event.volume,
            delivery_quantity = event.deliveryQuantity,
            delivery_percentage = event.deliveryPercentage,
            average_volume_10d = event.averageVolume10d?.roundTo2(),
            average_delivery_quantity_10d = event.averageDeliveryQuantity10d?.roundTo2(),
            volume_ratio = event.volumeRatio,
            delivery_ratio = event.deliveryRatio,
        )
    }

    private fun eventPriority(eventType: String): Int = when (eventType) {
        EVENT_BOTH -> 0
        EVENT_DELIVERY_ONLY -> 1
        else -> 2
    }

    private data class ResolvedWatchlist(
        val key: String,
        val members: List<IndexConstituentUpsertRow>,
    )

    private companion object {
        private const val EVENT_BOTH = "BOTH"
        private const val EVENT_DELIVERY_ONLY = "DELIVERY_ONLY"
        private const val EVENT_VOLUME_ONLY = "VOLUME_ONLY"
        private const val TRADING_DATE_LOOKBACK_CALENDAR_DAYS = 90L
        private const val CANDLE_HISTORY_CALENDAR_DAYS = 370L
        private const val MAX_PARALLEL_SYMBOL_LOADS = 16
    }
}
