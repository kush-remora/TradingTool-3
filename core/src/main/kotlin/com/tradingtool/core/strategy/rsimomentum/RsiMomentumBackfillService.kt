package com.tradingtool.core.strategy.rsimomentum

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.database.RsiMomentumSnapshotJdbiHandler
import com.tradingtool.core.database.StockJdbiHandler
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.technical.calculateRsi
import com.tradingtool.core.technical.getDoubleValue
import com.tradingtool.core.technical.roundTo2
import com.tradingtool.core.technical.toTa4jSeries
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

data class BackfillRequest(
    val profileId: String,
    val fromDate: String? = null,
    val toDate: String? = null,
    val skipExisting: Boolean = true,   // skip dates that already have a snapshot row
)

data class BackfillResult(
    val profileId: String,
    val fromDate: String,
    val toDate: String,
    val tradingDatesFound: Int,
    val datesSkipped: Int,              // already had snapshot
    val datesProcessed: Int,
    val datesFailed: Int,
    val message: String,
)

@Singleton
class RsiMomentumBackfillService @Inject constructor(
    private val configService: RsiMomentumConfigService,
    private val candleHandler: CandleJdbiHandler,
    private val stockHandler: StockJdbiHandler,
    private val snapshotHandler: RsiMomentumSnapshotJdbiHandler,
    private val instrumentCache: InstrumentCache,
) {
    private val log = LoggerFactory.getLogger(RsiMomentumBackfillService::class.java)
    private val mapper = ObjectMapper().registerKotlinModule()
    private val ist = ZoneId.of("Asia/Kolkata")
    private val semaphore = Semaphore(8)

    // How many days of candle history to load per stock when computing RSI for a given date
    private val candleLookbackDays: Long = 400

    suspend fun backfill(request: BackfillRequest): BackfillResult {
        val from = request.fromDate?.let { LocalDate.parse(it) } ?: LocalDate.now().minusMonths(3)
        val to = request.toDate?.let { LocalDate.parse(it) } ?: LocalDate.now()

        val config = configService.loadConfig()
        val profile = config.profiles.find { it.id == request.profileId }
            ?: return BackfillResult(
                profileId = request.profileId,
                fromDate = from.toString(),
                toDate = to.toString(),
                tradingDatesFound = 0,
                datesSkipped = 0,
                datesProcessed = 0,
                datesFailed = 0,
                message = "Profile '${request.profileId}' not found in config.",
            )

        // Get all distinct trading dates in the window from daily_candles
        val tradingDates = candleHandler.read { dao ->
            dao.getDistinctTradingDates(from, to)
        }

        if (tradingDates.isEmpty()) {
            return BackfillResult(
                profileId = request.profileId,
                fromDate = from.toString(),
                toDate = to.toString(),
                tradingDatesFound = 0,
                datesSkipped = 0,
                datesProcessed = 0,
                datesFailed = 0,
                message = "No candle data found in daily_candles for $from to $to.",
            )
        }

        // Find which dates already have snapshots
        val existingDates: Set<LocalDate> = if (request.skipExisting) {
            snapshotHandler.read { dao ->
                dao.listByProfileAndDateRange(request.profileId, from, to)
            }.map { it.asOfDate }.toSet()
        } else {
            emptySet()
        }

        val datesToProcess = tradingDates.filter { it !in existingDates }
        val skipped = tradingDates.size - datesToProcess.size

        log.info(
            "Backfill started: profile={} from={} to={} tradingDates={} skipped={} toProcess={}",
            request.profileId, from, to, tradingDates.size, skipped, datesToProcess.size,
        )

        if (datesToProcess.isEmpty()) {
            return BackfillResult(
                profileId = request.profileId,
                fromDate = from.toString(),
                toDate = to.toString(),
                tradingDatesFound = tradingDates.size,
                datesSkipped = skipped,
                datesProcessed = 0,
                datesFailed = 0,
                message = "All ${tradingDates.size} trading dates already have snapshots.",
            )
        }

        // Load universe once — same for all dates
        val watchlistStocks = stockHandler.read { dao -> dao.listAll() }
        val baseSymbols = configService.loadBaseUniverseSymbols(profile.baseUniversePreset)
        val universe = RsiMomentumUniverseBuilder.build(
            baseSymbols = baseSymbols,
            watchlistStocks = watchlistStocks,
            tokenLookup = { symbol -> instrumentCache.token("NSE", symbol) },
            companyNameLookup = { symbol -> instrumentCache.find("NSE", symbol)?.name?.takeIf { it.isNotBlank() } },
        )

        log.info("Backfill universe: profile={} members={}", request.profileId, universe.members.size)

        // Load all candles for all universe members once (full history up to `to`)
        // Then slice per date during ranking — avoids N*D DB queries
        val allCandlesByToken: Map<Long, List<com.tradingtool.core.candle.DailyCandle>> = run {
            val candleFrom = from.minusDays(candleLookbackDays)
            supervisorScope {
                universe.members.map { member ->
                    async {
                        semaphore.withPermit {
                            val candles = candleHandler.read { dao ->
                                dao.getDailyCandles(member.instrumentToken, candleFrom, to)
                            }.sortedBy { it.candleDate }
                            member.instrumentToken to candles
                        }
                    }
                }.awaitAll()
            }.toMap()
        }

        var processed = 0
        var failed = 0

        // Track previous holdings day-by-day for rebalance continuity
        var previousHoldings: List<String> = emptyList()

        for (asOfDate in datesToProcess.sorted()) {
            runCatching {
                val snapshot = computeSnapshotForDate(
                    asOfDate = asOfDate,
                    profile = profile,
                    config = config,
                    universe = universe,
                    allCandlesByToken = allCandlesByToken,
                    previousHoldings = previousHoldings,
                )

                if (snapshot.available) {
                    val snapshotJson = mapper.writeValueAsString(snapshot)
                    snapshotHandler.write { dao ->
                        dao.upsert(
                            profileId = snapshot.profileId,
                            asOfDate = asOfDate,
                            runAt = OffsetDateTime.ofInstant(Instant.now(), ist),
                            snapshotJson = snapshotJson,
                        )
                    }
                    previousHoldings = snapshot.holdings.map { it.symbol }
                    processed++
                    log.info("Backfill date done: profile={} date={} holdings={}", request.profileId, asOfDate, snapshot.holdings.size)
                }
            }.onFailure { e ->
                log.warn("Backfill failed for profile={} date={}: {}", request.profileId, asOfDate, e.message)
                failed++
            }
        }

        val message = "Backfill complete: $processed dates processed, $skipped skipped, $failed failed."
        log.info("Backfill finished: profile={} {}", request.profileId, message)

        return BackfillResult(
            profileId = request.profileId,
            fromDate = from.toString(),
            toDate = to.toString(),
            tradingDatesFound = tradingDates.size,
            datesSkipped = skipped,
            datesProcessed = processed,
            datesFailed = failed,
            message = message,
        )
    }

    private fun computeSnapshotForDate(
        asOfDate: LocalDate,
        profile: RsiMomentumProfileConfig,
        config: RsiMomentumConfig,
        universe: com.tradingtool.core.strategy.rsimomentum.UniverseBuildResult,
        allCandlesByToken: Map<Long, List<com.tradingtool.core.candle.DailyCandle>>,
        previousHoldings: List<String>,
    ): RsiMomentumSnapshot {
        val requiredBars = requiredBarCount(profile.rsiPeriods)

        val metrics = universe.members.mapNotNull { member ->
            val candles = allCandlesByToken[member.instrumentToken]
                ?.filter { it.candleDate <= asOfDate }
                ?: return@mapNotNull null

            if (candles.size < requiredBars) return@mapNotNull null

            val avgTradedValueCr = candles
                .takeLast(AVERAGE_TRADED_VALUE_LOOKBACK_DAYS)
                .map { c -> (c.close * c.volume) / CRORE_DIVISOR }
                .average()
                .roundTo2()

            if (avgTradedValueCr < profile.minAverageTradedValue) return@mapNotNull null

            runCatching {
                val series = candles.toTa4jSeries(member.symbol)
                val rsiValues = profile.rsiPeriods.sorted().associateWith { period ->
                    series.calculateRsi(period).getDoubleValue(series.endIndex, 50.0).roundTo2()
                }
                val latestClose = candles.last().close.roundTo2()
                val sma20 = candles.takeLast(AVERAGE_TRADED_VALUE_LOOKBACK_DAYS).map { it.close }.average().roundTo2()
                val buyZoneWindow = candles.takeLast(BUY_ZONE_LOOKBACK_DAYS)
                val rsi14Series = series.calculateRsi(14)
                val rsiStartIndex = (series.endIndex - RSI_BOUNDS_LOOKBACK_DAYS + 1).coerceAtLeast(0)
                val rsiWindow = (rsiStartIndex..series.endIndex).map { i -> rsi14Series.getDoubleValue(i, 50.0).roundTo2() }

                SecurityMetrics(
                    member = member,
                    asOfDate = asOfDate,
                    avgRsi = rsiValues.values.average().roundTo2(),
                    rsi22 = rsiValues[22] ?: rsiValues.values.first(),
                    rsi44 = rsiValues[44] ?: rsiValues.values.elementAtOrElse(1) { rsiValues.values.first() },
                    rsi66 = rsiValues[66] ?: rsiValues.values.last(),
                    close = latestClose,
                    sma20 = sma20,
                    buyZoneLow10w = buyZoneWindow.minOf { it.low }.roundTo2(),
                    buyZoneHigh10w = buyZoneWindow.maxOf { it.high }.roundTo2(),
                    lowestRsi50d = rsiWindow.minOrNull() ?: 50.0,
                    highestRsi50d = rsiWindow.maxOrNull() ?: 50.0,
                    avgTradedValueCr = avgTradedValueCr,
                )
            }.getOrNull()
        }

        val ranked = RsiMomentumRanker.rank(
            metrics = metrics,
            previousHoldings = previousHoldings,
            candidateCount = profile.candidateCount,
            boardDisplayCount = profile.boardDisplayCount,
            replacementPoolCount = profile.replacementPoolCount,
            holdingCount = profile.holdingCount,
            maxExtensionAboveSma20ForNewEntry = profile.maxExtensionAboveSma20ForNewEntry,
            maxExtensionAboveSma20ForSkipNewEntry = profile.maxExtensionAboveSma20ForSkipNewEntry,
        )

        return RsiMomentumSnapshot(
            profileId = profile.id,
            profileLabel = profile.label,
            available = ranked.topCandidates.isNotEmpty(),
            stale = false,
            config = profile.toSummary(config.enabled),
            runAt = Instant.now().toString(),
            asOfDate = asOfDate.toString(),
            resolvedUniverseCount = universe.members.size,
            eligibleUniverseCount = metrics.size,
            topCandidates = ranked.topCandidates,
            holdings = ranked.holdings,
            rebalance = ranked.rebalance,
            diagnostics = RsiMomentumDiagnostics(
                baseUniverseCount = universe.baseUniverseCount,
                watchlistCount = universe.watchlistCount,
                watchlistAdditionsCount = universe.watchlistAdditionsCount,
            ),
        )
    }

    private fun requiredBarCount(periods: List<Int>): Int {
        val maxPeriod = periods.maxOrNull() ?: 66
        return maxOf(maxPeriod + 1, AVERAGE_TRADED_VALUE_LOOKBACK_DAYS)
    }

    companion object {
        private const val AVERAGE_TRADED_VALUE_LOOKBACK_DAYS: Int = 20
        private const val BUY_ZONE_LOOKBACK_DAYS: Int = 50
        private const val RSI_BOUNDS_LOOKBACK_DAYS: Int = 50
        private const val CRORE_DIVISOR: Double = 10_000_000.0
    }
}
