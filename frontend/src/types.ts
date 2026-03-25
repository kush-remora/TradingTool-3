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
  created_at: string;
  updated_at: string;
}

export interface TradeWithTargets {
  trade: Trade;
  gtt_targets: GttTarget[];
  total_invested: string;
}

export interface CreateTradeInput {
  stock_id: number;
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
  avg_volume_20d: number | null;
  days: DayDetail[];
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
}

// ==================== Watchlist Dashboard ====================

export interface WatchlistRow {
  symbol: string;
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
