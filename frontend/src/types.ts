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

// ==================== Profit Lookback ====================

export interface ProfitLookbackRequest {
  symbol: string;
  instrumentToken: number;
  sellDate: string;
  lookbackDays: number;
  targetPercents: number[];
}

export interface ProfitLookbackBulkRowRequest {
  rowId: string;
  symbol: string;
  instrumentToken: number;
  sellDate: string;
}

export interface ProfitLookbackBulkRequest {
  lookbackDays: number;
  targetPercents: number[];
  rows: ProfitLookbackBulkRowRequest[];
}

export interface ProfitLookbackTargetResult {
  targetPercent: number;
  status: "ACHIEVED" | "NOT_ACHIEVABLE" | string;
  suggestedBuyDate: string | null;
  buyOpenPrice: number | null;
  daysBefore: number | null;
  returnPercent: number | null;
  maxDrawdownPercent: number | null;
  maxDrawdownDays: number | null;
}

export interface ProfitLookbackResponse {
  symbol: string;
  instrumentToken: number;
  requestedSellDate: string;
  resolvedSellDate: string;
  sellOpenPrice: number;
  results: ProfitLookbackTargetResult[];
}

export interface ProfitLookbackBulkRowResponse {
  rowId: string;
  ok: boolean;
  data: ProfitLookbackResponse | null;
  error: string | null;
}

export interface ProfitLookbackBulkResponse {
  rows: ProfitLookbackBulkRowResponse[];
}

// ==================== Earnings Dashboard ====================

export interface EarningsDashboardRow {
  symbol: string;
  instrumentToken: number;
  resultDate: string;
  daysToResult: number;
  isGrowwWatchlist: boolean;
  pre15dReturnPct: number | null;
  pre10dReturnPct: number | null;
  pre15dMaxDrawdownPct: number | null;
  eventDayOcPct: number | null;
  eventDayOhPct: number | null;
  nextDayOcPct: number | null;
  nextDayOhPct: number | null;
  latestClose: number | null;
  latestVolume: number | null;
  candleCoverage20d: number;
}

export interface EarningsDashboardResponse {
  asOfDate: string;
  windowStartDate: string;
  windowEndDate: string;
  daysAhead: number;
  growwOnly: boolean;
  rows: EarningsDashboardRow[];
}

export interface EarningsDashboardRawCandleBlock {
  symbol: string;
  instrumentToken: number;
  candles: Array<{
    candleDate: string;
    open: number;
    high: number;
    low: number;
    close: number;
    volume: number;
  }>;
}

export interface EarningsDashboardExportFilters {
  daysAhead: number;
  growwOnly: boolean;
}

export interface EarningsDashboardExportDocument {
  generated_at: string;
  filters: EarningsDashboardExportFilters;
  calculated_rows: EarningsDashboardRow[];
  raw_daily_candles_20d: EarningsDashboardRawCandleBlock[];
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

// ==================== Remora RSI Floor Scanner ====================

export type MarketCapBucket = "LARGE" | "MID" | "SMALL" | "UNKNOWN";
export type RsiFloorScanSource = "CACHE" | "FRESH";
export type RsiHistoryType = "FULL_1Y" | "PARTIAL_IPO";

export interface RsiFloorScannerRequest {
  universe?: string;
  freshScan?: boolean;
  lookbackMatchDays?: number;
  rsiPeriod?: number;
  yearWindowDays?: number;
  hardRsiLimit?: number;
}

export interface RsiFloorScannerRow {
  symbol: string;
  companyName: string;
  exchange: string;
  instrumentToken: number;
  currentRsi: number;
  yearLowRsiAtMatchedDay: number;
  matchedByYearLow: boolean;
  matchedByHardLimit: boolean;
  matchedDate: string;
  ltp: number | null;
  drawdownPct: number | null;
  high52w: number | null;
  low52w: number | null;
  marketCapCr: number | null;
  capBucket: MarketCapBucket;
  historyType: RsiHistoryType;
}

export interface RsiFloorScannerResult {
  runAt: string;
  universe: string;
  requestedSymbols: number;
  scannedSymbols: number;
  skippedInsufficientHistory: number;
  matchedCount: number;
  lookbackMatchDays: number;
  rsiPeriod: number;
  yearWindowDays: number;
  hardRsiLimit: number;
  source: RsiFloorScanSource;
  rows: RsiFloorScannerRow[];
}

// ==================== Weekly Screener ====================

export interface SwingSetup {
  buyZoneMin: number;
  buyZoneMax: number;
  safeTargetPct: number;
  recommendedTargetPct: number;
  aggressiveTargetPct: number;
  expectedSwingPct: number;
  hardStopLossPct: number;
  invalidationCondition: string;
  confidence: "HIGH" | "MEDIUM" | "LOW" | string;
  reasoning: string;
}

export interface WeeklyPatternResult {
  symbol: string;
  exchange: string;
  instrumentToken: number;
  companyName: string;
  sourceBuckets?: string[];
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
  lastWeekMondayDipPct?: number | null;
  avg8wMondayDipPct?: number | null;
  mondayDipSamples8w?: number;
  setupQualityScore?: number;
  expectedSwingPct?: number;
  baselineDistancePct?: number | null;
  sma200?: number | null;
  swingSetup?: SwingSetup | null;
}

export interface WeeklyPatternListResponse {
  runAt: string;
  lookbackWeeks: number;
  buyZoneLookbackWeeks: number;
  universeSourceTags?: string[];
  results: WeeklyPatternResult[];
}

export interface WeeklyCycleMetrics {
  weekLabel: string;
  startDay: string;
  endDay: string;
  startLow: number;
  endClose: number;
  weekHigh: number;
  highLowPct: number;
  rocPct: number;
  success: boolean;
}

export interface WeeklyCycleSuccessRow {
  symbol: string;
  companyName: string;
  instrumentToken: number;
  universeBuckets: string[];
  successCount: number;
  cycleCount: number;
  successRatePct: number;
  failedStartWeeks: string[];
  lastCycleMetrics: WeeklyCycleMetrics | null;
  stableBasePass: boolean;
  stableBaseReason: string | null;
  stableBaseDriftPct: number | null;
  stableBaseLowMin: number | null;
  stableBaseLowMax: number | null;
  stableBaseWeeksCount: number;
  lastWeekMondayDipPct?: number | null;
  avg8wMondayDipPct?: number | null;
  mondayDipSamples8w?: number;
}

export interface WeeklyCycleSuccessResponse {
  runAt: string;
  universe: "ALL" | "MIDCAP_250" | "SMALLCAP_250" | "BOTH" | "NIFTY_50" | "WATCHLIST" | "NIFTY_150" | string;
  weeksRequested: number;
  weeksEvaluated: number;
  highLowThresholdPct: number;
  rocThresholdPct: number;
  stableBaseMaxDriftPct: number;
  results: WeeklyCycleSuccessRow[];
}

export interface FundamentalsTableRow {
  symbol: string;
  companyName: string;
  exchange: string;
  instrumentToken: number;
  tag: string;
  fundamentalsSnapshotDate: string | null;
  marketCapCr: number | null;
  stockPe: number | null;
  rocePercent: number | null;
  roePercent: number | null;
  promoterHoldingPercent: number | null;
  industry: string | null;
  broadIndustry: string | null;
  ltp: number | null;
  rsi14: number | null;
  roc1w: number | null;
  roc3m: number | null;
  volumeVsAvg: number | null;
  isSelected: boolean | null;
  filterReasons: string[];
}

export interface FundamentalsTagOverviewResponse {
  tag: string;
  profile: string | null;
  totalStocks: number;
  selectedCount: number | null;
  rejectedCount: number | null;
  rows: FundamentalsTableRow[];
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
  maxMoveFrom30DayLowPct: number;
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
  moveFrom30DayLowPct: number;
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

export interface DrawdownBucketSummary {
  atLeast20Pct: number;
  atLeast30Pct: number;
  atLeast40Pct: number;
  atLeast50Pct: number;
  atLeast60Pct: number;
}

export interface DrawdownBucketFlags {
  atLeast20Pct: boolean;
  atLeast30Pct: boolean;
  atLeast40Pct: boolean;
  atLeast50Pct: boolean;
  atLeast60Pct: boolean;
}

export interface MomentumLeaderRow {
  symbol: string;
  companyName: string;
  instrumentToken: number;
  profileIds: string[];
  entryCount: number;
  bestRank: number;
  firstSeen: string;
  lastSeen: string;
  high1yClose: number | null;
  todayClose: number | null;
  minClose20d: number | null;
  ddTodayPct: number | null;
  dd20dMinPct: number | null;
  ddTodayBuckets: DrawdownBucketFlags;
  dd20dMinBuckets: DrawdownBucketFlags;
}

export interface LeadersDrawdownProfileSection {
  profileId: string;
  profileLabel: string;
  rowCount: number;
  rows: MomentumLeaderRow[];
  ddTodayBucketSummary: DrawdownBucketSummary;
  dd20dMinBucketSummary: DrawdownBucketSummary;
  warnings: string[];
}

export interface LeadersDrawdownCombinedSection {
  rowCount: number;
  rows: MomentumLeaderRow[];
  ddTodayBucketSummary: DrawdownBucketSummary;
  dd20dMinBucketSummary: DrawdownBucketSummary;
  warnings: string[];
}

export interface LeadersDrawdownMeta {
  fromDate: string;
  toDate: string;
  asOfDate: string;
  topN: number;
  profileIds: string[];
}

export interface LeadersDrawdownResponse {
  meta: LeadersDrawdownMeta;
  profiles: LeadersDrawdownProfileSection[];
  combined: LeadersDrawdownCombinedSection;
}

export type RsiBacktestLogicType = "LEADER" | "JUMPER" | "HYBRID";
export type RsiBacktestExitMode = "T_PLUS_3" | "RSI_60" | "T_PLUS_3_OR_RSI_60";

export interface RsiMomentumBacktestRequest {
  profileId: string;
  logicType: RsiBacktestLogicType;
  fromDate?: string;
  toDate?: string;
  initialCapital: number;
  targetPct: number;
  stopLossPct: number;
  runBackfill: boolean;
  entryRankMin: number;
  entryRankMax: number;
  rankLookbackDays: number;
  jumpMin: number;
  jumpMax: number;
  blockedEntryDays: string[];
  exitMode: RsiBacktestExitMode;
  rsiExitThreshold: number;
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
  entryRsi22: number | null;
  exitRsi22: number | null;
  entryFarthestRankInLookback: number | null;
  entryJumpFromFarthest: number | null;
  exitReason: string;
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
  entryRankMin: number;
  entryRankMax: number;
  rankLookbackDays: number;
  jumpMin: number;
  jumpMax: number;
  blockedEntryDays: string[];
  exitMode: RsiBacktestExitMode;
  rsiExitThreshold: number;
}

export interface RsiRankDriftBacktestRequest {
  profileId: string;
  fromDate?: string;
  toDate?: string;
  initialCapital: number;
  targetPct: number;
  atrStopMultiplier: number;
  entryRankMin: number;
  entryRankMax: number;
  priorBetterRankLookbackDays?: number;
  runBackfill?: boolean;
}

export interface RsiRankDriftBacktestReport {
  profileId: string;
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
  entryRankMin: number;
  entryRankMax: number;
  targetPct: number;
  atrStopMultiplier: number;
  atrPeriod: number;
  priorBetterRankLookbackDays: number;
  entriesSkippedByPriorBetterRankRule: number;
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

export interface SimpleMomentumBacktestRequest {
  profileId: string;
  fromDate?: string;
  toDate?: string;
  initialCapital?: number;
  entryRankMin?: number;
  entryRankMax?: number;
  holdRankMax?: number;
}

export interface SimpleMomentumTrade {
  symbol: string;
  companyName: string;
  entryDate: string;
  entryRank: number;
  entryPrice: number;
  quantity: number;
  investedAmount: number;
  exitDate: string | null;
  exitRank: number | null;
  exitPrice: number | null;
  exitAmount: number | null;
  pnlAmount: number | null;
  pnlPct: number | null;
  daysHeld: number;
  status: "OPEN" | "CLOSED";
  exitReason: string | null;
  peakCloseSinceEntry: number | null;
  trailingStopPriceAtExit: number | null;
}

export interface SimpleMomentumBacktestSummary {
  totalTrades: number;
  closedTrades: number;
  openPositions: number;
  winRate: number | null;
  finalCapital: number;
  totalProfit: number;
  totalProfitPct: number;
  cashBalance: number;
  entriesSkippedByDrawdownGuard: number;
  exitsByTrailingStop: number;
}

export interface SimpleMomentumBacktestResult {
  profileId: string;
  fromDate: string;
  toDate: string;
  firstSnapshotDate: string | null;
  lastSnapshotDate: string | null;
  initialCapital: number;
  entryRankMin: number;
  entryRankMax: number;
  holdRankMax: number;
  drawdownGuardLookbackDays: number;
  drawdownGuardThresholdPct: number;
  trailingStopPct: number;
  snapshotDaysUsed: number;
  summary: SimpleMomentumBacktestSummary;
  trades: SimpleMomentumTrade[];
}

export interface SimpleMomentumPrepareRequest {
  profileId: string;
  fromDate: string;
  toDate: string;
}

export interface DailyCandleSyncResult {
  fromDate: string;
  toDate: string;
  totalSymbols: number;
  symbolsSynced: number;
  symbolsFailed: number;
  failedSymbols: string[];
  dailyCandlesUpserted: number;
}

export interface SimpleMomentumPrepareResponse {
  profileId: string;
  profileLabel: string;
  baseUniversePreset: string;
  requestedFromDate: string;
  requestedToDate: string;
  symbolsTargeted: number;
  candleSync: DailyCandleSyncResult;
  snapshotBackfill: BackfillResult;
  warnings: string[];
}

export interface BackfillResult {
  profileId: string;
  fromDate: string;
  toDate: string;
  tradingDatesFound: number;
  datesSkipped: number;
  datesProcessed: number;
  datesFailed: number;
  message: string;
}

export interface BackfillFreshRequest {
  fromDate?: string;
  toDate?: string;
}

export interface BackfillFreshResult {
  fromDate: string;
  toDate: string;
  clearedRows: number;
  profiles: string[];
  profileResults: {
    profileId: string;
    fromDate: string;
    toDate: string;
    tradingDatesFound: number;
    datesSkipped: number;
    datesProcessed: number;
    datesFailed: number;
    message: string;
  }[];
  message: string;
}

export interface BackfillFreshResponse {
  rebuild: BackfillFreshResult;
  latest: RsiMomentumMultiSnapshot;
}

export interface MomentumDataPrepareResponse {
  profileId: string;
  fromDate: string;
  toDate: string;
  tradingDaysProcessed: number;
  todayAsOfDateUsed: string | null;
  top50Count: number;
  warnings: string[];
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

// ==================== Volume Spike Backtest ====================

export type EarningsFilterMode = "OFF" | "CUSTOM_WINDOW" | "MANUAL_SYMBOL";

export interface VolumeSpikeBacktestRequest {
  fromDate?: string;
  toDate?: string;
  delayMinutes: number;
  manualSymbols?: string[];
  earningsFilterMode?: EarningsFilterMode;
  earningsWindowStartOffsetDays?: number;
  earningsWindowEndOffsetDays?: number;
  rvolThreshold?: number;
  targetPct?: number;
  stopPct?: number;
  positionSizeInr?: number;
  feePerTradeInr?: number;
}

export interface VolumeSpikeBacktestTrade {
  symbol: string;
  instrumentToken: number;
  signalTime: string;
  entryTime: string;
  exitTime: string;
  entryPrice: number;
  exitPrice: number;
  quantity: number;
  investedAmount: number;
  targetPrice: number;
  stopPrice: number;
  rvolAtSignal: number;
  vwapAtSignal: number;
  prior30MinHigh: number;
  exitReason: "TARGET_HIT" | "STOP_HIT" | "EOD" | string;
  grossPnlInr: number;
  feeInr: number;
  netPnlInr: number;
  netReturnPct: number;
}

export interface VolumeSpikeBacktestSummary {
  symbolsConsidered: number;
  totalTrades: number;
  winningTrades: number;
  losingTrades: number;
  winRatePct: number;
  grossPnlInr: number;
  totalFeesInr: number;
  netPnlInr: number;
  avgNetReturnPct: number;
  maxDrawdownInr: number;
}

export interface VolumeSpikeBacktestDiagnostics {
  symbolsFromEarningsUniverse: number;
  symbolsFromManualInput: number;
  symbolsWithResolvedToken: number;
  symbolsWithNoToken: string[];
  symbolsWithNoIntradayData: string[];
  symbolsSkippedByEarningsFilter: string[];
  symbolsWithNoTrades: string[];
  cacheHits: number;
  cacheMisses: number;
  kiteFetchFailures: string[];
}

export interface VolumeSpikeBacktestConfigSnapshot {
  fromDate: string;
  toDate: string;
  delayMinutes: number;
  earningsFilterMode: EarningsFilterMode;
  earningsWindowStartOffsetDays: number | null;
  earningsWindowEndOffsetDays: number | null;
  rvolThreshold: number;
  targetPct: number;
  stopPct: number;
  positionSizeInr: number;
  feePerTradeInr: number;
}

export interface VolumeSpikeBacktestResponse {
  config: VolumeSpikeBacktestConfigSnapshot;
  summary: VolumeSpikeBacktestSummary;
  diagnostics: VolumeSpikeBacktestDiagnostics;
  trades: VolumeSpikeBacktestTrade[];
}

// ==================== Swing Analysis ====================

export interface SwingCandle {
  date: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export type SwingType = "PEAK" | "TROUGH";

export interface SwingPoint {
  date: string;
  dayOfWeek: string;
  price: number;
  type: SwingType;
  changePct: number;
  barsSinceLast: number;
}

export interface SwingStats {
  averageUpswingPct: number;
  averageDownswingPct: number;
  averageSwingDurationBars: number;
}

export interface SwingAnalysisResponse {
  symbol: string;
  reversalPct: number;
  points: SwingPoint[];
  candles: SwingCandle[];
  stats: SwingStats;
}
