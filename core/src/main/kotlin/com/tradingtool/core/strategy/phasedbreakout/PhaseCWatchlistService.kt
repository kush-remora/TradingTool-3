package com.tradingtool.core.strategy.phasedbreakout

import com.tradingtool.core.candle.dao.CandleReadDao
import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.database.StockDeliveryJdbiHandler
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.kite.InstrumentTokenResolverService
import java.time.LocalDate

private const val WAKE_UP_VOLUME_RATIO_THRESHOLD = 2.0

class PhaseCWatchlistService(
    private val watchlistHandler: PhaseCWatchlistJdbiHandler,
    private val stockDeliveryHandler: StockDeliveryJdbiHandler,
    private val candleHandler: CandleJdbiHandler,
    private val candleDataService: com.tradingtool.core.screener.CandleDataService,
    private val kiteClient: KiteConnectClient,
    private val instrumentTokenResolver: InstrumentTokenResolverService,
) {
    private val phase2Config = Phase2DeliveryConfig()

    suspend fun uploadChartinkCsv(request: PhaseCWatchlistUploadRequest): PhaseCWatchlistUploadResponse {
        val today = LocalDate.now()
        val rows = request.rows.map { dto ->
            val token = instrumentTokenResolver.resolve("NSE", dto.symbol)

            PhaseCWatchlistRow(
                symbol = dto.symbol,
                instrumentToken = token,
                addedOn = today,
                lastSeenOn = today,
                status = "chartinkFilter",
                stockName = dto.stockName,
                marketCapBucket = dto.marketCapBucket,
                closePrice = dto.closePrice,
                pctChange = dto.pctChange,
                volume = dto.volume,
                previousDayVolume = null,
                sector = dto.sector,
                industry = dto.industry,
                rocePct = dto.rocePct,
                ronwPct = dto.ronwPct,
                netProfitAfterTax = dto.netProfitAfterTax,
                debtEquityRatio = dto.debtEquityRatio,
                volDry200dMinCount = dto.volDry200dMinCount,
                volDry60dMinCount = dto.volDry60dMinCount,
                volDry200dMin105Count = dto.volDry200dMin105Count,
                volDry60dMin105Count = dto.volDry60dMin105Count,
                indianPromoterPct = dto.indianPromoterPct,
                foreignPromoterPct = dto.foreignPromoterPct,
                quarterlyGrossSales = dto.quarterlyGrossSales,
                high52w = dto.high52w,
                low52w = dto.low52w,
                dist200dHighPct = dto.dist200dHighPct,
                dist200dLowPct = dto.dist200dLowPct,
                atrLt2pctCount = dto.atrLt2pctCount,
                marketFieldsUpdatedOn = null,
                phase2DeliveryStatus = "NOT_RUN",
                phase2Reason = "awaiting_delivery_validation",
                phase2EvaluatedOn = null,
                deliveryQuantityToday = null,
                deliveryPctToday = null,
                wholesaleBaseDq = null,
                deliverySpikeRatio = null,
                deliverySpikeDays10d = null,
                deliverySpikeDays20d = null,
                deliverySupportDays10d = null,
                deliverySupportDays20d = null,
            )
        }

        if (rows.isEmpty()) {
            return PhaseCWatchlistUploadResponse(0, 0)
        }

        val results = watchlistHandler.transaction { _, writeDao ->
            writeDao.upsertBatch(rows)
        }

        val insertedOrUpdated = results.count { it > 0 }

        return PhaseCWatchlistUploadResponse(
            insertedCount = insertedOrUpdated,
            updatedCount = 0
        )
    }

    suspend fun getAllWatchlist(): List<PhaseCWatchlistRow> {
        return watchlistHandler.transaction { readDao, _ ->
            readDao.findAll()
        }
    }

    suspend fun refreshFreshFields(): PhaseCFreshFieldRefreshResponse {
        val watchlist = getAllWatchlist()
        if (watchlist.isEmpty()) {
            return PhaseCFreshFieldRefreshResponse(
                refreshedCount = 0,
                refreshedOn = null,
            )
        }

        val missingTokenSymbols = watchlist
            .filter { row -> row.instrumentToken == null }
            .map { row -> row.symbol }
            .sorted()
        require(missingTokenSymbols.isEmpty()) {
            "Cannot refresh fresh fields. Missing instrument token for: ${missingTokenSymbols.joinToString(", ")}"
        }

        val refreshFromDate = LocalDate.now().minusDays(400)
        val refreshToDate = LocalDate.now()
        val symbols = watchlist.map { row -> row.symbol }.distinct()
        val syncResult = candleDataService.syncDailyRange(
            symbols = symbols,
            fromDate = refreshFromDate,
            toDate = refreshToDate,
            kiteClient = kiteClient,
        )
        require(syncResult.symbolsFailed == 0) {
            "Failed to sync daily candles for: ${syncResult.failedSymbols.joinToString(", ")}"
        }

        val updates = watchlist.map { row ->
            val instrumentToken = requireNotNull(row.instrumentToken)
            val candles = candleHandler.read { dao: CandleReadDao ->
                dao.getDailyCandles(
                    token = instrumentToken,
                    from = refreshFromDate,
                    to = refreshToDate,
                )
            }
            val snapshot = try {
                PhaseCFreshFieldCalculator.calculate(candles)
            } catch (error: IllegalArgumentException) {
                throw IllegalStateException("Cannot refresh ${row.symbol}: ${error.message}")
            } catch (error: IllegalStateException) {
                throw IllegalStateException("Cannot refresh ${row.symbol}: ${error.message}")
            }

            PhaseCFreshFieldUpdate(
                symbol = row.symbol,
                closePrice = snapshot.closePrice,
                pctChange = snapshot.pctChange,
                volume = snapshot.volume,
                previousDayVolume = snapshot.previousDayVolume,
                high52w = snapshot.high52w,
                low52w = snapshot.low52w,
                dist200dHighPct = snapshot.dist200dHighPct,
                dist200dLowPct = snapshot.dist200dLowPct,
                marketFieldsUpdatedOn = snapshot.marketFieldsUpdatedOn,
            )
        }

        watchlistHandler.write { writeDao ->
            writeDao.updateFreshFields(updates)
        }

        return PhaseCFreshFieldRefreshResponse(
            refreshedCount = updates.size,
            refreshedOn = updates.maxOfOrNull { update -> update.marketFieldsUpdatedOn },
        )
    }

    suspend fun runDeliveryValidation(): Phase2DeliveryValidationRunResponse {
        val evaluationDate = stockDeliveryHandler.read { dao -> dao.getLatestTradingDate() }
            ?: return Phase2DeliveryValidationRunResponse(
                evaluatedOn = null,
                totalStocks = 0,
                passed = 0,
                watch = 0,
                notPassed = 0,
                notRun = 0,
                dataMissing = 0,
            )

        val watchlist = getAllWatchlist()
        if (watchlist.isEmpty()) {
            return Phase2DeliveryValidationRunResponse(
                evaluatedOn = evaluationDate,
                totalStocks = 0,
                passed = 0,
                watch = 0,
                notPassed = 0,
                notRun = 0,
                dataMissing = 0,
            )
        }

        val updates = watchlist.map { row -> buildPhase2Update(row, evaluationDate) }
        watchlistHandler.transaction { _, writeDao ->
            writeDao.updatePhase2Metrics(updates)
        }

        return Phase2DeliveryValidationRunResponse(
            evaluatedOn = evaluationDate,
            totalStocks = updates.size,
            passed = updates.count { update -> update.phase2DeliveryStatus == "PASSED" },
            watch = updates.count { update -> update.phase2DeliveryStatus == "WATCH" },
            notPassed = updates.count { update -> update.phase2DeliveryStatus == "NOT_PASSED" },
            notRun = updates.count { update -> update.phase2DeliveryStatus == "NOT_RUN" },
            dataMissing = updates.count { update -> update.phase2DeliveryStatus == "DATA_MISSING" },
        )
    }

    private suspend fun buildPhase2Update(
        row: PhaseCWatchlistRow,
        evaluationDate: LocalDate,
    ): Phase2DeliveryUpdate {
        val instrumentToken = row.instrumentToken
        if (instrumentToken == null) {
            return Phase2DeliveryUpdate(
                symbol = row.symbol,
                phase2DeliveryStatus = "DATA_MISSING",
                phase2Reason = "missing_instrument_token",
                phase2EvaluatedOn = evaluationDate,
                deliveryQuantityToday = null,
                deliveryPctToday = null,
                wholesaleBaseDq = null,
                deliverySpikeRatio = null,
                deliverySpikeDays10d = null,
                deliverySpikeDays20d = null,
                deliverySupportDays10d = null,
                deliverySupportDays20d = null,
            )
        }

        val fromDate = evaluationDate.minusDays(120)
        val deliveries = stockDeliveryHandler.read { dao ->
            dao.findByInstrumentTokenBetweenDates(
                instrumentToken = instrumentToken,
                fromDate = fromDate,
                toDate = evaluationDate,
            )
        }
        val candles = candleHandler.read { dao: CandleReadDao ->
            dao.getDailyCandles(
                token = instrumentToken,
                from = fromDate,
                to = evaluationDate,
            )
        }
        val metrics = PhaseCDeliveryValidationAnalyzer.evaluate(
            evaluationDate = evaluationDate,
            deliveries = deliveries,
            candles = candles,
            config = phase2Config,
        )

        return Phase2DeliveryUpdate(
            symbol = row.symbol,
            phase2DeliveryStatus = metrics.status,
            phase2Reason = metrics.reason,
            phase2EvaluatedOn = metrics.evaluatedOn,
            deliveryQuantityToday = metrics.deliveryQuantityToday,
            deliveryPctToday = metrics.deliveryPctToday,
            wholesaleBaseDq = metrics.wholesaleBaseDq,
            deliverySpikeRatio = metrics.deliverySpikeRatio,
            deliverySpikeDays10d = metrics.deliverySpikeDays10d,
            deliverySpikeDays20d = metrics.deliverySpikeDays20d,
            deliverySupportDays10d = metrics.deliverySupportDays10d,
            deliverySupportDays20d = metrics.deliverySupportDays20d,
        )
    }

    suspend fun getExportData(): PhaseCExportResponse {
        val watchlist = getAllWatchlist()
        val latestTradingDate = stockDeliveryHandler.read { dao -> dao.getLatestTradingDate() } ?: LocalDate.now()
        val fromDate = latestTradingDate.minusDays(30) // Buffer to ensure we get 10 trading days

        val stocks = watchlist.map { row ->
            val token = row.instrumentToken
            val history = if (token != null) {
                val deliveries = stockDeliveryHandler.read { it.findByInstrumentTokenBetweenDates(token, fromDate, latestTradingDate) }
                val candles = candleHandler.read { it.getDailyCandles(token, fromDate, latestTradingDate) }

                val deliveriesByDate = deliveries.associateBy { it.tradingDate }
                candles.map { candle ->
                    val delivery = deliveriesByDate[candle.candleDate]
                    PhaseCHistoryRow(
                        date = candle.candleDate,
                        open = candle.open,
                        high = candle.high,
                        low = candle.low,
                        close = candle.close,
                        volume = candle.volume,
                        deliveryQuantity = delivery?.delivQty,
                        deliveryPct = delivery?.delivPer
                    )
                }.sortedByDescending { it.date }.take(10)
            } else {
                emptyList()
            }

            PhaseCExportStockData(
                profile = row,
                history10d = history,
                wakeUpVolume = buildWakeUpVolumeExport(row),
            )
        }

        val schema = mapOf(
            "symbol" to "The NSE stock ticker symbol.",
            "instrumentToken" to "The unique Kite instrument token for the stock.",
            "addedOn" to "The date the stock was added to the watchlist.",
            "lastSeenOn" to "The date the stock was last seen on the Chartink screener.",
            "status" to "The current status of the stock in the Phase C workflow.",
            "stockName" to "The full company name.",
            "marketCapBucket" to "The market capitalization category (e.g., Large Cap, Mid Cap).",
            "closePrice" to "The latest daily closing price.",
            "pctChange" to "The recent percentage change in price.",
            "volume" to "The latest daily trading volume.",
            "previousDayVolume" to "The previous trading day's volume used as the T-1 comparison for Wake-Up checks.",
            "sector" to "The sector the company belongs to.",
            "industry" to "The industry the company operates in.",
            "rocePct" to "Return on Capital Employed percentage.",
            "ronwPct" to "Return on Net Worth percentage.",
            "netProfitAfterTax" to "Net Profit After Tax (in Crores).",
            "debtEquityRatio" to "The Debt to Equity ratio.",
            "volDry200dMinCount" to "Number of times volume hit a 200-day minimum.",
            "volDry60dMinCount" to "Number of times volume hit a 60-day minimum.",
            "volDry200dMin105Count" to "Number of times volume was within 105% of the 200-day minimum.",
            "volDry60dMin105Count" to "Number of times volume was within 105% of the 60-day minimum.",
            "indianPromoterPct" to "Percentage of shares held by Indian promoters.",
            "foreignPromoterPct" to "Percentage of shares held by Foreign promoters.",
            "quarterlyGrossSales" to "Quarterly gross sales (in Crores).",
            "high52w" to "52-week high price.",
            "low52w" to "52-week low price.",
            "dist200dHighPct" to "Percentage distance from the 200-day high.",
            "dist200dLowPct" to "Percentage distance from the 200-day low.",
            "atrLt2pctCount" to "Number of times the Average True Range was less than 2% of price.",
            "marketFieldsUpdatedOn" to "The date the market fields were last updated.",
            "phase2DeliveryStatus" to "The validation status for Phase 2 Delivery constraints (PASSED, WATCH, NOT_PASSED, DATA_MISSING).",
            "phase2Reason" to "The specific reason for the current Phase 2 Delivery status.",
            "phase2EvaluatedOn" to "The date when Phase 2 Delivery was last evaluated.",
            "deliveryQuantityToday" to "The delivery quantity on the evaluated date.",
            "deliveryPctToday" to "The delivery percentage on the evaluated date.",
            "wholesaleBaseDq" to "The wholesale base delivery quantity calculated for the stock.",
            "deliverySpikeRatio" to "The ratio of today's delivery quantity vs the wholesale base delivery quantity.",
            "deliverySpikeDays10d" to "Number of days in the last 10 trading days where delivery quantity met or exceeded the active base delivery quantity.",
            "deliverySpikeDays20d" to "Number of days in the last 20 trading days where delivery quantity met or exceeded the active base delivery quantity.",
            "deliverySupportDays10d" to "Number of days in the last 10 trading days where delivery percentage was >= 55%.",
            "deliverySupportDays20d" to "Number of days in the last 20 trading days where delivery percentage was >= 55%.",
            "wakeUpVolume.latestDayVolume" to "The latest completed trading day's volume used as the current-day value in AI export.",
            "wakeUpVolume.previousDayVolume" to "The prior trading day's volume used as the T-1 comparison in AI export.",
            "wakeUpVolume.volumeRatioVsPreviousDay" to "The ratio of latest completed day volume divided by the previous trading day's volume.",
            "wakeUpVolume.volumeIs2xOrMore" to "True when the latest completed day volume is at least 2x the previous trading day's volume."
        )

        return PhaseCExportResponse(
            metadataSchema = schema,
            stocks = stocks
        )
    }
}

internal fun buildWakeUpVolumeExport(row: PhaseCWatchlistRow): PhaseCWakeUpVolumeExport? {
    val latestDayVolume = row.volume ?: return null
    val previousDayVolume = row.previousDayVolume ?: return null
    if (previousDayVolume <= 0L) {
        return null
    }

    val volumeRatio = latestDayVolume.toDouble() / previousDayVolume.toDouble()
    return PhaseCWakeUpVolumeExport(
        latestDayVolume = latestDayVolume,
        previousDayVolume = previousDayVolume,
        volumeRatioVsPreviousDay = volumeRatio,
        volumeIs2xOrMore = volumeRatio >= WAKE_UP_VOLUME_RATIO_THRESHOLD,
    )
}
