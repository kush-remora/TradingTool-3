package com.tradingtool.core.strategy.rsimomentum

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.config.IndicatorConfig
import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.database.RedisHandler
import com.tradingtool.core.database.RsiMomentumSnapshotJdbiHandler
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.model.stock.Stock
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
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
    private val snapshotHandler: RsiMomentumSnapshotJdbiHandler,
    private val indicatorConfig: IndicatorConfig = IndicatorConfig.DEFAULT,
) {
    private val log = LoggerFactory.getLogger(RsiMomentumService::class.java)
    private val mapper = ObjectMapper().registerKotlinModule()
    private val ist = ZoneId.of("Asia/Kolkata")
    private val calibrationEngine = RsiMomentumCalibrationEngine(
        configService = configService,
        candleHandler = candleHandler,
        stockHandler = stockHandler,
        instrumentCache = instrumentCache,
        zoneId = ist,
    )
    private val historyFetchMutex = Mutex()
    private val analysisSemaphore = Semaphore(MAX_ANALYSIS_CONCURRENCY)

    suspend fun getLatest(): RsiMomentumMultiSnapshot {
        val config = configService.loadConfig()
        val cached = readAggregateSnapshotOrNull()

        if (cached == null) {
            return RsiMomentumMultiSnapshot(
                profiles = config.profiles.map { profile ->
                    emptySnapshot(
                        globalConfig = config,
                        profile = profile,
                        stale = true,
                        message = if (config.enabled) {
                            "No RSI momentum snapshot available yet. Run refresh first."
                        } else {
                            "RSI momentum strategy is disabled in configuration."
                        },
                    )
                },
            )
        }

        return alignProfilesWithConfig(
            config = config,
            snapshots = cached.profiles,
            errors = cached.errors,
            partialSuccess = cached.partialSuccess,
            applyStaleCheck = true,
        )
    }

    suspend fun refreshLatest(): RsiMomentumMultiSnapshot {
        val config = configService.loadConfig()
        if (config.profiles.isEmpty()) {
            return RsiMomentumMultiSnapshot(
                profiles = emptyList(),
                errors = listOf(RsiMomentumProfileError(profileId = "global", message = "No RSI momentum profiles configured.")),
                partialSuccess = false,
            )
        }

        if (!config.enabled) {
            return RsiMomentumMultiSnapshot(
                profiles = config.profiles.map { profile ->
                    emptySnapshot(
                        globalConfig = config,
                        profile = profile,
                        stale = true,
                        message = "RSI momentum strategy is disabled in configuration.",
                    )
                },
                partialSuccess = false,
            )
        }

        ensureInstrumentCacheLoaded()
        val watchlistStocks = stockHandler.read { dao -> dao.listAll() }
        val previousAggregate = readAggregateSnapshotOrNull()
        val previousSnapshots = previousAggregate?.let { aggregate ->
            alignProfilesWithConfig(
                config = config,
                snapshots = aggregate.profiles,
                errors = aggregate.errors,
                partialSuccess = aggregate.partialSuccess,
                applyStaleCheck = false,
            ).profiles
        } ?: emptyList()
        val previousByProfileId = previousSnapshots.associateBy { snapshot -> snapshot.profileId }

        val snapshots = mutableListOf<RsiMomentumSnapshot>()
        val errors = mutableListOf<RsiMomentumProfileError>()
        var successfulProfiles = 0

        for (profile in config.profiles) {
            val previousSnapshot = previousByProfileId[profile.id]
            val refreshed = runCatching {
                refreshProfile(
                    globalConfig = config,
                    profile = profile,
                    watchlistStocks = watchlistStocks,
                    previousSnapshot = previousSnapshot,
                )
            }

            refreshed.onSuccess { snapshot ->
                successfulProfiles += 1
                snapshots.add(snapshot)
            }.onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }

                val message = error.message ?: "Unknown refresh failure."
                log.error("RSI momentum refresh failed for profile={}: {}", profile.id, message, error)
                errors.add(
                    RsiMomentumProfileError(
                        profileId = profile.id,
                        message = message,
                    ),
                )
                val fallbackSnapshot = previousSnapshot?.copy(
                    profileId = profile.id,
                    profileLabel = profile.label,
                    stale = true,
                    message = "Refresh failed: $message",
                    config = profile.toSummary(config.enabled),
                ) ?: emptySnapshot(
                    globalConfig = config,
                    profile = profile,
                    stale = true,
                    message = "Refresh failed and no previous snapshot is available. Error: $message",
                )
                snapshots.add(fallbackSnapshot)
            }
        }

        val response = RsiMomentumMultiSnapshot(
            profiles = snapshots,
            errors = errors,
            partialSuccess = errors.isNotEmpty() && successfulProfiles > 0,
        )

        redis.set(
            LATEST_SNAPSHOT_KEY,
            mapper.writeValueAsString(response),
            SNAPSHOT_TTL_SECONDS,
        )

        // Persist daily snapshot for each successfully refreshed profile
        for (snapshot in snapshots) {
            val asOfDate = snapshot.asOfDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val runAt = snapshot.runAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
            if (asOfDate != null && runAt != null && snapshot.available) {
                runCatching {
                    val snapshotJson = mapper.writeValueAsString(snapshot)
                    snapshotHandler.write { dao ->
                        dao.upsert(
                            profileId = snapshot.profileId,
                            asOfDate = asOfDate,
                            runAt = OffsetDateTime.ofInstant(runAt, ist),
                            snapshotJson = snapshotJson,
                        )
                    }
                }.onFailure { e ->
                    log.warn("Failed to persist daily snapshot for profile={} date={}: {}", snapshot.profileId, asOfDate, e.message)
                }
            }
        }

        val failedProfileIds = errors.map { error -> error.profileId }
        log.info(
            "RSI momentum refresh completed: profiles={}, successfulProfiles={}, failedProfiles={}, partialSuccess={}",
            snapshots.size,
            successfulProfiles,
            failedProfileIds,
            response.partialSuccess,
        )
        return response
    }

    suspend fun clearLatestSnapshotCache() {
        redis.delete(LATEST_SNAPSHOT_KEY)
    }

    suspend fun calibrateRsiPeriods(
        options: RsiMomentumCalibrationOptions = RsiMomentumCalibrationOptions(),
    ): RsiMomentumCalibrationReport {
        ensureInstrumentCacheLoaded()
        val config = configService.loadConfig()
        return calibrationEngine.calibrate(config, options)
    }

    suspend fun calibrateAndApplyRsiPeriods(
        options: RsiMomentumCalibrationOptions = RsiMomentumCalibrationOptions(),
    ): RsiMomentumCalibrationReport {
        ensureInstrumentCacheLoaded()
        val config = configService.loadConfig()
        val report = calibrationEngine.calibrate(config, options)
        val selectedByProfileId = report.profileResults.associateBy { result -> result.profileId }
        val updatedProfiles = config.profiles.map { profile ->
            val selected = selectedByProfileId[profile.id] ?: return@map profile
            profile.copy(
                rsiPeriods = selected.selectedRsiPeriods,
                rsiCalibrationRunAt = report.runAt,
                rsiCalibrationMethod = report.method,
                rsiCalibrationSampleRange = selected.sampleRange,
            )
        }
        configService.writeConfig(config.copy(profiles = updatedProfiles))
        return report
    }

    private suspend fun refreshProfile(
        globalConfig: RsiMomentumConfig,
        profile: RsiMomentumProfileConfig,
        watchlistStocks: List<Stock>,
        previousSnapshot: RsiMomentumSnapshot?,
    ): RsiMomentumSnapshot {
        val baseSymbols = configService.loadBaseUniverseSymbols(profile.baseUniversePreset)
        val universe = RsiMomentumUniverseBuilder.build(
            baseSymbols = baseSymbols,
            watchlistStocks = watchlistStocks,
            tokenLookup = { symbol -> instrumentCache.token("NSE", symbol) },
            companyNameLookup = { symbol -> instrumentCache.find("NSE", symbol)?.name?.takeIf { it.isNotBlank() } },
        )
        log.info(
            "RSI momentum refresh started: profile={} totalStocks={} baseUniverseCount={} watchlistCount={}",
            profile.id,
            universe.members.size,
            universe.baseUniverseCount,
            universe.watchlistCount,
        )
        val previousHoldings = previousSnapshot?.holdings?.map { holding -> holding.symbol } ?: emptyList()

        val fiveDaysAgo = LocalDate.now(ist).minusDays(5)
        val snapshot5DaysAgoRecord = snapshotHandler.read { dao ->
            dao.getLatestOnOrBefore(profile.id, fiveDaysAgo)
        }
        val previousRanks = snapshot5DaysAgoRecord?.let { record ->
            runCatching {
                val snap = mapper.readValue(record.snapshotJson, RsiMomentumSnapshot::class.java)
                snap.topCandidates.associate { it.symbol.uppercase() to it.rank }
            }.getOrNull()
        } ?: emptyMap()

        val analysis = analyzeUniverse(universe, profile)

        val rankedPortfolio = RsiMomentumRanker.rank(
            metrics = analysis.metrics,
            previousHoldings = previousHoldings,
            candidateCount = profile.candidateCount,
            boardDisplayCount = profile.boardDisplayCount,
            replacementPoolCount = profile.replacementPoolCount,
            holdingCount = profile.holdingCount,
            maxMoveFrom30DayLowPct = profile.safeRules.maxMoveFrom30DayLowPct,
            minVolumeExhaustionRatio = profile.safeRules.minVolumeExhaustionRatio,
            blockedEntryDays = profile.blockedEntryDays,
            previousRanks = previousRanks,
        )

        val snapshot = RsiMomentumSnapshot(
            profileId = profile.id,
            profileLabel = profile.label,
            available = rankedPortfolio.topCandidates.isNotEmpty(),
            stale = false,
            message = if (rankedPortfolio.topCandidates.isEmpty()) {
                "No eligible stocks matched the RSI momentum rules."
            } else {
                null
            },
            config = profile.toSummary(globalConfig.enabled),
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
        log.info(
            "RSI momentum refresh finished: profile={} totalStocks={} eligibleStocks={} backfilledStocks={} failedStocks={}",
            profile.id,
            universe.members.size,
            analysis.metrics.size,
            analysis.backfilledSymbols.size,
            analysis.failedSymbols.size,
        )
        return snapshot
    }

    private suspend fun analyzeUniverse(
        universe: UniverseBuildResult,
        config: RsiMomentumProfileConfig,
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
        config: RsiMomentumProfileConfig,
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
                        val latestClose = candles.last().close.roundTo2()
                        val sma20 = candles
                            .takeLast(AVERAGE_TRADED_VALUE_LOOKBACK_DAYS)
                            .map { candle -> candle.close }
                            .average()
                            .roundTo2()
                        val maxDailyMove5dPct = candles
                            .takeLast(6) // 6 bars needed for 5 return calculations
                            .zipWithNext { a, b ->
                                if (a.close > 0.0) ((b.close - a.close) / a.close) * 100.0 else 0.0
                            }
                            .map { Math.abs(it) }
                            .maxOrNull()
                            ?.roundTo2() ?: 0.0
                        val buyZoneWindow = candles.takeLast(BUY_ZONE_LOOKBACK_DAYS)
                        val buyZoneLow10w = buyZoneWindow.minOf { candle -> candle.low }.roundTo2()
                        val buyZoneHigh10w = buyZoneWindow.maxOf { candle -> candle.high }.roundTo2()

                        val rsi14Series = series.calculateRsi(14)
                        val rsiStartIndex = (series.endIndex - RSI_BOUNDS_LOOKBACK_DAYS + 1).coerceAtLeast(0)
                        val rsiWindow = (rsiStartIndex..series.endIndex).map { index ->
                            rsi14Series.getDoubleValue(index, 50.0).roundTo2()
                        }
                        val lowestRsi50d = rsiWindow.minOrNull() ?: 50.0
                        val highestRsi50d = rsiWindow.maxOrNull() ?: 50.0

                        val lowestLow30d = candles.takeLast(30).minOf { it.low }.roundTo2()
                        val avgVol3d = candles.takeLast(3).map { it.volume.toDouble() }.average().roundTo2()
                        val avgVol20d = candles.takeLast(20).map { it.volume.toDouble() }.average().roundTo2()

                        MemberAnalysisResult.Success(
                            metrics = SecurityMetrics(
                                member = member,
                                asOfDate = candles.maxOf { candle -> candle.candleDate },
                                avgRsi = rsiValues.values.average().roundTo2(),
                                rsi22 = rsiValues[22] ?: 50.0,
                                rsi44 = rsiValues[44] ?: 50.0,
                                rsi66 = rsiValues[66] ?: 50.0,
                                close = latestClose,
                                sma20 = sma20,
                                maxDailyMove5dPct = maxDailyMove5dPct,
                                buyZoneLow10w = buyZoneLow10w,
                                buyZoneHigh10w = buyZoneHigh10w,
                                lowestRsi50d = lowestRsi50d,
                                highestRsi50d = highestRsi50d,
                                avgTradedValueCr = avgTradedValueCr,
                                lowestLow30d = lowestLow30d,
                                avgVol3d = avgVol3d,
                                avgVol20d = avgVol20d,
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
        return isCandleDateStale(lastDate, today)
    }

    private fun buildDateRange(): Pair<Date, Date> {
        val today = LocalDate.now(ist)
        return Pair(
            Date.from(today.minusDays(HISTORY_LOOKBACK_DAYS).atStartOfDay(ist).toInstant()),
            Date.from(today.atStartOfDay(ist).toInstant()),
        )
    }

    private suspend fun readAggregateSnapshotOrNull(): RsiMomentumMultiSnapshot? {
        val cached = redis.get(LATEST_SNAPSHOT_KEY) ?: return null

        return runCatching {
            mapper.readValue(cached, RsiMomentumMultiSnapshot::class.java)
        }.getOrElse { aggregateError ->
            log.warn("Failed to deserialize RSI momentum aggregate snapshot: {}", aggregateError.message)
            runCatching {
                val legacySnapshot = mapper.readValue(cached, RsiMomentumSnapshot::class.java)
                RsiMomentumMultiSnapshot(
                    profiles = listOf(legacySnapshot),
                    errors = emptyList(),
                    partialSuccess = false,
                )
            }.onFailure { legacyError ->
                log.warn("Failed to deserialize legacy RSI momentum snapshot: {}", legacyError.message)
            }.getOrNull()
        }
    }

    private fun alignProfilesWithConfig(
        config: RsiMomentumConfig,
        snapshots: List<RsiMomentumSnapshot>,
        errors: List<RsiMomentumProfileError>,
        partialSuccess: Boolean,
        applyStaleCheck: Boolean,
    ): RsiMomentumMultiSnapshot {
        val byProfileId = snapshots.associateBy { snapshot ->
            snapshot.profileId.takeIf { it.isNotBlank() } ?: snapshot.config.profileId
        }
        val byPreset = snapshots.associateBy { snapshot -> snapshot.config.baseUniversePreset }
        val alignedSnapshots = config.profiles.map { profile ->
            val matched = byProfileId[profile.id] ?: byPreset[profile.baseUniversePreset]
            val snapshot = if (matched == null) {
                emptySnapshot(
                    globalConfig = config,
                    profile = profile,
                    stale = true,
                    message = "No RSI momentum snapshot available for this profile yet. Run refresh.",
                )
            } else {
                matched.copy(
                    profileId = profile.id,
                    profileLabel = profile.label,
                    config = profile.toSummary(config.enabled),
                )
            }

            if (applyStaleCheck) snapshot.withStaleFlagIfNeeded() else snapshot
        }
        val validProfileIds = alignedSnapshots.map { it.profileId }.toSet()
        val filteredErrors = errors.filter { error -> error.profileId in validProfileIds }

        return RsiMomentumMultiSnapshot(
            profiles = alignedSnapshots,
            errors = filteredErrors,
            partialSuccess = partialSuccess && filteredErrors.isNotEmpty(),
        )
    }

    private fun emptySnapshot(
        globalConfig: RsiMomentumConfig,
        profile: RsiMomentumProfileConfig,
        stale: Boolean,
        message: String,
    ): RsiMomentumSnapshot = RsiMomentumSnapshot(
        profileId = profile.id,
        profileLabel = profile.label,
        available = false,
        stale = stale,
        message = message,
        config = profile.toSummary(globalConfig.enabled),
    )

    private fun RsiMomentumSnapshot.withStaleFlagIfNeeded(): RsiMomentumSnapshot {
        if (!isSnapshotStale(runAt)) {
            return this
        }
        return copy(stale = true, message = message ?: "Latest RSI momentum snapshot is older than 7 days.")
    }

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
        private const val BUY_ZONE_LOOKBACK_DAYS: Int = 50
        private const val RSI_BOUNDS_LOOKBACK_DAYS: Int = 50
        private const val CRORE_DIVISOR: Double = 10_000_000.0
        private const val MAX_ANALYSIS_CONCURRENCY: Int = 8
    }

    private data class CandleLoadResult(
        val candles: List<DailyCandle>,
        val backfilledSymbol: String?,
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

internal fun latestExpectedTradingDate(today: LocalDate): LocalDate = when (today.dayOfWeek) {
    DayOfWeek.SATURDAY -> today.minusDays(1) // Friday
    DayOfWeek.SUNDAY -> today.minusDays(2) // Friday
    DayOfWeek.MONDAY -> today.minusDays(3) // Friday
    else -> today.minusDays(1)
}

internal fun isCandleDateStale(
    lastCandleDate: LocalDate,
    today: LocalDate,
    holidayGraceDays: Long = 3,
): Boolean {
    val expectedTradingDate = latestExpectedTradingDate(today)
    val staleCutoff = expectedTradingDate.minusDays(holidayGraceDays.coerceAtLeast(0))
    return lastCandleDate.isBefore(staleCutoff)
}
