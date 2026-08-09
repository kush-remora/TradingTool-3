import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { SummaryConsolePage } from "./SummaryConsolePage";

const getJsonMock = vi.fn();

vi.mock("../utils/api", () => ({
  getJson: (...args: unknown[]) => getJsonMock(...args),
}));

const response = {
  requestedAsOfDate: "2026-08-07",
  lookbackSessions: 5,
  watchlists: ["growth_watchlist", "leaders"],
  scannedCount: 2,
  eventCount: 1,
  uniqueStockCount: 1,
  rows: [{
    symbol: "INFY",
    companyName: "Infosys",
    instrumentToken: 408065,
    watchlists: ["growth_watchlist", "leaders"],
    asOfDate: "2026-08-07",
    close: 1540.5,
    previousClose: 1492.74,
    dailyMovePct: 3.2,
    largeMove: true,
    sma200: 1500,
    sma200Crossed: true,
    volume: 200000,
    averageVolume5: 100000,
    volumeRatio: 2,
    volumeAnomaly: true,
    deliveryPercentage: 56,
    breakout20Level: 1500,
    breakout20LevelCrossed: true,
    breakout20CloseConfirmed: true,
    breakout40Level: 1480,
    breakout40LevelCrossed: false,
    breakout40CloseConfirmed: false,
    breakout60Level: 1460,
    breakout60LevelCrossed: true,
    breakout60CloseConfirmed: false,
  }],
};

describe("SummaryConsolePage", () => {
  it("scans multiple watchlists and exposes Kite and detail actions", async () => {
    getJsonMock.mockImplementation((path: string) => {
      if (path === "/api/strategy/summary-console/watchlists") {
        return Promise.resolve({
          options: [
            { label: "growth_watchlist", value: "growth_watchlist", count: 1 },
            { label: "leaders", value: "leaders", count: 1 },
          ],
        });
      }
      return Promise.resolve(response);
    });

    const onOpenStockReview = vi.fn();
    render(<SummaryConsolePage onOpenStockReview={onOpenStockReview} />);

    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlists" }));
    fireEvent.click(await screen.findByText("growth_watchlist (1)"));
    fireEvent.click(await screen.findByText("leaders (1)"));

    expect(await screen.findByText("INFY")).toBeInTheDocument();
    expect(screen.getAllByText("High").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Close").length).toBeGreaterThan(0);
    expect(screen.getByRole("link", { name: "Open INFY in Kite" })).toHaveAttribute(
      "href",
      "https://kite.zerodha.com/chart/web/tvc/NSE/INFY/408065",
    );
    expect(screen.getByRole("button", { name: /Download CSV/ })).toBeEnabled();
    expect(screen.getByRole("button", { name: /Download AI guide/ })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open INFY detail review" }));
    expect(onOpenStockReview).toHaveBeenCalledWith("INFY");
    await waitFor(() => expect(getJsonMock).toHaveBeenCalledWith(
      "/api/strategy/summary-console/scan?watchlists=growth_watchlist%2Cleaders",
      { useCache: false },
    ));
  });
});
