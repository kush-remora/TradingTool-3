import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { WeeklyLowAlignmentBacktestPage } from "./WeeklyLowAlignmentBacktestPage";

const getJsonMock = vi.fn();
const postJsonMock = vi.fn();

vi.mock("../utils/api", () => ({
  getJson: (...args: unknown[]) => getJsonMock(...args),
  postJson: (...args: unknown[]) => postJsonMock(...args),
}));

describe("WeeklyLowAlignmentBacktestPage", () => {
  it("runs a watchlist backtest with the selected target and holding period", async () => {
    getJsonMock.mockResolvedValue({ options: [{ label: "watchlist", value: "watchlist", count: 1 }] });
    postJsonMock.mockResolvedValue({
      watchlistKey: "watchlist",
      testedFromDate: "2026-02-10",
      testedToDate: "2026-08-07",
      targetPct: 8,
      maxHoldingTradingDays: 10,
      minimumRetestGapTradingDays: 5,
      retestTolerancePct: 1,
      summary: {
        setupCount: 1,
        noRetestCount: 0,
        tooSoonRetestCount: 0,
        filledTradeCount: 1,
        targetHitCount: 1,
        timeExitCount: 0,
        positionOpenSkipCount: 0,
        averageReturnPct: 8,
      },
      symbols: [{
        symbol: "INFY",
        companyName: "Infosys",
        testedFromDate: "2026-02-10",
        testedToDate: "2026-08-07",
        summary: {
          setupCount: 1,
          noRetestCount: 0,
          tooSoonRetestCount: 0,
          filledTradeCount: 1,
          targetHitCount: 1,
          timeExitCount: 0,
          positionOpenSkipCount: 0,
          averageReturnPct: 8,
        },
        trades: [{
          symbol: "INFY",
          instrumentToken: 1,
          previousWeekStartDate: "2026-06-29",
          entryWeekStartDate: "2026-07-06",
          previousWeekLow: 700,
          previousWeekLowDate: "2026-07-01",
          retestDate: "2026-07-08",
          retestLow: 704,
          retestGapTradingDays: 5,
          entryPrice: 707,
          targetPrice: 763.56,
          outcome: "TARGET_HIT",
          entryDate: "2026-07-08",
          exitDate: "2026-07-15",
          exitPrice: 763.56,
          holdingTradingDays: 5,
          returnPct: 8,
        }],
      }],
    });

    render(<WeeklyLowAlignmentBacktestPage />);
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("watchlist (1)"));
    fireEvent.change(screen.getByRole("spinbutton", { name: "Target percentage" }), { target: { value: "8" } });
    fireEvent.change(screen.getByRole("spinbutton", { name: "Maximum holding sessions" }), { target: { value: "10" } });
    fireEvent.click(screen.getByRole("button", { name: "Run six-month backtest" }));

    expect(postJsonMock).toHaveBeenCalledWith("/api/strategy/weekly-low-alignment-backtest/run", {
      watchlistKey: "watchlist",
      targetPct: 8,
      maxHoldingTradingDays: 10,
    });
    const table = await screen.findByText("Weekly audit trail");
    expect(table).toBeInTheDocument();
    expect(within(screen.getByRole("table")).getByText("Target hit")).toBeInTheDocument();
    expect(screen.getByText(/There is no stop-loss/)).toBeInTheDocument();
  }, 10000);
});
