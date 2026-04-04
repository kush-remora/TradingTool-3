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
  avgVol20d: number | null;
  volumeHeat: number | null;
  updatedAt: number;
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
  priceVs200maPct: number | null;
  rsi14: number | null;
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
}

export interface WeeklyPatternListResponse {
  runAt: string;
  lookbackWeeks: number;
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

export interface WeeklyPatternDetail extends WeeklyPatternResult {
  dayOfWeekProfile: DayProfile[];
  autocorrelation: AutocorrelationResult;
  patternSummary: string;
  weeklyHeatmap: WeekHeatmapRow[];
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

