package com.tradingtool.core.strategy.s4

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.config.IndicatorConfig
import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.database.RedisHandler
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.strategy.rsimomentum.RsiMomentumUniverseBuilder
import com.tradingtool.core.strategy.rsimomentum.UniverseBuildResult
import com.tradingtool.core.strategy.rsimomentum.UniverseMember
import com.tradingtool.core.strategy.volume.VolumeAnalyzer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date

@Singleton
class S4Service @Inject constructor(
    private val configService: S4ConfigService,
    private val candleHandler: CandleJdbiHandler,
    private val redis: RedisHandler,
    private val kiteClient: KiteConnectClient,
    private val instrumentCache: InstrumentCache,
    private val indicatorConfig: IndicatorConfig = IndicatorConfig.DEFAULT,
) {
    private val log = LoggerFactory.getLogger(S4Service::class.java)
    private val mapper = ObjectMapper().registerKotlinModule()
    private val ist = ZoneId.of("Asia/Kolkata")
    private val historyFetchMutex = Mutex()
    private val analysisSemaphore = Semaphore(MAX_ANALYSIS_CONCURRENCY)

    suspend fun getLatest(): S4MultiSnapshot {
        val config = configService.loadConfig()
        val cached = readAggregateSnapshotOrNull()
        if (cached == null) {
            return S4MultiSnapshot(
                profiles = config.profiles.map { profile ->
                    emptySnapshot(
                        globalConfig = config,
                        profile = profile,
                        stale = true,
                        message = if (config.enabled) {
                            "No S4 snapshot available yet. Run refresh first."
                        } else {
                            "S4 strategy is disabled in configuration."
                        },
                    )
                },
            )
        }

        return alignProfilesWithConfig(config, cached.profiles, cached.errors, cached.partialSuccess, applyStaleCheck = true)
    }

    suspend fun refreshLatest(): S4MultiSnapshot {
        val config = configService.loadConfig()
        if (config.profiles.isEmpty()) {
            return S4MultiSnapshot(
                profiles = emptyList(),
                errors = listOf(S4ProfileError(profileId = "global", message = "No S4 profiles configured.")),
                partialSuccess = false,
            )
        }

        if (!config.enabled) {
            return S4MultiSnapshot(
                profiles = config.profiles.map { profile ->
                    emptySnapshot(config, profile, stale = true, message = "S4 strategy is disabled in configuration.")
                },
            )
        }

        ensureInstrumentCacheLoaded()
        val previousAggregate = readAggregateSnapshotOrNull()
        val previousByProfileId = previousAggregate?.profiles?.associateBy { snapshot -> snapshot.profileId } ?: emptyMap()

        val snapshots = mutableListOf<S4Snapshot>()
        val errors = mutableListOf<S4ProfileError>()
        var successfulProfiles = 0

        for (profile in config.profiles) {
            val refreshed = runCatching {
                refreshProfile(config, profile, previousByProfileId[profile.id])
            }

            refreshed.onSuccess { snapshot ->
                successfulProfiles += 1
                snapshots.add(snapshot)
            }.onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
                val message = error.message ?: "Unknown refresh failure."
                log.error("S4 refresh failed for profile={}: {}", profile.id, message, error)
                errors.add(S4ProfileError(profileId = profile.id, message = message))
                snapshots.add(
                    previousByProfileId[profile.id]?.copy(
                        profileId = profile.id,
                        profileLabel = profile.label,
                        stale = true,
                        message = "Refresh failed: $message",
                        config = profile.toSummary(config.enabled),
                    ) ?: emptySnapshot(config, profile, stale = true, message = "Refresh failed and no previous snapshot is available. Error: $message"),
                )
            }
        }

        val response = S4MultiSnapshot(
            profiles = snapshots,
            errors = errors,
            partialSuccess = errors.isNotEmpty() && successfulProfiles > 0,
        )
        redis.set(LATEST_SNAPSHOT_KEY, mapper.writeValueAsString(response), SNAPSHOT_TTL_SECONDS)
        return response
    }

    private suspend fun refreshProfile(
        globalConfig: S4Config,
        profile: S4ProfileConfig,
        previousSnapshot: S4Snapshot?,
    ): S4Snapshot {
        val baseSymbols = configService.loadBaseUniverseSymbols(profile.baseUniversePreset)
        val universe = RsiMomentumUniverseBuilder.build(
            baseSymbols = baseSymbols,
            watchlistStocks = emptyList(),
            tokenLookup = { symbol -> instrumentCache.token("NSE", symbol) },
            companyNameLookup = { symbol -> instrumentCache.find("NSE", symbol)?.name?.takeIf { it.isNotBlank() } },
        )
        val analysis = analyzeUniverse(universe, profile)
        val topCandidates = S4Ranker.rank(
            profileId = profile.id,
            baseUniversePreset = profile.baseUniversePreset,
            candidates = analysis.qualifiedCandidates,
            candidateCount = profile.candidateCount,
            orderedUniverseSymbols = baseSymbols,
        )
        val latestDate = analysis.latestAnalyzedDate

        return S4Snapshot(
            profileId = profile.id,
            profileLabel = profile.label,
            available = topCandidates.isNotEmpty(),
            stale = false,
            message = if (topCandidates.isEmpty()) "No S4 candidates matched the current filters." else null,
            config = profile.toSummary(globalConfig.enabled),
            runAt = Instant.now().toString(),
            asOfDate = latestDate?.toString(),
            resolvedUniverseCount = universe.members.size,
            eligibleUniverseCount = analysis.qualifiedCandidates.size,
            topCandidates = topCandidates,
            diagnostics = S4Diagnostics(
                baseUniverseCount = universe.baseUniverseCount,
                unresolvedSymbols = universe.unresolvedSymbols,
                insufficientHistorySymbols = analysis.insufficientHistorySymbols,
                illiquidSymbols = analysis.illiquidSymbols,
                disqualifiedSymbols = analysis.disqualifiedSymbols,
                backfilledSymbols = analysis.backfilledSymbols,
                failedSymbols = analysis.failedSymbols,
            ),
        )
    }

    private suspend fun analyzeUniverse(universe: UniverseBuildResult, profile: S4ProfileConfig): S4ProfileAnalysis {
        val analysisResults = supervisorScope {
            universe.members.map { member ->
                async {
                    analysisSemaphore.withPermit {
                        analyzeMember(member, profile)
                    }
                }
            }.awaitAll()
        }

        return S4ProfileAnalysis(
            universe = universe,
            qualifiedCandidates = analysisResults.mapNotNull { result -> (result as? MemberAnalysisResult.Success)?.candidate },
            latestAnalyzedDate = analysisResults.mapNotNull { result ->
                when (result) {
                    is MemberAnalysisResult.Success -> result.candidate.analysis.asOfDate
                    is MemberAnalysisResult.InsufficientHistory -> result.asOfDate
                    is MemberAnalysisResult.Illiquid -> result.asOfDate
                    is MemberAnalysisResult.Disqualified -> result.asOfDate
                    else -> null
                }
            }.maxOrNull(),
            insufficientHistorySymbols = analysisResults.mapNotNull { result -> (result as? MemberAnalysisResult.InsufficientHistory)?.symbol }.sorted(),
            illiquidSymbols = analysisResults.mapNotNull { result -> (result as? MemberAnalysisResult.Illiquid)?.symbol }.sorted(),
            disqualifiedSymbols = analysisResults.mapNotNull { result -> (result as? MemberAnalysisResult.Disqualified)?.symbol }.sorted(),
            backfilledSymbols = analysisResults.mapNotNull { result -> result.backfilledSymbol }.distinct().sorted(),
            failedSymbols = analysisResults.mapNotNull { result -> (result as? MemberAnalysisResult.Failed)?.symbol }.sorted(),
        )
    }

    private suspend fun analyzeMember(member: UniverseMember, profile: S4ProfileConfig): MemberAnalysisResult {
        return try {
            val candleLoadResult = loadCandles(member)
            val candles = candleLoadResult.candles
            val latestCandleDate = candles.maxOfOrNull { candle -> candle.candleDate }
            if (candles.size < profile.minHistoryBars) {
                return MemberAnalysisResult.InsufficientHistory(member.symbol, latestCandleDate, candleLoadResult.backfilledSymbol)
            }

            val analysis = VolumeAnalyzer.analyze(candles)
                ?: return MemberAnalysisResult.InsufficientHistory(member.symbol, latestCandleDate, candleLoadResult.backfilledSymbol)

            if (analysis.avgTradedValueCr20d < profile.minAverageTradedValue) {
                return MemberAnalysisResult.Illiquid(member.symbol, analysis.asOfDate, candleLoadResult.backfilledSymbol)
            }

            val candidate = S4Ranker.qualify(S4CandidateInput(member = member, profile = profile, analysis = analysis))
                ?: return MemberAnalysisResult.Disqualified(member.symbol, analysis.asOfDate, candleLoadResult.backfilledSymbol)

            MemberAnalysisResult.Success(candidate, candleLoadResult.backfilledSymbol)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.warn("S4 analysis failed for {}: {}", member.symbol, error.message)
            MemberAnalysisResult.Failed(member.symbol, null)
        }
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

        return CandleLoadResult(candles = candles, backfilledSymbol = backfilledSymbol)
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

    private suspend fun readAggregateSnapshotOrNull(): S4MultiSnapshot? {
        val cached = redis.get(LATEST_SNAPSHOT_KEY) ?: return null
        return runCatching { mapper.readValue(cached, S4MultiSnapshot::class.java) }
            .getOrNull()
    }

    private fun alignProfilesWithConfig(
        config: S4Config,
        snapshots: List<S4Snapshot>,
        errors: List<S4ProfileError>,
        partialSuccess: Boolean,
        applyStaleCheck: Boolean,
    ): S4MultiSnapshot {
        val byProfileId = snapshots.associateBy { snapshot -> snapshot.profileId.takeIf { it.isNotBlank() } ?: snapshot.config.profileId }
        val byPreset = snapshots.associateBy { snapshot -> snapshot.config.baseUniversePreset }
        val alignedSnapshots = config.profiles.map { profile ->
            val matched = byProfileId[profile.id] ?: byPreset[profile.baseUniversePreset]
            val snapshot = if (matched == null) {
                emptySnapshot(config, profile, stale = true, message = "No S4 snapshot available for this profile yet. Run refresh.")
            } else {
                matched.copy(
                    profileId = profile.id,
                    profileLabel = profile.label,
                    config = profile.toSummary(config.enabled),
                )
            }
            if (applyStaleCheck) snapshot.withStaleFlagIfNeeded() else snapshot
        }
        val validProfileIds = alignedSnapshots.map { snapshot -> snapshot.profileId }.toSet()
        return S4MultiSnapshot(
            profiles = alignedSnapshots,
            errors = errors.filter { error -> error.profileId in validProfileIds },
            partialSuccess = partialSuccess && errors.any { error -> error.profileId in validProfileIds },
        )
    }

    private fun emptySnapshot(globalConfig: S4Config, profile: S4ProfileConfig, stale: Boolean, message: String): S4Snapshot =
        S4Snapshot(
            profileId = profile.id,
            profileLabel = profile.label,
            available = false,
            stale = stale,
            message = message,
            config = profile.toSummary(globalConfig.enabled),
        )

    private fun S4Snapshot.withStaleFlagIfNeeded(): S4Snapshot {
        if (!isSnapshotStale(runAt)) {
            return this
        }
        return copy(stale = true, message = message ?: "Latest S4 snapshot is older than 7 days.")
    }

    private fun isSnapshotStale(runAt: String?): Boolean {
        if (runAt.isNullOrBlank()) {
            return true
        }
        return runCatching { Instant.parse(runAt) }
            .map { instant -> instant.isBefore(ZonedDateTime.now(ist).minusDays(7).toInstant()) }
            .getOrDefault(true)
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

    companion object {
        private const val LATEST_SNAPSHOT_KEY: String = "strategy:s4:latest"
        private const val SNAPSHOT_TTL_SECONDS: Long = 7 * 24 * 60 * 60
        private const val HISTORY_LOOKBACK_DAYS: Long = 120
        private const val MAX_ANALYSIS_CONCURRENCY: Int = 8
    }

    private data class CandleLoadResult(
        val candles: List<DailyCandle>,
        val backfilledSymbol: String?,
    )

    private sealed class MemberAnalysisResult(open val backfilledSymbol: String?) {
        data class Success(val candidate: S4QualifiedCandidate, override val backfilledSymbol: String?) : MemberAnalysisResult(backfilledSymbol)
        data class InsufficientHistory(val symbol: String, val asOfDate: LocalDate?, override val backfilledSymbol: String?) : MemberAnalysisResult(backfilledSymbol)
        data class Illiquid(val symbol: String, val asOfDate: LocalDate, override val backfilledSymbol: String?) : MemberAnalysisResult(backfilledSymbol)
        data class Disqualified(val symbol: String, val asOfDate: LocalDate, override val backfilledSymbol: String?) : MemberAnalysisResult(backfilledSymbol)
        data class Failed(val symbol: String, override val backfilledSymbol: String?) : MemberAnalysisResult(backfilledSymbol)
    }
}

internal fun latestExpectedTradingDate(today: LocalDate): LocalDate = when (today.dayOfWeek) {
    DayOfWeek.SATURDAY -> today.minusDays(1)
    DayOfWeek.SUNDAY -> today.minusDays(2)
    DayOfWeek.MONDAY -> today.minusDays(3)
    else -> today.minusDays(1)
}

internal fun isCandleDateStale(lastCandleDate: LocalDate, today: LocalDate, holidayGraceDays: Long = 3): Boolean {
    val expectedTradingDate = latestExpectedTradingDate(today)
    val staleCutoff = expectedTradingDate.minusDays(holidayGraceDays.coerceAtLeast(0))
    return lastCandleDate.isBefore(staleCutoff)
}
