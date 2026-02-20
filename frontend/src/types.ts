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
  createdAt: string;
  updatedAt: string;
}

export interface WatchlistStock {
  id: number;
  watchlistId: number;
  stockId: number;
  createdAt: string;
}
