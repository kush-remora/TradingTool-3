package com.tradingtool.core.strategy.phasedbreakout

import com.tradingtool.core.candle.dao.CandleReadDao
import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.database.StockDeliveryJdbiHandler
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.kite.InstrumentTokenResolverService
import java.time.LocalDate

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
                convictionDays10d = null,
                convictionDays20d = null,
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
                convictionDays10d = null,
                convictionDays20d = null,
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
            convictionDays10d = metrics.convictionDays10d,
            convictionDays20d = metrics.convictionDays20d,
        )
    }
}
