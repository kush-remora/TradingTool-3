import { describe, expect, it } from "vitest";
import type { DayDetail, StockDetailResponse } from "../../types";
import { buildCompactDailyRows, buildCompactWeeklyRows } from "./compactStockReview";
import { buildCompactReviewMarkdown } from "./compactReviewExport";

const day = (date: string, close: number, volume: number): DayDetail => ({
  date,
  open: close - 10,
  high: close + 20,
  low: close - 30,
  close,
  volume,
  daily_change_pct: 1.2,
  rsi14: 55.4,
  vol_ratio: null,
});

const detail: StockDetailResponse = {
  symbol: "NETWEB",
  exchange: "NSE",
  avg_volume_20d: 1_000_000,
  pivot_levels: null,
  fundamentals: {
    currentPrice: 4855,
    fiftyTwoWeekLow: 2037.3,
    fiftyTwoWeekHigh: 5244,
    sma200: 3739.01,
    sma100: 4137.64,
  },
  days: Array.from({ length: 12 }, (_, index) => day(`2026-08-${String(index + 1).padStart(2, "0")}`, 4800 + index, 100_000 + index * 10_000)),
  delivery_days: [{ date: "2026-08-11", delivery_percentage: 26.4, delivered_quantity: 1, traded_quantity: 2 }],
  rsi14_range: { current: 55.4, min_60d: 35, max_60d: 70, direction_3d: "UP" },
  roc9: { current: 4.2, direction_3d: "UP" },
  breakout_dates: {
    breakout_20d: "2026-08-05",
    breakout_50d: null,
    breakout_52d: null,
    breakout_100d: null,
    breakout_20d_level: 4800,
  },
};

describe("buildCompactReviewMarkdown", () => {
  it("includes the snapshot, breakout context, notes, and daily rows", () => {
    const dailyRows = buildCompactDailyRows(detail.days, detail.delivery_days);
    const markdown = buildCompactReviewMarkdown({
      instrument: {
        instrument_token: 4462849,
        trading_symbol: "NETWEB",
        company_name: "Netweb Technologies India",
        exchange: "NSE",
        instrument_type: "EQ",
      },
      data: detail,
      liveData: null,
      dailyRows,
      weeklyRows: buildCompactWeeklyRows(dailyRows),
      notes: [{ id: 1, instrumentToken: 4462849, notes: "Watch the prior high.", createdAt: "2026-08-11T18:30:00Z", updatedAt: "2026-08-11T18:30:00Z" }],
    });

    expect(markdown).toContain("# NETWEB — compact stock review");
    expect(markdown).toContain("## Last fresh breakout");
    expect(markdown).toContain("₹4,800");
    expect(markdown).toContain("## Daily candles · latest 60 sessions");
    expect(markdown).toContain("Open → low");
    expect(markdown).toContain("2026-08-12");
    expect(markdown).toContain("Evidence: O/H/L/C");
    expect(markdown).toContain("Watch the prior high.");
  });
});
