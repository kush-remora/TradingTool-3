package com.tradingtool.core.strategy.rsimomentum

import java.time.LocalDate

// ─── History ────────────────────────────────────────────────────────────────

data class RsiMomentumHistoryEntry(
    val profileId: String,
    val asOfDate: String,
    val runAt: String,
    val snapshot: RsiMomentumSnapshot,
)

// ─── Backtest ────────────────────────────────────────────────────────────────

data class StatefulBacktestConfig(
    val enabled: Boolean = false,
    val entryRankMax: Int = 20,
    val takeProfitRank: Int = 1,
    val exitOnTakeProfitLeave: Boolean = true,
    val giveUpRankMin: Int = 25,
)

data class BacktestRequest(
    val profileId: String,
    val fromDate: String? = null,
    val toDate: String? = null,
    val initialCapital: Double = 1_000_000.0,
    val transactionCostBps: Int = 10,
    val topN: Int? = null,   // null = use all holdings (top 10); set to 3 or 5 to restrict
    val statefulConfig: StatefulBacktestConfig? = null,
)

data class StockTrade(
    val symbol: String,
    val companyName: String,
    val entryDate: String,
    val entryPrice: Double,
    val entryRank: Int,
    val entryAvgRsi: Double,
    val exitDate: String?,           // null = still held at end of window
    val exitPrice: Double?,
    val exitRank: Int?,
    val exitAvgRsi: Double?,
    val daysHeld: Int,
    val returnPct: Double?,          // null if still open
    val status: String,              // "CLOSED" | "OPEN"
)

data class BacktestSummary(
    val totalTrades: Int,
    val closedTrades: Int,
    val openPositions: Int,
    val winRate: Double?,            // % of closed trades with positive return
    val avgReturnPct: Double?,       // avg return across closed trades
    val avgDaysHeld: Double,
    val totalTurnover: Int,          // total entry + exit events
)

data class BacktestResult(
    val profileId: String,
    val fromDate: String,
    val toDate: String,
    val topN: Int?,                  // null = all holdings used
    val statefulConfig: StatefulBacktestConfig?,
    val snapshotDaysUsed: Int,
    val summary: BacktestSummary,
    val trades: List<StockTrade>,
)

// ─── Lifecycle ───────────────────────────────────────────────────────────────

data class RankTimelinePoint(
    val date: String,
    val rank: Int?,
    val inTop10: Boolean,
    val price: Double? = null,
    val avgRsi: Double? = null,
)

data class LifecycleEpisode(
    val symbol: String,
    val entryDate: String,
    val exitDate: String?,
    val daysInTop10: Int,
    val bestRank: Int,
    val bestRankDate: String,
    val exitReason: String?,  // "DROPPED_OUT" | "END_OF_WINDOW" | null
    val rankTimeline: List<RankTimelinePoint>,
)

data class RankBucketTransition(
    val from: String,   // "1-3" | "4-6" | "7-10" | "out"
    val to: String,
    val count: Int,
)

data class LifecycleSummary(
    val profileId: String,
    val fromDate: String,
    val toDate: String,
    val totalEpisodes: Int,
    val avgDaysInTop10: Double,
    val medianDaysInTop10: Double,
    val shortStayChurnRate: Double,   // fraction of episodes lasting <= 5 days
    val rankBucketTransitions: List<RankBucketTransition>,
)

data class LifecycleSymbolDetail(
    val profileId: String,
    val symbol: String,
    val fromDate: String,
    val toDate: String,
    val episodes: List<LifecycleEpisode>,
)

data class MultiSymbolHistoryResponse(
    val profileId: String,
    val fromDate: String,
    val toDate: String,
    val symbols: List<String>,
    val timelines: Map<String, List<RankTimelinePoint>>,
)
