import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ThreeWeekWatchlistReviewPage } from "./ThreeWeekWatchlistReviewPage";

const getJsonMock = vi.fn();

vi.mock("../utils/api", () => ({
  getJson: (...args: unknown[]) => getJsonMock(...args),
}));

describe("ThreeWeekWatchlistReviewPage", () => {
  it("shows four weekly rows for a selected watchlist and opens a stock review", async () => {
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
          days: [
            buildDay("2026-06-01", 100, 110),
            buildDay("2026-06-08", 101, 111),
            buildDay("2026-06-15", 102, 112),
            buildDay("2026-06-22", 103, 113),
            buildDay("2026-06-29", 104, 114),
          ],
        }],
      });
    });

    const onOpenStockReview = vi.fn();
    render(<ThreeWeekWatchlistReviewPage onOpenStockReview={onOpenStockReview} />);

    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("watchlist (1)"));

    expect(await screen.findByRole("columnheader", { name: "Week" })).toBeInTheDocument();
    expect(screen.getAllByText(/Week of 2026-/)).toHaveLength(4);
    expect(screen.getByRole("columnheader", { name: "Low day · Del / Vol" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open review" }));
    expect(onOpenStockReview).toHaveBeenCalledWith("INFY");
  });
});

function buildDay(date: string, low: number, high: number) {
  return {
    date,
    open: low + 1,
    high,
    low,
    close: high - 1,
    volume: 100_000,
    deliveryPercentage: 55,
  };
}
