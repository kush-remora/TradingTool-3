import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ShortHorizonSelectorPage } from "./ShortHorizonSelectorPage";

const getJsonMock = vi.fn();

vi.mock("../utils/api", () => ({
  getJson: (...args: unknown[]) => getJsonMock(...args),
}));

describe("ShortHorizonSelectorPage", () => {
  it("shows compact success evidence and opens the successful-day details", async () => {
    getJsonMock.mockImplementation((path: string) => {
      if (path === "/api/strategy/weekly-price-review/watchlists") {
        return Promise.resolve({ options: [{ label: "watchlist", value: "watchlist", count: 1 }] });
      }

      return Promise.resolve({
        watchlistKey: "watchlist",
        rows: [{
          symbol: "ABC",
          companyName: "ABC Limited",
          instrumentToken: 123,
          days: buildDays(),
        }],
      });
    });

    const onOpenCompactStockReview = vi.fn();
    render(<ShortHorizonSelectorPage onOpenCompactStockReview={onOpenCompactStockReview} />);

    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("watchlist (1)"));

    expect(await screen.findByTestId("short-horizon-selector-table")).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-selector-table")).getByRole("columnheader", { name: "No." })).toBeInTheDocument();
    expect(screen.getByText("20D 20 / 20")).toBeInTheDocument();
    expect(screen.getByText("6D 6 / 6")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("tab", { name: "Shortlist · 1" }));
    expect(await screen.findByTestId("short-horizon-shortlist-table")).toBeInTheDocument();
    expect(screen.getByText(/Passed 1 \/ 1 · Best 1 by each history/)).toBeInTheDocument();
    expect(screen.getByText(/reject only if the last 3 closes fall in a row and today's close breaks below the previous 5-session low/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("tab", { name: "Core · 1" }));
    expect(await screen.findByTestId("short-horizon-core-table")).toBeInTheDocument();
    expect(screen.getByText(/A stock must be in the top 1 by both 20-day success count and recent 6-day success count/)).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-core-table")).getByRole("columnheader", { name: "5D move" })).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-core-table")).getByRole("columnheader", { name: "20D move" })).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-core-table")).getByRole("columnheader", { name: "52W high" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Details" }));
    const dialog = await screen.findByRole("dialog");
    expect(dialog).toBeInTheDocument();
    expect(within(dialog).getByText("successful starting days out of 20")).toBeInTheDocument();
    expect(within(dialog).getByText("MID 20")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Review" }));
    await waitFor(() => expect(onOpenCompactStockReview).toHaveBeenCalledWith("ABC"));
  });
});

function buildDays() {
  return Array.from({ length: 25 }, (_, index) => ({
    date: `2026-07-${String(index + 1).padStart(2, "0")}`,
    open: 100,
    high: 110,
    low: 90,
    close: 100,
    volume: 100,
    deliveryPercentage: null,
  }));
}
