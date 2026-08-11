import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { FiftyTwoWeekMomentumRule5Page } from "./FiftyTwoWeekMomentumRule5Page";

const getJsonMock = vi.fn();

vi.mock("../utils/api", () => ({
  getJson: (...args: unknown[]) => getJsonMock(...args),
}));

describe("FiftyTwoWeekMomentumRule5Page", () => {
  it("scans multiple watchlists and shows recent fresh breakouts for the selected period", async () => {
    getJsonMock.mockImplementation((path: string) => {
      if (path === "/api/strategy/52w-momentum/rule5/watchlists") {
        return Promise.resolve({
          options: [
            { label: "growth_watchlist", value: "growth_watchlist", count: 1 },
            { label: "leaders", value: "leaders", count: 1 },
          ],
        });
      }
      return Promise.resolve({
        requestedAsOfDate: "2026-08-07",
        lookbackSessions: 5,
        breakoutPeriodSessions: 200,
        nearHighTolerancePct: 2,
        watchlists: ["growth_watchlist", "leaders"],
        scannedCount: 2,
        breakoutStockCount: 1,
        results: [{
          symbol: "INFY",
          companyName: "Infosys",
          instrumentToken: 408065,
          watchlists: ["growth_watchlist", "leaders"],
          latestBreakoutDate: "2026-08-07",
          latestHigh: 1545,
          latestClose: 1545,
          latestReferenceHigh: 1500,
          latestReferenceHighDaysAgo: 20,
          latestCloseVsReferenceHighPct: 3,
          freshBreakoutDays: [{
            date: "2026-08-07",
            high: 1545,
            close: 1545,
            referenceHigh: 1500,
            referenceHighDaysAgo: 20,
            closeVsReferenceHighPct: 3,
          }],
        }],
      });
    });

    render(<FiftyTwoWeekMomentumRule5Page />);

    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlists" }));
    fireEvent.click(await screen.findByText("growth_watchlist (1)"));
    fireEvent.click(await screen.findByText("leaders (1)"));

    expect(await screen.findByText("INFY")).toBeInTheDocument();
    expect(screen.getByText("+3.00%")).toBeInTheDocument();
    expect(screen.getByText(/prior-high proximity/)).toBeInTheDocument();
    await waitFor(() => expect(getJsonMock).toHaveBeenCalledWith(
      "/api/strategy/52w-momentum/rule5/scan?watchlists=growth_watchlist%2Cleaders&breakoutPeriodSessions=200&nearHighTolerancePct=2",
      { useCache: false },
    ));
  });

  it("reruns the scan when the breakout period changes", async () => {
    getJsonMock.mockImplementation((path: string) => {
      if (path === "/api/strategy/52w-momentum/rule5/watchlists") {
        return Promise.resolve({ options: [{ label: "leaders", value: "leaders", count: 1 }] });
      }
      return Promise.resolve({
        requestedAsOfDate: "2026-08-07",
        lookbackSessions: 5,
        breakoutPeriodSessions: 20,
        nearHighTolerancePct: 2,
        watchlists: ["leaders"],
        scannedCount: 1,
        breakoutStockCount: 0,
        results: [],
      });
    });

    render(<FiftyTwoWeekMomentumRule5Page />);
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlists" }));
    fireEvent.click(await screen.findByText("leaders (1)"));
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Breakout period" }));
    fireEvent.click(await screen.findByText("20D"));

    await waitFor(() => expect(getJsonMock).toHaveBeenCalledWith(
      "/api/strategy/52w-momentum/rule5/scan?watchlists=leaders&breakoutPeriodSessions=20&nearHighTolerancePct=2",
      { useCache: false },
    ));
  });

  it("shows detailed signals and entered trades in separate backtest tabs", async () => {
    getJsonMock.mockImplementation((path: string) => {
      if (path === "/api/strategy/52w-momentum/rule5/watchlists") {
        return Promise.resolve({ options: [{ label: "leaders", value: "leaders", count: 1 }] });
      }
      if (path.includes("/backtest")) {
        return Promise.resolve({
          requestedAsOfDate: "2026-08-10",
          periodStartDate: "2026-02-10",
          breakoutPeriodSessions: 20,
          nearHighTolerancePct: 2,
          targetPct: 10,
          scannedCount: 1,
          signalCount: 1,
          enteredTradeCount: 1,
          targetHitCount: 1,
          openTradeCount: 0,
          signals: [{
            symbol: "INFY",
            companyName: "Infosys",
            signalDate: "2026-07-01",
            breakoutHigh: 110,
            breakoutClose: 105,
            referenceHigh: 100,
            referenceHighDaysAgo: 20,
            closeVsReferenceHighPct: 5,
            outcome: "ENTERED",
            entryPrice: 105,
            targetPrice: 115.5,
            tradeStatus: "TARGET_HIT",
          }],
          trades: [{
            symbol: "INFY",
            companyName: "Infosys",
            instrumentToken: 408065,
            entryDate: "2026-07-01",
            entryPrice: 105,
            targetPrice: 115.5,
            exitDate: "2026-07-05",
            exitPrice: 115.5,
            latestPrice: 120,
            changeFromEntryPct: 14.285714,
            status: "TARGET_HIT",
            holdingTradingDays: 3,
          }],
        });
      }
      return Promise.resolve({
        requestedAsOfDate: "2026-08-10",
        lookbackSessions: 5,
        breakoutPeriodSessions: 20,
        watchlists: ["leaders"],
        scannedCount: 1,
        breakoutStockCount: 0,
        results: [],
      });
    });

    render(<FiftyTwoWeekMomentumRule5Page />);
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlists" }));
    fireEvent.click(await screen.findByText("leaders (1)"));
    fireEvent.click(await screen.findByRole("button", { name: "Run backtest" }));

    expect(await screen.findByText("Detailed results")).toBeInTheDocument();
    expect(screen.getByText("Entered trades")).toBeInTheDocument();
    expect(screen.getByText("INFY")).toBeInTheDocument();
    fireEvent.click(screen.getByText("Entered trades"));
    expect(screen.getByRole("columnheader", { name: "LTP" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open INFY in Kite" })).toHaveAttribute(
      "href",
      "https://kite.zerodha.com/chart/web/tvc/NSE/INFY/408065",
    );
    expect(screen.getByRole("link", { name: "Open INFY in Kite" })).toHaveAttribute("target", "_blank");
    expect(screen.getByText("₹120")).toBeInTheDocument();
    expect(screen.getByText("+14.29%")).toBeInTheDocument();
    await waitFor(() => expect(getJsonMock).toHaveBeenCalledWith(
      "/api/strategy/52w-momentum/rule5/backtest?watchlists=leaders&breakoutPeriodSessions=200&nearHighTolerancePct=2&targetPct=10",
      { useCache: false },
    ));
  });
});
