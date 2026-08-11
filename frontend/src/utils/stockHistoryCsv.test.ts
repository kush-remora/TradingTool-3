import { describe, expect, it } from "vitest";
import type { StockDetailResponse } from "../types";
import { buildStockHistoryCsv, buildStockHistoryRows } from "./stockHistoryCsv";

const detail: StockDetailResponse = {
  symbol: "INFY",
  exchange: "NSE",
  avg_volume_20d: null,
  pivot_levels: null,
  fundamentals: { currentPrice: 102, fiftyTwoWeekLow: null, fiftyTwoWeekHigh: null, sma200: null, sma100: null },
  days: [{ date: "2026-08-05", open: 100, high: 105, low: 98, close: 102, volume: 1000, daily_change_pct: null, rsi14: null, vol_ratio: null }],
  delivery_days: [{ date: "2026-08-05", delivery_percentage: 52.5, delivered_quantity: 525, traded_quantity: 1000 }],
};

describe("stock history CSV", () => {
  it("joins delivery data and calculates open-based percentages", () => {
    const [row] = buildStockHistoryRows(detail);

    expect(row.day).toBe("Wed");
    expect(row.openToHighPct).toBe(5);
    expect(row.openToClosePct).toBe(2);
    expect(row.deliveryVolume).toBe(525);
    expect(row.deliveryPct).toBe(52.5);
  });

  it("writes the requested headers and leaves missing delivery values blank", () => {
    const row = buildStockHistoryRows({ ...detail, delivery_days: [] });
    const csv = buildStockHistoryCsv(row);

    expect(csv.split("\n")[0]).toBe("Date,Day,Open,Close,Low,High,\"Intraday % (Open, High)\",\"% (Open, Close)\",Volume,Delivery Volume,Delivery %");
    expect(csv.split("\n")[1]).toContain("2026-08-05,Wed,100,102,98,105,5,2,1000,,");
  });
});
