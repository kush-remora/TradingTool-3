package com.tradingtool.resources

import com.google.inject.Inject
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.core.strategy.absolutedelivery.AbsoluteDeliveryBacktestService
import com.tradingtool.core.strategy.fiftytwohigh.ChartinkFiftyTwoWeekHighReportService
import com.tradingtool.core.strategy.hotsma.HotSmaRunConfig
import com.tradingtool.core.strategy.hotsma.HotSmaRunRequest
import com.tradingtool.core.strategy.hotsma.HotSmaScannerService
import com.tradingtool.core.strategy.hotsma.HotSmaTelegramRequest
import com.tradingtool.core.strategy.sma200backtest.Sma200BacktestRequest
import com.tradingtool.core.strategy.sma200backtest.Sma200BacktestService
import com.tradingtool.core.strategy.rsioversold.RsiOversoldScanRequest
import com.tradingtool.core.strategy.rsioversold.RsiOversoldScannerService
import com.tradingtool.core.strategy.twodaygreen.TwoDayGreenCandleBacktestRequest
import com.tradingtool.core.strategy.twodaygreen.TwoDayGreenCandleBacktestService
import com.tradingtool.core.strategy.volumeeventbacktest.VolumeEventConfirmationBacktestRequest
import com.tradingtool.core.strategy.volumeeventbacktest.VolumeEventConfirmationBacktestService
import com.tradingtool.core.strategy.deliverybreakout.DeliveryBreakoutScannerService
import com.tradingtool.core.strategy.wyckoff.phase1.WyckoffPhase1ConfigService
import com.tradingtool.core.strategy.wyckoff.phase1.WyckoffPhase1RunConfig
import com.tradingtool.core.strategy.wyckoff.phase1.WyckoffPhase1RunRequest
import com.tradingtool.core.strategy.wyckoff.phase1.WyckoffPhase1ScannerService
import com.tradingtool.core.volumeshocker.groww.GrowwVolumeShockerDashboardService
import com.tradingtool.core.strategy.chartinkevidence.ChartinkEvidenceService
import com.tradingtool.core.strategy.chartinkevidence.ChartinkEvidenceUploadRequest
import com.tradingtool.core.strategy.accumulationanalysis.AccumulationAnalysisRunRequest
import com.tradingtool.core.strategy.accumulationanalysis.AccumulationAnalysisService
import com.tradingtool.core.strategy.weeklyfloor.WeeklyFloorReboundRequest
import com.tradingtool.core.strategy.priceacceptance.PriceAcceptanceScannerService
import com.tradingtool.core.strategy.weeklyfloor.WeeklyFloorReboundRunConfig
import com.tradingtool.core.strategy.weeklyfloor.WeeklyFloorReboundService
import com.tradingtool.core.strategy.weeklylowlimit.WeeklyLowLimitBacktestRequest
import com.tradingtool.core.strategy.weeklylowlimit.WeeklyLowLimitBacktestRunConfig
import com.tradingtool.core.strategy.weeklylowlimit.WeeklyLowLimitBacktestService
import com.tradingtool.core.strategy.weeklylowlimit.WeeklyLowLimitDailyValidationRequest
import com.tradingtool.core.strategy.weeklylowalignmentbacktest.WeeklyLowAlignmentBacktestRequest
import com.tradingtool.core.strategy.weeklylowalignmentbacktest.WeeklyLowAlignmentBacktestRunConfig
import com.tradingtool.core.strategy.weeklylowalignmentbacktest.WeeklyLowAlignmentBacktestService
import com.tradingtool.core.strategy.twodayclosestrengthbacktest.TwoDayCloseStrengthBacktestRequest
import com.tradingtool.core.strategy.twodayclosestrengthbacktest.TwoDayCloseStrengthBacktestRunConfig
import com.tradingtool.core.strategy.twodayclosestrengthbacktest.TwoDayCloseStrengthBacktestService
import com.tradingtool.core.strategy.weeklybase.WeeklyBaseDefinitionRequest
import com.tradingtool.core.strategy.weeklybase.WeeklyBaseDefinitionRunConfig
import com.tradingtool.core.strategy.weeklybase.WeeklyBaseDefinitionService
import com.tradingtool.core.strategy.weeklybase.WeeklyBaseGroupBacktestRequest
import com.tradingtool.core.strategy.weeklybase.WeeklyBaseGroupBacktestService
import com.tradingtool.core.strategy.weeklyreview.WeeklyPriceWatchlistScannerService
import com.tradingtool.core.strategy.weeklyreview.ShortHorizonSelectorGuideService
import com.tradingtool.core.strategy.summaryconsole.SummaryConsoleService
import com.tradingtool.core.strategy.netwebcycle.NetwebCycleRequest
import com.tradingtool.core.strategy.netwebcycle.NetwebCycleRunConfig
import com.tradingtool.core.strategy.netwebcycle.NetwebCycleService
import com.tradingtool.resources.common.badRequest
import com.tradingtool.resources.common.endpoint
import com.tradingtool.resources.common.notFound
import com.tradingtool.resources.common.ok
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.time.LocalDate
import java.util.concurrent.CompletableFuture

@Path("/api/strategy")
@Produces(MediaType.APPLICATION_JSON)
class StrategyResource @Inject constructor(
    resourceScope: ResourceScope,
    private val hotSmaScannerService: HotSmaScannerService,
    private val sma200BacktestService: Sma200BacktestService,
    private val rsiOversoldScannerService: RsiOversoldScannerService,
    private val twoDayGreenCandleBacktestService: TwoDayGreenCandleBacktestService,
    private val volumeEventConfirmationBacktestService: VolumeEventConfirmationBacktestService,
    private val absoluteDeliveryBacktestService: AbsoluteDeliveryBacktestService,
    private val deliveryBreakoutScannerService: DeliveryBreakoutScannerService,
    private val wyckoffPhase1ScannerService: WyckoffPhase1ScannerService,
    private val wyckoffPhase1ConfigService: WyckoffPhase1ConfigService,
    private val growwVolumeShockerDashboardService: GrowwVolumeShockerDashboardService,
    private val chartinkFiftyTwoWeekHighReportService: ChartinkFiftyTwoWeekHighReportService,
    private val phaseCWatchlistService: com.tradingtool.core.strategy.phasedbreakout.PhaseCWatchlistService,
    private val trailingStopBacktestService: com.tradingtool.core.strategy.trailingstopbacktest.TrailingStopBacktestService,
    private val fiftyTwoWeekMomentumRule5Service: com.tradingtool.core.strategy.fiftytwomomentum.FiftyTwoWeekMomentumRule5Service,
    private val csvBacktestService: com.tradingtool.core.strategy.csvbacktest.CsvBacktestService,
    private val silentBreakoutBacktestService: com.tradingtool.core.strategy.silentbreakout.SilentBreakoutBacktestService,
    private val backtestTradeReviewService: com.tradingtool.core.strategy.csvbacktest.BacktestTradeReviewService,
    private val chartinkEvidenceService: ChartinkEvidenceService,
    private val accumulationAnalysisService: AccumulationAnalysisService,
    private val weeklyFloorReboundService: WeeklyFloorReboundService,
    private val weeklyLowLimitBacktestService: WeeklyLowLimitBacktestService,
    private val weeklyLowAlignmentBacktestService: WeeklyLowAlignmentBacktestService,
    private val twoDayCloseStrengthBacktestService: TwoDayCloseStrengthBacktestService,
    private val weeklyBaseDefinitionService: WeeklyBaseDefinitionService,
    private val weeklyBaseGroupBacktestService: WeeklyBaseGroupBacktestService,
    private val weeklyPriceWatchlistScannerService: WeeklyPriceWatchlistScannerService,
    private val shortHorizonSelectorGuideService: ShortHorizonSelectorGuideService,
    private val summaryConsoleService: SummaryConsoleService,
    private val priceAcceptanceScannerService: PriceAcceptanceScannerService,
    private val netwebCycleService: NetwebCycleService,
) {
    private val ioScope = resourceScope.ioScope

    @GET
    @Path("/hot-sma/universes")
    fun getHotSmaUniverseOptions(): CompletableFuture<Response> = ioScope.endpoint {
        ok(hotSmaScannerService.listUniverseOptions())
    }

    @GET
    @Path("/absolute-delivery/groupings")
    fun getAbsoluteDeliveryGroupings(): CompletableFuture<Response> = ioScope.endpoint {
        ok(absoluteDeliveryBacktestService.listGroupingOptions())
    }

    @GET
    @Path("/absolute-delivery/backtest")
    fun getAbsoluteDeliveryBacktest(
        @QueryParam("grouping") grouping: String?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        try {
            ok(absoluteDeliveryBacktestService.runBacktest(grouping))
        } catch (error: IllegalArgumentException) {
            badRequest(error.message ?: "Invalid institutional grouping.")
        } catch (error: IllegalStateException) {
            notFound(error.message ?: "No stock delivery data available.")
        }
    }

    @GET
    @Path("/delivery-breakout/dashboard")
    fun getDeliveryBreakoutDashboard(
        @QueryParam("watchlistKey") watchlistKey: String?,
        @QueryParam("tradeDate") tradeDate: String?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val requestedWatchlist = watchlistKey?.trim().orEmpty()
        if (requestedWatchlist.isEmpty()) {
            return@endpoint badRequest("watchlistKey is required.")
        }
        val parsedTradeDate = try {
            tradeDate?.takeIf { value -> value.isNotBlank() }?.let(LocalDate::parse)
        } catch (_: Exception) {
            return@endpoint badRequest("tradeDate must be a valid ISO date in YYYY-MM-DD format.")
        }
        try {
            ok(deliveryBreakoutScannerService.getDashboard(requestedWatchlist, parsedTradeDate))
        } catch (error: IllegalArgumentException) {
            badRequest(error.message ?: "Invalid delivery-breakout request.")
        } catch (error: IllegalStateException) {
            notFound(error.message ?: "No delivery data available for the selected request.")
        }
    }

    @GET
    @Path("/chartink-fiftytwo-week-high/report")
    fun getChartinkFiftyTwoWeekHighReport(): CompletableFuture<Response> = ioScope.endpoint {
        try {
            ok(chartinkFiftyTwoWeekHighReportService.loadLatestReport())
        } catch (error: IllegalArgumentException) {
            notFound(error.message ?: "Chartink 52-week-high report not found.")
        }
    }

    @POST
    @Path("/chartink-evidence/upload")
    @Consumes(MediaType.APPLICATION_JSON)
    fun uploadChartinkEvidence(
        request: ChartinkEvidenceUploadRequest?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val body = request ?: return@endpoint badRequest("Request body is required.")
        try {
            ok(chartinkEvidenceService.upload(body))
        } catch (error: IllegalArgumentException) {
            badRequest(error.message ?: "Invalid Chartink evidence upload.")
        }
    }

    @GET
    @Path("/chartink-evidence/dashboard")
    fun getChartinkEvidenceDashboard(
        @jakarta.ws.rs.QueryParam("months") months: Int?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val selectedMonths = months ?: 1
        try {
            ok(chartinkEvidenceService.getDashboard(selectedMonths))
        } catch (error: IllegalArgumentException) {
            badRequest(error.message ?: "Invalid dashboard period.")
        }
    }

    @POST
    @Path("/accumulation-analysis/runs")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runAccumulationAnalysis(request: AccumulationAnalysisRunRequest?): CompletableFuture<Response> = ioScope.endpoint {
        try { ok(accumulationAnalysisService.run(request ?: return@endpoint badRequest("Request body is required."))) }
        catch (error: IllegalArgumentException) { badRequest(error.message ?: "Invalid accumulation analysis request.") }
    }

    @GET
    @Path("/accumulation-analysis/runs")
    fun getAccumulationAnalysisRuns(): CompletableFuture<Response> = ioScope.endpoint { ok(accumulationAnalysisService.runs()) }

    @GET
    @Path("/accumulation-analysis/runs/{runId}")
    fun getAccumulationAnalysisSummary(@jakarta.ws.rs.PathParam("runId") runId: Long): CompletableFuture<Response> = ioScope.endpoint {
        try { ok(accumulationAnalysisService.summary(runId)) } catch (error: IllegalArgumentException) { notFound(error.message ?: "Run not found.") }
    }

    @GET
    @Path("/accumulation-analysis/runs/{runId}/symbols/{symbol}")
    fun getAccumulationAnalysisTimeline(@jakarta.ws.rs.PathParam("runId") runId: Long, @jakarta.ws.rs.PathParam("symbol") symbol: String, @jakarta.ws.rs.QueryParam("chainStart") chainStart: String?, @jakarta.ws.rs.QueryParam("chainEnd") chainEnd: String?): CompletableFuture<Response> = ioScope.endpoint {
        try { ok(accumulationAnalysisService.timeline(runId, symbol.uppercase(), chainStart?.let(java.time.LocalDate::parse), chainEnd?.let(java.time.LocalDate::parse))) } catch (error: IllegalArgumentException) { notFound(error.message ?: "Run not found.") }
    }

    @POST
    @Path("/hot-sma/run")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runHotSma(request: HotSmaRunRequest?): CompletableFuture<Response> = ioScope.endpoint {
        val body = request ?: return@endpoint badRequest("Request body is required.")
        val normalizedRequest = try {
            validateHotSmaRunRequest(body)
        } catch (error: IllegalArgumentException) {
            return@endpoint badRequest(error.message ?: "Invalid request.")
        }

        ok(hotSmaScannerService.run(HotSmaRunConfig(indexKeys = normalizedRequest.indexKeys)))
    }

    @POST
    @Path("/sma200-backtest/run")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runSma200Backtest(request: Sma200BacktestRequest?): CompletableFuture<Response> = ioScope.endpoint {
        val body = request ?: return@endpoint badRequest("Request body is required.")
        try {
            ok(sma200BacktestService.run(body.copy(symbol = body.symbol.trim().uppercase())))
        } catch (error: IllegalArgumentException) {
            badRequest(error.message ?: "Invalid SMA200 backtest request.")
        }
    }

    @GET
    @Path("/rsi-oversold/watchlists")
    fun getRsiOversoldWatchlists(): CompletableFuture<Response> = ioScope.endpoint {
        ok(rsiOversoldScannerService.listWatchlists())
    }

    @POST
    @Path("/rsi-oversold/scan")
    @Consumes(MediaType.APPLICATION_JSON)
    fun scanRsiOversold(request: RsiOversoldScanRequest?): CompletableFuture<Response> = ioScope.endpoint {
        val body = request ?: return@endpoint badRequest("Request body is required.")
        try {
            ok(rsiOversoldScannerService.scan(body))
        } catch (error: IllegalArgumentException) {
            badRequest(error.message ?: "Invalid RSI oversold scan request.")
        }
    }

    @POST
    @Path("/two-day-green-candle-backtest/run")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runTwoDayGreenCandleBacktest(request: TwoDayGreenCandleBacktestRequest?): CompletableFuture<Response> = ioScope.endpoint {
        val body = request ?: return@endpoint badRequest("Request body is required.")
        try {
            ok(twoDayGreenCandleBacktestService.run(body))
        } catch (error: IllegalArgumentException) {
            badRequest(error.message ?: "Invalid two-day green candle backtest request.")
        }
    }

    @POST
    @Path("/volume-event-confirmation-backtest/run")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runVolumeEventConfirmationBacktest(request: VolumeEventConfirmationBacktestRequest?): CompletableFuture<Response> = ioScope.endpoint {
        val body = request ?: return@endpoint badRequest("Request body is required.")
        try {
            ok(volumeEventConfirmationBacktestService.run(body))
        } catch (error: IllegalArgumentException) {
            badRequest(error.message ?: "Invalid volume-event confirmation backtest request.")
        }
    }

    @GET
    @Path("/wyckoff/phase1/universes")
    fun getWyckoffPhase1UniverseOptions(): CompletableFuture<Response> = ioScope.endpoint {
        ok(wyckoffPhase1ScannerService.listUniverseOptions())
    }

    @GET
    @Path("/weekly-price-review/watchlists")
    fun getWeeklyPriceReviewWatchlists(): CompletableFuture<Response> = ioScope.endpoint {
        ok(weeklyPriceWatchlistScannerService.listWatchlists())
    }

    @GET
    @Path("/short-horizon-selector/tab-one-guide")
    fun getShortHorizonSelectorTabOneGuide(): CompletableFuture<Response> = ioScope.endpoint {
        ok(shortHorizonSelectorGuideService.loadTabOneGuide())
    }

    @GET
    @Path("/summary-console/watchlists")
    fun getSummaryConsoleWatchlists(): CompletableFuture<Response> = ioScope.endpoint {
        ok(summaryConsoleService.listWatchlists())
    }

    @GET
    @Path("/summary-console/scan")
    fun getSummaryConsoleScan(
        @QueryParam("watchlists") watchlists: String?,
        @QueryParam("asOfDate") asOfDate: String?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val requestedWatchlists = watchlists.orEmpty().split(",").map(String::trim).filter(String::isNotEmpty)
        if (requestedWatchlists.isEmpty()) {
            return@endpoint badRequest("At least one watchlist is required.")
        }
        val parsedAsOfDate = try {
            asOfDate?.takeIf(String::isNotBlank)?.let(LocalDate::parse) ?: LocalDate.now()
        } catch (_: Exception) {
            return@endpoint badRequest("asOfDate must be a valid ISO date in YYYY-MM-DD format.")
        }
        try {
            ok(summaryConsoleService.scan(requestedWatchlists, parsedAsOfDate))
        } catch (error: IllegalArgumentException) {
            badRequest(error.message ?: "Invalid summary-console request.")
        }
    }

    @GET
    @Path("/price-acceptance/universes")
    fun getPriceAcceptanceUniverseOptions(): CompletableFuture<Response> = ioScope.endpoint {
        ok(priceAcceptanceScannerService.listUniverseOptions())
    }

    @GET
    @Path("/price-acceptance/scan")
    fun getPriceAcceptanceScan(
        @QueryParam("indexKey") indexKey: String?,
        @QueryParam("asOfDate") asOfDate: String?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val requestedIndexKey = indexKey?.trim().orEmpty()
        if (requestedIndexKey.isEmpty()) {
            return@endpoint badRequest("indexKey is required.")
        }

        val parsedAsOfDate = try {
            asOfDate?.takeIf { value -> value.isNotBlank() }?.let(LocalDate::parse) ?: LocalDate.now()
        } catch (_: Exception) {
            return@endpoint badRequest("asOfDate must be a valid ISO date in YYYY-MM-DD format.")
        }

        try {
            ok(priceAcceptanceScannerService.scan(requestedIndexKey, parsedAsOfDate))
        } catch (error: IllegalArgumentException) {
            badRequest(error.message ?: "Invalid price acceptance scan request.")
        }
    }

    @GET
    @Path("/weekly-price-review/scan")
    fun getWeeklyPriceReviewScan(@QueryParam("watchlist") watchlist: String?): CompletableFuture<Response> = ioScope.endpoint {
        try {
            ok(weeklyPriceWatchlistScannerService.scan(watchlist.orEmpty()))
        } catch (error: IllegalArgumentException) {
            badRequest(error.message ?: "Invalid watchlist.")
        }
    }

    @GET
    @Path("/wyckoff/phase1/config")
    fun getWyckoffPhase1Config(): CompletableFuture<Response> = ioScope.endpoint {
        ok(wyckoffPhase1ConfigService.loadPhase1Config())
    }

    @GET
    @Path("/wyckoff/phase1/columns")
    fun getWyckoffPhase1ColumnsConfig(): CompletableFuture<Response> = ioScope.endpoint {
        ok(wyckoffPhase1ConfigService.loadTableColumnsConfig())
    }

    @POST
    @Path("/wyckoff/phase1/run")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runWyckoffPhase1(request: WyckoffPhase1RunRequest?): CompletableFuture<Response> = ioScope.endpoint {
        val body = request ?: return@endpoint badRequest("Request body is required.")
        val config = wyckoffPhase1ConfigService.loadPhase1Config()
        
        val runConfig = WyckoffPhase1RunConfig(
            universeKeys = body.universeKeys,
            symbols = body.symbols,
            asOfDate = body.asOfDate?.let { LocalDate.parse(it) } ?: LocalDate.now(),
            applyStrictBaseFilter = body.applyStrictBaseFilter
        )
        ok(wyckoffPhase1ScannerService.run(runConfig, config))
    }

    @GET
    @Path("/volume-shocker/dates")
    fun getVolumeShockerDates(): CompletableFuture<Response> = ioScope.endpoint {
        ok(growwVolumeShockerDashboardService.listAvailableDates())
    }

    @GET
    @Path("/volume-shocker/dashboard")
    fun getVolumeShockerDashboard(
        @jakarta.ws.rs.QueryParam("tradeDate") tradeDate: String?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val parsedTradeDate = tradeDate?.takeIf { value -> value.isNotBlank() }?.let(LocalDate::parse)
            ?: return@endpoint badRequest("tradeDate query parameter is required.")
        ok(growwVolumeShockerDashboardService.getDashboard(parsedTradeDate))
    }

    @GET
    @Path("/volume-shocker/detail")
    fun getVolumeShockerDetail(
        @jakarta.ws.rs.QueryParam("tradeDate") tradeDate: String?,
        @jakarta.ws.rs.QueryParam("symbol") symbol: String?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val parsedTradeDate = tradeDate?.takeIf { value -> value.isNotBlank() }?.let(LocalDate::parse)
            ?: return@endpoint badRequest("tradeDate query parameter is required.")
        val requestedSymbol = symbol?.trim()?.uppercase()?.takeIf { value -> value.isNotBlank() }
            ?: return@endpoint badRequest("symbol query parameter is required.")
        ok(growwVolumeShockerDashboardService.getDetail(parsedTradeDate, requestedSymbol))
    }

    @POST
    @Path("/phase-c/upload")
    @Consumes(MediaType.APPLICATION_JSON)
    fun uploadPhaseCWatchlist(
        request: com.tradingtool.core.strategy.phasedbreakout.PhaseCWatchlistUploadRequest?
    ): CompletableFuture<Response> = ioScope.endpoint {
        val body = request ?: return@endpoint badRequest("Request body is required.")
        ok(phaseCWatchlistService.uploadChartinkCsv(body))
    }

    @GET
    @Path("/phase-c/dashboard")
    fun getPhaseCWatchlist(): CompletableFuture<Response> = ioScope.endpoint {
        ok(phaseCWatchlistService.getAllWatchlist())
    }

    @POST
    @Path("/phase-c/fresh-fields/update")
    fun refreshPhaseCFreshFields(): CompletableFuture<Response> = ioScope.endpoint {
        ok(phaseCWatchlistService.refreshFreshFields())
    }

    @POST
    @Path("/phase-c/delivery-validation/run")
    fun runPhase2DeliveryValidation(): CompletableFuture<Response> = ioScope.endpoint {
        ok(phaseCWatchlistService.runDeliveryValidation())
    }

    @GET
    @Path("/phase-c/export")
    fun exportPhaseCData(): CompletableFuture<Response> = ioScope.endpoint {
        ok(phaseCWatchlistService.getExportData())
    }

    @POST
    @Path("/trailing-stop-backtest/run")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runTrailingStopBacktest(
        request: com.tradingtool.core.strategy.trailingstopbacktest.TrailingStopBacktestApiRequest?
    ): CompletableFuture<Response> = ioScope.endpoint {
        val body = request ?: return@endpoint badRequest("Request body is required.")

        val tempFile = java.nio.file.Files.createTempFile("trailing_stop_", ".csv")
        try {
            java.nio.file.Files.writeString(tempFile, body.csvContent)
            
            val toDate = body.priceDataToDate?.let { LocalDate.parse(it) } ?: LocalDate.now()
            val config = com.tradingtool.core.strategy.trailingstopbacktest.TrailingStopBacktestConfig(
                inputFile = tempFile,
                priceDataToDate = toDate,
                allocationPerTrade = body.allocationPerTrade ?: 10000.0
            )
            
            val report = trailingStopBacktestService.run(config)
            ok(report)
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile)
        }
    }

    @GET
    @Path("/52w-momentum/rule5/watchlists")
    fun get52wMomentumRule5Watchlists(): CompletableFuture<Response> = ioScope.endpoint {
        ok(fiftyTwoWeekMomentumRule5Service.listWatchlists())
    }

    @GET
    @Path("/52w-momentum/rule5/scan")
    fun scan52wMomentumRule5(
        @QueryParam("watchlists") watchlists: String?,
        @QueryParam("asOfDate") asOfDate: String?,
        @QueryParam("breakoutPeriodSessions") breakoutPeriodSessions: Int?,
        @QueryParam("nearHighTolerancePct") nearHighTolerancePct: Double?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val requestedWatchlists = watchlists.orEmpty().split(",").map(String::trim).filter(String::isNotEmpty)
        if (requestedWatchlists.isEmpty()) {
            return@endpoint badRequest("At least one watchlist is required.")
        }
        val parsedAsOfDate = try {
            asOfDate?.takeIf(String::isNotBlank)?.let(LocalDate::parse) ?: LocalDate.now()
        } catch (_: Exception) {
            return@endpoint badRequest("asOfDate must be a valid ISO date in YYYY-MM-DD format.")
        }
        val selectedBreakoutPeriod = breakoutPeriodSessions ?: 200
        try {
            ok(
                fiftyTwoWeekMomentumRule5Service.scan(
                    requestedWatchlists = requestedWatchlists,
                    requestedAsOfDate = parsedAsOfDate,
                    breakoutPeriodSessions = selectedBreakoutPeriod,
                    nearHighTolerancePct = nearHighTolerancePct ?: 0.0,
                ),
            )
        } catch (error: IllegalArgumentException) {
            badRequest(error.message ?: "Invalid 52-week breakout request.")
        }
    }

    @GET
    @Path("/52w-momentum/rule5/backtest")
    fun backtest52wMomentumRule5(
        @QueryParam("watchlists") watchlists: String?,
        @QueryParam("asOfDate") asOfDate: String?,
        @QueryParam("breakoutPeriodSessions") breakoutPeriodSessions: Int?,
        @QueryParam("nearHighTolerancePct") nearHighTolerancePct: Double?,
        @QueryParam("targetPct") targetPct: Double?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val requestedWatchlists = watchlists.orEmpty().split(",").map(String::trim).filter(String::isNotEmpty)
        if (requestedWatchlists.isEmpty()) {
            return@endpoint badRequest("At least one watchlist is required.")
        }
        val parsedAsOfDate = try {
            asOfDate?.takeIf(String::isNotBlank)?.let(LocalDate::parse) ?: LocalDate.now()
        } catch (_: Exception) {
            return@endpoint badRequest("asOfDate must be a valid ISO date in YYYY-MM-DD format.")
        }
        try {
            ok(
                fiftyTwoWeekMomentumRule5Service.backtest(
                    requestedWatchlists = requestedWatchlists,
                    requestedAsOfDate = parsedAsOfDate,
                    breakoutPeriodSessions = breakoutPeriodSessions ?: 200,
                    nearHighTolerancePct = nearHighTolerancePct ?: 0.0,
                    targetPct = targetPct ?: 10.0,
                ),
            )
        } catch (error: IllegalArgumentException) {
            badRequest(error.message ?: "Invalid 52-week breakout backtest request.")
        }
    }

    @POST
    @Path("/weekly-floor-rebound/backtest")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runWeeklyFloorReboundBacktest(
        request: WeeklyFloorReboundRequest?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val body = request ?: return@endpoint badRequest("Request body is required.")
        try {
            ok(
                weeklyFloorReboundService.run(
                    WeeklyFloorReboundRunConfig(
                        symbol = body.symbol,
                        toDate = LocalDate.now(),
                        supportFloor = body.supportFloor ?: return@endpoint badRequest("supportFloor is required."),
                        supportCeiling = body.supportCeiling ?: return@endpoint badRequest("supportCeiling is required."),
                        activeFrom = body.activeFrom?.let(LocalDate::parse) ?: return@endpoint badRequest("activeFrom is required."),
                    ),
                ),
            )
        } catch (error: IllegalArgumentException) {
            badRequest(error.message ?: "Invalid weekly floor rebound request.")
        }
    }

    @POST
    @Path("/weekly-low-limit-backtest/run")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runWeeklyLowLimitBacktest(
        request: WeeklyLowLimitBacktestRequest?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val body = request ?: return@endpoint badRequest("Request body is required.")
        try {
            ok(
                weeklyLowLimitBacktestService.run(
                    WeeklyLowLimitBacktestRunConfig(
                        mode = body.mode,
                        entryRule = body.entryRule,
                        symbol = body.symbol,
                        instrumentToken = body.instrumentToken,
                        watchlistKey = body.watchlistKey,
                        toDate = LocalDate.now(),
                    ),
                ),
            )
        } catch (error: IllegalArgumentException) {
            badRequest(error.message ?: "Invalid weekly low limit backtest request.")
        }
    }

    @POST
    @Path("/weekly-low-alignment-backtest/run")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runWeeklyLowAlignmentBacktest(
        request: WeeklyLowAlignmentBacktestRequest?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val body = request ?: return@endpoint badRequest("Request body is required.")
        try {
            ok(
                weeklyLowAlignmentBacktestService.run(
                    WeeklyLowAlignmentBacktestRunConfig(
                        watchlistKey = body.watchlistKey,
                        targetPct = body.targetPct,
                        maxHoldingTradingDays = body.maxHoldingTradingDays,
                        toDate = LocalDate.now(),
                    ),
                ),
            )
        } catch (error: IllegalArgumentException) {
            badRequest(error.message ?: "Invalid weekly low alignment backtest request.")
        }
    }

    @POST
    @Path("/two-day-close-strength-backtest/run")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runTwoDayCloseStrengthBacktest(
        request: TwoDayCloseStrengthBacktestRequest?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val body = request ?: return@endpoint badRequest("Request body is required.")
        try {
            ok(
                twoDayCloseStrengthBacktestService.run(
                    TwoDayCloseStrengthBacktestRunConfig(
                        watchlistKey = body.watchlistKey,
                        toDate = LocalDate.now(),
                    ),
                ),
            )
        } catch (error: IllegalArgumentException) {
            badRequest(error.message ?: "Invalid two-day close-strength backtest request.")
        }
    }

    @POST
    @Path("/weekly-low-limit-backtest/daily-validation")
    @Consumes(MediaType.APPLICATION_JSON)
    fun loadWeeklyLowLimitDailyValidation(
        request: WeeklyLowLimitDailyValidationRequest?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val body = request ?: return@endpoint badRequest("Request body is required.")
        try {
            ok(weeklyLowLimitBacktestService.loadDailyValidation(body))
        } catch (error: IllegalArgumentException) {
            badRequest(error.message ?: "Invalid weekly low limit validation request.")
        }
    }

    @POST
    @Path("/weekly-base-definition/run")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runWeeklyBaseDefinition(
        request: WeeklyBaseDefinitionRequest?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val body = request ?: return@endpoint badRequest("Request body is required.")
        try {
            ok(weeklyBaseDefinitionService.run(WeeklyBaseDefinitionRunConfig(body.symbol, LocalDate.now())))
        } catch (error: IllegalArgumentException) {
            badRequest(error.message ?: "Invalid weekly base definition request.")
        }
    }

    @POST
    @Path("/netweb-cycle/run")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runNetwebCycle(
        request: NetwebCycleRequest?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val body = request ?: return@endpoint badRequest("Request body is required.")
        try {
            ok(
                netwebCycleService.run(
                    NetwebCycleRunConfig(
                        symbol = body.symbol,
                        toDate = LocalDate.now(),
                    ),
                ),
            )
        } catch (error: IllegalArgumentException) {
            badRequest(error.message ?: "Invalid NETWEB cycle request.")
        }
    }

    @POST
    @Path("/weekly-base-group-backtest/run")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runWeeklyBaseGroupBacktest(
        request: WeeklyBaseGroupBacktestRequest?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val body = request ?: return@endpoint badRequest("Request body is required.")
        try {
            ok(weeklyBaseGroupBacktestService.run(body))
        } catch (error: IllegalArgumentException) {
            badRequest(error.message ?: "Invalid weekly base group backtest request.")
        }
    }

    @POST
    @Path("/csv-backtest/run")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runCsvBacktest(
        request: com.tradingtool.core.strategy.csvbacktest.CsvBacktestApiRequest?
    ): CompletableFuture<Response> = ioScope.endpoint {
        val body = request ?: return@endpoint badRequest("Request body is required.")
        val targetPct = body.targetPct ?: return@endpoint badRequest("Target percentage is required.")
        if (body.type !in setOf("FIXED", "TRAILING")) {
            return@endpoint badRequest("Strategy type must be FIXED or TRAILING.")
        }
        if (targetPct <= 0.0 || body.stopLossPct <= 0.0) {
            return@endpoint badRequest("Target and stop-loss percentages must be positive.")
        }
        if (
            body.type == "TRAILING" &&
            (body.trailingStopLossPct !in 0.1..100.0 || body.initialStopLossSessions !in 1..20)
        ) {
            return@endpoint badRequest(
                "Trailing stop must be between 0.1% and 100%, and initial stop sessions must be between 1 and 20."
            )
        }
        if (body.retestWindowDays !in 1..20 || body.retestTolerancePct !in 0.0..10.0) {
            return@endpoint badRequest("Retest window must be 1-20 days and tolerance must be 0-10%.")
        }
        if (body.maxCloseToCloseGainPct !in 0.0..100.0) {
            return@endpoint badRequest("Maximum close-to-close gain must be between 0% and 100%.")
        }
        if (body.breakoutLookbackSessions !in 10..250) {
            return@endpoint badRequest("Breakout lookback must be between 10 and 250 trading sessions.")
        }
        if (com.tradingtool.core.strategy.csvbacktest.CsvBacktestEntryStrategy.entries.none { it.name == body.entryStrategy }) {
            return@endpoint badRequest("Entry strategy is invalid.")
        }
        val response = csvBacktestService.runBacktest(
            csvContent = body.csvContent,
            type = body.type,
            targetPct = targetPct,
            stopLossPct = body.stopLossPct,
            initialStopLossSessions = body.initialStopLossSessions,
            trailingStopLossPct = body.trailingStopLossPct,
            entryStrategy = body.entryStrategy,
            retestWindowDays = body.retestWindowDays,
            retestTolerancePct = body.retestTolerancePct,
            applyV2Validation = body.applyV2Validation,
            breakoutLookbackSessions = body.breakoutLookbackSessions,
            maxCloseToCloseGainPct = body.maxCloseToCloseGainPct,
        )
        ok(response)
    }

    @POST
    @Path("/silent-breakout-backtest/run")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runSilentBreakoutBacktest(
        request: com.tradingtool.core.strategy.silentbreakout.SilentBreakoutBacktestRequest?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val body = request ?: return@endpoint badRequest("Request body is required.")
        if (body.csvContent.isBlank()) {
            return@endpoint badRequest("CSV content is required.")
        }
        if (body.targetPct !in 0.1..1_000.0) {
            return@endpoint badRequest("Target must be between 0.1% and 1000%.")
        }
        try {
            ok(silentBreakoutBacktestService.run(body.csvContent, body.targetPct, body.signalMonth, body.marketCaps))
        } catch (error: IllegalArgumentException) {
            badRequest(error.message ?: "Invalid silent breakout CSV.")
        }
    }

    @GET
    @Path("/csv-backtest/reviews")
    fun getBacktestTradeReviews(): CompletableFuture<Response> = ioScope.endpoint {
        ok(backtestTradeReviewService.getAllReviews())
    }

    @POST
    @Path("/csv-backtest/reviews")
    @Consumes(MediaType.APPLICATION_JSON)
    fun upsertBacktestTradeReview(
        request: com.tradingtool.core.strategy.csvbacktest.BacktestTradeReviewApiRequest?
    ): CompletableFuture<Response> = ioScope.endpoint {
        val body = request ?: return@endpoint badRequest("Request body is required.")
        backtestTradeReviewService.upsertReview(body)
        ok(mapOf("success" to true))
    }

    @GET
    @Path("/csv-backtest/reviews/reasons")
    fun getBacktestTradeReviewReasons(): CompletableFuture<Response> = ioScope.endpoint {
        ok(com.tradingtool.core.strategy.csvbacktest.BacktestTradeReviewReasonConfig.reasons)
    }
}

internal fun validateHotSmaRunRequest(request: HotSmaRunRequest): HotSmaRunRequest {
    val normalizedIndexKeys = request.indexKeys
        .map(::normalizeIndexKeyForResource)
        .filter(String::isNotBlank)
        .distinct()
    require(normalizedIndexKeys.isNotEmpty()) { "At least one indexKey is required." }
    return request.copy(indexKeys = normalizedIndexKeys)
}

internal fun validateHotSmaTelegramRequest(request: HotSmaTelegramRequest): HotSmaTelegramRequest {
    val normalizedIndexKey = normalizeIndexKeyForResource(request.indexKey)
    require(normalizedIndexKey.isNotBlank()) { "indexKey is required." }

    val normalizedSymbol = request.symbol.trim().uppercase()
    require(normalizedSymbol.isNotBlank()) { "symbol is required." }
    require(request.currentPrice > 0.0) { "currentPrice must be a positive number." }

    return request.copy(
        indexKey = normalizedIndexKey,
        symbol = normalizedSymbol,
    )
}

private fun normalizeIndexKeyForResource(raw: String): String {
    return raw.trim()
        .replace(Regex("[^A-Za-z0-9]+"), "_")
        .trim('_')
        .uppercase()
}
