package com.tradingtool.core.volumeshocker.groww

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.database.GrowwVolumeShockerJdbiHandler
import com.tradingtool.core.database.StockDeliveryJdbiHandler
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import com.tradingtool.core.strategy.wyckoff.phase1.WyckoffPhase1ConfigService
import com.tradingtool.core.strategy.wyckoff.phase1.WyckoffPhase1RunConfig
import com.tradingtool.core.strategy.wyckoff.phase1.WyckoffPhase1ScannerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate
import java.time.ZoneId

@Singleton
class GrowwVolumeShockerDashboardService @Inject constructor(
    private val growwVolumeShockerHandler: GrowwVolumeShockerJdbiHandler,
    private val stockDeliveryHandler: StockDeliveryJdbiHandler,
    private val candleCacheService: CandleCacheService,
    private val wyckoffPhase1ScannerService: WyckoffPhase1ScannerService,
    private val wyckoffPhase1ConfigService: WyckoffPhase1ConfigService,
) {
    suspend fun listAvailableDates(): GrowwVolumeShockerDashboardDatesResponse {
        val availableDates = growwVolumeShockerHandler.read { dao -> dao.listAvailableTradeDates() }
        val today = LocalDate.now(IST_ZONE)
        val defaultDate = availableDates.firstOrNull { date -> date.isBefore(today) } ?: availableDates.firstOrNull()

        return GrowwVolumeShockerDashboardDatesResponse(
            available_dates = availableDates.map { date -> date.toString() },
            default_date = defaultDate?.toString(),
        )
    }

    suspend fun getDashboard(tradeDate: LocalDate): GrowwVolumeShockerDashboardResponse {
        val rows = growwVolumeShockerHandler.read { dao -> dao.findByTradeDate(tradeDate) }
        require(rows.isNotEmpty()) { "No volume-shocker rows found for $tradeDate." }

        val lookbackDatesDescending = loadLookbackDates(tradeDate)
        val appearancesBySymbol = loadAppearanceDatesBySymbol(lookbackDatesDescending)
        val phase1TaggedSymbols = loadPhase1TaggedSymbols(tradeDate, rows.map { row -> row.symbol })

        val historyFromDate = tradeDate.minusDays(DASHBOARD_HISTORY_CALENDAR_DAYS)
        val candlesBySymbol = loadCandlesBySymbol(rows, historyFromDate, tradeDate)
        val deliveriesByToken = loadDeliveriesByToken(rows, historyFromDate, tradeDate)

        val dashboardRows = rows.map { row ->
            val candles = candlesBySymbol[row.symbol].orEmpty()
            val deliveries = deliveriesByToken[row.instrumentToken].orEmpty()
            val eventDelivery = deliveries.firstOrNull { delivery -> delivery.tradingDate == tradeDate }
            val preEventMetrics = GrowwVolumeShockerDashboardAnalyzer.calculatePreEventMetrics(candles, deliveries, tradeDate)
            val maxBeforeEventDelivery = preEventMetrics.maxDeliveryVolume
            val todayDeliveryVolume = eventDelivery?.delivQty

            GrowwVolumeShockerDashboardRow(
                source_rank = row.sourceRank,
                symbol = row.symbol,
                company_name = row.companyName,
                ltp = row.ltp.toDouble(),
                volume = row.volume,
                delivery_volume = todayDeliveryVolume,
                delivery_pct = eventDelivery?.delivPer,
                max_delivery_volume_10d_before_event = maxBeforeEventDelivery,
                delivery_volume_vs_max_10d_before_event_ratio = calculateRatio(todayDeliveryVolume, maxBeforeEventDelivery),
                appearance_count_10d = GrowwVolumeShockerDashboardAnalyzer.calculateAppearanceCount(
                    lookbackDates = lookbackDatesDescending.asReversed(),
                    appearedDates = appearancesBySymbol[row.symbol].orEmpty(),
                ),
                streak_length_10d = GrowwVolumeShockerDashboardAnalyzer.calculateStreakLength(
                    lookbackDatesDescending = lookbackDatesDescending,
                    appearedDates = appearancesBySymbol[row.symbol].orEmpty(),
                ),
                sma200_price = GrowwVolumeShockerDashboardAnalyzer.calculateSma200Price(candles, tradeDate),
                distance_from_sma200_pct = GrowwVolumeShockerDashboardAnalyzer.calculateDistancePct(
                    price = row.ltp.toDouble(),
                    referencePrice = GrowwVolumeShockerDashboardAnalyzer.calculateSma200Price(candles, tradeDate),
                ),
                pre_event_accumulation_hint = preEventMetrics.hasAccumulationHint,
                tag = if (phase1TaggedSymbols.contains(row.symbol)) WYCKOFF_PHASE_D_TAG else NO_TAG,
            )
        }

        return GrowwVolumeShockerDashboardResponse(
            trade_date = tradeDate.toString(),
            rows = dashboardRows,
        )
    }

    suspend fun getDetail(
        tradeDate: LocalDate,
        symbol: String,
    ): GrowwVolumeShockerDashboardDetailResponse {
        val normalizedSymbol = symbol.trim().uppercase()
        val row = growwVolumeShockerHandler.read { dao ->
            dao.findByTradeDateAndSymbol(tradeDate = tradeDate, symbol = normalizedSymbol)
        } ?: throw IllegalArgumentException("No volume-shocker row found for $normalizedSymbol on $tradeDate.")

        val lookbackDatesDescending = loadLookbackDates(tradeDate)
        val appearancesBySymbol = loadAppearanceDatesBySymbol(lookbackDatesDescending)

        val historyFromDate = tradeDate.minusDays(DETAIL_HISTORY_LOOKBACK_CALENDAR_DAYS)
        val historyToDate = tradeDate.plusDays(DETAIL_HISTORY_FORWARD_CALENDAR_DAYS)
        val candles = loadCandles(row.symbol, row.instrumentToken, historyFromDate, historyToDate)
        val deliveries = loadDeliveries(row.instrumentToken, historyFromDate, historyToDate)
        val preEventMetrics = GrowwVolumeShockerDashboardAnalyzer.calculatePreEventMetrics(candles, deliveries, tradeDate)
        val eventDelivery = deliveries.firstOrNull { delivery -> delivery.tradingDate == tradeDate }

        return GrowwVolumeShockerDashboardDetailResponse(
            symbol = normalizedSymbol,
            trade_date = tradeDate.toString(),
            summary = GrowwVolumeShockerDashboardDetailSummary(
                appearance_count_10d = GrowwVolumeShockerDashboardAnalyzer.calculateAppearanceCount(
                    lookbackDates = lookbackDatesDescending.asReversed(),
                    appearedDates = appearancesBySymbol[normalizedSymbol].orEmpty(),
                ),
                streak_length_10d = GrowwVolumeShockerDashboardAnalyzer.calculateStreakLength(
                    lookbackDatesDescending = lookbackDatesDescending,
                    appearedDates = appearancesBySymbol[normalizedSymbol].orEmpty(),
                ),
                max_delivery_volume_10d_before_event = preEventMetrics.maxDeliveryVolume,
                delivery_volume_vs_max_10d_before_event_ratio = calculateRatio(
                    numerator = eventDelivery?.delivQty,
                    denominator = preEventMetrics.maxDeliveryVolume,
                ),
            ),
            days = GrowwVolumeShockerDashboardAnalyzer.buildDetailWindow(
                candles = candles,
                deliveries = deliveries,
                eventDate = tradeDate,
            ),
        )
    }

    private suspend fun loadLookbackDates(tradeDate: LocalDate): List<LocalDate> {
        return growwVolumeShockerHandler.read { dao -> dao.listAvailableTradeDates() }
            .filter { date -> !date.isAfter(tradeDate) }
            .take(REPEAT_LOOKBACK_TRADING_DAYS)
    }

    private suspend fun loadAppearanceDatesBySymbol(lookbackDatesDescending: List<LocalDate>): Map<String, Set<LocalDate>> {
        if (lookbackDatesDescending.isEmpty()) {
            return emptyMap()
        }
        return growwVolumeShockerHandler.read { dao -> dao.findByTradeDates(lookbackDatesDescending) }
            .groupBy { row -> row.symbol }
            .mapValues { (_, rows) -> rows.map { row -> row.tradeDate }.toSet() }
    }

    private suspend fun loadPhase1TaggedSymbols(
        tradeDate: LocalDate,
        symbols: List<String>,
    ): Set<String> {
        if (symbols.isEmpty()) {
            return emptySet()
        }
        val response = wyckoffPhase1ScannerService.run(
            runConfig = WyckoffPhase1RunConfig(
                universeKeys = emptyList(),
                symbols = symbols,
                asOfDate = tradeDate,
                applyStrictBaseFilter = false,
            ),
            config = wyckoffPhase1ConfigService.loadPhase1Config(),
        )
        return response.rows.map { row -> row.symbol }.toSet()
    }

    private suspend fun loadCandlesBySymbol(
        rows: List<GrowwVolumeShockerDailyRow>,
        fromDate: LocalDate,
        toDate: LocalDate,
    ): Map<String, List<DailyCandle>> = coroutineScope {
        val semaphore = Semaphore(MAX_PARALLEL_SYMBOL_LOADS)
        rows.map { row ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    row.symbol to loadCandles(
                        symbol = row.symbol,
                        instrumentToken = row.instrumentToken,
                        fromDate = fromDate,
                        toDate = toDate,
                    )
                }
            }
        }.awaitAll().toMap()
    }

    private suspend fun loadDeliveriesByToken(
        rows: List<GrowwVolumeShockerDailyRow>,
        fromDate: LocalDate,
        toDate: LocalDate,
    ): Map<Long, List<StockDeliveryDaily>> {
        val instrumentTokens = rows.map { row -> row.instrumentToken }.distinct()
        if (instrumentTokens.isEmpty()) {
            return emptyMap()
        }

        return stockDeliveryHandler.read { dao ->
            dao.findByInstrumentTokensBetweenDates(
                instrumentTokens = instrumentTokens,
                fromDate = fromDate,
                toDate = toDate,
            )
        }.groupBy { row -> row.instrumentToken }
    }

    private suspend fun loadCandles(
        symbol: String,
        instrumentToken: Long,
        fromDate: LocalDate,
        toDate: LocalDate,
    ): List<DailyCandle> {
        return candleCacheService
            .getDailyCandles(
                token = instrumentToken,
                symbol = symbol,
                from = fromDate,
                to = toDate,
            )
            .sortedBy { candle -> candle.candleDate }
    }

    private suspend fun loadDeliveries(
        instrumentToken: Long,
        fromDate: LocalDate,
        toDate: LocalDate,
    ): List<StockDeliveryDaily> {
        return stockDeliveryHandler.read { dao ->
            dao.findByInstrumentTokenBetweenDates(
                instrumentToken = instrumentToken,
                fromDate = fromDate,
                toDate = toDate,
            )
        }
    }

    private fun calculateRatio(numerator: Long?, denominator: Long?): Double? {
        if (numerator == null || denominator == null || denominator == 0L) {
            return null
        }
        return numerator.toDouble() / denominator.toDouble()
    }

    companion object {
        private val IST_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
        private const val REPEAT_LOOKBACK_TRADING_DAYS = 10
        private const val DASHBOARD_HISTORY_CALENDAR_DAYS = 320L
        private const val DETAIL_HISTORY_LOOKBACK_CALENDAR_DAYS = 30L
        private const val DETAIL_HISTORY_FORWARD_CALENDAR_DAYS = 30L
        private const val MAX_PARALLEL_SYMBOL_LOADS = 16
        private const val WYCKOFF_PHASE_D_TAG = "WYCKOFF_PHASE_D"
        private const val NO_TAG = "NO_TAG"
    }
}
