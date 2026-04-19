package com.tradingtool.core.strategy.rsimomentum

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.database.RsiMomentumSnapshotJdbiHandler
import com.tradingtool.core.technical.roundTo2
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Singleton
class RsiMomentumHistoryService @Inject constructor(
    private val snapshotHandler: RsiMomentumSnapshotJdbiHandler,
    private val candleHandler: CandleJdbiHandler,
    private val configService: RsiMomentumConfigService,
) {
    companion object {
        private const val DRAWDOWN_GUARD_LOOKBACK_DAYS: Int = 10
        private const val DRAWDOWN_GUARD_THRESHOLD_PCT: Double = 5.0
        private const val TRAILING_STOP_PCT: Double = 8.0
        private val DRAWDOWN_THRESHOLDS: List<Int> = listOf(20, 30, 40, 50, 60)
    }

    private val log = LoggerFactory.getLogger(RsiMomentumHistoryService::class.java)
    private val mapper = ObjectMapper().registerKotlinModule()

    // ─── History ─────────────────────────────────────────────────────────────

    suspend fun getHistory(
        profileId: String,
        from: LocalDate,
        to: LocalDate,
    ): List<RsiMomentumHistoryEntry> {
        val records = snapshotHandler.read { dao ->
            dao.listByProfileAndDateRange(profileId, from, to)
        }
        return records.mapNotNull { record ->
            val snapshot = parseSnapshot(record.snapshotJson) ?: return@mapNotNull null
            RsiMomentumHistoryEntry(
                profileId = record.profileId,
                asOfDate = record.asOfDate.toString(),
                runAt = record.runAt.toInstant().toString(),
                snapshot = snapshot,
            )
        }
    }

    suspend fun getHistoryForDate(
        profileId: String,
        asOfDate: LocalDate,
    ): RsiMomentumHistoryEntry? {
        val record = snapshotHandler.read { dao ->
            dao.getByProfileAndDate(profileId, asOfDate)
        } ?: return null
        val snapshot = parseSnapshot(record.snapshotJson) ?: return null
        return RsiMomentumHistoryEntry(
            profileId = record.profileId,
            asOfDate = record.asOfDate.toString(),
            runAt = record.runAt.toInstant().toString(),
            snapshot = snapshot,
        )
    }

    suspend fun getLeadersDrawdown(
        from: LocalDate,
        to: LocalDate,
        requestedProfileIds: List<String>?,
        topN: Int?,
    ): LeadersDrawdownResponse {
        val normalizedTopN = topN?.coerceIn(1, 10) ?: 10
        val configuredProfiles = configService.loadConfig().profiles
        val configuredProfileIds = configuredProfiles.map { profile -> profile.id }
        val selectedProfileIds = requestedProfileIds
            ?.map { profileId -> profileId.trim() }
            ?.filter { profileId -> profileId.isNotEmpty() }
            ?.distinct()
            ?.filter { profileId -> configuredProfileIds.contains(profileId) }
            ?.ifEmpty { configuredProfileIds }
            ?: configuredProfileIds

        val profileLabels = configuredProfiles.associate { profile -> profile.id to profile.label }
        val candleCache = mutableMapOf<Long, List<DailyCandle>>()
        val profileSections = mutableListOf<LeadersDrawdownProfileSection>()
        val combinedStats = linkedMapOf<String, MutableLeaderStats>()

        for (profileId in selectedProfileIds) {
            val records = snapshotHandler.read { dao ->
                dao.listByProfileAndDateRange(profileId, from, to)
            }

            val statsBySymbol = linkedMapOf<String, MutableLeaderStats>()
            for (record in records) {
                val snapshot = parseSnapshot(record.snapshotJson) ?: continue
                val topRows = extractDailyTopRankedRows(snapshot, normalizedTopN)
                for (row in topRows) {
                    val key = row.symbol.uppercase()
                    val day = record.asOfDate
                    val localStats = statsBySymbol[key]
                    if (localStats == null) {
                        statsBySymbol[key] = MutableLeaderStats.from(row, day, profileId)
                    } else {
                        localStats.update(row, day, profileId)
                    }

                    val combined = combinedStats[key]
                    if (combined == null) {
                        combinedStats[key] = MutableLeaderStats.from(row, day, profileId)
                    } else {
                        combined.update(row, day, profileId)
                    }
                }
            }

            val warnings = mutableListOf<String>()
            val rows = mutableListOf<MomentumLeaderRow>()
            for (stats in statsBySymbol.values) {
                rows.add(
                    stats.toRow(
                        candleCache = candleCache,
                        candleLoader = { token -> loadDailyCandles(token, from, to) },
                        warnings = warnings,
                    )
                )
            }
            val sortedRows = rows.sortedWith(compareByDescending<MomentumLeaderRow> { row -> row.entryCount }
                .thenBy { row -> row.bestRank }
                .thenBy { row -> row.symbol })

            profileSections.add(
                LeadersDrawdownProfileSection(
                    profileId = profileId,
                    profileLabel = profileLabels[profileId] ?: profileId,
                    rowCount = sortedRows.size,
                    rows = sortedRows,
                    ddTodayBucketSummary = summarizeBuckets(sortedRows) { row -> row.ddTodayPct },
                    dd20dMinBucketSummary = summarizeBuckets(sortedRows) { row -> row.dd20dMinPct },
                    warnings = warnings,
                )
            )
        }

        val combinedWarnings = mutableListOf<String>()
        val combinedRows = mutableListOf<MomentumLeaderRow>()
        for (stats in combinedStats.values) {
            combinedRows.add(
                stats.toRow(
                    candleCache = candleCache,
                    candleLoader = { token -> loadDailyCandles(token, from, to) },
                    warnings = combinedWarnings,
                )
            )
        }
        val sortedCombinedRows = combinedRows.sortedWith(compareByDescending<MomentumLeaderRow> { row -> row.entryCount }
            .thenBy { row -> row.bestRank }
            .thenBy { row -> row.symbol })

        return LeadersDrawdownResponse(
            meta = LeadersDrawdownMeta(
                fromDate = from.toString(),
                toDate = to.toString(),
                asOfDate = to.toString(),
                topN = normalizedTopN,
                profileIds = selectedProfileIds,
            ),
            profiles = profileSections,
            combined = LeadersDrawdownCombinedSection(
                rowCount = sortedCombinedRows.size,
                rows = sortedCombinedRows,
                ddTodayBucketSummary = summarizeBuckets(sortedCombinedRows) { row -> row.ddTodayPct },
                dd20dMinBucketSummary = summarizeBuckets(sortedCombinedRows) { row -> row.dd20dMinPct },
                warnings = combinedWarnings,
            ),
        )
    }

    // ─── Backtest ─────────────────────────────────────────────────────────────

    suspend fun runBacktest(request: BacktestRequest): BacktestResult {
        val from = request.fromDate?.let { LocalDate.parse(it) } ?: LocalDate.now().minusMonths(3)
        val to = request.toDate?.let { LocalDate.parse(it) } ?: LocalDate.now()

        val records = snapshotHandler.read { dao ->
            dao.listByProfileAndDateRange(request.profileId, from, to)
        }

        val snapshots: List<Pair<LocalDate, RsiMomentumSnapshot>> = records.mapNotNull { record ->
            val snapshot = parseSnapshot(record.snapshotJson) ?: return@mapNotNull null
            record.asOfDate to snapshot
        }.sortedBy { it.first }

        log.info("Backtest started: profileId={} from={} to={} snapshotDays={}", request.profileId, from, to, snapshots.size)

        // Track open positions: symbol -> (entryDate, entrySnapshot, hitPeak)
        data class OpenPosition(
            val entryDate: LocalDate,
            val entrySnapshot: RsiMomentumSnapshot,
            var hitPeak: Boolean = false,
        )

        val openPositions = mutableMapOf<String, OpenPosition>()
        val trades = mutableListOf<StockTrade>()
        val stateful = request.statefulConfig?.takeIf { it.enabled }

        for ((date, snapshot) in snapshots) {
            val previousSymbols = openPositions.keys.toSet()
            val allRankedSymbols = (snapshot.holdings + snapshot.topCandidates).associateBy { it.symbol }

            if (stateful != null) {
                // ─── Stateful Logic ──────────────────────────────────────────
                
                // 1. Check Exits
                for (sym in previousSymbols) {
                    val open = openPositions[sym] ?: continue
                    val current = allRankedSymbols[sym]
                    val currentRank = current?.rank ?: 1000 // effectively out of ranking

                    var shouldExit = false
                    if (currentRank > stateful.giveUpRankMin) {
                        shouldExit = true
                    } else if (currentRank <= stateful.takeProfitRank) {
                        if (!stateful.exitOnTakeProfitLeave) {
                            shouldExit = true
                        } else {
                            open.hitPeak = true
                        }
                    } else if (stateful.exitOnTakeProfitLeave && open.hitPeak && currentRank > stateful.takeProfitRank) {
                        shouldExit = true
                    }

                    if (shouldExit) {
                        openPositions.remove(sym)
                        val entryStock = open.entrySnapshot.holdings.find { it.symbol == sym }
                            ?: open.entrySnapshot.topCandidates.find { it.symbol == sym }
                        val exitStock = current

                        val entryPrice = entryStock?.close
                        val exitPrice = exitStock?.close ?: snapshot.holdings.firstOrNull()?.close
                        val returnPct = if (entryPrice != null && exitPrice != null && entryPrice > 0)
                            ((exitPrice - entryPrice) / entryPrice) * 100.0 else null
                        val daysHeld = ChronoUnit.DAYS.between(open.entryDate, date).toInt().coerceAtLeast(1)

                        trades.add(StockTrade(
                            symbol = sym,
                            companyName = entryStock?.companyName ?: sym,
                            entryDate = open.entryDate.toString(),
                            entryPrice = entryPrice ?: 0.0,
                            entryRank = entryStock?.rank ?: 0,
                            entryAvgRsi = entryStock?.avgRsi ?: 0.0,
                            exitDate = date.toString(),
                            exitPrice = exitPrice,
                            exitRank = exitStock?.rank,
                            exitAvgRsi = exitStock?.avgRsi,
                            daysHeld = daysHeld,
                            returnPct = returnPct,
                            status = "CLOSED",
                        ))
                    }
                }

                // 2. Check Entries
                val eligibleEntries = allRankedSymbols.values
                    .filter { it.rank <= stateful.entryRankMax }
                    .sortedBy { it.rank }
                
                for (stock in eligibleEntries) {
                    if (!openPositions.containsKey(stock.symbol)) {
                        openPositions[stock.symbol] = OpenPosition(entryDate = date, entrySnapshot = snapshot)
                    }
                }

            } else {
                // ─── Original Top-N Logic ────────────────────────────────────
                val entriesConsideration = if (request.topN != null && request.topN > 0)
                    snapshot.holdings.sortedBy { it.rank }.take(request.topN)
                else
                    snapshot.holdings

                val currentEntrySymbols = entriesConsideration.map { it.symbol }.toSet()
                val allCurrentHoldingSymbols = snapshot.holdings.map { it.symbol }.toSet()

                // Exits
                val exits = previousSymbols - allCurrentHoldingSymbols
                for (sym in exits) {
                    val open = openPositions.remove(sym) ?: continue
                    val entryStock = open.entrySnapshot.holdings.find { it.symbol == sym }
                    val exitStock = snapshot.holdings.find { it.symbol == sym }
                        ?: snapshot.topCandidates.find { it.symbol == sym }

                    val entryPrice = entryStock?.close
                    val exitPrice = exitStock?.close ?: snapshot.holdings.firstOrNull()?.close
                    val returnPct = if (entryPrice != null && exitPrice != null && entryPrice > 0)
                        ((exitPrice - entryPrice) / entryPrice) * 100.0 else null
                    val daysHeld = ChronoUnit.DAYS.between(open.entryDate, date).toInt().coerceAtLeast(1)

                    trades.add(StockTrade(
                        symbol = sym,
                        companyName = entryStock?.companyName ?: sym,
                        entryDate = open.entryDate.toString(),
                        entryPrice = entryPrice ?: 0.0,
                        entryRank = entryStock?.rank ?: 0,
                        entryAvgRsi = entryStock?.avgRsi ?: 0.0,
                        exitDate = date.toString(),
                        exitPrice = exitPrice,
                        exitRank = exitStock?.rank,
                        exitAvgRsi = exitStock?.avgRsi,
                        daysHeld = daysHeld,
                        returnPct = returnPct,
                        status = "CLOSED",
                    ))
                }

                // Entries
                val entries = currentEntrySymbols - previousSymbols
                for (sym in entries) {
                    openPositions[sym] = OpenPosition(entryDate = date, entrySnapshot = snapshot)
                }
            }
        }

        // Close open positions at end of window (still held)
        val lastSnapshot = snapshots.lastOrNull()?.second
        for ((sym, open) in openPositions) {
            val entryStock = open.entrySnapshot.holdings.find { it.symbol == sym }
            val currentStock = lastSnapshot?.holdings?.find { it.symbol == sym }
            val entryPrice = entryStock?.close
            val currentPrice = currentStock?.close
            val returnPct = if (entryPrice != null && currentPrice != null && entryPrice > 0)
                ((currentPrice - entryPrice) / entryPrice) * 100.0 else null
            val daysHeld = ChronoUnit.DAYS.between(open.entryDate, to).toInt().coerceAtLeast(1)

            trades.add(StockTrade(
                symbol = sym,
                companyName = entryStock?.companyName ?: sym,
                entryDate = open.entryDate.toString(),
                entryPrice = entryPrice ?: 0.0,
                entryRank = entryStock?.rank ?: 0,
                entryAvgRsi = entryStock?.avgRsi ?: 0.0,
                exitDate = null,
                exitPrice = currentPrice,
                exitRank = currentStock?.rank,
                exitAvgRsi = currentStock?.avgRsi,
                daysHeld = daysHeld,
                returnPct = returnPct,
                status = "OPEN",
            ))
        }

        val sortedTrades = trades.sortedWith(compareBy({ it.entryDate }, { it.symbol }))
        val closedTrades = sortedTrades.filter { it.status == "CLOSED" }
        val closedReturns = closedTrades.mapNotNull { it.returnPct }
        val wins = closedReturns.count { it > 0 }

        val summary = BacktestSummary(
            totalTrades = sortedTrades.size,
            closedTrades = closedTrades.size,
            openPositions = sortedTrades.count { it.status == "OPEN" },
            winRate = if (closedTrades.isNotEmpty()) (wins.toDouble() / closedTrades.size) * 100.0 else null,
            avgReturnPct = if (closedReturns.isNotEmpty()) closedReturns.average() else null,
            avgDaysHeld = if (sortedTrades.isNotEmpty()) sortedTrades.map { it.daysHeld.toDouble() }.average() else 0.0,
            totalTurnover = sortedTrades.size + closedTrades.size,
        )

        return BacktestResult(
            profileId = request.profileId,
            fromDate = from.toString(),
            toDate = to.toString(),
            topN = request.topN,
            statefulConfig = request.statefulConfig,
            snapshotDaysUsed = snapshots.size,
            summary = summary,
            trades = sortedTrades,
        )
    }

    suspend fun runSimpleMomentumBacktest(request: SimpleMomentumBacktestRequest): SimpleMomentumBacktestResult {
        val from = request.fromDate?.let { LocalDate.parse(it) } ?: LocalDate.now().minusYears(1)
        val to = request.toDate?.let { LocalDate.parse(it) } ?: LocalDate.now()

        val normalized = request.normalize()

        val records = snapshotHandler.read { dao ->
            dao.listByProfileAndDateRange(normalized.profileId, from, to)
        }

        val snapshots: List<Pair<LocalDate, RsiMomentumSnapshot>> = records.mapNotNull { record ->
            val snapshot = parseSnapshot(record.snapshotJson) ?: return@mapNotNull null
            record.asOfDate to snapshot
        }.sortedBy { it.first }

        if (snapshots.isEmpty()) {
            return SimpleMomentumBacktestResult(
                profileId = normalized.profileId,
                fromDate = from.toString(),
                toDate = to.toString(),
                firstSnapshotDate = null,
                lastSnapshotDate = null,
                initialCapital = normalized.initialCapital,
                entryRankMin = normalized.entryRankMin,
                entryRankMax = normalized.entryRankMax,
                holdRankMax = normalized.holdRankMax,
                drawdownGuardLookbackDays = DRAWDOWN_GUARD_LOOKBACK_DAYS,
                drawdownGuardThresholdPct = DRAWDOWN_GUARD_THRESHOLD_PCT,
                trailingStopPct = TRAILING_STOP_PCT,
                snapshotDaysUsed = 0,
                summary = SimpleMomentumBacktestSummary(
                    totalTrades = 0,
                    closedTrades = 0,
                    openPositions = 0,
                    winRate = null,
                    finalCapital = normalized.initialCapital.roundTo2(),
                    totalProfit = 0.0,
                    totalProfitPct = 0.0,
                    cashBalance = normalized.initialCapital.roundTo2(),
                    entriesSkippedByDrawdownGuard = 0,
                    exitsByTrailingStop = 0,
                ),
                trades = emptyList(),
            )
        }

        data class OpenPosition(
            val symbol: String,
            val companyName: String,
            val instrumentToken: Long,
            val entryDate: LocalDate,
            val entryRank: Int,
            val entryPrice: Double,
            val quantity: Int,
            val investedAmount: Double,
            var peakCloseSinceEntry: Double,
        )

        val openPositions = linkedMapOf<String, OpenPosition>()
        val trades = mutableListOf<SimpleMomentumTrade>()
        val recentHighBySymbolAndDate = mutableMapOf<Pair<Long, LocalDate>, Double?>()
        var entriesSkippedByDrawdownGuard = 0
        var exitsByTrailingStop = 0
        var cash = normalized.initialCapital

        for ((date, snapshot) in snapshots) {
            val ranked = (snapshot.holdings + snapshot.topCandidates)
                .groupBy { it.symbol.uppercase() }
                .mapValues { entry -> entry.value.minByOrNull { it.rank }!! }

            val currentSymbols = openPositions.keys.toList()
            for (symbol in currentSymbols) {
                val open = openPositions[symbol] ?: continue
                val current = ranked[symbol]
                val currentRank = current?.rank ?: Int.MAX_VALUE
                val currentClose = (
                    current?.close
                        ?: loadClosePriceForDate(open.instrumentToken, date)
                    )
                if (currentClose != null && currentClose > open.peakCloseSinceEntry) {
                    open.peakCloseSinceEntry = currentClose
                }
                val trailingStopPrice = (open.peakCloseSinceEntry * (1.0 - (TRAILING_STOP_PCT / 100.0))).roundTo2()
                val trailingStopHit = currentClose != null && currentClose <= trailingStopPrice
                val leftRankBand = currentRank > normalized.holdRankMax
                if (!trailingStopHit && !leftRankBand) {
                    continue
                }

                val exitPrice = (
                    currentClose
                        ?: open.entryPrice
                    ).roundTo2()
                val exitAmount = (exitPrice * open.quantity).roundTo2()
                val pnlAmount = (exitAmount - open.investedAmount).roundTo2()
                val pnlPct = if (open.investedAmount > 0.0) {
                    ((pnlAmount / open.investedAmount) * 100.0).roundTo2()
                } else {
                    0.0
                }

                cash = (cash + exitAmount).roundTo2()
                openPositions.remove(symbol)

                val exitReason = if (trailingStopHit) {
                    exitsByTrailingStop++
                    "TRAILING_STOP_${TRAILING_STOP_PCT.toInt()}_PCT"
                } else {
                    "LEFT_TOP_${normalized.holdRankMax}"
                }

                trades += SimpleMomentumTrade(
                    symbol = open.symbol,
                    companyName = open.companyName,
                    entryDate = open.entryDate.toString(),
                    entryRank = open.entryRank,
                    entryPrice = open.entryPrice,
                    quantity = open.quantity,
                    investedAmount = open.investedAmount.roundTo2(),
                    exitDate = date.toString(),
                    exitRank = if (currentRank == Int.MAX_VALUE) null else currentRank,
                    exitPrice = exitPrice,
                    exitAmount = exitAmount,
                    pnlAmount = pnlAmount,
                    pnlPct = pnlPct,
                    daysHeld = ChronoUnit.DAYS.between(open.entryDate, date).toInt().coerceAtLeast(1),
                    status = "CLOSED",
                    exitReason = exitReason,
                    peakCloseSinceEntry = open.peakCloseSinceEntry.roundTo2(),
                    trailingStopPriceAtExit = trailingStopPrice,
                )
            }

            val eligibleEntries = ranked.values
                .filter { stock -> stock.rank in normalized.entryRankMin..normalized.entryRankMax }
                .filterNot { stock -> openPositions.containsKey(stock.symbol.uppercase()) }
                .sortedBy { stock -> stock.rank }

            val targetPositionCount = (normalized.entryRankMax - normalized.entryRankMin + 1).coerceAtLeast(1)
            val availableSlots = (targetPositionCount - openPositions.size).coerceAtLeast(0)
            if (availableSlots <= 0 || cash <= 0.0) {
                continue
            }

            val entriesToTake = mutableListOf<RsiMomentumRankedStock>()
            for (stock in eligibleEntries) {
                if (entriesToTake.size >= availableSlots) break
                val entryPrice = stock.close.roundTo2()
                if (entryPrice <= 0.0) continue
                val recentHigh = recentHighBySymbolAndDate.getOrPut(stock.instrumentToken to date) {
                    loadRecentHighCloseForDate(
                        instrumentToken = stock.instrumentToken,
                        asOfDate = date,
                        lookbackDays = DRAWDOWN_GUARD_LOOKBACK_DAYS,
                    )
                }
                if (isBlockedByDrawdownGuard(entryPrice, recentHigh)) {
                    entriesSkippedByDrawdownGuard++
                    continue
                }
                entriesToTake += stock
            }

            if (entriesToTake.isEmpty()) {
                continue
            }

            val perPositionBudget = (cash / entriesToTake.size).roundTo2()
            for (stock in entriesToTake) {
                val price = stock.close.roundTo2()
                if (price <= 0.0) continue
                val quantity = (perPositionBudget / price).toInt()
                if (quantity <= 0) continue

                val investedAmount = (quantity * price).roundTo2()
                if (investedAmount > cash) continue

                cash = (cash - investedAmount).roundTo2()
                openPositions[stock.symbol.uppercase()] = OpenPosition(
                    symbol = stock.symbol,
                    companyName = stock.companyName,
                    instrumentToken = stock.instrumentToken,
                    entryDate = date,
                    entryRank = stock.rank,
                    entryPrice = price,
                    quantity = quantity,
                    investedAmount = investedAmount,
                    peakCloseSinceEntry = price,
                )
            }
        }

        val lastSnapshot = snapshots.last().second
        val lastRanked = (lastSnapshot.holdings + lastSnapshot.topCandidates)
            .groupBy { it.symbol.uppercase() }
            .mapValues { entry -> entry.value.minByOrNull { it.rank }!! }

        val openTrades = openPositions.values.map { open ->
            val current = lastRanked[open.symbol.uppercase()]
            val markedPrice = (
                current?.close
                    ?: loadClosePriceForDate(open.instrumentToken, to)
                    ?: open.entryPrice
                ).roundTo2()
            val markedAmount = (markedPrice * open.quantity).roundTo2()
            val pnlAmount = (markedAmount - open.investedAmount).roundTo2()
            val pnlPct = if (open.investedAmount > 0.0) {
                ((pnlAmount / open.investedAmount) * 100.0).roundTo2()
            } else {
                0.0
            }

            SimpleMomentumTrade(
                symbol = open.symbol,
                companyName = open.companyName,
                entryDate = open.entryDate.toString(),
                entryRank = open.entryRank,
                entryPrice = open.entryPrice,
                quantity = open.quantity,
                investedAmount = open.investedAmount.roundTo2(),
                exitDate = null,
                exitRank = current?.rank,
                exitPrice = markedPrice,
                exitAmount = markedAmount,
                pnlAmount = pnlAmount,
                pnlPct = pnlPct,
                daysHeld = ChronoUnit.DAYS.between(open.entryDate, to).toInt().coerceAtLeast(1),
                status = "OPEN",
                exitReason = null,
                peakCloseSinceEntry = open.peakCloseSinceEntry.roundTo2(),
                trailingStopPriceAtExit = null,
            )
        }

        val allTrades = (trades + openTrades).sortedWith(compareBy({ it.entryDate }, { it.symbol }))
        val closedTrades = allTrades.filter { it.status == "CLOSED" }
        val winningClosedTrades = closedTrades.count { (it.pnlAmount ?: 0.0) > 0.0 }
        val openMarkedValue = openTrades.sumOf { it.exitAmount ?: 0.0 }.roundTo2()
        val finalCapital = (cash + openMarkedValue).roundTo2()
        val totalProfit = (finalCapital - normalized.initialCapital).roundTo2()
        val totalProfitPct = if (normalized.initialCapital > 0.0) {
            ((totalProfit / normalized.initialCapital) * 100.0).roundTo2()
        } else {
            0.0
        }

        return SimpleMomentumBacktestResult(
            profileId = normalized.profileId,
            fromDate = from.toString(),
            toDate = to.toString(),
            firstSnapshotDate = snapshots.firstOrNull()?.first?.toString(),
            lastSnapshotDate = snapshots.lastOrNull()?.first?.toString(),
            initialCapital = normalized.initialCapital,
            entryRankMin = normalized.entryRankMin,
            entryRankMax = normalized.entryRankMax,
            holdRankMax = normalized.holdRankMax,
            drawdownGuardLookbackDays = DRAWDOWN_GUARD_LOOKBACK_DAYS,
            drawdownGuardThresholdPct = DRAWDOWN_GUARD_THRESHOLD_PCT,
            trailingStopPct = TRAILING_STOP_PCT,
            snapshotDaysUsed = snapshots.size,
            summary = SimpleMomentumBacktestSummary(
                totalTrades = allTrades.size,
                closedTrades = closedTrades.size,
                openPositions = openTrades.size,
                winRate = if (closedTrades.isNotEmpty()) {
                    ((winningClosedTrades.toDouble() / closedTrades.size) * 100.0).roundTo2()
                } else {
                    null
                },
                finalCapital = finalCapital,
                totalProfit = totalProfit,
                totalProfitPct = totalProfitPct,
                cashBalance = cash.roundTo2(),
                entriesSkippedByDrawdownGuard = entriesSkippedByDrawdownGuard,
                exitsByTrailingStop = exitsByTrailingStop,
            ),
            trades = allTrades,
        )
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    suspend fun getLifecycleForSymbol(
        profileId: String,
        symbol: String,
        from: LocalDate,
        to: LocalDate,
    ): LifecycleSymbolDetail {
        val records = snapshotHandler.read { dao ->
            dao.listByProfileAndDateRange(profileId, from, to)
        }
        val snapshots = records.mapNotNull { record ->
            val snap = parseSnapshot(record.snapshotJson) ?: return@mapNotNull null
            record.asOfDate to snap
        }.sortedBy { it.first }

        val episodes = buildEpisodesForSymbol(symbol.uppercase(), snapshots)
        return LifecycleSymbolDetail(
            profileId = profileId,
            symbol = symbol.uppercase(),
            fromDate = from.toString(),
            toDate = to.toString(),
            episodes = episodes,
        )
    }

    suspend fun getMultiSymbolHistory(
        profileId: String,
        symbols: List<String>,
        from: LocalDate,
        to: LocalDate,
    ): MultiSymbolHistoryResponse {
        val records = snapshotHandler.read { dao ->
            dao.listByProfileAndDateRange(profileId, from, to)
        }
        val snapshots = records.mapNotNull { record ->
            val snap = parseSnapshot(record.snapshotJson) ?: return@mapNotNull null
            record.asOfDate to snap
        }.sortedBy { it.first }

        val timelines = symbols.associateWith { symbol ->
            // Use the same timeline logic but without episode splitting
            snapshots.map { (date, snap) ->
                val holding = snap.holdings.find { it.symbol == symbol }
                val candidate = snap.topCandidates.find { it.symbol == symbol }
                val rank = holding?.rank ?: candidate?.rank
                RankTimelinePoint(
                    date = date.toString(),
                    rank = rank,
                    inTop10 = holding != null,
                    price = holding?.close ?: candidate?.close,
                    avgRsi = holding?.avgRsi ?: candidate?.avgRsi
                )
            }
        }

        return MultiSymbolHistoryResponse(
            profileId = profileId,
            fromDate = from.toString(),
            toDate = to.toString(),
            symbols = symbols,
            timelines = timelines
        )
    }

    suspend fun getLifecycleSummary(
        profileId: String,
        from: LocalDate,
        to: LocalDate,
    ): LifecycleSummary {
        val records = snapshotHandler.read { dao ->
            dao.listByProfileAndDateRange(profileId, from, to)
        }
        val snapshots = records.mapNotNull { record ->
            val snap = parseSnapshot(record.snapshotJson) ?: return@mapNotNull null
            record.asOfDate to snap
        }.sortedBy { it.first }

        // Collect all symbols that ever appeared in top 10
        val allSymbols = snapshots.flatMap { (_, snap) ->
            snap.holdings.map { it.symbol }
        }.distinct()

        val allEpisodes = allSymbols.flatMap { symbol ->
            buildEpisodesForSymbol(symbol, snapshots)
        }

        val durations = allEpisodes.map { it.daysInTop10.toDouble() }
        val avgDays = if (durations.isEmpty()) 0.0 else durations.average()
        val medianDays = if (durations.isEmpty()) 0.0 else {
            val sorted = durations.sorted()
            val mid = sorted.size / 2
            if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
        }
        val shortStayChurnRate = if (allEpisodes.isEmpty()) 0.0 else
            allEpisodes.count { it.daysInTop10 <= 5 }.toDouble() / allEpisodes.size

        val transitions = computeRankBucketTransitions(snapshots)

        return LifecycleSummary(
            profileId = profileId,
            fromDate = from.toString(),
            toDate = to.toString(),
            totalEpisodes = allEpisodes.size,
            avgDaysInTop10 = avgDays,
            medianDaysInTop10 = medianDays,
            shortStayChurnRate = shortStayChurnRate,
            rankBucketTransitions = transitions,
        )
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun extractDailyTopRankedRows(
        snapshot: RsiMomentumSnapshot,
        topN: Int,
    ): List<RsiMomentumRankedStock> {
        return (snapshot.holdings + snapshot.topCandidates)
            .groupBy { row -> row.symbol.uppercase() }
            .mapNotNull { (_, rows) -> rows.minByOrNull { row -> row.rank } }
            .filter { row -> row.rank <= topN }
    }

    private suspend fun loadDailyCandles(
        instrumentToken: Long,
        from: LocalDate,
        to: LocalDate,
    ): List<DailyCandle> {
        return candleHandler.read { dao ->
            dao.getDailyCandles(instrumentToken, from, to)
        }
    }

    private fun summarizeBuckets(
        rows: List<MomentumLeaderRow>,
        drawdownSelector: (MomentumLeaderRow) -> Double?,
    ): DrawdownBucketSummary {
        return summarizeDrawdownBuckets(
            drawdowns = rows.map(drawdownSelector),
            thresholds = DRAWDOWN_THRESHOLDS,
        )
    }

    private fun buildEpisodesForSymbol(
        symbol: String,
        snapshots: List<Pair<LocalDate, RsiMomentumSnapshot>>,
    ): List<LifecycleEpisode> {
        val episodes = mutableListOf<LifecycleEpisode>()
        var episodeStart: LocalDate? = null
        var bestRank = Int.MAX_VALUE
        var bestRankDate: LocalDate? = null
        val currentTimeline = mutableListOf<RankTimelinePoint>()

        for ((date, snap) in snapshots) {
            val holding = snap.holdings.find { it.symbol == symbol }
            val candidate = snap.topCandidates.find { it.symbol == symbol }
            val inTop10 = holding != null
            val rank = holding?.rank ?: candidate?.rank
            val price = holding?.close ?: candidate?.close
            val avgRsi = holding?.avgRsi ?: candidate?.avgRsi

            if (inTop10 && rank != null) {
                if (episodeStart == null) {
                    episodeStart = date
                    bestRank = rank
                    bestRankDate = date
                    currentTimeline.clear()
                }
                if (rank < bestRank) {
                    bestRank = rank
                    bestRankDate = date
                }
                currentTimeline.add(RankTimelinePoint(
                    date = date.toString(),
                    rank = rank,
                    inTop10 = true,
                    price = price,
                    avgRsi = avgRsi
                ))
            } else {
                if (episodeStart != null) {
                    // Episode ended
                    val days = ChronoUnit.DAYS.between(episodeStart, date).toInt().coerceAtLeast(1)
                    currentTimeline.add(RankTimelinePoint(
                        date = date.toString(),
                        rank = rank,
                        inTop10 = false,
                        price = price,
                        avgRsi = avgRsi
                    ))
                    episodes.add(
                        LifecycleEpisode(
                            symbol = symbol,
                            entryDate = episodeStart.toString(),
                            exitDate = date.toString(),
                            daysInTop10 = days,
                            bestRank = bestRank,
                            bestRankDate = bestRankDate!!.toString(),
                            exitReason = "DROPPED_OUT",
                            rankTimeline = currentTimeline.toList(),
                        )
                    )
                    episodeStart = null
                    bestRank = Int.MAX_VALUE
                    bestRankDate = null
                    currentTimeline.clear()
                } else {
                    currentTimeline.add(RankTimelinePoint(
                        date = date.toString(),
                        rank = rank,
                        inTop10 = false,
                        price = price,
                        avgRsi = avgRsi
                    ))
                }
            }
        }

        // Close open episode at end of window
        if (episodeStart != null && snapshots.isNotEmpty()) {
            val lastDate = snapshots.last().first
            val days = ChronoUnit.DAYS.between(episodeStart, lastDate).toInt().coerceAtLeast(1)
            episodes.add(
                LifecycleEpisode(
                    symbol = symbol,
                    entryDate = episodeStart.toString(),
                    exitDate = null,
                    daysInTop10 = days,
                    bestRank = bestRank,
                    bestRankDate = bestRankDate!!.toString(),
                    exitReason = "END_OF_WINDOW",
                    rankTimeline = currentTimeline.toList(),
                )
            )
        }

        return episodes
    }

    private fun computeRankBucketTransitions(
        snapshots: List<Pair<LocalDate, RsiMomentumSnapshot>>,
    ): List<RankBucketTransition> {
        val counts = mutableMapOf<Pair<String, String>, Int>()

        for (i in 1 until snapshots.size) {
            val prev = snapshots[i - 1].second
            val curr = snapshots[i].second
            val allSymbols = (prev.holdings + curr.holdings).map { it.symbol }.distinct()

            for (symbol in allSymbols) {
                val prevRank = prev.holdings.find { it.symbol == symbol }?.rank
                val currRank = curr.holdings.find { it.symbol == symbol }?.rank
                val fromBucket = rankBucket(prevRank)
                val toBucket = rankBucket(currRank)
                if (fromBucket != toBucket) {
                    val key = fromBucket to toBucket
                    counts[key] = (counts[key] ?: 0) + 1
                }
            }
        }

        return counts.map { (key, count) ->
            RankBucketTransition(from = key.first, to = key.second, count = count)
        }.sortedByDescending { it.count }
    }

    private fun rankBucket(rank: Int?): String = when {
        rank == null -> "out"
        rank <= 3 -> "1-3"
        rank <= 6 -> "4-6"
        else -> "7-10"
    }

    private fun generateDateRange(from: LocalDate, to: LocalDate): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        var current = from
        while (!current.isAfter(to)) {
            dates.add(current)
            current = current.plusDays(1)
        }
        return dates
    }

    private fun parseSnapshot(json: String): RsiMomentumSnapshot? =
        runCatching {
            mapper.readValue(json, RsiMomentumSnapshot::class.java)
        }.onFailure { e ->
            log.warn("Failed to parse snapshot JSON: {}", e.message)
        }.getOrNull()

    private fun SimpleMomentumBacktestRequest.normalize(): SimpleMomentumBacktestRequest {
        val safeCapital = initialCapital.takeIf { it > 0.0 } ?: 200000.0
        val safeEntryRankMin = entryRankMin.coerceAtLeast(1)
        val safeEntryRankMax = entryRankMax.coerceAtLeast(safeEntryRankMin)
        val safeHoldRankMax = holdRankMax.coerceAtLeast(safeEntryRankMax)
        return copy(
            initialCapital = safeCapital,
            entryRankMin = safeEntryRankMin,
            entryRankMax = safeEntryRankMax,
            holdRankMax = safeHoldRankMax,
        )
    }

    private suspend fun loadClosePriceForDate(instrumentToken: Long, asOfDate: LocalDate): Double? {
        return candleHandler.read { dao ->
            dao.getDailyCandles(instrumentToken, asOfDate, asOfDate)
        }.firstOrNull()?.close?.roundTo2()
    }

    private suspend fun loadRecentHighCloseForDate(
        instrumentToken: Long,
        asOfDate: LocalDate,
        lookbackDays: Int,
    ): Double? {
        val fromDate = asOfDate.minusDays((lookbackDays - 1).toLong())
        return candleHandler.read { dao ->
            dao.getDailyCandles(instrumentToken, fromDate, asOfDate)
        }.maxOfOrNull { it.close }?.roundTo2()
    }

    private fun isBlockedByDrawdownGuard(entryPrice: Double, recentHigh: Double?): Boolean {
        if (recentHigh == null || recentHigh <= 0.0) return false
        val drawdownPct = ((recentHigh - entryPrice) / recentHigh) * 100.0
        return drawdownPct > DRAWDOWN_GUARD_THRESHOLD_PCT
    }

    private data class MutableLeaderStats(
        val symbol: String,
        var companyName: String,
        var instrumentToken: Long,
        var entryCount: Int,
        var bestRank: Int,
        var firstSeen: LocalDate,
        var lastSeen: LocalDate,
        val profileIds: MutableSet<String>,
    ) {
        fun update(row: RsiMomentumRankedStock, day: LocalDate, profileId: String) {
            entryCount += 1
            if (row.rank < bestRank) {
                bestRank = row.rank
            }
            if (day.isBefore(firstSeen)) {
                firstSeen = day
            }
            if (day.isAfter(lastSeen)) {
                lastSeen = day
            }
            if (companyName.isBlank() && row.companyName.isNotBlank()) {
                companyName = row.companyName
            }
            if (instrumentToken == 0L && row.instrumentToken > 0L) {
                instrumentToken = row.instrumentToken
            }
            profileIds.add(profileId)
        }

        suspend fun toRow(
            candleCache: MutableMap<Long, List<DailyCandle>>,
            candleLoader: suspend (Long) -> List<DailyCandle>,
            warnings: MutableList<String>,
        ): MomentumLeaderRow {
            val loadedCandles = if (instrumentToken <= 0L) {
                emptyList()
            } else {
                candleCache[instrumentToken] ?: run {
                    val loaded = candleLoader(instrumentToken)
                    candleCache[instrumentToken] = loaded
                    loaded
                }
            }

            if (loadedCandles.isEmpty()) {
                warnings.add("Missing candles for $symbol")
            }

            val high1yClose = loadedCandles.maxOfOrNull { candle -> candle.close }?.roundTo2()
            val todayClose = loadedCandles.lastOrNull()?.close?.roundTo2()
            val minClose20d = loadedCandles.takeLast(20).minOfOrNull { candle -> candle.close }?.roundTo2()
            val ddTodayPct = calculateDrawdownPct(high1yClose, todayClose)
            val dd20dMinPct = calculateDrawdownPct(high1yClose, minClose20d)

            return MomentumLeaderRow(
                symbol = symbol,
                companyName = companyName,
                instrumentToken = instrumentToken,
                profileIds = profileIds.toList().sorted(),
                entryCount = entryCount,
                bestRank = bestRank,
                firstSeen = firstSeen.toString(),
                lastSeen = lastSeen.toString(),
                high1yClose = high1yClose,
                todayClose = todayClose,
                minClose20d = minClose20d,
                ddTodayPct = ddTodayPct,
                dd20dMinPct = dd20dMinPct,
                ddTodayBuckets = buildDrawdownBucketFlags(ddTodayPct, DRAWDOWN_THRESHOLDS),
                dd20dMinBuckets = buildDrawdownBucketFlags(dd20dMinPct, DRAWDOWN_THRESHOLDS),
            )
        }

        companion object {
            fun from(
                row: RsiMomentumRankedStock,
                day: LocalDate,
                profileId: String,
            ): MutableLeaderStats {
                return MutableLeaderStats(
                    symbol = row.symbol.uppercase(),
                    companyName = row.companyName,
                    instrumentToken = row.instrumentToken,
                    entryCount = 1,
                    bestRank = row.rank,
                    firstSeen = day,
                    lastSeen = day,
                    profileIds = linkedSetOf(profileId),
                )
            }
        }
    }
}

internal fun calculateDrawdownPct(highClose: Double?, currentOrMinClose: Double?): Double? {
    if (highClose == null || currentOrMinClose == null || highClose <= 0.0) {
        return null
    }
    return (((currentOrMinClose / highClose) - 1.0) * 100.0).roundTo2()
}

internal fun buildDrawdownBucketFlags(drawdownPct: Double?, thresholds: List<Int>): DrawdownBucketFlags {
    fun matches(threshold: Int): Boolean {
        if (!thresholds.contains(threshold)) return false
        if (drawdownPct == null) return false
        return drawdownPct <= -threshold.toDouble()
    }

    return DrawdownBucketFlags(
        atLeast20Pct = matches(20),
        atLeast30Pct = matches(30),
        atLeast40Pct = matches(40),
        atLeast50Pct = matches(50),
        atLeast60Pct = matches(60),
    )
}

internal fun summarizeDrawdownBuckets(
    drawdowns: List<Double?>,
    thresholds: List<Int>,
): DrawdownBucketSummary {
    fun count(threshold: Int): Int {
        if (!thresholds.contains(threshold)) return 0
        return drawdowns.count { value ->
            value != null && value <= -threshold.toDouble()
        }
    }

    return DrawdownBucketSummary(
        atLeast20Pct = count(20),
        atLeast30Pct = count(30),
        atLeast40Pct = count(40),
        atLeast50Pct = count(50),
        atLeast60Pct = count(60),
    )
}
