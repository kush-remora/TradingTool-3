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
    private val etfService: DeliveryBreakoutEtfService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun getDashboard(requestedTradeDate: LocalDate?): DeliveryBreakoutDashboardResponse {
        val tradeDate = requestedTradeDate ?: stockDeliveryHandler.read { dao -> dao.getLatestTradingDate() }
        requireNotNull(tradeDate) { "No stock delivery data available." }
        val config = configService.loadConfig()

        val currentRows = stockDeliveryHandler.read { dao -> dao.findAllByTradingDate(tradeDate) }
        val nonEtfRows = etfService.filterNonEtfRows(currentRows)
        val excludedEtfCount = currentRows.size - nonEtfRows.size
        if (excludedEtfCount > 0) {
            log.info("Delivery breakout excluded {} ETF rows for {}", excludedEtfCount, tradeDate)
        }

        val liquidityEligibleRows = nonEtfRows.filter { row ->
            val volume = row.ttlTrdQnty
            val deliveryQuantity = row.delivQty
            volume != null && volume >= config.minCurrentVolume && deliveryQuantity != null
        }

        if (liquidityEligibleRows.isEmpty()) {
            return DeliveryBreakoutDashboardResponse(
                meta = DeliveryBreakoutDashboardMeta(
                    trade_date = tradeDate.toString(),
                    scanned_count = nonEtfRows.size,
                    liquidity_eligible_count = 0,
                    shortlisted_count = 0,
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
                    scanned_count = nonEtfRows.size,
                    liquidity_eligible_count = liquidityEligibleRows.size,
                    shortlisted_count = 0,
                ),
                rows = emptyList(),
            )
        }

        val candlesBySymbol = loadCandlesBySymbol(stage1Candidates, tradeDate)
        val dashboardRows = stage1Candidates.mapNotNull { candidate ->
            val candles = candlesBySymbol[candidate.symbol].orEmpty()
            val deliveries = buildDeliverySeries(
                history = deliveryHistoryByToken[candidate.instrumentToken].orEmpty(),
                currentRow = requireNotNull(currentRowsByToken[candidate.instrumentToken]),
            )
            val candleIndex = candles.indexOfFirst { candle -> candle.candleDate == tradeDate }
            val close = candles.getOrNull(candleIndex)?.close?.roundTo2()
            val prevClose = if (candleIndex > 0) candles[candleIndex - 1].close.roundTo2() else null
            val closePctChange = DeliveryBreakoutAnalyzer.calculatePctChange(candles, tradeDate)

            DeliveryBreakoutDashboardRow(
                symbol = candidate.symbol,
                trade_date = candidate.tradeDate,
                close = close,
                prev_close = prevClose,
                close_pct_change = closePctChange,
                volume = candidate.volume,
                delivery_quantity = candidate.deliveryQuantity,
                delivery_percentage = candidate.deliveryPercentage,
                prev_volume = candidate.prevVolume,
                prev_delivery_quantity = candidate.prevDeliveryQuantity,
                volume_ratio = candidate.volumeRatio,
                delivery_ratio = candidate.deliveryRatio,
            )
        }.sortedWith(
            compareByDescending<DeliveryBreakoutDashboardRow> { row -> row.delivery_ratio }
                .thenByDescending { row -> row.volume_ratio },
        )

        return DeliveryBreakoutDashboardResponse(
            meta = DeliveryBreakoutDashboardMeta(
                trade_date = tradeDate.toString(),
                scanned_count = nonEtfRows.size,
                liquidity_eligible_count = liquidityEligibleRows.size,
                shortlisted_count = dashboardRows.size,
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
        private const val DELIVERY_HISTORY_CALENDAR_DAYS = 15L
        private const val CANDLE_HISTORY_CALENDAR_DAYS = 10L
        private const val MAX_PARALLEL_SYMBOL_LOADS = 16
    }
}
