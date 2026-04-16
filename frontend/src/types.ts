// ==================== Live Tick (SSE Stream) ====================

export interface TickSnapshot {
  instrumentToken: number;
  ltp: number;
  volume: number;
  changePercent: number;
  open: number;
  high: number;
  low: number;
  close: number;
  updatedAt: number;
}

export interface LiveMarketUpdate {
  symbol: string;
  instrumentToken: number;
  ltp: number;
  changePercent: number;
  high: number;
  low: number;
  volume: number;
  buyQuantity: number;
  sellQuantity: number;
  buyPressurePct: number | null;
  sellPressurePct: number | null;
  pressureSide: "BUYERS_AGGRESSIVE" | "SELLERS_AGGRESSIVE" | "NEUTRAL" | string;
  avgVol20d: number | null;
  volumeHeat: number | null;
  updatedAt: number;
}

export interface TradeReadinessAlert {
  raw_text: string;
  action: "BUY" | "SELL" | string | null;
  limit_price: number | null;
  target_price: number | null;
  received_at: string;
}

export interface TradeReadinessSymbol {
  symbol: string;
  company_name: string;
  rsi14: number | null;
  rsi15m: number | null;
  alerts: TradeReadinessAlert[];
}

export interface TradeReadinessResponse {
  symbols: TradeReadinessSymbol[];
}

// ==================== Stock (Master Record) ====================

export interface StockTag {
  name: string;
  color: string;
}

export interface Stock {
  id: number;
  symbol: string;
  instrument_token: number;
  company_name: string;
  exchange: string;
  notes: string | null;
  priority: number | null;
  tags: StockTag[];
  created_at: string;
  updated_at: string;
}

// ==================== Kite Instruments ====================

export interface InstrumentSearchResult {
  instrument_token: number;
  trading_symbol: string;
  company_name: string;
  exchange: string;
  instrument_type: string;
}

// ==================== Trades ====================

export interface GttTarget {
  percent: number;
  price: string;
  yield_percent: string;
}

export interface Trade {
  id: number;
  stock_id: number | null;
  nse_symbol: string;
  quantity: number;
  avg_buy_price: string;
  today_low: string | null;
  stop_loss_percent: string;
  stop_loss_price: string;
  notes: string | null;
  trade_date: string;
  close_price: string | null; // null = OPEN, set = CLOSED
  close_date: string | null;
  created_at: string;
  updated_at: string;
}

export interface CloseTradeInput {
  close_price: string;
  close_date: string;
}

export interface TradeWithTargets {
  trade: Trade;
  gtt_targets: GttTarget[];
  total_invested: string;
}

export interface CreateTradeInput {
  instrument_token: number;
  company_name: string;
  exchange: string;
  nse_symbol: string;
  quantity: number;
  avg_buy_price: string;
  today_low?: string;
  stop_loss_percent: string;
  notes?: string;
  trade_date?: string;
}

// ==================== Stock 7-Day Detail ====================

export interface DayDetail {
  date: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  daily_change_pct: number | null;
  rsi14: number | null;
  vol_ratio: number | null;
}

export interface StockDetailResponse {
  symbol: string;
  exchange: string;
  avg_volume_20d: number | null;
  days: DayDetail[];
}

export interface StockQuoteSnapshot {
  symbol: string;
  ltp: number | null;
  day_open: number | null;
  day_high: number | null;
  day_low: number | null;
  volume: number | null;
  updated_at: string;
}

// ==================== Remora Strategy ====================

export interface RemoraSignal {
  id: number;
  stock_id: number;
  symbol: string;
  company_name: string;
  exchange: string;
  signal_type: "ACCUMULATION" | "DISTRIBUTION";
  volume_ratio: number;
  price_change_pct: number;
  consecutive_days: number;
  signal_date: string;
  computed_at: string;
  delivery_pct: number;
  delivery_ratio: number;
}

export interface RemoraEnvelope {
  signals: RemoraSignal[];
  as_of_date: string | null;
  is_stale: boolean;
  stale_reason: string | null;
}

// ==================== Watchlist Dashboard ====================

export interface WatchlistRow {
  symbol: string;
  instrumentToken: number;
  companyName: string;
  exchange: string;
  sector: string | null;
  ltp: number | null;
  changePercent: number | null;
  sma50: number | null;
  sma200: number | null;
  trendState: string | null;
  high60d: number | null;
  low60d: number | null;
  rangePosition60dPct: number | null;
  gapTo3mLowPct: number | null;
  gapTo3mHighPct: number | null;
  rsiAtHigh60d: number | null;
  rsiAtLow60d: number | null;
  volumeAtHigh60d: number | null;
  volumeAtLow60d: number | null;
  priceVs200maPct: number | null;
  rsi14: number | null;
  atr14: number | null;
  atr14Pct: number | null;
  roc1w: number | null;
  roc3m: number | null;
  macdSignal: string | null;
  drawdownPct: number | null;
  maxDd1y: number | null;
  volumeVsAvg: number | null;
}

// ==================== Weekly Screener ====================

export interface WeeklyPatternResult {
  symbol: string;
  exchange: string;
  instrumentToken: number;
  companyName: string;
  weeksAnalyzed: number;
  buyDay: string;
  entryReboundPct: number;
  rsiLookbackDays: number;
  rsiOverboughtPercentile: number;
  stopLossPct: number;
  buyDayAvgDipPct: number;
  reboundConsistency: number;
  sellDay: string;
  swingAvgPct: number;
  avgPotentialPct: number;
  swingConsistency: number;
  compositeScore: number;
  patternConfirmed: boolean;
  cycleType: string;
  reason: string | null;
  buyDayLowMin: number;
  buyDayLowMax: number;
  currentRsiStatus: AdaptiveRsiStatus | null;
  targetRecommendation: TargetRecommendation | null;
  vcpTightnessPct: number | null;
  volumeSignatureRatio: number | null;
  mondayStrikeRatePct: number | null;
}

export interface WeeklyPatternListResponse {
  runAt: string;
  lookbackWeeks: number;
  buyZoneLookbackWeeks: number;
  results: WeeklyPatternResult[];
}

export interface DayProfile {
  day: string;
  action: string;
  avgChangePct: number;
}

export interface AutocorrelationResult {
  lag5: number;
  lag10: number;
  lag21: number;
}

export interface WeekHeatmapRow {
  weekLabel: string;
  startDate: string;
  endDate: string;
  mondayChangePct: number | null;
  tuesdayChangePct: number | null;
  wednesdayChangePct: number | null;
  thursdayChangePct: number | null;
  fridayChangePct: number | null;
  entryTriggered: boolean;
  swingTargetHit: boolean;
  buyPriceActual: number | null;
  sellPriceActual: number | null;
  buyRsi: number | null;
  netSwingPct: number | null;
  maxPotentialPct: number | null;
  reasoning: string | null;
}

export interface TargetScenario {
  targetPct: number;
  entries: number;
  winRatePct: number;
  stopLossRatePct: number;
  avgSwingPct: number;
  captureRatioPct: number;
  feasible: boolean;
}

export interface TargetRecommendation {
  recommendedTargetPct: number;
  safeTargetPct: number;
  aggressiveTargetPct: number;
  confidence: "HIGH" | "MEDIUM" | "LOW" | string;
  expectedSwingPct: number;
  expectedWinRatePct: number;
  expectedStopLossRatePct: number;
  captureRatioPct: number;
}

export interface WeeklyPatternDetail extends WeeklyPatternResult {
  dayOfWeekProfile: DayProfile[];
  autocorrelation: AutocorrelationResult;
  patternSummary: string;
  weeklyHeatmap: WeekHeatmapRow[];
  targetScenarios: TargetScenario[];
}

// ==================== Technical Context API ====================

export interface SessionCandle {
  date: string;
  dayOfWeek: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  range: number;
  lowToHighPct: number;
}

export interface AdaptiveRsiStatus {
  isOverbought: boolean;
  isOversold: boolean;
  percentile: number;
  currentRsi: number;
  highestRsi: number;
  lowestRsi: number;
}

export interface TechnicalContext {
  symbol: string;
  atr14: number;
  rsi14: number;
  lowestRsi50d: number;
  highestRsi50d: number;
  lowestRsi100d: number;
  highestRsi100d: number;
  lowestRsi200d: number;
  highestRsi200d: number;
  sma200: number;
  ltp: number;
  recentSessions: SessionCandle[];
  adaptiveRsi: AdaptiveRsiStatus | null;
}

// ==================== RSI Momentum Strategy ====================

export interface RsiMomentumConfigSummary {
  enabled: boolean;
  profileId: string;
  profileLabel: string;
  baseUniversePreset: string;
  candidateCount: number;
  boardDisplayCount: number;
  replacementPoolCount: number;
  holdingCount: number;
  rsiPeriods: number[];
  minAverageTradedValue: number;
  maxExtensionAboveSma20ForNewEntry: number;
  maxExtensionAboveSma20ForNewEntryPct: number;
  maxExtensionAboveSma20ForSkipNewEntry: number;
  maxExtensionAboveSma20ForSkipNewEntryPct: number;
  rebalanceDay: string;
  rebalanceTime: string;
  rsiCalibrationRunAt: string | null;
  rsiCalibrationMethod: string | null;
  rsiCalibrationSampleRange: string | null;
  safeRules: SafeRulesConfig;
  blockedEntryDays: string[];
}

export interface SafeRulesConfig {
  initialRankFilter: number;
  maxMoveFrom3WeekLowPct: number;
  maxDailyMove5dPct: number;
  displayCount: number;
  minVolumeExhaustionRatio: number | null;
}

export interface RsiMomentumRankedStock {
  rank: number;
  rank5DaysAgo: number | null;
  rankImprovement: number | null;
  symbol: string;
  companyName: string;
  instrumentToken: number;
  avgRsi: number;
  rsi22: number;
  rsi44: number;
  rsi66: number;
  close: number;
  sma20: number;
  extensionAboveSma20Pct: number;
  moveFrom3WeekLowPct: number;
  maxDailyMove5dPct: number;
  buyZoneLow10w: number;
  buyZoneHigh10w: number;
  lowestRsi50d: number;
  highestRsi50d: number;
  avgTradedValueCr: number;
  avgVol3d: number;
  avgVol20d: number;
  volumeRatio: number;
  inBaseUniverse: boolean;
  inWatchlist: boolean;
  entryBlocked: boolean;
  entryBlockReason: string | null;
  entryAction: "ENTRY" | "HOLD" | "SKIP" | "WATCH" | "WATCH_PULLBACK";
  targetWeightPct: number | null;
}

export interface RsiMomentumRebalance {
  entries: string[];
  exits: string[];
  holds: string[];
}

export interface RsiMomentumDiagnostics {
  baseUniverseCount: number;
  watchlistCount: number;
  watchlistAdditionsCount: number;
  unresolvedSymbols: string[];
  insufficientHistorySymbols: string[];
  illiquidSymbols: string[];
  backfilledSymbols: string[];
  failedSymbols: string[];
}

export interface RsiMomentumSnapshot {
  profileId: string;
  profileLabel: string;
  available: boolean;
  stale: boolean;
  message: string | null;
  config: RsiMomentumConfigSummary;
  runAt: string | null;
  asOfDate: string | null;
  resolvedUniverseCount: number;
  eligibleUniverseCount: number;
  topCandidates: RsiMomentumRankedStock[];
  holdings: RsiMomentumRankedStock[];
  rebalance: RsiMomentumRebalance;
  diagnostics: RsiMomentumDiagnostics;
}

export interface RsiMomentumProfileError {
  profileId: string;
  message: string;
}

export interface RsiMomentumMultiSnapshot {
  profiles: RsiMomentumSnapshot[];
  errors: RsiMomentumProfileError[];
  partialSuccess: boolean;
}

// ─── RSI Momentum History ────────────────────────────────────────────────────

export interface RsiMomentumHistoryEntry {
  profileId: string;
  asOfDate: string;
  runAt: string;
  snapshot: RsiMomentumSnapshot;
}

export type RsiBacktestLogicType = "LEADER" | "JUMPER" | "HYBRID";

export interface RsiMomentumBacktestRequest {
  profileId: string;
  logicType: RsiBacktestLogicType;
  fromDate?: string;
  toDate?: string;
  initialCapital: number;
  targetPct: number;
  stopLossPct: number;
  runBackfill: boolean;
}

export interface BacktestTrade {
  symbol: string;
  companyName: string;
  entryDate: string;
  exitDate: string;
  entryPrice: number;
  exitPrice: number;
  targetPrice: number;
  stopLossPrice: number;
  result: "PROFIT" | "LOSS";
  profitPct: number;
  profitAmount: number;
  holdingDays: number;
  entryRank: number;
  entryRankImprovement: number | null;
}

export interface RsiMomentumBacktestReport {
  profileId: string;
  logicType: RsiBacktestLogicType;
  fromDate: string;
  toDate: string;
  initialCapital: number;
  finalCapital: number;
  totalProfit: number;
  totalProfitPct: number;
  totalTrades: number;
  winningTrades: number;
  losingTrades: number;
  winRate: number;
  avgHoldingDays: number;
  trades: BacktestTrade[];
}

// ─── RSI Momentum Backtest ───────────────────────────────────────────────────

export interface StatefulBacktestConfig {
  enabled: boolean;
  entryRankMax: number;
  takeProfitRank: number;
  exitOnTakeProfitLeave: boolean;
  giveUpRankMin: number;
}

export interface StockTrade {
  symbol: string;
  companyName: string;
  entryDate: string;
  entryPrice: number;
  entryRank: number;
  entryAvgRsi: number;
  exitDate: string | null;
  exitPrice: number | null;
  exitRank: number | null;
  exitAvgRsi: number | null;
  daysHeld: number;
  returnPct: number | null;
  status: "CLOSED" | "OPEN";
}

export interface BacktestSummary {
  totalTrades: number;
  closedTrades: number;
  openPositions: number;
  winRate: number | null;
  avgReturnPct: number | null;
  avgDaysHeld: number;
  totalTurnover: number;
}

export interface BacktestRequest {
  profileId: string;
  fromDate?: string;
  toDate?: string;
  initialCapital?: number;
  transactionCostBps?: number;
  topN?: number;
  statefulConfig?: StatefulBacktestConfig;
}

export interface BacktestResult {
  profileId: string;
  fromDate: string;
  toDate: string;
  topN: number | null;
  statefulConfig: StatefulBacktestConfig | null;
  snapshotDaysUsed: number;
  summary: BacktestSummary;
  trades: StockTrade[];
}

// ─── RSI Momentum Lifecycle ──────────────────────────────────────────────────

export interface RankTimelinePoint {
  date: string;
  rank: number | null;
  inTop10: boolean;
  price: number | null;
  avgRsi: number | null;
}

export interface LifecycleEpisode {
  symbol: string;
  entryDate: string;
  exitDate: string | null;
  daysInTop10: number;
  bestRank: number;
  bestRankDate: string;
  exitReason: "DROPPED_OUT" | "END_OF_WINDOW" | null;
  rankTimeline: RankTimelinePoint[];
}

export interface MultiSymbolHistoryResponse {
  profileId: string;
  fromDate: string;
  toDate: string;
  symbols: string[];
  timelines: Record<string, RankTimelinePoint[]>;
}

export interface RankBucketTransition {
  from: string;
  to: string;
  count: number;
}

export interface LifecycleSummary {
  profileId: string;
  fromDate: string;
  toDate: string;
  totalEpisodes: number;
  avgDaysInTop10: number;
  medianDaysInTop10: number;
  shortStayChurnRate: number;
  rankBucketTransitions: RankBucketTransition[];
}

export interface LifecycleSymbolDetail {
  profileId: string;
  symbol: string;
  fromDate: string;
  toDate: string;
  episodes: LifecycleEpisode[];
}

// ==================== S4 Volume Spike Strategy ====================

export interface S4ConfigSummary {
  enabled: boolean;
  profileId: string;
  profileLabel: string;
  baseUniversePreset: string;
  candidateCount: number;
  minAverageTradedValue: number;
  minHistoryBars: number;
  todayVolumeRatioThreshold: number;
  recent3dAvgVolumeRatioThreshold: number;
  recent5dMaxVolumeRatioThreshold: number;
  spikePersistenceThreshold: number;
  breakoutPriceChangePctThreshold: number;
  breakoutReturn3dPctThreshold: number;
}

export interface S4RankedCandidate {
  rank: number;
  symbol: string;
  companyName: string;
  instrumentToken: number;
  profileId: string;
  baseUniversePreset: string;
  close: number;
  avgVolume20d: number;
  avgTradedValueCr20d: number;
  todayVolumeRatio: number;
  recent3dAvgVolumeRatio: number;
  recent5dMaxVolumeRatio: number;
  spikePersistenceDays5d: number;
  recent10dAvgVolumeRatio: number;
  elevatedVolumeDays10d: number;
  todayPriceChangePct: number;
  priceReturn3dPct: number;
  breakoutAbove20dHigh: boolean;
  indexRank: number;
  indexSize: number;
  indexLayer: string;
  todayVolumeScore: number;
  recent3dVolumeScore: number;
  persistenceScore: number;
  priceScore: number;
  classification: "FRESH_SPIKE" | "BUILDUP_BREAKOUT" | "EXTENDED_SPIKE" | string;
  score: number;
}

export interface S4Diagnostics {
  baseUniverseCount: number;
  unresolvedSymbols: string[];
  insufficientHistorySymbols: string[];
  illiquidSymbols: string[];
  disqualifiedSymbols: string[];
  backfilledSymbols: string[];
  failedSymbols: string[];
}

export interface S4Snapshot {
  profileId: string;
  profileLabel: string;
  available: boolean;
  stale: boolean;
  message: string | null;
  config: S4ConfigSummary;
  runAt: string | null;
  asOfDate: string | null;
  resolvedUniverseCount: number;
  eligibleUniverseCount: number;
  topCandidates: S4RankedCandidate[];
  diagnostics: S4Diagnostics;
}

export interface S4ProfileError {
  profileId: string;
  message: string;
}

export interface S4MultiSnapshot {
  profiles: S4Snapshot[];
  errors: S4ProfileError[];
  partialSuccess: boolean;
}
