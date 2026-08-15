import { act, fireEvent, render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { WeeklyLowRetestBacktestPage } from "./WeeklyLowRetestBacktestPage";

const getJsonMock = vi.fn();
const postJsonMock = vi.fn();

vi.mock("../utils/api", () => ({
  getJson: (...args: unknown[]) => getJsonMock(...args),
  postJson: (...args: unknown[]) => postJsonMock(...args),
}));

const watchlistResponse = { options: [{ label: "watchlist", value: "watchlist", count: 1 }] };
const membersResponse = [{ instrument_token: 1, trading_symbol: "NETWEB", company_name: "Netweb", exchange: "NSE", instrument_type: "EQ" }];
const detailDates = [
  "2026-06-24", "2026-06-25", "2026-06-26", "2026-06-29", "2026-06-30",
  "2026-07-01", "2026-07-02", "2026-07-03", "2026-07-06", "2026-07-07",
  "2026-07-08", "2026-07-09", "2026-07-10", "2026-07-13", "2026-07-14",
  "2026-07-15", "2026-07-16",
];
const detailResponse = {
  days: detailDates.map((date, index) => {
    return {
      date,
      open: 100,
      high: index === 8 ? 106 : 103,
      low: index === 7 ? 100.5 : 99,
      close: index === 8 ? 105.53 : 101,
      volume: 1000,
      daily_change_pct: index === 0 ? null : 0.5,
      rsi14: null,
      vol_ratio: 1,
    };
  }),
  delivery_days: [],
};

function report(selectedSymbol: string | null = null) {
  return {
    watchlistKey: "watchlist",
    selectedSymbol,
    testedFromDate: "2026-02-10",
    testedToDate: "2026-08-14",
    limitOffsetPct: 0.5,
    orderWindowSessions: 4,
    targetPct: 5,
    summary: {
      signalCount: 1,
      noFillCount: 0,
      filledTradeCount: 1,
      targetHitCount: 1,
      fourthSessionExitCount: 0,
      profitableExitCount: 1,
      lossExitCount: 0,
      averageRealizedReturnPct: 5,
      medianRealizedReturnPct: 5,
      worstRealizedReturnPct: 5,
      totalRealizedReturnPct: 5,
      totalHoldingSessions: 2,
    },
    observations: [{
      symbol: "NETWEB",
      companyName: "Netweb",
      instrumentToken: 1,
      lookbackStartDate: "2026-07-01",
      lookbackEndDate: "2026-07-07",
      anchorDate: "2026-07-06",
      anchorLow: 100,
      anchorVolumeVs10DayAveragePct: 100,
      anchorCloseNearHighPct: 75,
      recentCycleLowDate: "2026-07-02",
      recentCycleLow: 101,
      triggerDate: "2026-07-07",
      triggerHigh: 106,
      triggerMovePct: 6,
      cycleSequence: "LOW_BEFORE_HIGH",
      limitOrderDate: "2026-07-08",
      limitOrderExpiryDate: "2026-07-14",
      limitPrice: 100.5,
      orderWindowLowDate: "2026-07-08",
      orderWindowLow: 100.5,
      orderWindowLowVolumeVs10DayAveragePct: 100,
      orderWindowLowCloseNearHighPct: 50,
      fillDate: "2026-07-08",
      fillLow: 100,
      fillPrice: 100.5,
      fillVolumeVs10DayAveragePct: 100,
      fillCloseNearHighPct: 50,
      targetPrice: 105,
      peakHighDate: "2026-07-09",
      peakHigh: 105,
      peakReturnPct: 4.48,
      fourthSessionCloseDate: "2026-07-13",
      fourthSessionClose: 104,
      noFillFourthSessionPnlPct: null,
      targetReachedInOrderWindow: true,
      exitDate: "2026-07-09",
      exitPrice: 105.53,
      outcome: "TARGET_HIT",
      realizedReturnPct: 5,
      holdingSessions: 2,
    }],
  };
}

describe("WeeklyLowRetestBacktestPage", () => {
  beforeEach(() => {
    getJsonMock.mockReset();
    postJsonMock.mockReset();
    getJsonMock.mockImplementation((path: string) => {
      if (path.includes("/members")) return Promise.resolve(membersResponse);
      if (path.includes("/detail")) return Promise.resolve(detailResponse);
      return Promise.resolve(watchlistResponse);
    });
    postJsonMock.mockImplementation((_path: string, request: { symbol?: string }) => Promise.resolve(report(request.symbol ?? null)));
  });

  it("runs the selected watchlist and shows raw daily audit values", async () => {
    render(<WeeklyLowRetestBacktestPage />);
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("watchlist (1)"));
    fireEvent.click(screen.getByRole("button", { name: "Run six-month backtest" }));

    expect(postJsonMock).toHaveBeenCalledWith("/api/strategy/weekly-low-retest-backtest/run", { watchlistKey: "watchlist", limitOffsetPct: 0.5, targetPct: 5 });
    expect(await screen.findByText("Daily trigger audit trail")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "All signals (1)" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Actually entered (1)" })).toBeInTheDocument();
    expect(document.body.textContent).toContain("Ref ₹100 · ₹101 → ₹106 · 6.00%");
    expect(document.body.textContent).toContain("₹105 · 4.48%");
    fireEvent.click(screen.getByRole("button", { name: "Show debug details" }));
    expect(document.body.textContent).toContain("Vol 100.00% of 10D avg");
    expect(document.body.textContent).toContain("5.00% target · 5.00%");
  }, 10000);

  it("opens the trade window and highlights entry and exit sessions", async () => {
    render(<WeeklyLowRetestBacktestPage />);
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("watchlist (1)"));
    fireEvent.click(screen.getByRole("button", { name: "Run six-month backtest" }));
    await screen.findByText("Daily trigger audit trail");

    fireEvent.click(screen.getByRole("button", { name: "View" }));
    const dialog = await screen.findByRole("dialog");
    expect(document.querySelector(".ant-modal-mask")).not.toBeInTheDocument();
    expect(within(dialog).getByRole("columnheader", { name: "Low → high %" })).toBeInTheDocument();
    expect(within(dialog).getByRole("columnheader", { name: "Delivery %" })).toBeInTheDocument();
    expect(within(dialog).getByRole("columnheader", { name: "Close position" })).toBeInTheDocument();
    expect(within(dialog).getByText(/Wed, 24 Jun.*Thu, 16 Jul.*5 sessions before cycle start \+ 5 after exit/)).toBeInTheDocument();
    expect(dialog.querySelector(".weekly-low-retest-entry-row")).toBeInTheDocument();
    expect(dialog.querySelector(".weekly-low-retest-exit-row")).toBeInTheDocument();
    expect(dialog.querySelector(".weekly-low-retest-order-row")).toBeInTheDocument();
    expect(within(dialog).getByText("Order")).toBeInTheDocument();
    expect(within(dialog).getByText("Entry")).toBeInTheDocument();
    expect(within(dialog).getByText("Exit")).toBeInTheDocument();
    expect(within(dialog).getByText("HIGH")).toBeInTheDocument();
    expect(within(dialog).getAllByText(/16 Jul/).length).toBeGreaterThan(0);
  }, 10000);

  it("sends a configured target percentage while keeping the low-based rule", async () => {
    render(<WeeklyLowRetestBacktestPage />);
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("watchlist (1)"));
    await act(async () => {
      fireEvent.change(screen.getByRole("spinbutton", { name: "Target percent" }), { target: { value: "3" } });
      await new Promise<void>((resolve) => setTimeout(resolve, 0));
    });
    fireEvent.click(screen.getByRole("button", { name: "Run six-month backtest" }));

    expect(postJsonMock).toHaveBeenCalledWith("/api/strategy/weekly-low-retest-backtest/run", { watchlistKey: "watchlist", limitOffsetPct: 0.5, targetPct: 3 });
  }, 10000);

  it("runs one selected stock from the watchlist", async () => {
    render(<WeeklyLowRetestBacktestPage />);
    fireEvent.click(screen.getByRole("radio", { name: "Single stock" }));
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("watchlist (1)"));
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Stock" }));
    fireEvent.click(await screen.findByText("NETWEB · Netweb"));
    fireEvent.click(screen.getByRole("button", { name: "Run six-month backtest" }));

    expect(postJsonMock).toHaveBeenCalledWith("/api/strategy/weekly-low-retest-backtest/run", { watchlistKey: "watchlist", limitOffsetPct: 0.5, targetPct: 5, symbol: "NETWEB" });
    expect(await screen.findByText("Daily trigger audit trail")).toBeInTheDocument();
    expect(document.body.textContent).toContain("Total P/L (sum)+5.00%");
    expect(document.body.textContent).toContain("Total hold (sessions)2");
  }, 10000);
});
