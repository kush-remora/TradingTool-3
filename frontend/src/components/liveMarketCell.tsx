import { LiveMarketWidget } from "./LiveMarketWidget";
import { getCachedLiveMarketData } from "../hooks/useLiveMarketData";
import type { StockQuoteSnapshot } from "../types";

type RenderLiveMarketCellOptions = {
  symbol: string;
  snapshot?: StockQuoteSnapshot | null;
  fallbackLtp?: number | null;
  fallbackChangePercent?: number | null;
  showDetails?: boolean;
};

export function resolveMarketChangePercent(
  symbol: string,
  snapshot: StockQuoteSnapshot | null | undefined,
  fallbackChangePercent?: number | null,
): number | null {
  return getCachedLiveMarketData(`NSE:${symbol}`)?.changePercent ?? snapshot?.change_percent ?? fallbackChangePercent ?? null;
}

export function renderLiveMarketCell({
  symbol,
  snapshot = null,
  fallbackLtp = null,
  fallbackChangePercent = null,
  showDetails = true,
}: RenderLiveMarketCellOptions) {
  return (
    <LiveMarketWidget
      symbol={`NSE:${symbol}`}
      snapshot={snapshot}
      fallbackLtp={fallbackLtp}
      fallbackChangePercent={fallbackChangePercent}
      showDetails={showDetails}
    />
  );
}
