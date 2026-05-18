package com.tradingtool.core.strategy.deliverythreshold

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.database.StockDeliveryJdbiHandler
import com.tradingtool.core.indexconstituents.dao.IndexConstituentUpsertRow
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.indexconstituents.dao.IndexSummary
import com.tradingtool.core.screener.CandleDataService
import java.time.LocalDate
import org.slf4j.LoggerFactory

@Singleton
class DeliveryThresholdBacktestService @Inject constructor(
    private val indexConstituentHandler: IndexConstituentJdbiHandler,
    private val deliveryHandler: StockDeliveryJdbiHandler,
    private val candleCacheService: CandleCacheService,
    private val candleDataService: CandleDataService,
    private val kiteClient: KiteConnectClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val engine = DeliveryThresholdBacktestEngine()

    suspend fun runBacktest(config: DeliveryThresholdBacktestRunConfig): DeliveryThresholdBacktestResponse {
        val resolvedUniverse = resolveUniverse(config)
        if (resolvedUniverse.symbols.isEmpty()) {
            return DeliveryThresholdBacktestResponse(
                config = DeliveryThresholdBacktestConfigSnapshot(
                    indexKeys = config.indexKeys,
                    symbols = config.symbols,
                    thresholds = config.thresholdsByIndex,
                    profitPct = config.profitPct,
                    fromDate = config.fromDate.toString(),
                    toDate = config.toDate.toString(),
                ),
                summary = DeliveryThresholdBacktestSummary(
                    totalBuys = 0,
                    hitCount = 0,
                    hitRatePct = 0.0,
                    daysToHitAvg = null,
                    daysToHitMedian = null,
                    daysToHitMin = null,
                    daysToHitMax = null,
                    openCount = 0,
                ),
                rows = emptyList(),
            )
        }

        val warmupFrom = config.fromDate.minusDays(HISTORY_WARMUP_DAYS)
        val candlesBySymbol = resolvedUniverse.symbols.associate { symbol ->
            val candles = loadCandles(symbol.symbol, symbol.instrumentToken, warmupFrom, config.toDate)
            symbol.symbol to candles
        }.toMutableMap()

        fun candlesForSymbol(symbol: String): List<com.tradingtool.core.candle.DailyCandle> {
            return candlesBySymbol[symbol].orEmpty()
        }

        val requiredLookbackStart = config.fromDate.minusDays(INDICATOR_LOOKBACK_DAYS)
        val symbolsNeedingBackfill = resolvedUniverse.symbols
            .filter { symbol ->
                val candles = candlesForSymbol(symbol.symbol)
                val earliest = candles.firstOrNull()?.candleDate
                val latest = candles.lastOrNull()?.candleDate
                candles.isEmpty() ||
                    earliest == null ||
                    earliest.isAfter(requiredLookbackStart) ||
                    latest == null ||
                    latest.isBefore(config.toDate)
            }
            .map { symbol -> symbol.symbol }
            .distinct()

        if (symbolsNeedingBackfill.isNotEmpty()) {
            runCatching {
                candleDataService.syncDailyRange(
                    symbols = symbolsNeedingBackfill,
                    fromDate = warmupFrom,
                    toDate = config.toDate,
                    kiteClient = kiteClient,
                )
            }.onFailure { error ->
                log.warn(
                    "Delivery threshold backtest candle backfill failed for {} symbols: {}",
                    symbolsNeedingBackfill.size,
                    error.message,
                )
            }

            resolvedUniverse.symbols
                .filter { symbol -> symbolsNeedingBackfill.contains(symbol.symbol) }
                .forEach { symbol ->
                    candlesBySymbol[symbol.symbol] = loadCandles(
                        symbol = symbol.symbol,
                        instrumentToken = symbol.instrumentToken,
                        fromDate = warmupFrom,
                        toDate = config.toDate,
                    )
                }
        }

        val contexts = resolvedUniverse.symbols.mapNotNull { symbol ->
            val candles = candlesForSymbol(symbol.symbol)
            if (candles.isEmpty()) {
                return@mapNotNull null
            }
            val deliveries = deliveryHandler.read { dao ->
                dao.findByInstrumentTokenBetweenDates(
                    instrumentToken = symbol.instrumentToken,
                    fromDate = config.fromDate,
                    toDate = config.toDate,
                )
            }

            DeliveryThresholdSymbolContext(
                symbol = symbol.symbol,
                instrumentToken = symbol.instrumentToken,
                companyName = symbol.companyName,
                resolvedIndexKey = symbol.resolvedIndexKey,
                threshold = symbol.threshold,
                candles = candles,
                deliveries = deliveries,
            )
        }

        return engine.run(config, contexts)
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

    private suspend fun resolveUniverse(config: DeliveryThresholdBacktestRunConfig): DeliveryThresholdResolvedUniverse {
        val members = loadIndexMembers(config.indexKeys)
        if (members.isEmpty()) {
            return DeliveryThresholdResolvedUniverse(symbols = emptyList())
        }

        val requestedSymbols = config.symbols
            .map { symbol -> symbol.trim().uppercase() }
            .filter { symbol -> symbol.isNotEmpty() }
            .toSet()

        val groupedBySymbol = members
            .groupBy { member -> member.symbol.trim().uppercase() }
            .filterKeys { symbol -> requestedSymbols.isEmpty() || requestedSymbols.contains(symbol) }

        val resolved = groupedBySymbol.mapNotNull { (symbol, symbolMembers) ->
            val resolvedMembership = resolveHighestThresholdMembership(symbolMembers, config.thresholdsByIndex)
                ?: return@mapNotNull null

            DeliveryThresholdResolvedSymbol(
                symbol = symbol,
                instrumentToken = resolvedMembership.member.instrumentToken,
                companyName = resolvedMembership.member.companyName,
                resolvedIndexKey = resolvedMembership.member.indexKey,
                threshold = resolvedMembership.threshold,
            )
        }.sortedBy { symbol -> symbol.symbol }

        return DeliveryThresholdResolvedUniverse(symbols = resolved)
    }

    private suspend fun loadIndexMembers(indexKeys: List<String>): List<IndexConstituentUpsertRow> {
        val summaries: List<IndexSummary> = indexConstituentHandler.read { dao -> dao.listUniqueIndices() }
        val indexKeysByNormalized = summaries
            .groupBy { summary -> normalizeIndexKeyInCore(summary.indexKey) }
            .mapValues { (_, rows) -> rows.map { row -> row.indexKey } }

        val membersByResolvedKey = mutableMapOf<String, List<IndexConstituentUpsertRow>>()
        val members = mutableListOf<IndexConstituentUpsertRow>()
        indexKeys.forEach { indexKey ->
            val normalized = normalizeIndexKeyInCore(indexKey)
            val resolvedKeys = indexKeysByNormalized[normalized].orEmpty()
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

    companion object {
        private const val HISTORY_WARMUP_DAYS: Long = 500
        private const val INDICATOR_LOOKBACK_DAYS: Long = 252
    }
}

internal fun resolveHighestThresholdMembership(
    members: List<IndexConstituentUpsertRow>,
    thresholdsByIndex: Map<String, Double>,
): ResolvedMembership? {
    val ranked = members
        .mapNotNull { member ->
            val threshold = thresholdsByIndex[normalizeIndexKeyInCore(member.indexKey)] ?: return@mapNotNull null
            ResolvedMembership(member = member, threshold = threshold)
        }
        .sortedWith(
            compareByDescending<ResolvedMembership> { item -> item.threshold }
                .thenBy { item -> item.member.indexKey },
        )

    return ranked.firstOrNull()
}

internal data class ResolvedMembership(
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
