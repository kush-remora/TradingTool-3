package com.tradingtool.core.strategy.rsimomentum

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.database.RsiMomentumSnapshotJdbiHandler
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Singleton
class RsiMomentumHistoryService @Inject constructor(
    private val snapshotHandler: RsiMomentumSnapshotJdbiHandler,
) {
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
}
