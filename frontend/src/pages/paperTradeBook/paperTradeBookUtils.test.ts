import { describe, expect, it } from "vitest";
import type { TradeWithTargets } from "../../types";
import {
  formatTradeDate,
  getDaysSinceTrade,
  getTradePnl,
} from "./paperTradeBookUtils";

const trade: TradeWithTargets = {
  trade: {
    id: 7,
    instrument_token: 123,
    nse_symbol: "NETWEB",
    quantity: 1,
    avg_buy_price: "100",
    today_low: null,
    stop_loss_percent: "5",
    stop_loss_price: "95",
    notes: null,
    trade_date: "2026-08-01",
    close_price: null,
    close_date: null,
    created_at: "2026-08-01T10:00:00Z",
    updated_at: "2026-08-01T10:00:00Z",
  },
  gtt_targets: [],
  total_invested: "100",
};

describe("paper Trade Book helpers", () => {
  it("calculates calendar days held from date-only values", () => {
    expect(getDaysSinceTrade("2026-08-01", "2026-08-12")).toBe(11);
    expect(getDaysSinceTrade("2026-08-12", "2026-08-12")).toBe(0);
  });

  it("formats entry dates for compact display", () => {
    expect(formatTradeDate("2026-08-01")).toBe("01 Aug 2026");
  });

  it("calculates one-share P&L from the live price", () => {
    const pnl = getTradePnl(trade, 112.5);
    expect(pnl?.pnl).toBe(12.5);
    expect(pnl?.pnlPct).toBe(12.5);
    expect(pnl?.isProfit).toBe(true);
  });
});

