import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { WeeklyPriceWatchlistScannerPage } from "./WeeklyPriceWatchlistScannerPage";

const getJsonMock = vi.fn();

vi.mock("../utils/api", () => ({
  getJson: (...args: unknown[]) => getJsonMock(...args),
}));

describe("WeeklyPriceWatchlistScannerPage", () => {
  it("shows ten raw candidate lows, highlights strongest rows and opens stock review", async () => {
    getJsonMock.mockImplementation((path: string) => {
      if (path === "/api/strategy/weekly-price-review/watchlists") {
        return Promise.resolve({ options: [{ label: "watchlist", value: "watchlist", count: 1 }] });
      }

      return Promise.resolve({
        watchlistKey: "watchlist",
        rows: [{
          symbol: "INFY",
          companyName: "Infosys",
          instrumentToken: 408065,
          days: buildDays(),
        }],
      });
    });

    const onOpenStockReview = vi.fn();
    const { container } = render(<WeeklyPriceWatchlistScannerPage onOpenStockReview={onOpenStockReview} />);

    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("watchlist (1)"));

    await screen.findByRole("columnheader", { name: "Stock" });
    expect(screen.getByRole("columnheader", { name: "Stock" })).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "Reference date" })).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "Reference low" })).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "Hits in previous 20 sessions" })).toBeInTheDocument();
    expect(screen.getAllByRole("row")).toHaveLength(11);
    expect(screen.getAllByText("15")).toHaveLength(5);
    expect(container.querySelectorAll(".base-consolidation-focus-row")).toHaveLength(5);
    expect(container.querySelector(".base-consolidation-focus-stock")).toBeInTheDocument();

    const kiteLink = screen.getByRole("link", { name: "Open INFY in Kite" });
    expect(kiteLink).toHaveAttribute("href", "https://kite.zerodha.com/chart/web/tvc/NSE/INFY/408065");
    expect(kiteLink).toHaveAttribute("target", "_blank");

    fireEvent.click(screen.getAllByRole("link", { name: "INFY" })[0]);
    await waitFor(() => expect(onOpenStockReview).toHaveBeenCalledWith("INFY"));
  });
});

function buildDays() {
  return Array.from({ length: 30 }, (_, index) => ({
    date: `2026-06-${String(index + 1).padStart(2, "0")}`,
    open: 100,
    high: 110,
    low: index < 20 || index >= 25 ? 100 : 110,
    close: 105,
    volume: 100,
    deliveryPercentage: null,
  }));
}
