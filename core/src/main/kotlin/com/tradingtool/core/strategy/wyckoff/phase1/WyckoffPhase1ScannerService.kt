package com.tradingtool.core.strategy.wyckoff.phase1

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.database.StockDeliveryJdbiHandler
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.indexconstituents.dao.IndexConstituentUpsertRow
import com.tradingtool.core.indexconstituents.dao.IndexSummary
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.screener.CandleDataService
import org.slf4j.LoggerFactory
import java.time.LocalDate

@Singleton
class WyckoffPhase1ScannerService @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val stockHandler: StockJdbiHandler,
    private val deliveryHandler: StockDeliveryJdbiHandler,
    private val candleCacheService: CandleCacheService,
    private val candleDataService: CandleDataService,
    private val kiteClient: KiteConnectClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val engine = WyckoffPhase1ScannerEngine()

    suspend fun run(
        runConfig: WyckoffPhase1RunConfig,
        config: WyckoffPhase1Config,
    ): WyckoffPhase1RunResponse {
        val resolvedSymbols = resolveUniverse(runConfig, config)
        if (resolvedSymbols.isEmpty()) {
            return WyckoffPhase1RunResponse(
                rows = emptyList(),
                meta = WyckoffPhase1RunMeta(
                    as_of_date = runConfig.asOfDate.toString(),
                    evaluated_trading_dates = emptyList(),
                    universe_count = 0,
                    matched_count = 0,
                ),
            )
        }

        val warmupFrom = runConfig.asOfDate.minusDays(HISTORY_WARMUP_DAYS)
        val candlesBySymbol = resolvedSymbols.associate { resolved ->
            resolved.symbol to loadCandles(
                symbol = resolved.symbol,
                instrumentToken = resolved.instrumentToken,
                fromDate = warmupFrom,
                toDate = runConfig.asOfDate,
            )
        }.toMutableMap()

        val symbolsNeedingBackfill = resolvedSymbols
            .filter { resolved ->
                val candles = candlesBySymbol[resolved.symbol].orEmpty()
                val latest = candles.lastOrNull()?.candleDate
                candles.isEmpty() || latest == null || latest.isBefore(runConfig.asOfDate)
            }
            .map { resolved -> resolved.symbol }

        if (symbolsNeedingBackfill.isNotEmpty()) {
            runCatching {
                candleDataService.syncDailyRange(
                    symbols = symbolsNeedingBackfill,
                    fromDate = warmupFrom,
                    toDate = runConfig.asOfDate,
                    kiteClient = kiteClient,
                )
            }.onFailure { error ->
                log.warn(
                    "Wyckoff Phase-1 candle backfill failed for {} symbols: {}",
                    symbolsNeedingBackfill.size,
                    error.message,
                )
            }

            resolvedSymbols
                .filter { resolved -> symbolsNeedingBackfill.contains(resolved.symbol) }
                .forEach { resolved ->
                    candlesBySymbol[resolved.symbol] = loadCandles(
                        symbol = resolved.symbol,
                        instrumentToken = resolved.instrumentToken,
                        fromDate = warmupFrom,
                        toDate = runConfig.asOfDate,
                    )
                }
        }

        val contexts = resolvedSymbols.mapNotNull { resolved ->
            val candles = candlesBySymbol[resolved.symbol].orEmpty()
            if (candles.isEmpty()) {
                return@mapNotNull null
            }
            val deliveries = deliveryHandler.read { dao ->
                dao.findByInstrumentTokenBetweenDates(
                    instrumentToken = resolved.instrumentToken,
                    fromDate = warmupFrom,
                    toDate = runConfig.asOfDate,
                )
            }

            WyckoffPhase1SymbolContext(
                symbol = resolved.symbol,
                instrumentToken = resolved.instrumentToken,
                companyName = resolved.companyName,
                indexKey = resolved.indexKey,
                deliveryThresholdPct = resolved.deliveryThresholdPct,
                candles = candles,
                deliveries = deliveries,
            )
        }

        return engine.evaluate(
            config = config,
            runConfig = runConfig,
            contexts = contexts,
        )
    }

    private suspend fun resolveUniverse(
        runConfig: WyckoffPhase1RunConfig,
        config: WyckoffPhase1Config,
    ): List<WyckoffPhase1ResolvedSymbol> {
        val normalizedUniverseKeys = runConfig.universeKeys
            .map { raw -> normalizeIndexKeyInCore(raw) }
            .distinct()

        val includeWatchlist = normalizedUniverseKeys.contains(WATCHLIST_KEY)
        val selectedIndexKeys = normalizedUniverseKeys.filterNot { key -> key == WATCHLIST_KEY }

        val members = loadIndexMembers(selectedIndexKeys)
        val watchlistStocks = if (includeWatchlist || runConfig.symbols.isNotEmpty()) {
            stockHandler.read { dao -> dao.listAll() }
        } else {
            emptyList()
        }

        val manualSymbols = runConfig.symbols
            .map { symbol -> symbol.trim().uppercase() }
            .filter { symbol -> symbol.isNotEmpty() }

        val membershipBySymbol = members
            .groupBy { member -> member.symbol.trim().uppercase() }

        val watchlistBySymbol = watchlistStocks
            .associateBy { stock -> stock.symbol.trim().uppercase() }

        val seedSymbols = linkedSetOf<String>()
        seedSymbols += membershipBySymbol.keys
        if (includeWatchlist) {
            seedSymbols += watchlistBySymbol.keys
        }
        seedSymbols += manualSymbols

        return seedSymbols.mapNotNull { symbol ->
            val memberships = membershipBySymbol[symbol].orEmpty()
            if (memberships.isNotEmpty()) {
                val resolvedMembership = resolveMembershipByHighestThreshold(memberships, config)
                    ?: return@mapNotNull null
                return@mapNotNull WyckoffPhase1ResolvedSymbol(
                    symbol = symbol,
                    instrumentToken = resolvedMembership.member.instrumentToken,
                    companyName = resolvedMembership.member.companyName,
                    indexKey = resolvedMembership.member.indexKey,
                    deliveryThresholdPct = resolvedMembership.threshold,
                )
            }

            val watchlist = watchlistBySymbol[symbol] ?: return@mapNotNull null
            if (watchlist.instrumentToken <= 0L) {
                return@mapNotNull null
            }
            WyckoffPhase1ResolvedSymbol(
                symbol = symbol,
                instrumentToken = watchlist.instrumentToken,
                companyName = watchlist.companyName,
                indexKey = WATCHLIST_KEY,
                deliveryThresholdPct = thresholdForIndex(config, WATCHLIST_KEY),
            )
        }.sortedBy { resolved -> resolved.symbol }
    }

    private suspend fun loadIndexMembers(indexKeys: List<String>): List<IndexConstituentUpsertRow> {
        if (indexKeys.isEmpty()) {
            return emptyList()
        }

        val summaries: List<IndexSummary> = indexConstituentHandler.read { dao -> dao.listUniqueIndices() }
        val indexKeysByNormalized = summaries
            .groupBy { summary -> normalizeIndexKeyInCore(summary.indexKey) }
            .mapValues { (_, rows) -> rows.map { row -> row.indexKey } }

        val membersByResolvedKey = mutableMapOf<String, List<IndexConstituentUpsertRow>>()
        val members = mutableListOf<IndexConstituentUpsertRow>()

        indexKeys.forEach { indexKey ->
            val resolvedKeys = indexKeysByNormalized[indexKey].orEmpty()
            if (resolvedKeys.isEmpty()) {
                val rows = membersByResolvedKey.getOrPut(indexKey) {
                    indexConstituentHandler.read { dao -> dao.listActiveByIndex(indexKey) }
                }
                members += rows
            } else {
                resolvedKeys.distinct().forEach { resolvedKey ->
                    val rows = membersByResolvedKey.getOrPut(resolvedKey) {
                        indexConstituentHandler.read { dao -> dao.listActiveByIndex(resolvedKey) }
                    }
                    members += rows
                }
            }
        }

        return members
    }

    private suspend fun loadCandles(
        symbol: String,
        instrumentToken: Long,
        fromDate: LocalDate,
        toDate: LocalDate,
    ) = candleCacheService
        .getDailyCandles(
            token = instrumentToken,
            symbol = symbol,
            from = fromDate,
            to = toDate,
        )
        .sortedBy { candle -> candle.candleDate }

    private fun resolveMembershipByHighestThreshold(
        members: List<IndexConstituentUpsertRow>,
        config: WyckoffPhase1Config,
    ): Phase1ResolvedMembership? {
        return members
            .map { member ->
                val threshold = thresholdForIndex(config, member.indexKey)
                Phase1ResolvedMembership(member = member, threshold = threshold)
            }
            .sortedWith(
                compareByDescending<Phase1ResolvedMembership> { resolved -> resolved.threshold }
                    .thenBy { resolved -> resolved.member.indexKey },
            )
            .firstOrNull()
    }

    private fun thresholdForIndex(config: WyckoffPhase1Config, indexKey: String): Double {
        val normalized = normalizeIndexKeyInCore(indexKey)
        val capKey = when {
            normalized.contains("NANOCAP") -> "NANO_CAP"
            normalized.contains("MICROCAP") -> "MICRO_CAP"
            normalized.contains("SMALLCAP") -> "SMALL_CAP"
            else -> "MID_CAP"
        }
        return config.trackA.deliveryThresholdByCap[capKey]
            ?: config.trackA.deliveryThresholdByCap["MID_CAP"]
            ?: DEFAULT_MID_CAP_THRESHOLD
    }

    companion object {
        private const val HISTORY_WARMUP_DAYS: Long = 500
        private const val DEFAULT_MID_CAP_THRESHOLD = 55.0
        private const val WATCHLIST_KEY = "WATCHLIST"
    }
}

private data class Phase1ResolvedMembership(
    val member: IndexConstituentUpsertRow,
    val threshold: Double,
)

internal fun normalizeIndexKeyInCore(raw: String): String {
    return raw.trim()
        .uppercase()
        .replace(Regex("[^A-Z0-9]+"), "_")
        .replace(Regex("_+"), "_")
        .trim('_')
}
