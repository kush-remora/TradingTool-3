import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { BaseRetestBacktestPage } from "./BaseRetestBacktestPage";

const getJsonMock = vi.fn();
const postJsonMock = vi.fn();

vi.mock("../utils/api", () => ({
  getJson: (...args: unknown[]) => getJsonMock(...args),
  postJson: (...args: unknown[]) => postJsonMock(...args),
}));

const report = {
  watchlistKey: "watchlist",
  selectedSymbol: null,
  testedFromDate: "2026-02-14",
  testedToDate: "2026-08-14",
  lowTolerancePct: 1,
  reboundPct: 5,
  limitOffsetPct: 1,
  invalidationPct: 1,
  targetPct: 5,
  stopLossPct: 5,
  summary: {
    setupCount: 1,
    filledTradeCount: 1,
    noFillCount: 0,
    baseInvalidatedCount: 0,
    targetHitCount: 1,
    stopLossCount: 0,
    endOfDataExitCount: 0,
    profitableTradeCount: 1,
    lossTradeCount: 0,
    winRatePct: 100,
    averagePnlPct: 5,
    medianPnlPct: 5,
    worstPnlPct: 5,
    totalPnlPct: 5,
    totalHoldingSessions: 2,
  },
  observations: [{
    symbol: "NETWEB",
    companyName: "Netweb",
    instrumentToken: 1,
    firstLowDate: "2026-07-01",
    firstLow: 100,
    firstReboundDate: "2026-07-02",
    firstReboundHigh: 105,
    firstReboundMovePct: 5,
    secondLowDate: "2026-07-03",
    secondLow: 100.5,
    lowDifferencePct: 0.5,
    confirmationDate: "2026-07-06",
    confirmationHigh: 105.6,
    confirmationMovePct: 5.07,
    basePrice: 100,
    limitPrice: 101,
    invalidationClosePrice: 99,
    orderActiveDate: "2026-07-07",
    orderEndDate: "2026-07-08",
    invalidationDate: null,
    fillDate: "2026-07-07",
    fillPrice: 101,
    targetPrice: 106.05,
    stopLossPrice: 95.95,
    exitDate: "2026-07-08",
    exitPrice: 106.05,
    outcome: "TARGET_HIT",
    pnlPct: 5,
    holdingSessions: 2,
  }],
};

describe("BaseRetestBacktestPage", () => {
  beforeEach(() => {
    getJsonMock.mockReset();
    postJsonMock.mockReset();
    getJsonMock.mockImplementation((path: string) => path.includes("/members")
      ? Promise.resolve([{ instrument_token: 1, trading_symbol: "NETWEB", company_name: "Netweb", exchange: "NSE", instrument_type: "EQ" }])
      : Promise.resolve({ options: [{ label: "watchlist", value: "watchlist", count: 1 }] }));
    postJsonMock.mockResolvedValue(report);
  });

  it("runs a watchlist with configurable target and stop loss and shows P L and holding days", async () => {
    render(<BaseRetestBacktestPage />);
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("watchlist (1)"));
    fireEvent.click(screen.getByRole("button", { name: "Run six-month backtest" }));

    expect(postJsonMock).toHaveBeenCalledWith("/api/strategy/base-retest-backtest/run", {
      watchlistKey: "watchlist",
      targetPct: 5,
      stopLossPct: 5,
    });
    expect(await screen.findByText("Base and trade audit trail")).toBeInTheDocument();
    expect(document.body.textContent).toContain("Target hit");
    expect(document.body.textContent).toContain("+5.00%");
    expect(screen.getByRole("cell", { name: "2" })).toBeInTheDocument();
  }, 10000);

  it("runs one selected stock from a watchlist", async () => {
    render(<BaseRetestBacktestPage />);
    fireEvent.click(screen.getByRole("radio", { name: "Single stock" }));
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("watchlist (1)"));
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Stock" }));
    fireEvent.click(await screen.findByText("NETWEB · Netweb"));
    fireEvent.click(screen.getByRole("button", { name: "Run six-month backtest" }));

    expect(postJsonMock).toHaveBeenCalledWith("/api/strategy/base-retest-backtest/run", {
      watchlistKey: "watchlist",
      targetPct: 5,
      stopLossPct: 5,
      symbol: "NETWEB",
    });
    expect(await screen.findByText("Base and trade audit trail")).toBeInTheDocument();
  }, 10000);
});
