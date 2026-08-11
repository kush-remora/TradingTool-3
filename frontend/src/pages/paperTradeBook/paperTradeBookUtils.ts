import dayjs from "dayjs";
import type { StockQuoteSnapshot, TradeWithTargets } from "../../types";
import { calculatePnL, type PnLResult } from "../../utils/pnlUtils";

export function getDaysSinceTrade(tradeDate: string, today: string = dayjs().format("YYYY-MM-DD")): number {
  const entered = dayjs(tradeDate, "YYYY-MM-DD");
  const current = dayjs(today, "YYYY-MM-DD");

  if (!entered.isValid() || !current.isValid()) return 0;
  return Math.max(0, current.startOf("day").diff(entered.startOf("day"), "day"));
}

export function formatTradeDate(tradeDate: string): string {
  const parsed = dayjs(tradeDate, "YYYY-MM-DD");
  return parsed.isValid() ? parsed.format("DD MMM YYYY") : tradeDate;
}

export function getTradeCurrentPrice(
  trade: TradeWithTargets,
  quotesBySymbol: Record<string, StockQuoteSnapshot>,
): number | null {
  return quotesBySymbol[trade.trade.nse_symbol.toUpperCase()]?.ltp ?? null;
}

export function getTradePnl(
  trade: TradeWithTargets,
  currentPrice: number | null,
): PnLResult | null {
  return calculatePnL(trade.trade.avg_buy_price, currentPrice, trade.trade.quantity);
}

