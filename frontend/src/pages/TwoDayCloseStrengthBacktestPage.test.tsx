import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { TwoDayCloseStrengthBacktestPage } from "./TwoDayCloseStrengthBacktestPage";

const getJsonMock = vi.fn();
const postJsonMock = vi.fn();

vi.mock("../utils/api", () => ({
  getJson: (...args: unknown[]) => getJsonMock(...args),
  postJson: (...args: unknown[]) => postJsonMock(...args),
}));

describe("TwoDayCloseStrengthBacktestPage", () => {
  it("runs the selected watchlist and shows the realized return", async () => {
    getJsonMock.mockResolvedValue({ options: [{ label: "watchlist", value: "watchlist", count: 1 }] });
    postJsonMock.mockResolvedValue({
      watchlistKey: "watchlist",
      testedFromDate: "2026-02-10",
      testedToDate: "2026-08-14",
      closePositionThresholdPct: 80,
      targetPct: 5,
      summary: {
        signalCount: 1,
        targetHitCount: 1,
        thursdayCloseExitCount: 0,
        profitableExitCount: 1,
        lossExitCount: 0,
        averageRealizedReturnPct: 5,
        medianRealizedReturnPct: 5,
        worstRealizedReturnPct: 5,
      },
      observations: [{
        symbol: "INFY",
        companyName: "Infosys",
        instrumentToken: 1,
        patternStartDate: "2026-07-06",
        patternEndDate: "2026-07-10",
        patternClosePositionPct: [50, 60, 75, 85, 90],
        entryDate: "2026-07-13",
        entryPrice: 100,
        targetPrice: 105,
        exitDate: "2026-07-14",
        exitPrice: 105,
        exitReason: "TARGET_HIT",
        realizedReturnPct: 5,
      }],
    });

    render(<TwoDayCloseStrengthBacktestPage />);
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("watchlist (1)"));
    fireEvent.click(screen.getByRole("button", { name: "Run six-month backtest" }));

    expect(postJsonMock).toHaveBeenCalledWith("/api/strategy/two-day-close-strength-backtest/run", { watchlistKey: "watchlist" });
    expect(await screen.findByText("Five-session pattern audit trail")).toBeInTheDocument();
    expect(screen.getByText("50% · 60% · 75% · 85% · 90%")).toBeInTheDocument();
    expect(document.body.textContent).toContain("5.00%");
  }, 10000);
});
