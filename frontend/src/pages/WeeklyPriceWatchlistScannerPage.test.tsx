import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { WeeklyPriceWatchlistScannerPage } from "./WeeklyPriceWatchlistScannerPage";

const getJsonMock = vi.fn();

vi.mock("../utils/api", () => ({
  getJson: (...args: unknown[]) => getJsonMock(...args),
}));

describe("WeeklyPriceWatchlistScannerPage", () => {
  it("shows four weekly comparisons and opens the selected stock review", async () => {
    getJsonMock.mockImplementation((path: string) => {
      if (path === "/api/strategy/weekly-price-review/watchlists") {
        return Promise.resolve({ options: [{ label: "weekly", value: "weekly", count: 1 }] });
      }
      return Promise.resolve({
        watchlistKey: "weekly",
        rows: [{ symbol: "INFY", companyName: "Infosys", days: [
          day("2026-07-06", 100, 110),
          day("2026-07-13", 105, 115),
          day("2026-07-20", 110, 120),
          day("2026-07-27", 115, 125),
        ] }],
      });
    });
    const onOpenStockReview = vi.fn();
    render(<WeeklyPriceWatchlistScannerPage onOpenStockReview={onOpenStockReview} />);

    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("weekly (1)"));

    await screen.findByText("INFY");
    expect(screen.getAllByText(/Range /)).toHaveLength(4);
    expect(screen.getAllByText("2026-07-06 (Mon)")).toHaveLength(2);
    expect(screen.getAllByText("2026-07-27 (Mon)")).toHaveLength(2);
    expect(screen.getByTestId("floating-change-calculator")).toHaveStyle({
      position: "fixed",
      right: "24px",
      bottom: "24px",
    });

    fireEvent.click(screen.getByRole("button", { name: "Open review" }));
    await waitFor(() => expect(onOpenStockReview).toHaveBeenCalledWith("INFY"));
  });
});

function day(date: string, low: number, high: number) {
  return { date, open: low, low, high, close: high, volume: 100 };
}
