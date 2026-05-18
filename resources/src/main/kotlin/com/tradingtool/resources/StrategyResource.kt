package com.tradingtool.resources

import com.google.inject.Inject
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.core.strategy.s4.S4Service
import com.tradingtool.core.strategy.bollinger.BollingerBacktestRequest
import com.tradingtool.core.strategy.bollinger.BollingerMeanReversionBacktestRequest
import com.tradingtool.core.strategy.bollinger.BollingerMeanReversionBacktestService
import com.tradingtool.core.strategy.bollinger.BollingerSqueezeBacktestService
import com.tradingtool.core.strategy.volume.EarningsFilterMode
import com.tradingtool.core.strategy.volume.VolumeSpikeBacktestRequest
import com.tradingtool.core.strategy.volume.VolumeSpikeBacktestRunConfig
import com.tradingtool.core.strategy.volume.VolumeSpikeBacktestService
import com.tradingtool.core.strategy.deliverythreshold.DeliveryThresholdBacktestRequest
import com.tradingtool.core.strategy.deliverythreshold.DeliveryThresholdBacktestRunConfig
import com.tradingtool.core.strategy.deliverythreshold.DeliveryThresholdBacktestService
import com.tradingtool.core.strategy.profitlookback.ProfitLookbackRequest
import com.tradingtool.core.strategy.profitlookback.ProfitLookbackBulkRequest
import com.tradingtool.core.strategy.profitlookback.ProfitLookbackBulkRowRequest
import com.tradingtool.core.strategy.profitlookback.ProfitLookbackBulkRowResponse
import com.tradingtool.core.strategy.profitlookback.ProfitLookbackBulkResponse
import com.tradingtool.core.strategy.profitlookback.ProfitLookbackService
import com.tradingtool.core.strategy.rsimomentum.BackfillRequest
import com.tradingtool.core.strategy.rsimomentum.BackfillFreshRequest
import com.tradingtool.core.strategy.rsimomentum.BacktestRequest
import com.tradingtool.core.strategy.rsimomentum.MomentumDataPrepareRequest
import com.tradingtool.core.strategy.rsimomentum.MomentumDataPrepService
import com.tradingtool.core.strategy.rsimomentum.RsiMomentumBacktestRequest
import com.tradingtool.core.strategy.rsimomentum.RsiMomentumBackfillService
import com.tradingtool.core.strategy.rsimomentum.RsiMomentumBacktestService
import com.tradingtool.core.strategy.rsimomentum.RsiRankDriftBacktestRequest
import com.tradingtool.core.strategy.rsimomentum.RsiMomentumHistoryService
import com.tradingtool.core.strategy.rsimomentum.RsiMomentumService
import com.tradingtool.core.strategy.rsimomentum.SimpleMomentumBacktestPrepService
import com.tradingtool.core.strategy.rsimomentum.SimpleMomentumBacktestPrepareRequest
import com.tradingtool.core.strategy.rsimomentum.SimpleMomentumBacktestRequest
import com.tradingtool.resources.common.badRequest
import com.tradingtool.resources.common.endpoint
import com.tradingtool.resources.common.ok
import com.tradingtool.resources.common.serviceUnavailable
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.CompletableFuture

@Path("/api/strategy")
@Produces(MediaType.APPLICATION_JSON)
class StrategyResource @Inject constructor(
    private val rsiMomentumService: RsiMomentumService,
    private val rsiMomentumHistoryService: RsiMomentumHistoryService,
    private val rsiMomentumBackfillService: RsiMomentumBackfillService,
    private val rsiMomentumBacktestService: RsiMomentumBacktestService,
    private val simpleMomentumBacktestPrepService: SimpleMomentumBacktestPrepService,
    private val momentumDataPrepService: MomentumDataPrepService,
    private val s4Service: S4Service,
    private val volumeSpikeBacktestService: VolumeSpikeBacktestService,
    private val bollingerSqueezeBacktestService: BollingerSqueezeBacktestService,
    private val bollingerMeanReversionBacktestService: BollingerMeanReversionBacktestService,
    private val deliveryThresholdBacktestService: DeliveryThresholdBacktestService,
    private val profitLookbackService: ProfitLookbackService,
    private val kiteClient: com.tradingtool.core.kite.KiteConnectClient,
    resourceScope: ResourceScope,
) {
    private val ioScope = resourceScope.ioScope

    @GET
    @Path("/rsi-momentum/latest")
    fun getLatestRsiMomentum(): CompletableFuture<Response> = ioScope.endpoint {
        ok(rsiMomentumService.getLatest())
    }

    @POST
    @Path("/rsi-momentum/refresh")
    fun refreshRsiMomentum(): CompletableFuture<Response> = ioScope.endpoint {
        ok(rsiMomentumService.refreshLatest())
    }

    @GET
    @Path("/rsi-momentum/history")
    fun getRsiMomentumHistory(
        @QueryParam("profileId") profileId: String,
        @QueryParam("from") from: String?,
        @QueryParam("to") to: String?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val fromDate = from?.let { LocalDate.parse(it) } ?: LocalDate.now().minusMonths(3)
        val toDate = to?.let { LocalDate.parse(it) } ?: LocalDate.now()
        ok(rsiMomentumHistoryService.getHistory(profileId, fromDate, toDate))
    }

    @GET
    @Path("/rsi-momentum/leaders-drawdown")
    fun getRsiMomentumLeadersDrawdown(
        @QueryParam("from") from: String?,
        @QueryParam("to") to: String?,
        @QueryParam("profileIds") profileIds: List<String>,
        @QueryParam("topN") topN: Int?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val toDate = to?.let { LocalDate.parse(it) } ?: LocalDate.now()
        val fromDate = from?.let { LocalDate.parse(it) } ?: toDate.minusDays(365)
        val normalizedProfileIds = profileIds
            .flatMap { value -> value.split(",") }
            .map { value -> value.trim() }
            .filter { value -> value.isNotEmpty() }
            .distinct()
            .ifEmpty { emptyList() }
        ok(
            rsiMomentumHistoryService.getLeadersDrawdown(
                from = fromDate,
                to = toDate,
                requestedProfileIds = normalizedProfileIds,
                topN = topN,
            )
        )
    }

    @GET
    @Path("/rsi-momentum/history/{date}")
    fun getRsiMomentumHistoryForDate(
        @PathParam("date") date: String,
        @QueryParam("profileId") profileId: String,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val asOfDate = LocalDate.parse(date)
        val entry = rsiMomentumHistoryService.getHistoryForDate(profileId, asOfDate)
        if (entry == null) ok(mapOf("available" to false, "message" to "No snapshot for $date"))
        else ok(entry)
    }

    @POST
    @Path("/rsi-momentum/backtest")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runRsiMomentumBacktest(request: BacktestRequest): CompletableFuture<Response> = ioScope.endpoint {
        ok(rsiMomentumHistoryService.runBacktest(request))
    }

    @POST
    @Path("/rsi-momentum/backtest/simple")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runSimpleMomentumBacktest(request: SimpleMomentumBacktestRequest): CompletableFuture<Response> = ioScope.endpoint {
        ok(rsiMomentumHistoryService.runSimpleMomentumBacktest(request))
    }

    @POST
    @Path("/rsi-momentum/backtest/simple/prepare")
    @Consumes(MediaType.APPLICATION_JSON)
    fun prepareSimpleMomentumBacktest(request: SimpleMomentumBacktestPrepareRequest): CompletableFuture<Response> = ioScope.endpoint {
        runCatching { simpleMomentumBacktestPrepService.prepare(request) }
            .fold(
                onSuccess = { result -> ok(result) },
                onFailure = { error ->
                    if (error is IllegalArgumentException) badRequest(error.message ?: "Invalid request.")
                    else throw error
                },
            )
    }

    @POST
    @Path("/rsi-momentum/backtest/sniper")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runRsiMomentumSniperBacktest(request: RsiMomentumBacktestRequest): CompletableFuture<Response> = ioScope.endpoint {
        ok(rsiMomentumBacktestService.runBacktest(request))
    }

    @POST
    @Path("/rsi-momentum/backtest/rank-drift")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runRsiRankDriftBacktest(request: RsiRankDriftBacktestRequest): CompletableFuture<Response> = ioScope.endpoint {
        ok(rsiMomentumBacktestService.runRankDriftBacktest(request))
    }

    @POST
    @Path("/rsi-momentum/backfill")
    @Consumes(MediaType.APPLICATION_JSON)
    fun backfillRsiMomentumSnapshots(request: BackfillRequest): CompletableFuture<Response> = ioScope.endpoint {
        ok(rsiMomentumBackfillService.backfill(request))
    }

    @POST
    @Path("/rsi-momentum/backfill/fresh")
    @Consumes(MediaType.APPLICATION_JSON)
    fun backfillRsiMomentumSnapshotsFresh(request: BackfillFreshRequest): CompletableFuture<Response> = ioScope.endpoint {
        val rebuild = rsiMomentumBackfillService.backfillFresh(request)
        rsiMomentumService.clearLatestSnapshotCache()
        val latest = rsiMomentumService.refreshLatest()
        ok(mapOf("rebuild" to rebuild, "latest" to latest))
    }

    @GET
    @Path("/rsi-momentum/lifecycle")
    fun getRsiMomentumLifecycle(
        @QueryParam("profileId") profileId: String,
        @QueryParam("symbol") symbol: String,
        @QueryParam("from") from: String?,
        @QueryParam("to") to: String?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val fromDate = from?.let { LocalDate.parse(it) } ?: LocalDate.now().minusMonths(3)
        val toDate = to?.let { LocalDate.parse(it) } ?: LocalDate.now()
        ok(rsiMomentumHistoryService.getLifecycleForSymbol(profileId, symbol, fromDate, toDate))
    }

    @GET
    @Path("/rsi-momentum/lifecycle/summary")
    fun getRsiMomentumLifecycleSummary(
        @QueryParam("profileId") profileId: String,
        @QueryParam("from") from: String?,
        @QueryParam("to") to: String?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val fromDate = from?.let { LocalDate.parse(it) } ?: LocalDate.now().minusMonths(3)
        val toDate = to?.let { LocalDate.parse(it) } ?: LocalDate.now()
        ok(rsiMomentumHistoryService.getLifecycleSummary(profileId, fromDate, toDate))
    }

    @GET
    @Path("/rsi-momentum/history/symbols")
    fun getRsiMomentumMultiSymbolHistory(
        @QueryParam("profileId") profileId: String,
        @QueryParam("symbols") symbols: List<String>,
        @QueryParam("from") from: String?,
        @QueryParam("to") to: String?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val fromDate = from?.let { LocalDate.parse(it) } ?: LocalDate.now().minusMonths(3)
        val toDate = to?.let { LocalDate.parse(it) } ?: LocalDate.now()
        ok(rsiMomentumHistoryService.getMultiSymbolHistory(profileId, symbols, fromDate, toDate))
    }

    @POST
    @Path("/momentum-data/prepare")
    @Consumes(MediaType.APPLICATION_JSON)
    fun prepareMomentumData(request: MomentumDataPrepareRequest): CompletableFuture<Response> = ioScope.endpoint {
        runCatching { momentumDataPrepService.prepare(request) }
            .fold(
                onSuccess = { result -> ok(result) },
                onFailure = { error ->
                    if (error is IllegalArgumentException) badRequest(error.message ?: "Invalid request.")
                    else throw error
                },
            )
    }

    @POST
    @Path("/bollinger/backtest")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runBollingerBacktest(request: BollingerBacktestRequest?): CompletableFuture<Response> = ioScope.endpoint {
        val normalized = request ?: BollingerBacktestRequest()
        ok(bollingerSqueezeBacktestService.runBacktest(normalized))
    }

    @POST
    @Path("/bollinger/mean-reversion/backtest")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runBollingerMeanReversionBacktest(request: BollingerMeanReversionBacktestRequest?): CompletableFuture<Response> = ioScope.endpoint {
        val normalized = request ?: BollingerMeanReversionBacktestRequest()
        ok(bollingerMeanReversionBacktestService.runBacktest(normalized))
    }

    @POST
    @Path("/profit-lookback")
    @Consumes(MediaType.APPLICATION_JSON)
    fun analyzeProfitLookback(request: ProfitLookbackRequest?): CompletableFuture<Response> = ioScope.endpoint {
        if (!kiteClient.isAuthenticated) {
            return@endpoint serviceUnavailable("Kite is not authenticated")
        }

        val validatedRequest = runCatching {
            validateProfitLookbackRequest(request)
        }.getOrElse { error ->
            if (error is IllegalArgumentException) {
                return@endpoint badRequest(error.message ?: "Invalid request.")
            }
            throw error
        }

        runCatching { profitLookbackService.analyze(validatedRequest) }
            .fold(
                onSuccess = { result -> ok(result) },
                onFailure = { error ->
                    if (error is IllegalArgumentException) badRequest(error.message ?: "Invalid request.")
                    else throw error
                },
            )
    }

    @POST
    @Path("/profit-lookback/bulk")
    @Consumes(MediaType.APPLICATION_JSON)
    fun analyzeProfitLookbackBulk(request: ProfitLookbackBulkRequest?): CompletableFuture<Response> = ioScope.endpoint {
        if (!kiteClient.isAuthenticated) {
            return@endpoint serviceUnavailable("Kite is not authenticated")
        }

        val validatedRequest = runCatching {
            validateProfitLookbackBulkRequest(request)
        }.getOrElse { error ->
            if (error is IllegalArgumentException) {
                return@endpoint badRequest(error.message ?: "Invalid request.")
            }
            throw error
        }

        val successfulRows = validatedRequest.rows.filter { item -> item.error == null }.mapNotNull { item -> item.request }
        val validationErrors = validatedRequest.rows.filter { item -> item.error != null }.map { item ->
            ProfitLookbackBulkRowResponse(
                rowId = item.rowId,
                ok = false,
                data = null,
                error = item.error,
            )
        }

        if (successfulRows.isEmpty()) {
            return@endpoint ok(ProfitLookbackBulkResponse(rows = validationErrors))
        }

        val bulkRequest = ProfitLookbackBulkRequest(
            lookbackDays = validatedRequest.lookbackDays,
            targetPercents = validatedRequest.targetPercents,
            rows = successfulRows,
        )

        runCatching { profitLookbackService.analyzeBulk(bulkRequest) }
            .fold(
                onSuccess = { result ->
                    val resultByRowId = result.rows.associateBy { item -> item.rowId }
                    val orderedRows = validatedRequest.rows.map { item ->
                        if (item.error != null) {
                            ProfitLookbackBulkRowResponse(
                                rowId = item.rowId,
                                ok = false,
                                data = null,
                                error = item.error,
                            )
                        } else {
                            resultByRowId[item.rowId] ?: ProfitLookbackBulkRowResponse(
                                rowId = item.rowId,
                                ok = false,
                                data = null,
                                error = "Missing bulk result for row ${item.rowId}",
                            )
                        }
                    }
                    ok(ProfitLookbackBulkResponse(rows = orderedRows))
                },
                onFailure = { error ->
                    if (error is IllegalArgumentException) badRequest(error.message ?: "Invalid request.")
                    else throw error
                },
            )
    }

    @GET
    @Path("/momentum-data/export/range")
    fun exportMomentumRange(
        @QueryParam("profileId") profileId: String?,
        @QueryParam("from") from: String?,
        @QueryParam("to") to: String?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val requiredProfileId = profileId?.trim()?.takeIf { it.isNotEmpty() }
            ?: return@endpoint badRequest("profileId query param is required.")
        val requiredFrom = from?.trim()?.takeIf { it.isNotEmpty() }
            ?: return@endpoint badRequest("from query param is required.")
        val requiredTo = to?.trim()?.takeIf { it.isNotEmpty() }
            ?: return@endpoint badRequest("to query param is required.")
        val document = runCatching {
            momentumDataPrepService.buildRangeExport(requiredProfileId, requiredFrom, requiredTo)
        }.getOrElse { error ->
            if (error is IllegalArgumentException) return@endpoint badRequest(error.message ?: "Invalid request.")
            throw error
        }
        val json = momentumDataPrepService.toJson(document)
        val fileName = "top50_by_day_${requiredProfileId}_${requiredFrom}_${requiredTo}.json"
        Response.ok(json, MediaType.APPLICATION_JSON)
            .header("Content-Disposition", "attachment; filename=\"$fileName\"")
            .build()
    }

    @GET
    @Path("/momentum-data/export/today")
    fun exportMomentumToday(
        @QueryParam("profileId") profileId: String?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val requiredProfileId = profileId?.trim()?.takeIf { it.isNotEmpty() }
            ?: return@endpoint badRequest("profileId query param is required.")
        val document = runCatching {
            momentumDataPrepService.buildTodayExport(requiredProfileId)
        }.getOrElse { error ->
            if (error is IllegalArgumentException) return@endpoint badRequest(error.message ?: "Invalid request.")
            throw error
        }
        val json = momentumDataPrepService.toJson(document)
        val fileName = "top50_today_with_1y_candles_${requiredProfileId}_${document.as_of_date_used}.json"
        Response.ok(json, MediaType.APPLICATION_JSON)
            .header("Content-Disposition", "attachment; filename=\"$fileName\"")
            .build()
    }

    @GET
    @Path("/s4/latest")
    fun getLatestS4(): CompletableFuture<Response> = ioScope.endpoint {
        ok(s4Service.getLatest())
    }

    @POST
    @Path("/s4/refresh")
    fun refreshS4(): CompletableFuture<Response> = ioScope.endpoint {
        ok(s4Service.refreshLatest())
    }

    @POST
    @Path("/volume-spike/backtest")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runVolumeSpikeBacktest(request: VolumeSpikeBacktestRequest?): CompletableFuture<Response> = ioScope.endpoint {
        if (!kiteClient.isAuthenticated) {
            return@endpoint serviceUnavailable("Kite is not authenticated")
        }

        val validatedRequest = runCatching {
            validateVolumeSpikeBacktestRequest(request)
        }.getOrElse { error ->
            if (error is IllegalArgumentException) {
                return@endpoint badRequest(error.message ?: "Invalid request.")
            }
            throw error
        }

        ok(volumeSpikeBacktestService.runBacktest(validatedRequest))
    }

    @POST
    @Path("/delivery-threshold/backtest")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runDeliveryThresholdBacktest(request: DeliveryThresholdBacktestRequest?): CompletableFuture<Response> = ioScope.endpoint {
        val validatedRequest = runCatching {
            validateDeliveryThresholdBacktestRequest(request)
        }.getOrElse { error ->
            if (error is IllegalArgumentException) {
                return@endpoint badRequest(error.message ?: "Invalid request.")
            }
            throw error
        }

        ok(deliveryThresholdBacktestService.runBacktest(validatedRequest))
    }
}

internal fun validateProfitLookbackRequest(request: ProfitLookbackRequest?): ProfitLookbackRequest {
    val body = request ?: throw IllegalArgumentException("Request body is required.")
    val symbol = body.symbol.trim().uppercase()
    if (symbol.isEmpty()) {
        throw IllegalArgumentException("symbol is required.")
    }
    if (body.instrumentToken <= 0) {
        throw IllegalArgumentException("instrumentToken must be positive.")
    }
    val sellDate = runCatching { LocalDate.parse(body.sellDate) }.getOrNull()
        ?: throw IllegalArgumentException("sellDate must be in YYYY-MM-DD format.")
    if (body.lookbackDays !in 1..1000) {
        throw IllegalArgumentException("lookbackDays must be between 1 and 1000.")
    }

    val targetPercents = body.targetPercents
        .filter { value -> value.isFinite() && value > 0.0 }
        .distinct()
        .sorted()

    if (targetPercents.isEmpty()) {
        throw IllegalArgumentException("targetPercents must contain at least one positive value.")
    }

    return ProfitLookbackRequest(
        symbol = symbol,
        instrumentToken = body.instrumentToken,
        sellDate = sellDate.toString(),
        lookbackDays = body.lookbackDays,
        targetPercents = targetPercents,
    )
}

internal fun validateProfitLookbackBulkRequest(request: ProfitLookbackBulkRequest?): ValidatedProfitLookbackBulkRequest {
    val body = request ?: throw IllegalArgumentException("Request body is required.")
    if (body.lookbackDays !in 1..1000) {
        throw IllegalArgumentException("lookbackDays must be between 1 and 1000.")
    }

    val targetPercents = body.targetPercents
        .filter { value -> value.isFinite() && value > 0.0 }
        .distinct()
        .sorted()

    if (targetPercents.isEmpty()) {
        throw IllegalArgumentException("targetPercents must contain at least one positive value.")
    }

    if (body.rows.isEmpty()) {
        throw IllegalArgumentException("rows must contain at least one row.")
    }

    val normalizedRows = body.rows.mapIndexed { index, row ->
        val normalizedRowId = row.rowId.trim().ifEmpty { "row-${index + 1}" }
        val normalizedSymbol = row.symbol.trim().uppercase()
        val sellDate = runCatching { LocalDate.parse(row.sellDate) }.getOrNull()
        val rowError = when {
            normalizedSymbol.isEmpty() -> "symbol is required."
            row.instrumentToken <= 0 -> "instrumentToken must be positive."
            sellDate == null -> "sellDate must be in YYYY-MM-DD format."
            else -> null
        }

        if (rowError != null) {
            ValidatedProfitLookbackBulkRow(
                rowId = normalizedRowId,
                request = null,
                error = rowError,
            )
        } else {
            ValidatedProfitLookbackBulkRow(
                rowId = normalizedRowId,
                request = ProfitLookbackBulkRowRequest(
                    rowId = normalizedRowId,
                    symbol = normalizedSymbol,
                    instrumentToken = row.instrumentToken,
                    sellDate = sellDate.toString(),
                ),
                error = null,
            )
        }
    }

    val duplicateRowIds = normalizedRows
        .groupingBy { item -> item.rowId }
        .eachCount()
        .filterValues { count -> count > 1 }
        .keys

    if (duplicateRowIds.isNotEmpty()) {
        throw IllegalArgumentException("rows contain duplicate rowId values.")
    }

    return ValidatedProfitLookbackBulkRequest(
        lookbackDays = body.lookbackDays,
        targetPercents = targetPercents,
        rows = normalizedRows,
    )
}

internal data class ValidatedProfitLookbackBulkRequest(
    val lookbackDays: Int,
    val targetPercents: List<Double>,
    val rows: List<ValidatedProfitLookbackBulkRow>,
)

internal data class ValidatedProfitLookbackBulkRow(
    val rowId: String,
    val request: ProfitLookbackBulkRowRequest?,
    val error: String?,
)

internal fun validateVolumeSpikeBacktestRequest(request: VolumeSpikeBacktestRequest?): VolumeSpikeBacktestRunConfig {
    val body = request ?: throw IllegalArgumentException("Request body is required.")

    val fromDate = body.fromDate?.let { value ->
        runCatching { LocalDate.parse(value) }.getOrNull()
            ?: throw IllegalArgumentException("fromDate must be in YYYY-MM-DD format.")
    } ?: LocalDate.now().minusMonths(1)

    val toDate = body.toDate?.let { value ->
        runCatching { LocalDate.parse(value) }.getOrNull()
            ?: throw IllegalArgumentException("toDate must be in YYYY-MM-DD format.")
    } ?: LocalDate.now()

    if (fromDate.isAfter(toDate)) {
        throw IllegalArgumentException("fromDate must be on or before toDate.")
    }

    val delayMinutes = body.delayMinutes
        ?: throw IllegalArgumentException("delayMinutes is required.")
    if (delayMinutes !in 1..120) {
        throw IllegalArgumentException("delayMinutes must be between 1 and 120.")
    }

    val normalizedManualSymbols = body.manualSymbols
        .map { value -> value.trim().uppercase() }
        .filter { value -> value.isNotEmpty() }
        .distinct()

    val rvolThreshold = body.rvolThreshold
    if (!rvolThreshold.isFinite() || rvolThreshold <= 0.0) {
        throw IllegalArgumentException("rvolThreshold must be a positive number.")
    }

    val minDayMoveFromOpenPct = body.minDayMoveFromOpenPct
    if (!minDayMoveFromOpenPct.isFinite() || minDayMoveFromOpenPct < 0.0) {
        throw IllegalArgumentException("minDayMoveFromOpenPct must be a non-negative number.")
    }

    val targetPct = body.targetPct
    if (!targetPct.isFinite() || targetPct <= 0.0) {
        throw IllegalArgumentException("targetPct must be a positive number.")
    }

    val stopPct = body.stopPct
    if (!stopPct.isFinite() || stopPct <= 0.0) {
        throw IllegalArgumentException("stopPct must be a positive number.")
    }

    val minThirtyMinReturnPct = body.minThirtyMinReturnPct
    if (!minThirtyMinReturnPct.isFinite() || minThirtyMinReturnPct < 0.0) {
        throw IllegalArgumentException("minThirtyMinReturnPct must be a non-negative number.")
    }

    val latestEntryTime = body.latestEntryTime?.let { value ->
        runCatching { LocalTime.parse(value) }.getOrNull()
            ?: throw IllegalArgumentException("latestEntryTime must be in HH:MM format.")
    } ?: LocalTime.of(14, 30)

    val buyerDominancePct = body.buyerDominancePct
    if (buyerDominancePct != null && (!buyerDominancePct.isFinite() || buyerDominancePct < 50.0 || buyerDominancePct > 99.0)) {
        throw IllegalArgumentException("buyerDominancePct must be between 50 and 99 when provided.")
    }

    val positionSizeInr = body.positionSizeInr
    if (!positionSizeInr.isFinite() || positionSizeInr <= 0.0) {
        throw IllegalArgumentException("positionSizeInr must be a positive number.")
    }

    val feePerTradeInr = body.feePerTradeInr
    if (!feePerTradeInr.isFinite() || feePerTradeInr < 0.0) {
        throw IllegalArgumentException("feePerTradeInr must be a non-negative number.")
    }

    val earningsFilterMode = body.earningsFilterMode
    val customWindowStart = body.earningsWindowStartOffsetDays
    val customWindowEnd = body.earningsWindowEndOffsetDays

    if (earningsFilterMode == EarningsFilterMode.CUSTOM_WINDOW) {
        if (customWindowStart == null || customWindowEnd == null) {
            throw IllegalArgumentException(
                "earningsWindowStartOffsetDays and earningsWindowEndOffsetDays are required when earningsFilterMode=CUSTOM_WINDOW.",
            )
        }
        if (customWindowStart > customWindowEnd) {
            throw IllegalArgumentException("earningsWindowStartOffsetDays must be <= earningsWindowEndOffsetDays.")
        }
    }
    if (earningsFilterMode == EarningsFilterMode.MANUAL_SYMBOL && normalizedManualSymbols.isEmpty()) {
        throw IllegalArgumentException("manualSymbols must contain at least one symbol when earningsFilterMode=MANUAL_SYMBOL.")
    }

    return VolumeSpikeBacktestRunConfig(
        fromDate = fromDate,
        toDate = toDate,
        delayMinutes = delayMinutes,
        manualSymbols = normalizedManualSymbols,
        earningsFilterMode = earningsFilterMode,
        earningsWindowStartOffsetDays = if (earningsFilterMode == EarningsFilterMode.CUSTOM_WINDOW) customWindowStart else null,
        earningsWindowEndOffsetDays = if (earningsFilterMode == EarningsFilterMode.CUSTOM_WINDOW) customWindowEnd else null,
        rvolThreshold = rvolThreshold,
        minDayMoveFromOpenPct = minDayMoveFromOpenPct,
        targetPct = targetPct,
        stopPct = stopPct,
        minThirtyMinReturnPct = minThirtyMinReturnPct,
        latestEntryTime = latestEntryTime,
        buyerDominancePct = buyerDominancePct,
        positionSizeInr = positionSizeInr,
        feePerTradeInr = feePerTradeInr,
    )
}

internal fun validateDeliveryThresholdBacktestRequest(request: DeliveryThresholdBacktestRequest?): DeliveryThresholdBacktestRunConfig {
    val body = request ?: throw IllegalArgumentException("Request body is required.")

    val indexKeys = body.indexKeys
        .map { value -> value.trim() }
        .filter { value -> value.isNotEmpty() }
        .distinct()
    if (indexKeys.isEmpty()) {
        throw IllegalArgumentException("indexKeys must contain at least one value.")
    }
    val normalizedIndexKeys = indexKeys
        .map { value -> normalizeIndexKey(value) }
        .distinct()

    val thresholds = body.config.thresholds
        .mapKeys { entry -> normalizeIndexKey(entry.key) }
        .mapValues { entry -> entry.value }

    normalizedIndexKeys.forEach { indexKey ->
        val threshold = thresholds[indexKey]
        if (threshold == null) {
            throw IllegalArgumentException("Missing threshold for selected indexKey=$indexKey.")
        }
        if (!threshold.isFinite() || threshold <= 0.0) {
            throw IllegalArgumentException("Threshold for indexKey=$indexKey must be a positive number.")
        }
    }

    val profitPct = body.config.profitPct
    if (!profitPct.isFinite() || profitPct <= 0.0) {
        throw IllegalArgumentException("profitPct must be a positive number.")
    }

    val toDate = body.config.toDate?.let { value ->
        runCatching { LocalDate.parse(value) }.getOrNull()
            ?: throw IllegalArgumentException("toDate must be in YYYY-MM-DD format.")
    } ?: LocalDate.now()

    val fromDate = body.config.fromDate?.let { value ->
        runCatching { LocalDate.parse(value) }.getOrNull()
            ?: throw IllegalArgumentException("fromDate must be in YYYY-MM-DD format.")
    } ?: toDate.minusDays(365)

    if (fromDate.isAfter(toDate)) {
        throw IllegalArgumentException("fromDate must be on or before toDate.")
    }

    val symbols = body.symbols
        .map { value -> value.trim().uppercase() }
        .filter { value -> value.isNotEmpty() }
        .distinct()

    return DeliveryThresholdBacktestRunConfig(
        indexKeys = indexKeys,
        symbols = symbols,
        thresholdsByIndex = thresholds,
        profitPct = profitPct,
        fromDate = fromDate,
        toDate = toDate,
    )
}

internal fun normalizeIndexKey(raw: String): String {
    return raw.trim()
        .uppercase()
        .replace(Regex("[^A-Z0-9]+"), "_")
        .replace(Regex("_+"), "_")
        .trim('_')
}
