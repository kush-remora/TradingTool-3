package com.tradingtool.resources

import com.google.inject.Inject
import com.tradingtool.core.database.StockDeliveryJdbiHandler
import com.tradingtool.core.database.StockFundamentalsJdbiHandler
import com.tradingtool.core.fundamentals.config.FundamentalsConfigService
import com.tradingtool.core.fundamentals.config.NseIndexConstituentsService
import com.tradingtool.core.fundamentals.filter.FundamentalsFilterConfigService
import com.tradingtool.core.fundamentals.filter.FundamentalsProfileRule
import com.tradingtool.core.fundamentals.refresh.FundamentalsRefreshService
import com.tradingtool.core.stock.service.StockService
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.screener.CandleDataService
import com.tradingtool.core.screener.RsiFloorScannerRequest
import com.tradingtool.core.screener.DeliverySurgeConfirmationRow
import com.tradingtool.core.screener.RemoraRsiFloorChainedResult
import com.tradingtool.core.screener.RsiFloorScannerService
import com.tradingtool.core.screener.WeeklyCycleSuccessScanResponse
import com.tradingtool.core.screener.WeeklyCycleSuccessService
import com.tradingtool.core.screener.WeeklyPatternService
import com.tradingtool.core.watchlist.WatchlistService
import com.tradingtool.core.delivery.reconciliation.DeliveryReconciliationService
import com.tradingtool.core.delivery.source.NseDeliverySourceAdapter
import com.tradingtool.resources.common.badRequest
import com.tradingtool.resources.common.endpoint
import com.tradingtool.resources.common.ok
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import java.time.Instant
import com.tradingtool.core.screener.WeeklyPatternListResponse
import com.tradingtool.core.screener.BaseSwingResult
import com.tradingtool.core.screener.BaseSwingListResponse
import com.tradingtool.core.screener.BaseSwingService
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CompletableFuture

data class FundamentalsFilterOverrides(
    val debtToEquityMax: Double? = null,
    val roceMinPercent: Double? = null,
    val promoterPledgeMaxPercent: Double? = null,
    val salesCagr3yMinPercent: Double? = null,
    val patCagr3yMinPercent: Double? = null,
    val ocfPositiveYearsMin: Int? = null,
    val ocfPositiveYearsWindow: Int? = null,
    val avgTradedValue20dMinCr: Double? = null,
)

data class FundamentalsFilterRequest(
    val tag: String,
    val profile: String = FundamentalsFilterConfigService.PROFILE_STANDARD,
    val strictMissingData: Boolean = false,
    val overrides: FundamentalsFilterOverrides? = null,
)

data class FundamentalsTableRow(
    val symbol: String,
    val companyName: String,
    val exchange: String,
    val instrumentToken: Long,
    val tag: String,
    val fundamentalsSnapshotDate: LocalDate?,
    val marketCapCr: Double?,
    val stockPe: Double?,
    val rocePercent: Double?,
    val roePercent: Double?,
    val promoterHoldingPercent: Double?,
    val industry: String?,
    val broadIndustry: String?,
    val ltp: Double?,
    val rsi14: Double?,
    val roc1w: Double?,
    val roc3m: Double?,
    val volumeVsAvg: Double?,
    val isSelected: Boolean? = null,
    val filterReasons: List<String> = emptyList(),
)

data class FundamentalsTagOverviewResponse(
    val tag: String,
    val profile: String?,
    val totalStocks: Int,
    val selectedCount: Int?,
    val rejectedCount: Int?,
    val rows: List<FundamentalsTableRow>,
)

@Path("/api/screener")
@Produces(MediaType.APPLICATION_JSON)
class ScreenerResource @Inject constructor(
    private val candleDataService: CandleDataService,
    private val weeklyPatternService: WeeklyPatternService,
    private val fundamentalsRefreshService: FundamentalsRefreshService,
    private val fundamentalsConfigService: FundamentalsConfigService,
    private val nseIndexConstituentsService: NseIndexConstituentsService,
    private val fundamentalsFilterConfigService: FundamentalsFilterConfigService,
    private val fundamentalsHandler: StockFundamentalsJdbiHandler,
    private val deliveryHandler: StockDeliveryJdbiHandler,
    private val drawdownScannerService: com.tradingtool.core.screener.DrawdownScannerService,
    private val rsiFloorScannerService: RsiFloorScannerService,
    private val baseSwingService: BaseSwingService,
    private val bollingerScreenerService: com.tradingtool.core.screener.BollingerScreenerService,
    private val weeklyCycleSuccessService: WeeklyCycleSuccessService,
    private val watchlistService: WatchlistService,
    private val deliveryReconciliationService: DeliveryReconciliationService,
    private val nseDeliverySourceAdapter: NseDeliverySourceAdapter,
    private val stockService: StockService,
    private val kiteClient: KiteConnectClient,
    private val resourceScope: ResourceScope,
) {
    private val ioScope = resourceScope.ioScope
    private val log = LoggerFactory.getLogger(javaClass)
    private data class WeeklyIndexUniverse(
        val symbols: List<String>,
        val symbolBuckets: Map<String, List<String>>,
    )
    private data class ResolvedWeeklyUniverse(
        val symbols: List<String>,
        val sourceTags: List<String>,
        val symbolBuckets: Map<String, List<String>>,
    )

    /**
     * Fetches raw daily + 15-min candles from Kite and upserts them into the database.
     * Pass ?symbols=NETWEB,INFY or omit to sync all watchlist stocks.
     *
     * This is a synchronous call — expect 30–90 seconds for a full watchlist sync.
     */
    @POST
    @Path("/sync")
    fun sync(
        @QueryParam("symbols") symbolsParam: String?,
        @QueryParam("universe") universeParam: String?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val universe = resolveWeeklyPatternUniverse(symbolsParam, universeParam)
        if (universe.symbols.isEmpty()) return@endpoint badRequest("No symbols found. Check weekly scanner index universe config.")

        val synced = candleDataService.sync(universe.symbols, kiteClient)
        ok(mapOf("synced" to synced, "total" to universe.symbols.size, "symbols" to universe.symbols))
    }

    /**
     * Returns a weekly pattern scorecard for each symbol, computed from stored candle data.
     * Run POST /api/screener/sync first if no data is available.
     */
    @GET
    @Path("/weekly-pattern")
    fun weeklyPattern(
        @QueryParam("symbols") symbolsParam: String?,
        @QueryParam("universe") universeParam: String?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val universe = resolveWeeklyPatternUniverse(symbolsParam, universeParam)
        if (universe.symbols.isEmpty()) return@endpoint badRequest("No symbols found. Check weekly scanner index universe config.")

        val results = weeklyPatternService.analyze(universe.symbols).map { row ->
            row.copy(sourceBuckets = universe.symbolBuckets[row.symbol] ?: emptyList())
        }
        ok(WeeklyPatternListResponse(
            runAt = Instant.now().toString(),
            lookbackWeeks = weeklyPatternService.lookbackWeeks(),
            buyZoneLookbackWeeks = weeklyPatternService.buyZoneLookbackWeeks(),
            universeSourceTags = universe.sourceTags,
            results = results
        ))
    }

    private suspend fun resolveWeeklyPatternUniverse(symbolsParam: String?, universeParam: String?): ResolvedWeeklyUniverse {
        if (!symbolsParam.isNullOrBlank()) {
            return resolveWeeklyUniverse(symbolsParam)
        }

        val mode = WeeklyPatternUniverseMode.fromRaw(universeParam)
        if (mode == WeeklyPatternUniverseMode.WATCHLIST) {
            val symbols = stockService.listAll()
                .asSequence()
                .filter { stock -> stock.exchange.equals("NSE", ignoreCase = true) }
                .map { stock -> stock.symbol.trim().uppercase() }
                .filter { symbol -> symbol.isNotEmpty() }
                .distinct()
                .sorted()
                .toList()

            return ResolvedWeeklyUniverse(
                symbols = symbols,
                sourceTags = listOf("WATCHLIST"),
                symbolBuckets = symbols.associateWith { listOf("WATCHLIST") },
            )
        }

        return resolveWeeklyUniverse(null)
    }

    @GET
    @Path("/drawdown")
    fun drawdownScanner(@QueryParam("universe") universe: String?): CompletableFuture<Response> = ioScope.endpoint {
        val selectedUniverse = universe?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: "WATCHLIST"
        ok(drawdownScannerService.scanUniverse(selectedUniverse))
    }

    @GET
    @Path("/base-swing")
    fun baseSwing(@QueryParam("universe") universeParam: String?): CompletableFuture<Response> = ioScope.endpoint {
        val universeRaw = universeParam?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: "WATCHLIST"
        val indexKeys = universeRaw.split(",").map { it.trim() }
        
        val results = baseSwingService.analyze(indexKeys)
        ok(BaseSwingListResponse(
            runAt = Instant.now().toString(),
            lookbackDays = 30,
            results = results
        ))
    }

    @GET
    @Path("/bollinger")
    fun bollinger(@QueryParam("universe") universeParam: String?): CompletableFuture<Response> = ioScope.endpoint {
        val universeRaw = universeParam?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: "WATCHLIST"
        val indexKeys = universeRaw.split(",").map { it.trim() }
        
        val results = bollingerScreenerService.analyze(indexKeys)
        ok(com.tradingtool.core.screener.BollingerScanResponse(
            runAt = Instant.now().toString(),
            universe = universeRaw,
            results = results
        ))
    }

    @POST
    @Path("/remora-rsi-floor/scan")
    @Consumes(MediaType.APPLICATION_JSON)
    fun remoraRsiFloorScan(request: RsiFloorScannerRequest?): CompletableFuture<Response> = ioScope.endpoint {
        val normalizedRequest = request ?: RsiFloorScannerRequest()
        ok(rsiFloorScannerService.scan(normalizedRequest))
    }

    @POST
    @Path("/remora-rsi-floor/remora-confirm")
    @Consumes(MediaType.APPLICATION_JSON)
    fun remoraRsiFloorRemoraConfirm(request: RsiFloorScannerRequest?): CompletableFuture<Response> = ioScope.endpoint {
        val normalizedRequest = request ?: RsiFloorScannerRequest()
        val rsiResult = rsiFloorScannerService.scan(normalizedRequest)
        val rsiTokens = rsiResult.rows.map { row -> row.instrumentToken }.toSet()
        val missingAfterCoverage = ensureDeliveryCoverage(rsiTokens)
        if (missingAfterCoverage.isNotEmpty()) {
            upsertLatestDeliveryForSymbols(
                symbols = rsiResult.rows.map { row -> row.symbol }.toSet(),
                missingTokens = missingAfterCoverage,
            )
        }
        val deliveryRows = rsiResult.rows.map { row ->
            val history = deliveryHandler.read { dao ->
                dao.findRecentByInstrumentToken(
                    instrumentToken = row.instrumentToken,
                    beforeDate = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1),
                    limit = 27,
                )
            }.asSequence()
                .filter { deliveryRow ->
                    deliveryRow.reconciliationStatus == com.tradingtool.core.delivery.model.DeliveryReconciliationStatus.PRESENT &&
                        deliveryRow.delivQty != null
                }
                .toList()

            val recentWindow = history.take(7)
            val baselineWindow = history.drop(recentWindow.size).take(20)
            val hasBaselineHistory = baselineWindow.size >= 5
            val hasAnyDeliveryHistory = recentWindow.isNotEmpty()
            val avgDeliveredQty20d = if (hasBaselineHistory) {
                baselineWindow.mapNotNull { deliveryRow -> deliveryRow.delivQty?.toDouble() }.average()
            } else if (baselineWindow.isNotEmpty()) {
                baselineWindow.mapNotNull { deliveryRow -> deliveryRow.delivQty?.toDouble() }.average()
            } else {
                null
            }
            val latestTradingDate = recentWindow.firstOrNull()?.tradingDate?.toString()
            val latestDeliverySurgePct = if (avgDeliveredQty20d != null && avgDeliveredQty20d > 0.0) {
                val latestQty = recentWindow.firstOrNull()?.delivQty?.toDouble() ?: 0.0
                ((latestQty / avgDeliveredQty20d) - 1.0) * 100.0
            } else {
                null
            }
            val surgePctList = if (avgDeliveredQty20d != null && avgDeliveredQty20d > 0.0) {
                recentWindow.map { deliveryRow ->
                    val qty = deliveryRow.delivQty?.toDouble() ?: 0.0
                    ((qty / avgDeliveredQty20d) - 1.0) * 100.0
                }
            } else {
                emptyList()
            }
            val maxDeliverySurgePct7d = surgePctList.maxOrNull()
            val surgeDays7d = if (avgDeliveredQty20d != null && avgDeliveredQty20d > 0.0) {
                recentWindow.count { deliveryRow -> (deliveryRow.delivQty ?: 0L).toDouble() > avgDeliveredQty20d }
            } else {
                0
            }

            DeliverySurgeConfirmationRow(
                symbol = row.symbol,
                companyName = row.companyName,
                exchange = row.exchange,
                instrumentToken = row.instrumentToken,
                latestTradingDate = latestTradingDate,
                avgDeliveredQty20d = avgDeliveredQty20d,
                latestDeliverySurgePct = latestDeliverySurgePct,
                maxDeliverySurgePct7d = maxDeliverySurgePct7d,
                surgeDays7d = surgeDays7d,
                recentDaysUsed = recentWindow.size,
                baselineDaysUsed = baselineWindow.size,
                insufficientHistory = !hasAnyDeliveryHistory,
            )
        }
        val confirmedCount = deliveryRows.count { row ->
            !row.insufficientHistory && row.surgeDays7d >= 3 && (row.maxDeliverySurgePct7d ?: Double.NEGATIVE_INFINITY) >= 25.0
        }
        ok(
            RemoraRsiFloorChainedResult(
                rsiResult = rsiResult,
                deliveryRequestedSymbols = rsiResult.rows.size,
                deliveryConfirmedCount = confirmedCount,
                deliveryConfirmedRows = deliveryRows,
            ),
        )
    }

    private suspend fun ensureDeliveryCoverage(
        instrumentTokens: Set<Long>,
        requiredTradingDays: Int = 8,
    ): Set<Long> {
        if (instrumentTokens.isEmpty()) return emptySet()

        val today = LocalDate.now(ZoneId.of("Asia/Kolkata"))
        suspend fun missingCoverageTokens(): Set<Long> {
            return instrumentTokens.filterTo(mutableSetOf()) { token ->
                val presentCount = deliveryHandler.read { dao ->
                    dao.findRecentByInstrumentToken(
                        instrumentToken = token,
                        beforeDate = today.plusDays(1),
                        limit = requiredTradingDays,
                    )
                }.count { row ->
                    row.reconciliationStatus == com.tradingtool.core.delivery.model.DeliveryReconciliationStatus.PRESENT &&
                        row.delivQty != null
                }
                presentCount < requiredTradingDays
            }
        }

        var missing = missingCoverageTokens()
        if (missing.isEmpty()) return emptySet()

        val latestDate = runCatching { deliveryReconciliationService.latestAvailableTradingDate() }.getOrNull()
        if (latestDate != null) {
            runCatching { deliveryReconciliationService.reconcileDate(latestDate) }
                .onFailure { error -> log.debug("Delivery reconcile skipped for {}: {}", latestDate, error.message) }
            runCatching { deliveryReconciliationService.reconcileDate(latestDate.minusDays(1)) }
                .onFailure { error -> log.debug("Delivery reconcile skipped for {}: {}", latestDate.minusDays(1), error.message) }
        }

        missing = missingCoverageTokens()
        if (missing.isEmpty()) {
            log.info("Delivery backfill complete for chained scan. requestedTokens={}", instrumentTokens.size)
            return emptySet()
        }

        log.warn(
            "Delivery backfill incomplete for chained scan. missingCoverageTokens={} requiredDays={}",
            missing.size,
            requiredTradingDays,
        )
        return missing
    }

    private suspend fun upsertLatestDeliveryForSymbols(
        symbols: Set<String>,
        missingTokens: Set<Long>,
    ) {
        if (symbols.isEmpty() || missingTokens.isEmpty()) return

        val latestRows = runCatching { nseDeliverySourceAdapter.fetchLatestDeliveryData() }
            .getOrElse { error ->
                log.warn("Failed to fetch direct NSE latest delivery rows: {}", error.message)
                return
            }
        if (latestRows.isEmpty()) return

        val rowsBySymbol = latestRows.associateBy { row -> row.symbol.trim().uppercase() }
        val stocksBySymbol = stockService.listAll()
            .asSequence()
            .filter { stock -> stock.exchange.equals("NSE", ignoreCase = true) }
            .associateBy { stock -> stock.symbol.trim().uppercase() }

        var upserted = 0
        deliveryHandler.write { dao ->
            symbols.forEach { symbolRaw ->
                val symbol = symbolRaw.trim().uppercase()
                val row = rowsBySymbol[symbol] ?: return@forEach
                val stock = stocksBySymbol[symbol] ?: return@forEach
                if (!missingTokens.contains(stock.instrumentToken)) return@forEach
                dao.upsert(
                    stockId = stock.id,
                    instrumentToken = stock.instrumentToken,
                    symbol = symbol,
                    exchange = stock.exchange,
                    universe = com.tradingtool.core.delivery.model.DeliveryUniverse.WATCHLIST.storageValue,
                    tradingDate = row.tradingDate,
                    reconciliationStatus = com.tradingtool.core.delivery.model.DeliveryReconciliationStatus.PRESENT.name,
                    series = row.series,
                    ttlTrdQnty = row.ttlTrdQnty,
                    delivQty = row.delivQty,
                    delivPer = row.delivPer,
                    sourceFileName = row.sourceFileName,
                    sourceUrl = row.sourceUrl,
                )
                upserted++
            }
        }
        if (upserted > 0) {
            log.info("Direct NSE latest delivery upserted for {} RSI-filtered symbols", upserted)
        }
    }

    @GET
    @Path("/weekly-cycle-success")
    fun weeklyCycleSuccess(
        @QueryParam("universe") universeParam: String?,
        @QueryParam("weeks") weeksParam: Int?,
        @QueryParam("highLowPct") highLowPctParam: Double?,
        @QueryParam("rocPct") rocPctParam: Double?,
        @QueryParam("stableBaseDriftPct") stableBaseDriftPctParam: Double?,
        @QueryParam("prepare") prepareParam: String?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        val request = WeeklyCycleSuccessRequest.fromQuery(
            universeRaw = universeParam,
            weeksRaw = weeksParam,
            highLowPctRaw = highLowPctParam,
            rocPctRaw = rocPctParam,
            stableBaseDriftPctRaw = stableBaseDriftPctParam,
            prepareRaw = prepareParam,
        ) ?: return@endpoint badRequest("Invalid query params. weeks must be 1..52, thresholds must be >= 0.")

        val universe = resolveWeeklyCycleUniverse(request.universe)
        if (universe.symbols.isEmpty()) {
            return@endpoint badRequest("No symbols found for selected universe: ${request.universe.name}")
        }

        if (request.prepareMissingDaily) {
            val today = LocalDate.now(ZoneId.of("Asia/Kolkata"))
            val fromDate = today.minusWeeks((request.weeks + 12).toLong())
            val syncResult = candleDataService.syncDailyRange(
                symbols = universe.symbols,
                fromDate = fromDate,
                toDate = today,
                kiteClient = kiteClient,
            )
            universe.symbols.forEach { symbol ->
                // Ensure scanner reads freshly synced candles, not stale cached payloads.
                runCatching { weeklyCycleSuccessService.invalidateDailyCache(symbol) }
                    .onFailure { error ->
                        log.warn("Failed to invalidate daily candle cache for {}: {}", symbol, error.message)
                    }
            }
            log.info(
                "Weekly cycle success daily prep complete: synced={} failed={} upserted={}",
                syncResult.symbolsSynced,
                syncResult.symbolsFailed,
                syncResult.dailyCandlesUpserted,
            )
        }

        val scan = weeklyCycleSuccessService.scan(
            symbols = universe.symbols,
            symbolBuckets = universe.symbolBuckets,
            weeksRequested = request.weeks,
            highLowThresholdPct = request.highLowPct,
            rocThresholdPct = request.rocPct,
            stableBaseMaxDriftPct = request.stableBaseDriftPct,
        )

        ok(
            WeeklyCycleSuccessScanResponse(
                runAt = Instant.now().toString(),
                universe = request.universe.name,
                weeksRequested = request.weeks,
                weeksEvaluated = scan.weeksEvaluated,
                highLowThresholdPct = request.highLowPct,
                rocThresholdPct = request.rocPct,
                stableBaseMaxDriftPct = request.stableBaseDriftPct,
                results = scan.results,
            ),
        )
    }

    /**
     * Returns the detailed scorecard and heatmap for a single symbol.
     */
    @GET
    @Path("/weekly-pattern/{symbol}")
    fun weeklyPatternDetail(@PathParam("symbol") symbol: String): CompletableFuture<Response> = ioScope.endpoint {
        val detail = weeklyPatternService.analyzeDetail(symbol.uppercase())
        if (detail == null) {
            badRequest("No data found for symbol $symbol")
        } else {
            ok(detail)
        }
    }

    @POST
    @Path("/fundamentals/refresh-by-tag")
    fun refreshFundamentalsByTag(@QueryParam("tag") rawTag: String?): CompletableFuture<Response> = ioScope.endpoint {
        val indexTag = FundamentalsIndexTag.fromRaw(rawTag)
            ?: return@endpoint badRequest(
                "Unsupported or missing tag. Allowed tags: NIFTY_50, NIFTY_100, NIFTY_200, NIFTY_SMALLCAP_250.",
            )

        val symbols = resolveFundamentalsSymbolsForTag(indexTag)
        if (symbols.isEmpty()) {
            return@endpoint badRequest(
                "No symbols mapped to tag ${indexTag.key}. Add matching stock tags or configure a preset resource for this index.",
            )
        }

        val deletedRows = fundamentalsRefreshService.deleteSnapshotsForSymbols(symbols)
        val refreshResult = fundamentalsRefreshService.refreshDailySnapshots(symbolsOverride = symbols)

        ok(
            mapOf(
                "tag" to indexTag.key,
                "symbolsCount" to symbols.size,
                "deletedRows" to deletedRows,
                "refresh" to refreshResult,
            ),
        )
    }

    @GET
    @Path("/fundamentals/by-tag")
    fun fundamentalsByTag(@QueryParam("tag") rawTag: String?): CompletableFuture<Response> = ioScope.endpoint {
        val indexTag = FundamentalsIndexTag.fromRaw(rawTag)
            ?: return@endpoint badRequest(
                "Unsupported or missing tag. Allowed tags: NIFTY_50, NIFTY_100, NIFTY_200, NIFTY_SMALLCAP_250.",
            )

        val baseRows = buildFundamentalsRowsForTag(indexTag)
        ok(
            FundamentalsTagOverviewResponse(
                tag = indexTag.key,
                profile = null,
                totalStocks = baseRows.size,
                selectedCount = null,
                rejectedCount = null,
                rows = baseRows,
            ),
        )
    }

    @POST
    @Path("/fundamentals/filter")
    @Consumes(MediaType.APPLICATION_JSON)
    fun filterFundamentals(request: FundamentalsFilterRequest?): CompletableFuture<Response> = ioScope.endpoint {
        val payload = request ?: return@endpoint badRequest("Request body is required.")
        val indexTag = FundamentalsIndexTag.fromRaw(payload.tag)
            ?: return@endpoint badRequest(
                "Unsupported tag. Allowed tags: NIFTY_50, NIFTY_100, NIFTY_200, NIFTY_SMALLCAP_250.",
            )

        val rule = resolveEffectiveRule(indexTag.key, payload.profile, payload.overrides)
            ?: return@endpoint badRequest("No filter rule found for tag=${indexTag.key} profile=${payload.profile}.")

        val evaluatedRows = buildFundamentalsRowsForTag(indexTag).map { row ->
            val reasons = evaluateFilterReasons(row, rule, payload.strictMissingData)
            row.copy(
                isSelected = reasons.isEmpty(),
                filterReasons = reasons,
            )
        }

        val selectedCount = evaluatedRows.count { it.isSelected == true }
        ok(
            FundamentalsTagOverviewResponse(
                tag = indexTag.key,
                profile = payload.profile,
                totalStocks = evaluatedRows.size,
                selectedCount = selectedCount,
                rejectedCount = evaluatedRows.size - selectedCount,
                rows = evaluatedRows,
            ),
        )
    }

    private suspend fun resolveWeeklyUniverse(param: String?): ResolvedWeeklyUniverse {
        if (!param.isNullOrBlank()) {
            val symbols = param.split(",")
                .asSequence()
                .map { it.trim().uppercase() }
                .filter { it.isNotEmpty() }
                .distinct()
                .toList()
            return ResolvedWeeklyUniverse(
                symbols = symbols,
                sourceTags = listOf("MANUAL_OVERRIDE"),
                symbolBuckets = symbols.associateWith { listOf("MANUAL_OVERRIDE") },
            )
        }

        val indexUniverse = loadWeeklyScannerUniverseSymbols()
        if (indexUniverse.symbols.isNotEmpty()) {
            return ResolvedWeeklyUniverse(
                symbols = indexUniverse.symbols,
                sourceTags = WEEKLY_SCANNER_INDEX_NAMES,
                symbolBuckets = indexUniverse.symbolBuckets,
            )
        }

        log.warn("Weekly scanner index universe returned no symbols. Falling back to weekly watchlist tag.")
        val fallbackSymbols = stockService.listByTag("weekly")
            .asSequence()
            .map { stock -> stock.symbol.trim().uppercase() }
            .filter { symbol -> symbol.isNotEmpty() }
            .distinct()
            .sorted()
            .toList()

        return ResolvedWeeklyUniverse(
            symbols = fallbackSymbols,
            sourceTags = listOf("WATCHLIST:weekly"),
            symbolBuckets = fallbackSymbols.associateWith { listOf("WATCHLIST:weekly") },
        )
    }

    private suspend fun loadWeeklyScannerUniverseSymbols(): WeeklyIndexUniverse = coroutineScope {
        val fetches = WEEKLY_SCANNER_INDEX_NAMES.map { indexName ->
            async {
                indexName to nseIndexConstituentsService.fetchSymbols(indexName)
            }
        }

        val byIndex = fetches.awaitAll()
        val symbols = byIndex.asSequence()
            .flatMap { (_, members) -> members.asSequence() }
            .map { symbol -> symbol.trim().uppercase() }
            .filter { symbol -> symbol.isNotEmpty() }
            .distinct()
            .sorted()
            .toList()
        val symbolBuckets = mutableMapOf<String, MutableSet<String>>()
        byIndex.forEach { (indexName, members) ->
            members.forEach { symbol ->
                val normalized = symbol.trim().uppercase()
                if (normalized.isNotEmpty()) {
                    symbolBuckets.getOrPut(normalized) { linkedSetOf() }.add(indexName)
                }
            }
        }

        val missingIndexes = byIndex.filter { (_, members) -> members.isEmpty() }.map { (indexName, _) -> indexName }
        if (missingIndexes.isNotEmpty()) {
            log.warn("Weekly scanner universe fetch returned zero members for indexes={}", missingIndexes)
        }
        log.info("Weekly scanner resolved {} symbols from {} indexes", symbols.size, WEEKLY_SCANNER_INDEX_NAMES.size)
        WeeklyIndexUniverse(
            symbols = symbols,
            symbolBuckets = symbolBuckets.mapValues { (_, buckets) -> buckets.toList() },
        )
    }

    private suspend fun resolveWeeklyCycleUniverse(universe: WeeklyCycleUniverse): ResolvedWeeklyUniverse = coroutineScope {
        val includeWatchlist = universe == WeeklyCycleUniverse.WATCHLIST || universe == WeeklyCycleUniverse.ALL

        val selectedIndexes = when (universe) {
            WeeklyCycleUniverse.ALL -> listOf(
                INDEX_NIFTY_50,
                INDEX_NIFTY_100,
                INDEX_NIFTY_MIDCAP_250,
                INDEX_NIFTY_SMALLCAP_250,
            )
            WeeklyCycleUniverse.MIDCAP_250 -> listOf(INDEX_NIFTY_MIDCAP_250)
            WeeklyCycleUniverse.SMALLCAP_250 -> listOf(INDEX_NIFTY_SMALLCAP_250)
            WeeklyCycleUniverse.BOTH -> listOf(INDEX_NIFTY_MIDCAP_250, INDEX_NIFTY_SMALLCAP_250)
            WeeklyCycleUniverse.NIFTY_50 -> listOf(INDEX_NIFTY_50)
            WeeklyCycleUniverse.NIFTY_150 -> listOf(INDEX_NIFTY_150, INDEX_NIFTY_MIDCAP_150)
            WeeklyCycleUniverse.WATCHLIST -> emptyList()
        }

        val fetches = selectedIndexes.map { indexName ->
            async {
                indexName to nseIndexConstituentsService.fetchSymbols(indexName)
            }
        }
        val byIndex = fetches.awaitAll()

        val symbolBuckets = mutableMapOf<String, MutableSet<String>>()
        byIndex.forEach { (indexName, members) ->
            members.forEach { symbol ->
                val normalized = symbol.trim().uppercase()
                if (normalized.isNotEmpty()) {
                    symbolBuckets.getOrPut(normalized) { linkedSetOf() }.add(indexName)
                }
            }
        }

        val watchlistBucket = "WATCHLIST"
        val watchlistSymbols = if (includeWatchlist) {
            stockService.listAll()
                .asSequence()
                .filter { stock -> stock.exchange.equals("NSE", ignoreCase = true) }
                .map { stock -> stock.symbol.trim().uppercase() }
                .filter { symbol -> symbol.isNotEmpty() }
                .distinct()
                .toList()
        } else {
            emptyList()
        }
        watchlistSymbols.forEach { symbol ->
            symbolBuckets.getOrPut(symbol) { linkedSetOf() }.add(watchlistBucket)
        }

        val missingIndexes = byIndex.filter { (_, members) -> members.isEmpty() }.map { (indexName, _) -> indexName }
        if (missingIndexes.isNotEmpty()) {
            log.warn("Weekly cycle success universe fetch returned zero members for indexes={}", missingIndexes)
        }

        val symbols = symbolBuckets.keys.sorted()
        val sourceTags = if (includeWatchlist) {
            selectedIndexes + watchlistBucket
        } else {
            selectedIndexes
        }

        ResolvedWeeklyUniverse(
            symbols = symbols,
            sourceTags = sourceTags,
            symbolBuckets = symbolBuckets.mapValues { (_, buckets) -> buckets.toList() },
        )
    }

    private suspend fun resolveFundamentalsSymbolsForTag(tag: FundamentalsIndexTag): List<String> {
        val fromNseApi = nseIndexConstituentsService.fetchSymbols(tag.nseIndexName)
        if (fromNseApi.isNotEmpty()) {
            return fromNseApi
        }

        val fromStockTags = stockService.listAll().asSequence()
            .filter { stock -> stock.exchange.equals("NSE", ignoreCase = true) }
            .filter { stock ->
                stock.tags.any { stockTag ->
                    normalizeTag(stockTag.name) in tag.aliases
                }
            }
            .map { stock -> stock.symbol.uppercase() }
            .distinct()
            .sorted()
            .toList()

        if (fromStockTags.isNotEmpty()) {
            return fromStockTags
        }

        return when (tag) {
            FundamentalsIndexTag.NIFTY_SMALLCAP_250 -> fundamentalsConfigService.loadPresetSymbols(
                FundamentalsConfigService.PRESET_SMALLCAP_250,
            )
            else -> emptyList()
        }
    }

    private suspend fun buildFundamentalsRowsForTag(tag: FundamentalsIndexTag): List<FundamentalsTableRow> {
        val symbols = resolveFundamentalsSymbolsForTag(tag)
        if (symbols.isEmpty()) {
            return emptyList()
        }

        val stocksBySymbol = stockService.listAll().asSequence()
            .filter { stock -> stock.exchange.equals("NSE", ignoreCase = true) }
            .associateBy { stock -> stock.symbol.uppercase() }

        val watchlistRowsBySymbol = watchlistService.getRows(null)
            .associateBy { row -> row.symbol.uppercase() }

        val fundamentalsBySymbol = fundamentalsHandler.read { dao ->
            dao.findLatestBySymbols(symbols)
        }.associateBy { row -> row.symbol.uppercase() }

        return symbols.map { symbol ->
            val stock = stocksBySymbol[symbol]
            val watchRow = watchlistRowsBySymbol[symbol]
            val fundamentals = fundamentalsBySymbol[symbol]
            val instrumentToken = stock?.instrumentToken ?: fundamentals?.instrumentToken ?: 0L
            FundamentalsTableRow(
                symbol = symbol,
                companyName = stock?.companyName ?: fundamentals?.companyName ?: symbol,
                exchange = stock?.exchange ?: fundamentals?.exchange ?: "NSE",
                instrumentToken = instrumentToken,
                tag = tag.key,
                fundamentalsSnapshotDate = fundamentals?.snapshotDate,
                marketCapCr = fundamentals?.marketCapCr,
                stockPe = fundamentals?.stockPe,
                rocePercent = fundamentals?.rocePercent,
                roePercent = fundamentals?.roePercent,
                promoterHoldingPercent = fundamentals?.promoterHoldingPercent,
                industry = fundamentals?.industry,
                broadIndustry = fundamentals?.broadIndustry,
                ltp = watchRow?.ltp,
                rsi14 = watchRow?.rsi14,
                roc1w = watchRow?.roc1w,
                roc3m = watchRow?.roc3m,
                volumeVsAvg = watchRow?.volumeVsAvg,
            )
        }
    }

    private fun resolveEffectiveRule(
        tag: String,
        profile: String,
        overrides: FundamentalsFilterOverrides?,
    ): FundamentalsProfileRule? {
        val baseRule = fundamentalsFilterConfigService.findRule(tag, profile) ?: return null
        if (overrides == null) {
            return baseRule
        }

        return baseRule.copy(
            debtToEquityMax = overrides.debtToEquityMax ?: baseRule.debtToEquityMax,
            roceMinPercent = overrides.roceMinPercent ?: baseRule.roceMinPercent,
            promoterPledgeMaxPercent = overrides.promoterPledgeMaxPercent ?: baseRule.promoterPledgeMaxPercent,
            salesCagr3yMinPercent = overrides.salesCagr3yMinPercent ?: baseRule.salesCagr3yMinPercent,
            patCagr3yMinPercent = overrides.patCagr3yMinPercent ?: baseRule.patCagr3yMinPercent,
            ocfPositiveYearsMin = overrides.ocfPositiveYearsMin ?: baseRule.ocfPositiveYearsMin,
            ocfPositiveYearsWindow = overrides.ocfPositiveYearsWindow ?: baseRule.ocfPositiveYearsWindow,
            avgTradedValue20dMinCr = overrides.avgTradedValue20dMinCr ?: baseRule.avgTradedValue20dMinCr,
        )
    }

    private fun evaluateFilterReasons(
        row: FundamentalsTableRow,
        rule: FundamentalsProfileRule,
        strictMissingData: Boolean,
    ): List<String> {
        val reasons = mutableListOf<String>()
        if (row.fundamentalsSnapshotDate == null) {
            reasons += "MISSING_FUNDAMENTALS"
            return reasons
        }

        val roceMin = rule.roceMinPercent
        if (roceMin != null) {
            val roce = row.rocePercent
            if (roce == null) {
                if (strictMissingData) {
                    reasons += "MISSING_ROCE"
                }
            } else if (roce < roceMin) {
                reasons += "FAIL_ROCE"
            }
        }

        if (rule.debtToEquityMax != null && strictMissingData) {
            reasons += "MISSING_DEBT_TO_EQUITY"
        }
        if (rule.promoterPledgeMaxPercent != null && strictMissingData) {
            reasons += "MISSING_PROMOTER_PLEDGE"
        }
        if (rule.salesCagr3yMinPercent != null && strictMissingData) {
            reasons += "MISSING_SALES_CAGR_3Y"
        }
        if (rule.patCagr3yMinPercent != null && strictMissingData) {
            reasons += "MISSING_PAT_CAGR_3Y"
        }
        if (rule.ocfPositiveYearsMin != null && strictMissingData) {
            reasons += "MISSING_OCF_HISTORY"
        }
        if (rule.avgTradedValue20dMinCr != null && strictMissingData) {
            reasons += "MISSING_AVG_TRADED_VALUE_20D"
        }

        return reasons
    }

    private fun normalizeTag(value: String): String {
        return value.trim()
            .replace("-", "_")
            .replace(" ", "_")
            .uppercase()
    }

    private enum class FundamentalsIndexTag(
        val key: String,
        val nseIndexName: String,
        aliasesRaw: Set<String>,
    ) {
        NIFTY_50(
            key = "NIFTY_50",
            nseIndexName = "NIFTY 50",
            aliasesRaw = setOf("NIFTY_50", "NIFTY 50"),
        ),
        NIFTY_100(
            key = "NIFTY_100",
            nseIndexName = "NIFTY 100",
            aliasesRaw = setOf("NIFTY_100", "NIFTY 100"),
        ),
        NIFTY_200(
            key = "NIFTY_200",
            nseIndexName = "NIFTY 200",
            aliasesRaw = setOf("NIFTY_200", "NIFTY 200"),
        ),
        NIFTY_SMALLCAP_250(
            key = "NIFTY_SMALLCAP_250",
            nseIndexName = "NIFTY SMALLCAP 250",
            aliasesRaw = setOf("NIFTY_SMALLCAP_250", "NIFTY SMALLCAP 250"),
        );

        val aliases: Set<String> = aliasesRaw.map { raw ->
            raw.trim().replace("-", "_").replace(" ", "_").uppercase()
        }.toSet()

        companion object {
            fun fromRaw(rawTag: String?): FundamentalsIndexTag? {
                if (rawTag.isNullOrBlank()) {
                    return null
                }
                val normalized = rawTag.trim().replace("-", "_").replace(" ", "_").uppercase()
                return entries.firstOrNull { tag -> normalized in tag.aliases }
            }
        }
    }

    private enum class WeeklyPatternUniverseMode {
        ALL,
        WATCHLIST,
        ;

        companion object {
            fun fromRaw(raw: String?): WeeklyPatternUniverseMode {
                if (raw.isNullOrBlank()) return ALL
                return when (raw.trim().uppercase()) {
                    "WATCHLIST", "WATCHLIST_ONLY" -> WATCHLIST
                    else -> ALL
                }
            }
        }
    }

    private companion object {
        private const val INDEX_NIFTY_50 = "NIFTY 50"
        private const val INDEX_NIFTY_100 = "NIFTY 100"
        private const val INDEX_NIFTY_150 = "NIFTY 150"
        private const val INDEX_NIFTY_MIDCAP_150 = "NIFTY MIDCAP 150"
        private const val INDEX_NIFTY_MIDCAP_250 = "NIFTY MIDCAP 250"
        private const val INDEX_NIFTY_SMALLCAP_250 = "NIFTY SMALLCAP 250"
        private val WEEKLY_SCANNER_INDEX_NAMES: List<String> = listOf(
            "NIFTY 50",
            "NIFTY 100",
            "NIFTY MIDCAP 250",
            "NIFTY LARGEMIDCAP 250",
            "NIFTY SMALLCAP 250",
            "NIFTY BANK",
            "NIFTY FINANCIAL SERVICES",
            "NIFTY AUTO",
            "NIFTY FMCG",
            "NIFTY IT",
            "NIFTY MEDIA",
            "NIFTY METAL",
            "NIFTY PHARMA",
            "NIFTY REALTY",
            "NIFTY PSU BANK",
            "NIFTY PRIVATE BANK",
            "NIFTY OIL & GAS",
            "NIFTY CONSUMER DURABLES",
            "NIFTY FOOD & BEVERAGE",
        )
    }
}
