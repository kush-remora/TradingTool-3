export interface Watchlist {
  id: number;
  name: string;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Stock {
  id: number;
  symbol: string;
  instrumentToken: number;
  companyName: string;
  exchange: string;
  description: string | null;
  priority: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface WatchlistStock {
  id: number;
  watchlistId: number;
  stockId: number;
  createdAt: string;
}

export interface Tag {
  id: number;
  name: string;
  createdAt: string;
  updatedAt: string;
}

export interface StockTag {
  id: number;
  stockId: number;
  tagId: number;
  createdAt: string;
}

export interface WatchlistTag {
  id: number;
  watchlistId: number;
  tagId: number;
  createdAt: string;
}

export interface StockNote {
  id: number;
  stockId: number;
  content: string;
  createdAt: string;
}

export interface UserLayout {
  id: number;
  layoutData: string; // raw JSON string: { watchlistOrder: number[], stockOrder: Record<string, number[]> }
  updatedAt: string;
}

export interface LayoutData {
  watchlistOrder: number[];
  stockOrder: Record<string, number[]>; // key is watchlistId string
}

export interface InstrumentSearchResult {
  instrumentToken: number;
  tradingSymbol: string;
  companyName: string;
  exchange: string;
  instrumentType: string;
}
