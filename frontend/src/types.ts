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
