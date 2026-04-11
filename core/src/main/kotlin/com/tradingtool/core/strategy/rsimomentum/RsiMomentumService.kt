package com.tradingtool.core.strategy.rsimomentum

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.config.IndicatorConfig
import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.database.RedisHandler
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.technical.calculateRsi
import com.tradingtool.core.technical.getDoubleValue
import com.tradingtool.core.technical.roundTo2
import com.tradingtool.core.technical.toTa4jSeries
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import kotlin.system.measureTimeMillis
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date

@Singleton
class RsiMomentumService @Inject constructor(
    private val configService: RsiMomentumConfigService,
    private val candleHandler: CandleJdbiHandler,
    private val stockHandler: StockJdbiHandler,
    private val redis: RedisHandler,
    private val kiteClient: KiteConnectClient,
    private val instrumentCache: InstrumentCache,
    private val indicatorConfig: IndicatorConfig = IndicatorConfig.DEFAULT,
) {
    private val log = LoggerFactory.getLogger(RsiMomentumService::class.java)
    private val mapper = ObjectMapper().registerKotlinModule()
    private val ist = ZoneId.of("Asia/Kolkata")
    private val historyFetchMutex = Mutex()
    private val analysisSemaphore = Semaphore(MAX_ANALYSIS_CONCURRENCY)

    suspend fun getLatest(): RsiMomentumSnapshot {
        val config = configService.loadConfig()
        val cached = redis.get(LATEST_SNAPSHOT_KEY) ?: return emptySnapshot(
            config = config,
            stale = true,
            message = "No RSI momentum snapshot available yet. Run refresh first.",
        )

        val snapshot = runCatching { mapper.readValue(cached, RsiMomentumSnapshot::class.java) }
            .getOrElse { error ->
                log.warn("Failed to deserialize RSI momentum snapshot: {}", error.message)
                return emptySnapshot(
                    config = config,
                    stale = true,
                    message = "Latest RSI momentum snapshot is unreadable. Run refresh again.",
                )
            }

        return if (isSnapshotStale(snapshot.runAt)) {
            snapshot.copy(stale = true, message = snapshot.message ?: "Latest RSI momentum snapshot is older than 7 days.")
        } else {
            snapshot
        }
    }

    suspend fun refreshLatest(): RsiMomentumSnapshot {
        val config = configService.loadConfig()
        if (!config.enabled) {
            return emptySnapshot(
                config = config,
                stale = true,
                message = "RSI momentum strategy is disabled in configuration.",
            )
        }

        ensureInstrumentCacheLoaded()

        val refreshInputs = loadRefreshInputs(config)
        val universe = RsiMomentumUniverseBuilder.build(
            baseSymbols = refreshInputs.baseSymbols,
            watchlistStocks = refreshInputs.watchlistStocks,
            tokenLookup = { symbol -> instrumentCache.token("NSE", symbol) },
            companyNameLookup = { symbol -> instrumentCache.find("NSE", symbol)?.name?.takeIf { it.isNotBlank() } },
        )
        log.info(
            "RSI momentum refresh started: totalStocks={}, baseUniverseCount={}, watchlistCount={}",
            universe.members.size,
            universe.baseUniverseCount,
            universe.watchlistCount,
        )
        val previousHoldings = refreshInputs.previousSnapshot?.holdings
            ?.map { holding -> holding.symbol }
            ?: emptyList()
        val analysis = analyzeUniverse(universe, config)

        val rankedPortfolio = RsiMomentumRanker.rank(
            metrics = analysis.metrics,
            previousHoldings = previousHoldings,
            candidateCount = config.candidateCount,
            holdingCount = config.holdingCount,
        )

        val snapshot = RsiMomentumSnapshot(
            available = rankedPortfolio.topCandidates.isNotEmpty(),
            stale = false,
            message = if (rankedPortfolio.topCandidates.isEmpty()) {
                "No eligible stocks matched the RSI momentum rules."
            } else {
                null
            },
            config = config.toSummary(),
            runAt = Instant.now().toString(),
            asOfDate = rankedPortfolio.asOfDate?.toString(),
            resolvedUniverseCount = universe.members.size,
            eligibleUniverseCount = analysis.metrics.size,
            topCandidates = rankedPortfolio.topCandidates,
            holdings = rankedPortfolio.holdings,
            rebalance = rankedPortfolio.rebalance,
            diagnostics = RsiMomentumDiagnostics(
                baseUniverseCount = universe.baseUniverseCount,
                watchlistCount = universe.watchlistCount,
                watchlistAdditionsCount = universe.watchlistAdditionsCount,
                unresolvedSymbols = universe.unresolvedSymbols,
                insufficientHistorySymbols = analysis.insufficientHistorySymbols,
                illiquidSymbols = analysis.illiquidSymbols,
                backfilledSymbols = analysis.backfilledSymbols,
                failedSymbols = analysis.failedSymbols,
            ),
        )

        redis.set(
            LATEST_SNAPSHOT_KEY,
            mapper.writeValueAsString(snapshot),
            SNAPSHOT_TTL_SECONDS,
        )
        log.info(
            "RSI momentum refresh finished: totalStocks={}, eligibleStocks={}, backfilledStocks={}, failedStocks={}",
            universe.members.size,
            analysis.metrics.size,
            analysis.backfilledSymbols.size,
            analysis.failedSymbols.size,
        )
        return snapshot
    }

    private suspend fun loadRefreshInputs(config: RsiMomentumConfig): RefreshInputs = supervisorScope {
        val baseSymbolsDeferred = async {
            configService.loadBaseUniverseSymbols(config.baseUniversePreset)
        }
        val watchlistStocksDeferred = async {
            stockHandler.read { dao -> dao.listAll() }
        }
        val previousSnapshotDeferred = async {
            readSnapshotOrNull()
        }

        RefreshInputs(
            baseSymbols = baseSymbolsDeferred.await(),
            watchlistStocks = watchlistStocksDeferred.await(),
            previousSnapshot = previousSnapshotDeferred.await(),
        )
    }

    private suspend fun analyzeUniverse(
        universe: UniverseBuildResult,
        config: RsiMomentumConfig,
    ): AnalysisSummary {
        log.info("RSI momentum batch started: totalStocks={}", universe.members.size)
        val analysisResults = supervisorScope {
            universe.members.mapIndexed { index, member ->
                async {
                    analysisSemaphore.withPermit {
                        analyzeMember(
                            member = member,
                            config = config,
                            stockIndex = index + 1,
                            totalStocks = universe.members.size,
                        )
                    }
                }
            }.awaitAll()
        }
        log.info("RSI momentum batch finished: totalStocks={}", universe.members.size)

        return AnalysisSummary(
            metrics = analysisResults.mapNotNull { result ->
                (result as? MemberAnalysisResult.Success)?.metrics
            },
            insufficientHistorySymbols = analysisResults.mapNotNull { result ->
                (result as? MemberAnalysisResult.InsufficientHistory)?.symbol
            }.sorted(),
            illiquidSymbols = analysisResults.mapNotNull { result ->
                (result as? MemberAnalysisResult.Illiquid)?.symbol
            }.sorted(),
            backfilledSymbols = analysisResults.mapNotNull { result ->
                result.backfilledSymbol
            }.distinct().sorted(),
            failedSymbols = analysisResults.mapNotNull { result ->
                (result as? MemberAnalysisResult.Failed)?.symbol
            }.sorted(),
        )
    }

    private suspend fun analyzeMember(
        member: UniverseMember,
        config: RsiMomentumConfig,
        stockIndex: Int,
        totalStocks: Int,
    ): MemberAnalysisResult {
        log.info(
            "RSI momentum stock processing started: stock={}/{} symbol={}",
            stockIndex,
            totalStocks,
            member.symbol,
        )
        var outcome: String = "UNKNOWN"
        lateinit var result: MemberAnalysisResult
        val durationMs = measureTimeMillis {
            result = try {
                val candleLoadResult = loadCandles(member)
                val candles = candleLoadResult.candles
                if (candles.size < requiredBarCount(config.rsiPeriods)) {
                    outcome = "INSUFFICIENT_HISTORY"
                    MemberAnalysisResult.InsufficientHistory(
                        symbol = member.symbol,
                        backfilledSymbol = candleLoadResult.backfilledSymbol,
                    )
                } else {
                    val avgTradedValueCr = candles
                        .takeLast(AVERAGE_TRADED_VALUE_LOOKBACK_DAYS)
                        .map { candle -> (candle.close * candle.volume) / CRORE_DIVISOR }
                        .average()
                        .roundTo2()

                    if (avgTradedValueCr < config.minAverageTradedValue) {
                        outcome = "ILLIQUID"
                        MemberAnalysisResult.Illiquid(
                            symbol = member.symbol,
                            backfilledSymbol = candleLoadResult.backfilledSymbol,
                        )
                    } else {
                        val series = candles.toTa4jSeries(member.symbol)
                        val rsiValues = config.rsiPeriods.sorted().associateWith { period ->
                            series.calculateRsi(period).getDoubleValue(series.endIndex, 50.0).roundTo2()
                        }

                        outcome = "SUCCESS"
                        MemberAnalysisResult.Success(
                            metrics = SecurityMetrics(
                                member = member,
                                asOfDate = candles.maxOf { candle -> candle.candleDate },
                                avgRsi = rsiValues.values.average().roundTo2(),
                                rsi22 = rsiValues[22] ?: 50.0,
                                rsi44 = rsiValues[44] ?: 50.0,
                                rsi66 = rsiValues[66] ?: 50.0,
                                avgTradedValueCr = avgTradedValueCr,
                            ),
                            backfilledSymbol = candleLoadResult.backfilledSymbol,
                        )
                    }
                }
            } catch (error: CancellationException) {
                outcome = "CANCELLED"
                throw error
            } catch (error: Exception) {
                log.warn("RSI momentum analysis failed for {}: {}", member.symbol, error.message)
                outcome = "FAILED"
                MemberAnalysisResult.Failed(
                    symbol = member.symbol,
                    backfilledSymbol = null,
                )
            }
        }
        log.info(
            "RSI momentum stock processing finished: stock={}/{} symbol={} outcome={} durationMs={}",
            stockIndex,
            totalStocks,
            member.symbol,
            outcome,
            durationMs,
        )
        return result
    }

    private suspend fun loadCandles(member: UniverseMember): CandleLoadResult {
        val today = LocalDate.now(ist)
        val from = today.minusDays(HISTORY_LOOKBACK_DAYS)

        var candles = candleHandler.read { dao ->
            dao.getDailyCandles(member.instrumentToken, from, today)
        }.sortedBy { candle -> candle.candleDate }
        var backfilledSymbol: String? = null

        if (candles.isEmpty() || isCandleDataStale(candles)) {
            syncDailyCandles(member)
            backfilledSymbol = member.symbol
            candles = candleHandler.read { dao ->
                dao.getDailyCandles(member.instrumentToken, from, today)
            }.sortedBy { candle -> candle.candleDate }
        }

        return CandleLoadResult(
            candles = candles,
            backfilledSymbol = backfilledSymbol,
        )
    }

    private suspend fun syncDailyCandles(member: UniverseMember) {
        val history = historyFetchMutex.withLock {
            delay(indicatorConfig.kiteRateLimitDelayMs)

            val (fromDate, toDate) = buildDateRange()
            withContext(Dispatchers.IO) {
                kiteClient.client().getHistoricalData(
                    fromDate,
                    toDate,
                    member.instrumentToken.toString(),
                    "day",
                    false,
                    false,
                )
            }
        }

        val candles = history.dataArrayList.mapNotNull { bar ->
            if (bar == null) return@mapNotNull null

            runCatching {
                val timestamp = LocalDateTime.parse(bar.timeStamp.substring(0, 19)).toLocalDate()
                DailyCandle(
                    instrumentToken = member.instrumentToken,
                    symbol = member.symbol,
                    candleDate = timestamp,
                    open = bar.open,
                    high = bar.high,
                    low = bar.low,
                    close = bar.close,
                    volume = bar.volume.toLong(),
                )
            }.getOrNull()
        }

        if (candles.isNotEmpty()) {
            candleHandler.write { dao -> dao.upsertDailyCandles(candles) }
        }
    }

    private suspend fun ensureInstrumentCacheLoaded() {
        if (!instrumentCache.isEmpty()) {
            return
        }

        val instruments = withContext(Dispatchers.IO) {
            kiteClient.client().getInstruments("NSE")
        }
        instrumentCache.refresh(instruments)
    }

    private fun isCandleDataStale(candles: List<DailyCandle>): Boolean {
        val lastDate = candles.maxOfOrNull { candle -> candle.candleDate } ?: return true
        val today = LocalDate.now(ist)
        return lastDate.isBefore(today.minusDays(1))
    }

    private fun buildDateRange(): Pair<Date, Date> {
        val today = LocalDate.now(ist)
        return Pair(
            Date.from(today.minusDays(HISTORY_LOOKBACK_DAYS).atStartOfDay(ist).toInstant()),
            Date.from(today.atStartOfDay(ist).toInstant()),
        )
    }

    private suspend fun readSnapshotOrNull(): RsiMomentumSnapshot? {
        val cached = redis.get(LATEST_SNAPSHOT_KEY) ?: return null
        return runCatching { mapper.readValue(cached, RsiMomentumSnapshot::class.java) }
            .getOrNull()
    }

    private fun emptySnapshot(
        config: RsiMomentumConfig,
        stale: Boolean,
        message: String,
    ): RsiMomentumSnapshot = RsiMomentumSnapshot(
        available = false,
        stale = stale,
        message = message,
        config = config.toSummary(),
    )

    private fun isSnapshotStale(runAt: String?): Boolean {
        if (runAt.isNullOrBlank()) {
            return true
        }

        return runCatching { Instant.parse(runAt) }
            .map { instant ->
                val cutoff = ZonedDateTime.now(ist).minusDays(7).toInstant()
                instant.isBefore(cutoff)
            }
            .getOrDefault(true)
    }

    private fun requiredBarCount(periods: List<Int>): Int {
        val maxPeriod = periods.maxOrNull() ?: 66
        return maxOf(maxPeriod + 1, AVERAGE_TRADED_VALUE_LOOKBACK_DAYS)
    }

    companion object {
        private const val LATEST_SNAPSHOT_KEY: String = "strategy:rsi-momentum:latest"
        private const val SNAPSHOT_TTL_SECONDS: Long = 7 * 24 * 60 * 60
        private const val HISTORY_LOOKBACK_DAYS: Long = 400
        private const val AVERAGE_TRADED_VALUE_LOOKBACK_DAYS: Int = 20
        private const val CRORE_DIVISOR: Double = 10_000_000.0
        private const val MAX_ANALYSIS_CONCURRENCY: Int = 8
    }

    private data class CandleLoadResult(
        val candles: List<DailyCandle>,
        val backfilledSymbol: String?,
    )

    private data class RefreshInputs(
        val baseSymbols: List<String>,
        val watchlistStocks: List<com.tradingtool.core.model.stock.Stock>,
        val previousSnapshot: RsiMomentumSnapshot?,
    )

    private data class AnalysisSummary(
        val metrics: List<SecurityMetrics>,
        val insufficientHistorySymbols: List<String>,
        val illiquidSymbols: List<String>,
        val backfilledSymbols: List<String>,
        val failedSymbols: List<String>,
    )

    private sealed class MemberAnalysisResult(open val backfilledSymbol: String?) {
        data class Success(
            val metrics: SecurityMetrics,
            override val backfilledSymbol: String?,
        ) : MemberAnalysisResult(backfilledSymbol)

        data class InsufficientHistory(
            val symbol: String,
            override val backfilledSymbol: String?,
        ) : MemberAnalysisResult(backfilledSymbol)

        data class Illiquid(
            val symbol: String,
            override val backfilledSymbol: String?,
        ) : MemberAnalysisResult(backfilledSymbol)

        data class Failed(
            val symbol: String,
            override val backfilledSymbol: String?,
        ) : MemberAnalysisResult(backfilledSymbol)
    }
}
