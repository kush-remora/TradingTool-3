package com.tradingtool.resources

import com.google.inject.Inject
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
import com.tradingtool.core.screener.WeeklyPatternService
import com.tradingtool.core.watchlist.WatchlistService
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
import java.time.LocalDate
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
    private val watchlistService: WatchlistService,
    private val stockService: StockService,
    private val kiteClient: KiteConnectClient,
    private val resourceScope: ResourceScope,
) {
    private val ioScope = resourceScope.ioScope
    private val log = LoggerFactory.getLogger(javaClass)
    private data class ResolvedWeeklyUniverse(
        val symbols: List<String>,
        val sourceTags: List<String>,
    )

    /**
     * Fetches raw daily + 15-min candles from Kite and upserts them into the database.
     * Pass ?symbols=NETWEB,INFY or omit to sync all watchlist stocks.
     *
     * This is a synchronous call — expect 30–90 seconds for a full watchlist sync.
     */
    @POST
    @Path("/sync")
    fun sync(@QueryParam("symbols") symbolsParam: String?): CompletableFuture<Response> = ioScope.endpoint {
        val universe = resolveWeeklyUniverse(symbolsParam)
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
    fun weeklyPattern(@QueryParam("symbols") symbolsParam: String?): CompletableFuture<Response> = ioScope.endpoint {
        val universe = resolveWeeklyUniverse(symbolsParam)
        if (universe.symbols.isEmpty()) return@endpoint badRequest("No symbols found. Check weekly scanner index universe config.")

        val results = weeklyPatternService.analyze(universe.symbols)
        ok(WeeklyPatternListResponse(
            runAt = Instant.now().toString(),
            lookbackWeeks = weeklyPatternService.lookbackWeeks(),
            buyZoneLookbackWeeks = weeklyPatternService.buyZoneLookbackWeeks(),
            universeSourceTags = universe.sourceTags,
            results = results
        ))
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
            )
        }

        val indexSymbols = loadWeeklyScannerUniverseSymbols()
        if (indexSymbols.isNotEmpty()) {
            return ResolvedWeeklyUniverse(
                symbols = indexSymbols,
                sourceTags = WEEKLY_SCANNER_INDEX_NAMES,
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
        )
    }

    private suspend fun loadWeeklyScannerUniverseSymbols(): List<String> = coroutineScope {
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

        val missingIndexes = byIndex.filter { (_, members) -> members.isEmpty() }.map { (indexName, _) -> indexName }
        if (missingIndexes.isNotEmpty()) {
            log.warn("Weekly scanner universe fetch returned zero members for indexes={}", missingIndexes)
        }
        log.info("Weekly scanner resolved {} symbols from {} indexes", symbols.size, WEEKLY_SCANNER_INDEX_NAMES.size)
        symbols
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

    private companion object {
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
