package com.tradingtool.core.strategy.rsimomentum

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.database.CandleJdbiHandler
import com.tradingtool.core.database.RsiMomentumSnapshotJdbiHandler
import com.tradingtool.core.technical.calculateEma
import com.tradingtool.core.technical.calculateRsi
import com.tradingtool.core.technical.getDoubleValue
import com.tradingtool.core.technical.roundTo2
import com.tradingtool.core.technical.toTa4jSeries
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
        val normalized = request.normalize()

        if (normalized.runBackfill) {
            backfillService.backfill(
                BackfillRequest(
                    profileId = normalized.profileId,
                    fromDate = from.toString(),
                    toDate = to.toString(),
                    skipExisting = true,
                ),
            )
        }

        val snapshots = snapshotHandler.read { dao ->
            dao.listByProfileAndDateRange(normalized.profileId, from, to)
        }.map { record ->
            mapper.readValue(record.snapshotJson, RsiMomentumSnapshot::class.java)
        }.sortedBy { snapshot -> snapshot.asOfDate }

        if (snapshots.isEmpty()) {
            return emptyReport(normalized, from, to)
        }

        val blockedDays = normalized.blockedEntryDays.map { it.trim().uppercase() }.toSet()

        val trades = mutableListOf<BacktestTrade>()
        var currentCapital = normalized.initialCapital
        var activeTrade: ActiveBacktestTrade? = null

        snapshots.forEachIndexed { index, snapshot ->
            val asOfDate = LocalDate.parse(snapshot.asOfDate!!)

            if (activeTrade == null) {
                if (blockedDays.contains(asOfDate.dayOfWeek.name)) {
                    return@forEachIndexed
                }

                val candidateContext = findCandidate(
                    snapshot = snapshot,
                    snapshots = snapshots,
                    currentIndex = index,
                    request = normalized,
                )

                if (candidateContext != null) {
                    val candidate = candidateContext.stock
                    val entryPrice = candidate.close
                    val targetPrice = (entryPrice * (1.0 + normalized.targetPct / 100.0)).roundTo2()
                    val stopLossPrice = (entryPrice * (1.0 - normalized.stopLossPct / 100.0)).roundTo2()

                    activeTrade = ActiveBacktestTrade(
                        symbol = candidate.symbol,
                        companyName = candidate.companyName,
                        instrumentToken = candidate.instrumentToken,
                        entryDate = asOfDate,
                        entrySnapshotIndex = index,
                        entryPrice = entryPrice,
                        targetPrice = targetPrice,
                        stopLossPrice = stopLossPrice,
                        entryRank = candidate.rank,
                        entryRankImprovement = candidate.rankImprovement,
                        entryRsi22 = candidate.rsi22,
                        entryFarthestRankInLookback = candidateContext.farthestRankInLookback,
                        entryJumpFromFarthest = candidateContext.jumpFromFarthest,
                    )
                }
                return@forEachIndexed
            }

            val trade = activeTrade ?: return@forEachIndexed
            val candle = candleHandler.read { dao ->
                dao.getDailyCandles(trade.instrumentToken, asOfDate, asOfDate)
            }.firstOrNull() ?: return@forEachIndexed

            val holdingTradingDays = index - trade.entrySnapshotIndex
            val shouldExitByTime = holdingTradingDays >= 3
            val rsi22 = loadRsi22ForDate(trade.instrumentToken, asOfDate)
            val shouldExitByRsi = rsi22 != null && rsi22 >= normalized.rsiExitThreshold

            val shouldExit = when (normalized.exitMode) {
                RsiBacktestExitMode.T_PLUS_3 -> shouldExitByTime
                RsiBacktestExitMode.RSI_60 -> shouldExitByRsi
                RsiBacktestExitMode.T_PLUS_3_OR_RSI_60 -> shouldExitByTime || shouldExitByRsi
            }

            if (!shouldExit) {
                return@forEachIndexed
            }

            val exitPrice = candle.close.roundTo2()
            val profitPct = (((exitPrice / trade.entryPrice) - 1.0) * 100.0).roundTo2()
            val profitAmount = (currentCapital * (profitPct / 100.0)).roundTo2()
            val result = if (profitPct >= 0.0) "PROFIT" else "LOSS"
            currentCapital += profitAmount

            val exitReason = when {
                shouldExitByRsi && normalized.exitMode == RsiBacktestExitMode.T_PLUS_3_OR_RSI_60 ->
                    "RSI_${normalized.rsiExitThreshold.roundTo2()}_HIT"
                shouldExitByRsi -> "RSI_${normalized.rsiExitThreshold.roundTo2()}_HIT"
                else -> "T_PLUS_3"
            }

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
                    entryRankImprovement = trade.entryRankImprovement,
                    entryRsi22 = trade.entryRsi22,
                    exitRsi22 = rsi22,
                    entryFarthestRankInLookback = trade.entryFarthestRankInLookback,
                    entryJumpFromFarthest = trade.entryJumpFromFarthest,
                    exitReason = exitReason,
                ),
            )
            activeTrade = null
        }

        val winningTrades = trades.count { it.result == "PROFIT" }
        val losingTrades = trades.count { it.result == "LOSS" }
        val totalProfit = (currentCapital - normalized.initialCapital).roundTo2()

        return RsiMomentumBacktestReport(
            profileId = normalized.profileId,
            logicType = normalized.logicType,
            fromDate = from.toString(),
            toDate = to.toString(),
            initialCapital = normalized.initialCapital,
            finalCapital = currentCapital.roundTo2(),
            totalProfit = totalProfit,
            totalProfitPct = ((totalProfit / normalized.initialCapital) * 100.0).roundTo2(),
            totalTrades = trades.size,
            winningTrades = winningTrades,
            losingTrades = losingTrades,
            winRate = if (trades.isNotEmpty()) ((winningTrades.toDouble() / trades.size) * 100.0).roundTo2() else 0.0,
            avgHoldingDays = if (trades.isNotEmpty()) trades.map { it.holdingDays }.average().roundTo2() else 0.0,
            trades = trades,
            entryRankMin = normalized.entryRankMin,
            entryRankMax = normalized.entryRankMax,
            rankLookbackDays = normalized.rankLookbackDays,
            jumpMin = normalized.jumpMin,
            jumpMax = normalized.jumpMax,
            blockedEntryDays = normalized.blockedEntryDays,
            exitMode = normalized.exitMode,
            rsiExitThreshold = normalized.rsiExitThreshold,
        )
    }

    private suspend fun findCandidate(
        snapshot: RsiMomentumSnapshot,
        snapshots: List<RsiMomentumSnapshot>,
        currentIndex: Int,
        request: RsiMomentumBacktestRequest,
    ): CandidateContext? {
        val asOfDate = LocalDate.parse(snapshot.asOfDate!!)
        val ema20Cache = mutableMapOf<Long, Double?>()
        val candidates = mutableListOf<CandidateContext>()

        for (candidate in snapshot.topCandidates) {
            if (candidate.rank !in request.entryRankMin..request.entryRankMax) continue
            if (candidate.rsi22 !in ENTRY_RSI22_MIN..ENTRY_RSI22_MAX) continue

            val ema20 = ema20Cache.getOrPut(candidate.instrumentToken) {
                loadEma20ForDate(candidate.instrumentToken, asOfDate)
            }
            if (ema20 == null || candidate.close <= ema20) continue

            val farthestRank = findFarthestRankInLookback(
                snapshots = snapshots,
                currentIndex = currentIndex,
                symbol = candidate.symbol,
                rankLookbackDays = request.rankLookbackDays,
            ) ?: continue

            val jumpFromFarthest = farthestRank - candidate.rank
            if (jumpFromFarthest < request.jumpMin || jumpFromFarthest > request.jumpMax) continue

            candidates += CandidateContext(
                stock = candidate,
                farthestRankInLookback = farthestRank,
                jumpFromFarthest = jumpFromFarthest,
            )
        }

        if (candidates.isEmpty()) {
            return null
        }

        return when (request.logicType) {
            RsiBacktestLogicType.LEADER -> candidates.minByOrNull { it.stock.rank }
            RsiBacktestLogicType.JUMPER -> candidates.maxByOrNull { it.jumpFromFarthest }
            RsiBacktestLogicType.HYBRID -> candidates
                .sortedWith(compareByDescending<CandidateContext> { it.jumpFromFarthest }.thenBy { it.stock.rank })
                .firstOrNull()
        }
    }

    private fun findFarthestRankInLookback(
        snapshots: List<RsiMomentumSnapshot>,
        currentIndex: Int,
        symbol: String,
        rankLookbackDays: Int,
    ): Int? {
        val startIndex = (currentIndex - rankLookbackDays + 1).coerceAtLeast(0)
        val upperSymbol = symbol.uppercase()
        var farthest: Int? = null

        for (i in startIndex..currentIndex) {
            val rank = snapshots[i].topCandidates
                .firstOrNull { candidate -> candidate.symbol.equals(upperSymbol, ignoreCase = true) }
                ?.rank
                ?: continue
            farthest = if (farthest == null) rank else maxOf(farthest, rank)
        }

        return farthest
    }

    private suspend fun loadRsi22ForDate(instrumentToken: Long, asOfDate: LocalDate): Double? {
        val lookbackFrom = asOfDate.minusDays(80)
        val candles = candleHandler.read { dao ->
            dao.getDailyCandles(instrumentToken, lookbackFrom, asOfDate)
        }.sortedBy { candle -> candle.candleDate }

        if (candles.size < 23) {
            return null
        }

        val series = candles.toTa4jSeries(instrumentToken.toString())
        return series.calculateRsi(22).getDoubleValue(series.endIndex, 50.0).roundTo2()
    }

    private suspend fun loadEma20ForDate(instrumentToken: Long, asOfDate: LocalDate): Double? {
        val lookbackFrom = asOfDate.minusDays(80)
        val candles = candleHandler.read { dao ->
            dao.getDailyCandles(instrumentToken, lookbackFrom, asOfDate)
        }.sortedBy { candle -> candle.candleDate }

        if (candles.size < 20) {
            return null
        }

        val series = candles.toTa4jSeries(instrumentToken.toString())
        return series.calculateEma(20).getDoubleValue(series.endIndex, candles.last().close).roundTo2()
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
        trades = emptyList(),
        entryRankMin = request.entryRankMin,
        entryRankMax = request.entryRankMax,
        rankLookbackDays = request.rankLookbackDays,
        jumpMin = request.jumpMin,
        jumpMax = request.jumpMax,
        blockedEntryDays = request.blockedEntryDays,
        exitMode = request.exitMode,
        rsiExitThreshold = request.rsiExitThreshold,
    )

    private fun RsiMomentumBacktestRequest.normalize(): RsiMomentumBacktestRequest {
        val normalizedRankMin = entryRankMin.coerceAtLeast(1)
        val normalizedRankMax = entryRankMax.coerceAtLeast(normalizedRankMin)
        val normalizedLookbackDays = rankLookbackDays.coerceIn(5, 10)
        val normalizedJumpMin = jumpMin
        val normalizedJumpMax = jumpMax.coerceAtLeast(normalizedJumpMin)

        return copy(
            entryRankMin = normalizedRankMin,
            entryRankMax = normalizedRankMax,
            rankLookbackDays = normalizedLookbackDays,
            jumpMin = normalizedJumpMin,
            jumpMax = normalizedJumpMax,
            blockedEntryDays = blockedEntryDays.map { day -> day.trim().uppercase() }.filter { it.isNotBlank() },
        )
    }

    private data class CandidateContext(
        val stock: RsiMomentumRankedStock,
        val farthestRankInLookback: Int,
        val jumpFromFarthest: Int,
    )

    private data class ActiveBacktestTrade(
        val symbol: String,
        val companyName: String,
        val instrumentToken: Long,
        val entryDate: LocalDate,
        val entrySnapshotIndex: Int,
        val entryPrice: Double,
        val targetPrice: Double,
        val stopLossPrice: Double,
        val entryRank: Int,
        val entryRankImprovement: Int?,
        val entryRsi22: Double?,
        val entryFarthestRankInLookback: Int?,
        val entryJumpFromFarthest: Int?,
    )

    companion object {
        private const val ENTRY_RSI22_MIN = 50.0
        private const val ENTRY_RSI22_MAX = 55.0
    }
}
