package com.tradingtool.core.strategy.rsimomentum

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.database.RsiMomentumSnapshotJdbiHandler
import com.tradingtool.core.technical.roundTo2
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Singleton
class RsiMomentumBacktestService @Inject constructor(
    private val snapshotHandler: RsiMomentumSnapshotJdbiHandler,
    private val candleHandler: CandleJdbiHandler,
    private val backfillService: RsiMomentumBackfillService,
) {
    private val log = LoggerFactory.getLogger(RsiMomentumBacktestService::class.java)
    private val mapper = ObjectMapper().registerKotlinModule()

    suspend fun runBacktest(request: RsiMomentumBacktestRequest): RsiMomentumBacktestReport {
        val from = request.fromDate?.let { LocalDate.parse(it) } ?: LocalDate.now().minusMonths(3)
        val to = request.toDate?.let { LocalDate.parse(it) } ?: LocalDate.now()

        if (request.runBackfill) {
            backfillService.backfill(
                BackfillRequest(
                    profileId = request.profileId,
                    fromDate = from.toString(),
                    toDate = to.toString(),
                    skipExisting = true
                )
            )
        }

        val snapshots = snapshotHandler.read { dao ->
            dao.listByProfileAndDateRange(request.profileId, from, to)
        }.map { record ->
            mapper.readValue(record.snapshotJson, RsiMomentumSnapshot::class.java)
        }

        if (snapshots.isEmpty()) {
            return emptyReport(request, from, to)
        }

        val trades = mutableListOf<BacktestTrade>()
        var currentCapital = request.initialCapital
        var activeTrade: ActiveBacktestTrade? = null

        for (snapshot in snapshots) {
            val asOfDate = LocalDate.parse(snapshot.asOfDate!!)

            if (activeTrade == null) {
                // Try to find a new entry
                val candidate = findCandidate(snapshot, request.logicType)
                if (candidate != null) {
                    val entryPrice = candidate.close
                    val targetPrice = (entryPrice * (1.0 + request.targetPct / 100.0)).roundTo2()
                    val stopLossPrice = (entryPrice * (1.0 - request.stopLossPct / 100.0)).roundTo2()
                    
                    activeTrade = ActiveBacktestTrade(
                        symbol = candidate.symbol,
                        companyName = candidate.companyName,
                        instrumentToken = candidate.instrumentToken,
                        entryDate = asOfDate,
                        entryPrice = entryPrice,
                        targetPrice = targetPrice,
                        stopLossPrice = stopLossPrice,
                        entryRank = candidate.rank,
                        entryRankImprovement = candidate.rankImprovement
                    )
                }
            } else {
                // Check if active trade exit conditions are met
                activeTrade?.let { trade ->
                    val candles = candleHandler.read { dao ->
                        dao.getDailyCandles(trade.instrumentToken, asOfDate, asOfDate)
                    }

                    if (candles.isNotEmpty()) {
                        val candle = candles.first()
                        var exited = false
                        var exitPrice = 0.0
                        var result = ""

                        if (candle.high >= trade.targetPrice) {
                            exitPrice = trade.targetPrice
                            result = "PROFIT"
                            exited = true
                        } else if (candle.low <= trade.stopLossPrice) {
                            exitPrice = trade.stopLossPrice
                            result = "LOSS"
                            exited = true
                        }

                        if (exited) {
                            val profitPct = (((exitPrice / trade.entryPrice) - 1.0) * 100.0).roundTo2()
                            val profitAmount = (currentCapital * (profitPct / 100.0)).roundTo2()
                            currentCapital += profitAmount

                            trades.add(
                                BacktestTrade(
                                    symbol = trade.symbol,
                                    companyName = trade.companyName,
                                    entryDate = trade.entryDate.toString(),
                                    exitDate = asOfDate.toString(),
                                    entryPrice = trade.entryPrice,
                                    exitPrice = exitPrice,
                                    targetPrice = trade.targetPrice,
                                    stopLossPrice = trade.stopLossPrice,
                                    result = result,
                                    profitPct = profitPct,
                                    profitAmount = profitAmount,
                                    holdingDays = ChronoUnit.DAYS.between(trade.entryDate, asOfDate).toInt(),
                                    entryRank = trade.entryRank,
                                    entryRankImprovement = trade.entryRankImprovement
                                )
                            )
                            activeTrade = null
                        }
                    }
                }
            }
        }

        val winningTrades = trades.count { it.result == "PROFIT" }
        val losingTrades = trades.count { it.result == "LOSS" }
        val totalProfit = (currentCapital - request.initialCapital).roundTo2()

        return RsiMomentumBacktestReport(
            profileId = request.profileId,
            logicType = request.logicType,
            fromDate = from.toString(),
            toDate = to.toString(),
            initialCapital = request.initialCapital,
            finalCapital = currentCapital.roundTo2(),
            totalProfit = totalProfit,
            totalProfitPct = ((totalProfit / request.initialCapital) * 100.0).roundTo2(),
            totalTrades = trades.size,
            winningTrades = winningTrades,
            losingTrades = losingTrades,
            winRate = if (trades.isNotEmpty()) ((winningTrades.toDouble() / trades.size) * 100.0).roundTo2() else 0.0,
            avgHoldingDays = if (trades.isNotEmpty()) trades.map { it.holdingDays }.average().roundTo2() else 0.0,
            trades = trades
        )
    }

    private fun findCandidate(snapshot: RsiMomentumSnapshot, logic: RsiBacktestLogicType): RsiMomentumRankedStock? {
        val safeRules = snapshot.config.safeRules
        val asOfDate = LocalDate.parse(snapshot.asOfDate!!)

        // Rule 2: Blocked Entry Days (Applicable always)
        val isBlockedDay = snapshot.config.blockedEntryDays.any { 
            it.equals(asOfDate.dayOfWeek.name, ignoreCase = true) 
        }
        if (isBlockedDay) return null

        val candidates = snapshot.topCandidates
            .filter { it.rank <= safeRules.initialRankFilter }
            // Rule 1: Replace SMA20 extension with 30-day move from low
            .filter { it.moveFrom30DayLowPct <= safeRules.maxMoveFrom30DayLowPct }
            .filter { it.maxDailyMove5dPct <= safeRules.maxDailyMove5dPct }
            // Rule 3: Volume Exhaustion (Separate Logic)
            .filter { candidate ->
                val threshold = safeRules.minVolumeExhaustionRatio
                if (threshold == null) true
                else candidate.volumeRatio >= threshold
            }

        return when (logic) {
            RsiBacktestLogicType.LEADER -> candidates.minByOrNull { it.rank }
            RsiBacktestLogicType.JUMPER -> candidates.maxByOrNull { it.rankImprovement ?: -999 }
            RsiBacktestLogicType.HYBRID -> candidates.filter { (it.rankImprovement ?: 0) > 0 }.minByOrNull { it.rank }
        }
    }

    private fun emptyReport(request: RsiMomentumBacktestRequest, from: LocalDate, to: LocalDate) = RsiMomentumBacktestReport(
        profileId = request.profileId,
        logicType = request.logicType,
        fromDate = from.toString(),
        toDate = to.toString(),
        initialCapital = request.initialCapital,
        finalCapital = request.initialCapital,
        totalProfit = 0.0,
        totalProfitPct = 0.0,
        totalTrades = 0,
        winningTrades = 0,
        losingTrades = 0,
        winRate = 0.0,
        avgHoldingDays = 0.0,
        trades = emptyList()
    )

    private data class ActiveBacktestTrade(
        val symbol: String,
        val companyName: String,
        val instrumentToken: Long,
        val entryDate: LocalDate,
        val entryPrice: Double,
        val targetPrice: Double,
        val stopLossPrice: Double,
        val entryRank: Int,
        val entryRankImprovement: Int?
    )
}
