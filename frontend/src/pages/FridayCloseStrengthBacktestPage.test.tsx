import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { FridayCloseStrengthBacktestPage } from "./FridayCloseStrengthBacktestPage";

const getJsonMock = vi.fn();
const postJsonMock = vi.fn();

vi.mock("../utils/api", () => ({
  getJson: (...args: unknown[]) => getJsonMock(...args),
  postJson: (...args: unknown[]) => postJsonMock(...args),
}));

describe("FridayCloseStrengthBacktestPage", () => {
  it("runs the selected watchlist and shows maximum upside observations", async () => {
    getJsonMock.mockResolvedValue({ options: [{ label: "watchlist", value: "watchlist", count: 1 }] });
    postJsonMock.mockResolvedValue({
      watchlistKey: "watchlist",
      testedFromDate: "2026-02-10",
      testedToDate: "2026-08-14",
      closePositionThresholdPct: 70,
      fridayMoveThresholdPct: 2,
      summary: {
        signalCount: 1,
        maximumUpsideAtLeast2PctCount: 1,
        maximumUpsideAtLeast5PctCount: 1,
        maximumUpsideAtLeast2PctRatePct: 100,
        averageMaximumUpsidePct: 8.5,
        medianMaximumUpsidePct: 8.5,
      },
      observations: [{
        symbol: "INFY",
        companyName: "Infosys",
        instrumentToken: 1,
        signalDate: "2026-07-03",
        thursdayClose: 100,
        fridayHigh: 106,
        fridayLow: 102,
        fridayClose: 105,
        fridayClosePositionPct: 75,
        fridayMovePct: 5,
        entryDate: "2026-07-06",
        entryPrice: 110,
        followingWeekHighDate: "2026-07-08",
        followingWeekHigh: 120,
        maximumUpsidePct: 9.09,
      }],
    });

    render(<FridayCloseStrengthBacktestPage />);
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("watchlist (1)"));
    fireEvent.click(screen.getByRole("button", { name: "Run six-month backtest" }));

    expect(postJsonMock).toHaveBeenCalledWith("/api/strategy/friday-close-strength-backtest/run", { watchlistKey: "watchlist" });
    expect(await screen.findByText("Friday signal audit trail")).toBeInTheDocument();
    expect(within(screen.getByRole("table")).getByText("9.09%")).toBeInTheDocument();
    expect(screen.getByText(/retrospective opportunity metric/)).toBeInTheDocument();
  }, 10000);
});
