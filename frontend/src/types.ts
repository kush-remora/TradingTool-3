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
  buyerDominancePass: boolean | null;
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

export interface WatchlistSymbolOption {
  symbol: string;
  company_name: string;
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
  instrument_token: number;
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
  bbUpper: number | null;
  bbLower: number | null;
  bbMiddle: number | null;
  bbPercentB: number | null;
  bbBandwidth: number | null;
  bbSqueeze: boolean;
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

export interface DeliverySurgeConfirmationRow {
  symbol: string;
  companyName: string;
  exchange: string;
  instrumentToken: number;
  latestTradingDate: string | null;
  avgDeliveredQty20d: number | null;
  latestDeliverySurgePct: number | null;
  maxDeliverySurgePct7d: number | null;
  surgeDays7d: number;
  recentDaysUsed: number;
  baselineDaysUsed: number;
  insufficientHistory: boolean;
}

export interface RemoraRsiFloorChainedResult {
  runAt: string;
  rsiResult: RsiFloorScannerResult;
  deliveryRequestedSymbols: number;
  deliveryConfirmedCount: number;
  deliveryConfirmedRows: DeliverySurgeConfirmationRow[];
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
  weeklyBaseConsistencyPct: number | null;
  avgWeeklyRocPct: number | null;
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

export interface BaseSwingResult {
  symbol: string;
  companyName: string;
  instrumentToken: number;
  currentPrice: number;
  price30dAgo: number | null;
  baseDriftPct: number | null;
  high30d: number;
  low30d: number;
  internalVolPct: number;
  distFrom52wHighPct: number | null;
  high52w: number | null;
  weeklyPulses: WeeklyPulse[];
  setupScore: number;
  reasoning: string;
}

export interface WeeklyPulse {
  label: string;
  startDate: string;
  endDate: string;
  swingPct: number;
}

export interface BaseSwingListResponse {
  runAt: string;
  lookbackDays: number;
  results: BaseSwingResult[];
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
  minDayMoveFromOpenPct?: number;
  targetPct?: number;
  stopPct?: number;
  minThirtyMinReturnPct?: number;
  latestEntryTime?: string;
  buyerDominancePct?: number;
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
  signalCandleVolume: number;
  avgSlotVolume20d: number;
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
  minDayMoveFromOpenPct: number;
  targetPct: number;
  stopPct: number;
  minThirtyMinReturnPct: number;
  latestEntryTime: string;
  buyerDominancePct: number | null;
  positionSizeInr: number;
  feePerTradeInr: number;
}

export interface VolumeSpikeBacktestResponse {
  config: VolumeSpikeBacktestConfigSnapshot;
  summary: VolumeSpikeBacktestSummary;
  diagnostics: VolumeSpikeBacktestDiagnostics;
  trades: VolumeSpikeBacktestTrade[];
}

// ==================== Delivery Threshold Backtest ====================

export interface DeliveryThresholdBacktestConfig {
  thresholds: Record<string, number>;
  profitPct: number;
  roc20ByIndex?: Record<string, {
    accumulationMinPct: number;
    accumulationMaxPct: number;
    distributionMinPct: number;
  }>;
  sma200ByIndex?: Record<string, {
    accumulationMaxDistancePct: number;
    distributionMinDistancePct: number;
  }>;
  fromDate?: string;
  toDate?: string;
}

export interface DeliveryThresholdBacktestRequest {
  indexKeys: string[];
  symbols?: string[];
  config: DeliveryThresholdBacktestConfig;
}

export interface DeliveryThresholdBacktestConfigSnapshot {
  indexKeys: string[];
  symbols: string[];
  thresholds: Record<string, number>;
  profitPct: number;
  roc20ByIndex: Record<string, {
    accumulationMinPct: number;
    accumulationMaxPct: number;
    distributionMinPct: number;
  }>;
  sma200ByIndex: Record<string, {
    accumulationMaxDistancePct: number;
    distributionMinDistancePct: number;
  }>;
  fromDate: string;
  toDate: string;
}

export interface DeliveryThresholdBacktestSummary {
  totalBuys: number;
  hitCount: number;
  hitRatePct: number;
  daysToHitAvg: number | null;
  daysToHitMedian: number | null;
  daysToHitMin: number | null;
  daysToHitMax: number | null;
  openCount: number;
}

export interface DeliveryThresholdBacktestRow {
  symbol: string;
  index: string;
  entryDate: string;
  entryPrice: number;
  entryDeliveryPct: number;
  totalVolumeCount: number | null;
  avg20dVolumeAtSignal: number | null;
  signalVolumeVs20dPct: number | null;
  roc20AtSignalPct: number | null;
  sma200AtSignal: number | null;
  distFromSma200AtSignalPct: number | null;
  targetPrice: number;
  fiftyTwoWeekHighAtBuy: number | null;
  fiftyTwoWeekLowAtBuy: number | null;
  pctFrom52WeekHighAtBuy: number | null;
  pctFrom52WeekLowAtBuy: number | null;
  buyDayOfWeek: string;
  exitDate: string | null;
  exitPrice: number | null;
  holdingDays: number;
  rsiBuy: number | null;
  rsiSell: number | null;
  maxDrawdownAtBuyPct: number | null;
  status: "HIT" | "OPEN" | string;
  currentPrice: number;
  floatingPnlPct: number | null;
  thresholdUsed: number;
}

export interface DeliveryThresholdBacktestResponse {
  config: DeliveryThresholdBacktestConfigSnapshot;
  summary: DeliveryThresholdBacktestSummary;
  rows: DeliveryThresholdBacktestRow[];
}

// ==================== Wyckoff Phase-1 Scanner ====================

export interface WyckoffPhase1RunRequest {
  universeKeys: string[];
  symbols?: string[];
  asOfDate?: string;
  applyStrictBaseFilter?: boolean;
}

export interface WyckoffPhase1Config {
  enabled: boolean;
  signalLookbackDays?: number;
  trackA: {
    deliveryThresholdByCap: Record<string, number>;
    rollingDensity: {
      enabled: boolean;
      lookbackDays: number;
      minThresholdBreaches: number;
    };
    deliveryVolumeZScore: {
      enabled: boolean;
      baselineDays: number;
      minZScore: number;
    };
    lvqDq: {
      enabled: boolean;
      rollingMinDays: number;
      nearMinPctOfRollingMin: number;
      lookbackDays: number;
      requireDeliveryPass: boolean;
    };
    absorptionCheck: {
      enabled: boolean;
      spreadLookbackDays: number;
    };
    lowVolumeHighDeliveryInfo: {
      enabled: boolean;
      mode: string;
      volumeBaselineDays: number;
      maxVolumeVsBaselineRatio: number;
    };
  };
  contextFilter: {
    roc20Range: {
      enabled: boolean;
      minDistancePct: number;
      maxDistancePct: number;
    };
    dma200Proximity: {
      enabled: boolean;
      minDistancePct: number;
      maxDistancePct: number;
    };
  };
}

export interface WyckoffPhase1TableColumnsConfig {
  enabled: boolean;
  defaultSort: Array<{ key: string; direction: string }>;
  columns: Array<{ key: string; enabled: boolean }>;
}

export interface WyckoffPhase1Row {
  symbol: string;
  company_name: string;
  signal_date: string;
  days_ago: number;
  index_key: string;
  tier_80_count_15d: number;
  tier_70_count_15d: number;
  tier_65_count_15d: number;
  tier_60_count_15d: number;
  tier_55_count_15d: number;
  delivery_volume_zscore_60d: number | null;
  lvq_hit_count_15d: number;
  spread_pct: number | null;
  avg_spread_pct_20d: number | null;
  rsi_14: number | null;
  roc20_pct: number | null;
  sma50_distance_pct: number | null;
  sma200_distance_pct: number | null;
  distance_from_52w_low_pct: number | null;
  volume_vs_50d_ratio: number | null;
  accumulation_run_length_days: number;
}

export interface WyckoffPhase1RunResponse {
  rows: WyckoffPhase1Row[];
  meta: {
    as_of_date: string;
    evaluated_trading_dates: string[];
    universe_count: number;
    matched_count: number;
  };
}

// ==================== Volume Shocker Dashboard ====================

export interface VolumeShockerDatesResponse {
  available_dates: string[];
  default_date: string | null;
}

export interface VolumeShockerDashboardRow {
  source_rank: number;
  symbol: string;
  company_name: string;
  ltp: number;
  volume: number;
  delivery_volume: number | null;
  delivery_pct: number | null;
  max_delivery_volume_10d_before_event: number | null;
  delivery_volume_vs_max_10d_before_event_ratio: number | null;
  appearance_count_10d: number;
  streak_length_10d: number;
  sma200_price: number | null;
  distance_from_sma200_pct: number | null;
  pre_event_accumulation_hint: boolean;
  tag: string;
}

export interface VolumeShockerDashboardResponse {
  trade_date: string;
  rows: VolumeShockerDashboardRow[];
}

export interface VolumeShockerDetailSummary {
  appearance_count_10d: number;
  streak_length_10d: number;
  max_delivery_volume_10d_before_event: number | null;
  delivery_volume_vs_max_10d_before_event_ratio: number | null;
}

export interface VolumeShockerDetailDay {
  date: string;
  open: number;
  close: number;
  volume: number;
  delivery_volume: number | null;
  delivery_pct: number | null;
  daily_change_pct: number | null;
  is_event_day: boolean;
}

export interface VolumeShockerDetailResponse {
  symbol: string;
  trade_date: string;
  summary: VolumeShockerDetailSummary;
  days: VolumeShockerDetailDay[];
}

// ==================== Delivery Breakout Dashboard ====================

export interface DeliveryBreakoutDashboardMeta {
  trade_date: string;
  scanned_count: number;
  liquidity_eligible_count: number;
  shortlisted_count: number;
  confirmed_breakout_count: number;
  quiet_clue_count: number;
}

export interface DeliveryBreakoutDashboardRow {
  symbol: string;
  trade_date: string;
  close: number | null;
  close_pct_change: number | null;
  volume: number;
  delivery_quantity: number;
  delivery_percentage: number | null;
  prior_10d_max_volume: number;
  prior_10d_max_delivery_quantity: number;
  volume_ratio_vs_10d_max: number;
  delivery_ratio_vs_10d_max: number;
  has_quiet_clue: boolean;
  quiet_clue_day: string | null;
  is_confirmed_breakout_today: boolean;
  sma200: number | null;
  distance_from_sma200_pct: number | null;
  is_near_200_sma: boolean | null;
  label: string;
}

export interface DeliveryBreakoutDashboardResponse {
  meta: DeliveryBreakoutDashboardMeta;
  rows: DeliveryBreakoutDashboardRow[];
}

// ==================== Bollinger Squeeze ====================

export interface BollingerSqueezeScanResult {
  symbol: string;
  companyName: string;
  instrumentToken: number;
  ltp: number;
  above200Sma: boolean;
  filter1Passed: boolean;
  filter1OriginDate: string | null;
  filter1LatestDate: string | null;
  filter1OriginPrice: number | null;
  filter2Passed: boolean;
  filter2OriginDate: string | null;
  filter2LatestDate: string | null;
  filter2OriginPrice: number | null;
  filter2MovePctFromFilter1: number | null;
  filter2Type: string | null;
  alertStatus: string;
  trendPatternFromFilter1: string | null;
  trendOverallFromFilter1: string | null;
  trendNetMovePctFromFilter1: number | null;
  currentRsi: number | null;
  triggerRsi: number | null;
  maxRsi52w: number | null;
  maxDrawdownPct: number;
  bbUpper: number;
  bbMiddle: number;
  bbLower: number;
}

export interface BollingerSqueezeScanResponse {
  runAt: string;
  universe: string;
  results: BollingerSqueezeScanResult[];
}

export interface UniverseOption {
  label: string;
  value: string;
  count: number;
}

export interface UniverseOptionsResponse {
  options: UniverseOption[];
}

export interface FiftyTwoWeekHighBacktestConfig {
  profitPct: number;
  historyDays: number;
  backtestDays: number;
  cooldownDays: number;
  toDate?: string;
}

export interface FiftyTwoWeekHighBacktestRequest {
  indexKeys: string[];
  symbols: string[];
  config: FiftyTwoWeekHighBacktestConfig;
}

export interface FiftyTwoWeekHighBacktestRow {
  symbol: string;
  indexBucket: string;
  enterTrade: string;
  exitTrade: string | null;
  holdingDays: number;
  status: "OPEN" | "CLOSED" | string;
}

export interface FiftyTwoWeekHighBacktestSummary {
  totalTrades: number;
  closedTrades: number;
  openTrades: number;
}

export interface FiftyTwoWeekHighBacktestConfigSnapshot extends FiftyTwoWeekHighBacktestConfig {
  indexKeys: string[];
  symbols: string[];
  fromDate: string;
}

export interface FiftyTwoWeekHighBacktestResponse {
  config: FiftyTwoWeekHighBacktestConfigSnapshot;
  summary: FiftyTwoWeekHighBacktestSummary;
  rows: FiftyTwoWeekHighBacktestRow[];
}

export interface FiftyTwoWeekLowBacktestConfig {
  profitPct: number;
  lookbackDays: number;
  toDate?: string;
}

export interface FiftyTwoWeekLowBacktestRequest {
  indexKeys: string[];
  symbols: string[];
  config: FiftyTwoWeekLowBacktestConfig;
}

export interface FiftyTwoWeekLowBacktestRow {
  symbol: string;
  indexBucket: string;
  enterTrade: string;
  exitTrade: string | null;
  buyPrice: number;
  sellPrice: number | null;
  holdingDays: number;
  profitPct: number | null;
  status: "OPEN" | "CLOSED" | string;
  ltp: number | null;
  currentProfitPct: number | null;
}

export interface FiftyTwoWeekLowBacktestSummary {
  totalTrades: number;
  closedTrades: number;
  openTrades: number;
  avgDaysHeldClosed: number | null;
}

export interface FiftyTwoWeekLowBacktestConfigSnapshot extends FiftyTwoWeekLowBacktestConfig {
  indexKeys: string[];
  symbols: string[];
  fromDate: string;
}

export interface FiftyTwoWeekLowBacktestResponse {
  config: FiftyTwoWeekLowBacktestConfigSnapshot;
  summary: FiftyTwoWeekLowBacktestSummary;
  rows: FiftyTwoWeekLowBacktestRow[];
}

export interface FiftyTwoWeekHighLiveRequest {
  universeKeys: string[];
  symbols: string[];
}

export interface FiftyTwoWeekHighLiveTelegramRequest {
  symbol: string;
  bucket: string;
  breakoutLevel: number;
  latestHigh: number;
  latestClose: number;
  gapToBreakoutPct: number;
  latestDate: string;
  lastHitDate: string | null;
}

export interface FiftyTwoWeekHighLiveRow {
  symbol: string;
  indexBucket: string;
  latestDate: string;
  breakoutLevel: number;
  latestHigh: number;
  latestClose: number;
  gapToBreakoutPct: number;
  lastHitDate: string | null;
  cooldownActive: boolean;
}

export interface FiftyTwoWeekHighLiveSummary {
  nearBreakout: number;
  hitInLast2Weeks: number;
  hitToday: number;
}

export interface FiftyTwoWeekHighLiveConfigSnapshot {
  nearThresholdPct: number;
  breakoutLookbackDays: number;
  hitLookbackTradingDays: number;
  hitTodayTradingDays: number;
  cooldownTradingDays: number;
}

export interface FiftyTwoWeekHighLiveResponse {
  config: FiftyTwoWeekHighLiveConfigSnapshot;
  summary: FiftyTwoWeekHighLiveSummary;
  nearBreakout: FiftyTwoWeekHighLiveRow[];
  hitInLast2Weeks: FiftyTwoWeekHighLiveRow[];
  hitToday: FiftyTwoWeekHighLiveRow[];
}

export type HotSmaSignalTag = "AGGRESSIVE_BUY" | "STANDARD_BUY" | "WATCH_ZONE";
export type HotSmaZoneStatus = "BUY_ZONE" | "ABOVE_BUY_ZONE" | "NO_SMA200";

export interface HotSmaRunRequest {
  indexKey: string;
}

export interface HotSmaTelegramRequest {
  indexKey: string;
  symbol: string;
  signalTag: HotSmaSignalTag;
  currentPrice: number;
  sma50: number | null;
  sma100: number | null;
  sma200: number | null;
  pctToSma50: number | null;
  pctToSma100: number | null;
  pctToSma200: number | null;
  rsi14: number | null;
}

export interface HotSmaUniverseOption {
  value: string;
  count: number;
}

export interface HotSmaRow {
  symbol: string;
  companyName: string;
  indexKey: string;
  instrumentToken: number;
  latestDate: string;
  currentPrice: number;
  sma50: number | null;
  sma100: number | null;
  sma200: number | null;
  pctToSma50: number | null;
  pctToSma100: number | null;
  pctToSma200: number | null;
  distanceToSma200AbsPct: number | null;
  rsi14: number | null;
  drawdownFromHigh20Pct: number | null;
  drawdownFromHigh60Pct: number | null;
  consecutiveRedDays: number;
  move3dPct: number | null;
  sma100TouchedInLast5d: boolean;
  sma100TouchDate: string | null;
  sma200TouchedInLast5d: boolean;
  sma200TouchDate: string | null;
  signalTag: HotSmaSignalTag | null;
  zoneStatus: HotSmaZoneStatus;
}

export interface HotSmaSummary {
  totalStocks: number;
  buyZoneCount: number;
  aboveBuyZoneCount: number;
  noSma200Count: number;
}

export interface HotSmaConfigSnapshot {
  rsiPeriod: number;
  sma50Window: number;
  sma100Window: number;
  sma200Window: number;
  buyZoneUpperPct: number;
  drawdownWindow20: number;
  drawdownWindow60: number;
  useAvailableHistoryForSma200: boolean;
}

export interface HotSmaRunResponse {
  runAt: string;
  selectedIndexKey: string;
  config: HotSmaConfigSnapshot;
  summary: HotSmaSummary;
  rows: HotSmaRow[];
}

export interface SqueezePositionInput {
  symbol: string;
  buyDate: string;
  buyPrice: number;
}

export interface SqueezeTrackResult {
  symbol: string;
  companyName: string;
  buyDate: string;
  buyPrice: number;
  ltp: number;
  profitPct: number;
  currentPhase: string;
  requiredSl: number;
  todayRsi: number | null;
  maxRsi1y: number | null;
  maxDrawdownPct: number;
  bbUpper: number;
  bbMiddle: number;
  bbLower: number;
}

export interface SqueezeTrackResponse {
  results: SqueezeTrackResult[];
}

// ==================== Bollinger Backtest ====================

export interface BollingerBacktestConfig {
  capital: number;
  maxOpenPositions: number;
  fromDate?: string;
  toDate?: string;
  setupWindowDays: number;
  tightSqueezeTolerancePct: number;
  volumeMultiplier: number;
  breakEvenProfitPct: number;
  maxHoldDays: number;
}

export interface BollingerBacktestRequest {
  universe: string;
  symbols?: string[];
  config: BollingerBacktestConfig;
}

export interface BollingerCriteriaSnapshot {
  percentB: number;
  rsi14: number | null;
  bandwidthPct: number;
  volumeRatio20: number;
  closeAboveSma200: boolean | null;
  signal: string;
  reasoning: string;
}

export interface BollingerBacktestTrade {
  symbol: string;
  companyName: string;
  entryDate: string;
  exitDate: string;
  holdingDays: number;
  quantity: number;
  investedAmount: number;
  entryPrice: number;
  exitPrice: number;
  exitReason: string;
  grossPnlInr: number;
  netPnlInr: number;
  netReturnPct: number;
  entryCriteria: BollingerCriteriaSnapshot;
  exitCriteria: BollingerCriteriaSnapshot;
  debugRows: BollingerBacktestDebugRow[];
}

export interface BollingerBacktestDebugRow {
  date: string;
  ltp: number;
  bbUpper: number;
  bbMiddle: number;
  bbLower: number;
  percentB: number;
  bandwidthPct: number;
  rsi14: number | null;
  volumeRatio20: number;
  closeAboveSma200: boolean | null;
  signal: string;
  reasoning: string;
}

export interface BollingerBacktestSummary {
  totalTrades: number;
  winningTrades: number;
  losingTrades: number;
  winRatePct: number;
  grossPnlInr: number;
  totalBrokerageInr: number;
  netPnlInr: number;
  totalReturnPct: number;
  avgReturnPerTradePct: number;
  maxDrawdownInr: number;
  finalCapital: number;
}

export interface BollingerBacktestDiagnostics {
  symbolsConsidered: number;
  symbolsWithInsufficientData: string[];
  symbolsWithNoTrades: string[];
}

export interface BollingerBacktestConfigSnapshot extends BollingerBacktestConfig {
  universe: string;
}

export interface BollingerBacktestResponse {
  config: BollingerBacktestConfigSnapshot;
  summary: BollingerBacktestSummary;
  diagnostics: BollingerBacktestDiagnostics;
  trades: BollingerBacktestTrade[];
}

// ==================== Bollinger Mean Reversion Backtest ====================

export interface BollingerMeanReversionBacktestConfig {
  capital: number;
  maxOpenPositions: number;
  fromDate?: string;
  toDate?: string;
  signalWindowDays: number;
  volumeMultiplier: number;
  bandwidthRecoveryThreshold: number;
  maxHoldDays: number;
}

export interface BollingerMeanReversionBacktestRequest {
  universe: string;
  symbols?: string[];
  config: BollingerMeanReversionBacktestConfig;
}

export interface BollingerMeanReversionCriteriaSnapshot {
  percentB: number;
  rsi14: number | null;
  bandwidthPct: number;
  volumeRatio20: number;
  closeAboveSma200: boolean | null;
  signal: string;
  reasoning: string;
}

export interface BollingerMeanReversionBacktestTrade {
  symbol: string;
  companyName: string;
  entryDate: string;
  exitDate: string;
  holdingDays: number;
  quantity: number;
  investedAmount: number;
  entryPrice: number;
  exitPrice: number;
  exitReason: string;
  grossPnlInr: number;
  netPnlInr: number;
  netReturnPct: number;
  entryCriteria: BollingerMeanReversionCriteriaSnapshot;
  exitCriteria: BollingerMeanReversionCriteriaSnapshot;
  debugRows: BollingerMeanReversionBacktestDebugRow[];
}

export interface BollingerMeanReversionBacktestDebugRow {
  date: string;
  ltp: number;
  bbUpper: number;
  bbMiddle: number;
  bbLower: number;
  percentB: number;
  bandwidthPct: number;
  rsi14: number | null;
  volumeRatio20: number;
  closeAboveSma200: boolean | null;
  signal: string;
  reasoning: string;
}

export interface BollingerMeanReversionBacktestSummary {
  totalTrades: number;
  winningTrades: number;
  losingTrades: number;
  winRatePct: number;
  grossPnlInr: number;
  totalBrokerageInr: number;
  netPnlInr: number;
  totalReturnPct: number;
  avgReturnPerTradePct: number;
  maxDrawdownInr: number;
  finalCapital: number;
}

export interface BollingerMeanReversionBacktestDiagnostics {
  symbolsConsidered: number;
  symbolsWithInsufficientData: string[];
  symbolsWithNoTrades: string[];
}

export interface BollingerMeanReversionBacktestConfigSnapshot extends BollingerMeanReversionBacktestConfig {
  universe: string;
}

export interface BollingerMeanReversionBacktestResponse {
  config: BollingerMeanReversionBacktestConfigSnapshot;
  summary: BollingerMeanReversionBacktestSummary;
  diagnostics: BollingerMeanReversionBacktestDiagnostics;
  trades: BollingerMeanReversionBacktestTrade[];
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

// ==================== Intraday Volume Shock ====================

export interface IntradayShockBacktestRequest {
  fromDate?: string;
  toDate?: string;
  universe?: string;
  manualSymbols?: string[];
  scanEndMinutes?: number;
  entryDelayMinutes?: number;
  gapUpTolerancePct?: number;
  targetPct?: number;
  hardStopPct?: number;
  minTurnover?: number;
  minVolumeSma?: number;
  positionSizeInr?: number;
  feePerTradeInr?: number;
}

export interface IntradayShockBacktestTrade {
  symbol: string;
  entryTime: string;
  exitTime: string;
  exitReason: "TARGET_HIT" | "STOP_HIT" | "EOD" | "EOD_FORCED";
  quantity: number;
  entryPrice: number;
  exitPrice: number;
  gapPct: number;
  morningVolume: number;
  maxDailyVolume60d: number;
  maxDailyVolume63d: number;
  maxDailyVolume126d: number;
  maxDailyVolume252d: number;
  netPnlInr: number;
  netReturnPct: number;
}

export interface IntradayShockBacktestConfigSnapshot {
  fromDate: string;
  toDate: string;
  universe?: string;
  manualSymbols: string[];
  scanEndMinutes: number;
  entryDelayMinutes: number;
  gapUpTolerancePct: number;
  targetPct: number;
  hardStopPct: number;
}

export interface IntradayShockBacktestSummary {
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

export interface IntradayShockBacktestDiagnostics {
  symbolsConsidered: number;
  symbolsWithResolvedToken: number;
  symbolsWithNoToken: string[];
  symbolsWithNoIntradayData: string[];
  symbolsWithNoTrades: string[];
  cacheHits: number;
  cacheMisses: number;
  kiteFetchFailures: string[];
}

export interface IntradayShockBacktestResponse {
  config: IntradayShockBacktestConfigSnapshot;
  summary: IntradayShockBacktestSummary;
  diagnostics: IntradayShockBacktestDiagnostics;
  trades: IntradayShockBacktestTrade[];
}
