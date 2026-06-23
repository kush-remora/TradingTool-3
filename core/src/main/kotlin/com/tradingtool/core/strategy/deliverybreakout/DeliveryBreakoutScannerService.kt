package com.tradingtool.core.strategy.deliverybreakout

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.database.StockDeliveryJdbiHandler
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.screener.CandleDataService
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
    private val stockDeliveryHandler: StockDeliveryJdbiHandler,
    private val candleCacheService: CandleCacheService,
    private val candleDataService: CandleDataService,
    private val kiteClient: KiteConnectClient,
    private val configService: DeliveryBreakoutConfigService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun getDashboard(requestedTradeDate: LocalDate?): DeliveryBreakoutDashboardResponse {
        val tradeDate = requestedTradeDate ?: stockDeliveryHandler.read { dao -> dao.getLatestTradingDate() }
        requireNotNull(tradeDate) { "No stock delivery data available." }
        val config = configService.loadConfig()

        val currentRows = stockDeliveryHandler.read { dao -> dao.findAllByTradingDate(tradeDate) }
        val liquidityEligibleRows = currentRows.filter { row ->
            val volume = row.ttlTrdQnty
            val deliveryQuantity = row.delivQty
            volume != null && volume >= config.minCurrentVolume && deliveryQuantity != null
        }

        if (liquidityEligibleRows.isEmpty()) {
            return DeliveryBreakoutDashboardResponse(
                meta = DeliveryBreakoutDashboardMeta(
                    trade_date = tradeDate.toString(),
                    scanned_count = currentRows.size,
                    liquidity_eligible_count = 0,
                    shortlisted_count = 0,
                    confirmed_breakout_count = 0,
                    quiet_clue_count = 0,
                ),
                rows = emptyList(),
            )
        }

        val tokens = liquidityEligibleRows.map { row -> row.instrumentToken }.distinct()
        val deliveryHistoryByToken = loadDeliveryHistoryByToken(tokens = tokens, tradeDate = tradeDate)
        val stage1Candidates = liquidityEligibleRows.mapNotNull { row ->
            DeliveryBreakoutAnalyzer.buildStage1Candidate(
                row = row,
                history = deliveryHistoryByToken[row.instrumentToken].orEmpty(),
                config = config,
            )
        }
        val currentRowsByToken = liquidityEligibleRows.associateBy { row -> row.instrumentToken }

        if (stage1Candidates.isEmpty()) {
            return DeliveryBreakoutDashboardResponse(
                meta = DeliveryBreakoutDashboardMeta(
                    trade_date = tradeDate.toString(),
                    scanned_count = currentRows.size,
                    liquidity_eligible_count = liquidityEligibleRows.size,
                    shortlisted_count = 0,
                    confirmed_breakout_count = 0,
                    quiet_clue_count = 0,
                ),
                rows = emptyList(),
            )
        }

        val candlesBySymbol = loadCandlesBySymbol(stage1Candidates, tradeDate)
        val dashboardRows = stage1Candidates.map { candidate ->
            val candles = candlesBySymbol[candidate.symbol].orEmpty()
            val deliveries = buildDeliverySeries(
                history = deliveryHistoryByToken[candidate.instrumentToken].orEmpty(),
                currentRow = requireNotNull(currentRowsByToken[candidate.instrumentToken]),
            )
            val close = candles.firstOrNull { candle -> candle.candleDate == tradeDate }?.close?.roundTo2()
            val closePctChange = DeliveryBreakoutAnalyzer.calculatePctChange(candles, tradeDate)
            val quietClueDay = DeliveryBreakoutAnalyzer.resolveQuietClueDay(
                deliveries = deliveries,
                candles = candles,
                tradeDate = tradeDate,
                config = config,
            )
            val hasQuietClue = quietClueDay != null
            val isConfirmedBreakoutToday = DeliveryBreakoutAnalyzer.isConfirmedBreakoutToday(closePctChange)
            val sma200 = DeliveryBreakoutAnalyzer.calculateSma200(candles, tradeDate)
            val distanceFromSma200Pct = DeliveryBreakoutAnalyzer.calculateDistanceFromSma200(close, sma200)
            val isNearSma200 = DeliveryBreakoutAnalyzer.isNearSma200(distanceFromSma200Pct)

            DeliveryBreakoutDashboardRow(
                symbol = candidate.symbol,
                trade_date = candidate.tradeDate,
                close = close,
                close_pct_change = closePctChange,
                volume = candidate.volume,
                delivery_quantity = candidate.deliveryQuantity,
                delivery_percentage = candidate.deliveryPercentage,
                prior_10d_max_volume = candidate.prior10dMaxVolume,
                prior_10d_max_delivery_quantity = candidate.prior10dMaxDeliveryQuantity,
                volume_ratio_vs_10d_max = candidate.volumeRatioVs10dMax,
                delivery_ratio_vs_10d_max = candidate.deliveryRatioVs10dMax,
                has_quiet_clue = hasQuietClue,
                quiet_clue_day = quietClueDay?.toString(),
                is_confirmed_breakout_today = isConfirmedBreakoutToday,
                sma200 = sma200,
                distance_from_sma200_pct = distanceFromSma200Pct,
                is_near_200_sma = isNearSma200,
                label = DeliveryBreakoutAnalyzer.resolveLabel(
                    closePctChange = closePctChange,
                    hasQuietClue = hasQuietClue,
                ),
            )
        }.sortedWith(
            compareByDescending<DeliveryBreakoutDashboardRow> { row -> row.is_confirmed_breakout_today }
                .thenByDescending { row -> row.has_quiet_clue }
                .thenByDescending { row -> row.delivery_ratio_vs_10d_max }
                .thenByDescending { row -> row.volume_ratio_vs_10d_max },
        )

        return DeliveryBreakoutDashboardResponse(
            meta = DeliveryBreakoutDashboardMeta(
                trade_date = tradeDate.toString(),
                scanned_count = currentRows.size,
                liquidity_eligible_count = liquidityEligibleRows.size,
                shortlisted_count = dashboardRows.size,
                confirmed_breakout_count = dashboardRows.count { row -> row.is_confirmed_breakout_today },
                quiet_clue_count = dashboardRows.count { row -> row.has_quiet_clue },
            ),
            rows = dashboardRows,
        )
    }

    private suspend fun loadDeliveryHistoryByToken(
        tokens: List<Long>,
        tradeDate: LocalDate,
    ): Map<Long, List<StockDeliveryDaily>> {
        val fromDate = tradeDate.minusDays(DELIVERY_HISTORY_CALENDAR_DAYS)
        return stockDeliveryHandler.read { dao ->
            dao.findByInstrumentTokensBetweenDates(
                instrumentTokens = tokens,
                fromDate = fromDate,
                toDate = tradeDate.minusDays(1),
            )
        }.groupBy { row -> row.instrumentToken }
            .mapValues { (_, rows) -> rows.sortedBy { row -> row.tradingDate } }
    }

    private suspend fun loadCandlesBySymbol(
        candidates: List<DeliveryBreakoutStage1Candidate>,
        tradeDate: LocalDate,
    ): Map<String, List<DailyCandle>> = coroutineScope {
        val semaphore = Semaphore(MAX_PARALLEL_SYMBOL_LOADS)
        val fromDate = tradeDate.minusDays(CANDLE_HISTORY_CALENDAR_DAYS)

        candidates.map { candidate ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    candidate.symbol to loadCandlesForCandidate(candidate, fromDate, tradeDate)
                }
            }
        }.awaitAll().toMap()
    }

    private suspend fun loadCandlesForCandidate(
        candidate: DeliveryBreakoutStage1Candidate,
        fromDate: LocalDate,
        toDate: LocalDate,
    ): List<DailyCandle> {
        var candles = candleCacheService.getDailyCandles(
            token = candidate.instrumentToken,
            symbol = candidate.symbol,
            from = fromDate,
            to = toDate,
        ).sortedBy { candle -> candle.candleDate }

        val latestCandleDate = candles.lastOrNull()?.candleDate
        if (candles.isEmpty() || latestCandleDate == null || latestCandleDate.isBefore(toDate)) {
            runCatching {
                candleDataService.syncDailyRange(
                    symbols = listOf(candidate.symbol),
                    fromDate = fromDate,
                    toDate = toDate,
                    kiteClient = kiteClient,
                )
            }.onFailure { error ->
                log.warn("Delivery breakout candle backfill failed for {}: {}", candidate.symbol, error.message)
            }
            candles = candleCacheService.getDailyCandles(
                token = candidate.instrumentToken,
                symbol = candidate.symbol,
                from = fromDate,
                to = toDate,
            ).sortedBy { candle -> candle.candleDate }
        }

        return candles
    }

    private fun buildDeliverySeries(
        history: List<StockDeliveryDaily>,
        currentRow: StockDeliveryDaily,
    ): List<StockDeliveryDaily> {
        return (history + currentRow)
            .distinctBy { row -> row.tradingDate }
            .sortedBy { row -> row.tradingDate }
    }

    private companion object {
        private const val DELIVERY_HISTORY_CALENDAR_DAYS = 45L
        private const val CANDLE_HISTORY_CALENDAR_DAYS = 320L
        private const val MAX_PARALLEL_SYMBOL_LOADS = 16
    }
}
