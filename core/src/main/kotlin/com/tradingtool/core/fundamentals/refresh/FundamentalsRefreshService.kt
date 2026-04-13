package com.tradingtool.core.fundamentals.refresh

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.database.StockFundamentalsJdbiHandler
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.fundamentals.config.FundamentalsConfigService
import com.tradingtool.core.fundamentals.config.FundamentalsDataSource
import com.tradingtool.core.fundamentals.config.FundamentalsUniverseService
import com.tradingtool.core.fundamentals.screener.ScreenerFundamentalsParser
import com.tradingtool.core.fundamentals.screener.ScreenerFundamentalsSnapshot
import com.tradingtool.core.fundamentals.screener.ScreenerFundamentalsSourceAdapter
import com.tradingtool.core.http.Result
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.model.stock.Stock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.ZoneId

@Singleton
class FundamentalsRefreshService @Inject constructor(
    private val configService: FundamentalsConfigService,
    private val fundamentalsUniverseService: FundamentalsUniverseService,
    private val stockHandler: StockJdbiHandler,
    private val fundamentalsHandler: StockFundamentalsJdbiHandler,
    private val instrumentCache: InstrumentCache,
    private val kiteClient: KiteConnectClient,
    private val sourceAdapter: ScreenerFundamentalsSourceAdapter,
) {
    private val log = LoggerFactory.getLogger(FundamentalsRefreshService::class.java)

    suspend fun refreshDailySnapshots(
        symbolsOverride: List<String>? = null,
        snapshotDate: LocalDate = LocalDate.now(IST_ZONE),
    ): FundamentalsRefreshResult {
        val config = configService.loadConfig()
        if (!config.enabled) {
            error("Fundamentals refresh is disabled in ${FundamentalsConfigService.CONFIG_FILE_NAME}.")
        }
        if (config.source != FundamentalsDataSource.SCREENER) {
            error("Unsupported fundamentals source: ${config.source}")
        }

        ensureInstrumentCacheLoaded()
        val universeAssignments = fundamentalsUniverseService.resolveTargetAssignments(symbolsOverride)
        if (universeAssignments.isEmpty()) {
            return FundamentalsRefreshResult(
                snapshotDate = snapshotDate,
                requestedSymbolsOverride = symbolsOverride?.map { symbol -> symbol.trim().uppercase() }?.sorted(),
                expectedSymbolCount = 0,
                successfulCount = 0,
                resolvedInstrumentTokens = emptyList(),
                failures = emptyList(),
            )
        }

        val trackedStocks = loadTrackedStocks(universeAssignments.keys.toList())
        val targets = mutableListOf<FundamentalsTarget>()
        val failures = mutableListOf<FundamentalsRefreshFailure>()

        universeAssignments.forEach { (symbol, universe) ->
            val instrumentToken = instrumentCache.token(NSE_EXCHANGE, symbol)
            if (instrumentToken == null) {
                failures += FundamentalsRefreshFailure(
                    symbol = symbol,
                    reason = "Instrument token could not be resolved from Kite instruments.",
                )
            } else {
                targets += FundamentalsTarget(
                    stockId = trackedStocks[symbol]?.id,
                    instrumentToken = instrumentToken,
                    symbol = symbol,
                    exchange = NSE_EXCHANGE,
                    universe = universe,
                )
            }
        }

        val upserts = mutableListOf<FundamentalsUpsert>()
        targets.forEachIndexed { index, target ->
            when (val response = sourceAdapter.fetchCompanyPage(target.symbol)) {
                is Result.Success -> {
                    runCatching {
                        val snapshot = ScreenerFundamentalsParser.parse(target.symbol, response.data)
                        upserts += FundamentalsUpsert(
                            stockId = target.stockId,
                            instrumentToken = target.instrumentToken,
                            symbol = target.symbol,
                            exchange = target.exchange,
                            universe = target.universe,
                            snapshotDate = snapshotDate,
                            snapshot = snapshot,
                            sourceName = SOURCE_NAME,
                            sourceUrl = sourceAdapter.buildCompanyPageUrl(target.symbol),
                        )
                    }.onFailure { error ->
                        failures += FundamentalsRefreshFailure(
                            symbol = target.symbol,
                            reason = error.message ?: "Screener page parse failed.",
                        )
                    }
                }
                is Result.Failure -> {
                    failures += FundamentalsRefreshFailure(
                        symbol = target.symbol,
                        reason = response.error.describe(),
                    )
                }
            }

            if (index != targets.lastIndex && config.requestDelayMs > 0) {
                delay(config.requestDelayMs)
            }
        }

        if (upserts.isNotEmpty()) {
            fundamentalsHandler.write { dao ->
                upserts.forEach { upsert ->
                    dao.upsert(
                        stockId = upsert.stockId,
                        instrumentToken = upsert.instrumentToken,
                        symbol = upsert.symbol,
                        exchange = upsert.exchange,
                        universe = upsert.universe.storageValue,
                        snapshotDate = upsert.snapshotDate,
                        companyName = upsert.snapshot.companyName,
                        marketCapCr = upsert.snapshot.marketCapCr,
                        stockPe = upsert.snapshot.stockPe,
                        rocePercent = upsert.snapshot.rocePercent,
                        roePercent = upsert.snapshot.roePercent,
                        promoterHoldingPercent = upsert.snapshot.promoterHoldingPercent,
                        broadIndustry = upsert.snapshot.broadIndustry,
                        industry = upsert.snapshot.industry,
                        sourceName = upsert.sourceName,
                        sourceUrl = upsert.sourceUrl,
                    )
                }
            }
        }

        log.info(
            "Refreshed fundamentals for {} symbols on {} (successful={}, failed={})",
            universeAssignments.size,
            snapshotDate,
            upserts.size,
            failures.size,
        )

        return FundamentalsRefreshResult(
            snapshotDate = snapshotDate,
            requestedSymbolsOverride = symbolsOverride?.map { symbol -> symbol.trim().uppercase() }?.filter { symbol -> symbol.isNotEmpty() }?.distinct()?.sorted(),
            expectedSymbolCount = universeAssignments.size,
            successfulCount = upserts.size,
            resolvedInstrumentTokens = targets.map { target -> target.instrumentToken },
            failures = failures.toList(),
        )
    }

    suspend fun findRowsForDate(
        snapshotDate: LocalDate,
        instrumentTokens: List<Long>,
    ) = if (instrumentTokens.isEmpty()) {
        emptyList()
    } else {
        fundamentalsHandler.read { dao ->
            dao.findBySnapshotDateAndInstrumentTokens(snapshotDate, instrumentTokens)
        }
    }

    private suspend fun loadTrackedStocks(symbols: List<String>): Map<String, Stock> {
        return stockHandler.read { dao ->
            dao.listBySymbols(symbols, NSE_EXCHANGE)
        }.associateBy { stock -> stock.symbol.uppercase() }
    }

    private suspend fun ensureInstrumentCacheLoaded() {
        if (!instrumentCache.isEmpty()) {
            return
        }

        val instruments = withContext(Dispatchers.IO) {
            kiteClient.client().getInstruments(NSE_EXCHANGE)
        }
        instrumentCache.refresh(instruments)
    }

    private companion object {
        const val NSE_EXCHANGE: String = "NSE"
        const val SOURCE_NAME: String = "screener"
        val IST_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
    }
}

private data class FundamentalsTarget(
    val stockId: Long?,
    val instrumentToken: Long,
    val symbol: String,
    val exchange: String,
    val universe: com.tradingtool.core.delivery.model.DeliveryUniverse,
)

private data class FundamentalsUpsert(
    val stockId: Long?,
    val instrumentToken: Long,
    val symbol: String,
    val exchange: String,
    val universe: com.tradingtool.core.delivery.model.DeliveryUniverse,
    val snapshotDate: LocalDate,
    val snapshot: ScreenerFundamentalsSnapshot,
    val sourceName: String,
    val sourceUrl: String,
)
