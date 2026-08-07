import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ThreeWeekWatchlistReviewPage } from "./ThreeWeekWatchlistReviewPage";

const getJsonMock = vi.fn();
const useStockQuotesMock = vi.fn(() => ({ quotesBySymbol: { INFY: { symbol: "INFY", ltp: 115 } }, loading: false, error: null }));

vi.mock("../utils/api", () => ({
  getJson: (...args: unknown[]) => getJsonMock(...args),
}));

vi.mock("../hooks/useStockQuotes", () => ({
  useStockQuotes: (...args: unknown[]) => useStockQuotesMock(...args),
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
          momentum_evidence: {
            as_of_date: "2026-07-03",
            current_close: 112,
            sma200: 100,
            above_sma200: true,
            distance_from_sma200_pct: 12,
            fifty_two_week_high: 120,
            distance_from_fifty_two_week_high_pct: -6.67,
            weekly_returns: [
              { week_start: "2026-06-08", week_end: "2026-06-12", return_pct: 4.8 },
              { week_start: "2026-06-15", week_end: "2026-06-19", return_pct: 5.2 },
              { week_start: "2026-06-22", week_end: "2026-06-26", return_pct: 4.5 },
              { week_start: "2026-06-29", week_end: "2026-07-03", return_pct: 6.1 },
            ],
            participation_events: [{ event_date: "2026-06-29", close: 111, volume: 2400000, volume_ratio: 2.4, daily_return_pct: 3.1, price_since_event_pct: 4.2, delivery_percentage: 62.1 }],
            participation_threshold: 2,
            participation_lookback_days: 90,
            data_status: "AVAILABLE",
          },
          days: [
            buildDay("2026-06-01", 100, 110),
            buildDay("2026-06-08", 100, 110, 200, 70),
            buildDay("2026-06-09", 101, 111, 100, 50),
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
    expect(screen.getByRole("columnheader", { name: "Structure" })).toBeInTheDocument();
    expect(screen.getAllByRole("img", { name: "Uptrend: higher high and higher low" })).toHaveLength(3);
    expect(screen.getByText("Low-day D/V higher")).toBeInTheDocument();
    expect(screen.getAllByText("Above 200 DMA").length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText("W1 +4.80%")).toBeInTheDocument();
    expect(screen.getByText(/High-volume days: 1 · lookback: 90 days/)).toBeInTheDocument();
    expect(screen.getByText("Dates: 29 Jun")).toBeInTheDocument();
    fireEvent.click(screen.getByText("Volume events · last 90 days (1)"));
    expect(screen.getByTestId("momentum-participation-table")).toBeInTheDocument();
    expect(screen.getByText("Current LTP")).toBeInTheDocument();
    expect(screen.getByText("+3.60%")).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "Delivery" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open review" }));
    expect(onOpenStockReview).toHaveBeenCalledWith("INFY");
  }, 10000);

  it("can focus the watchlist on stocks above the 200 DMA", async () => {
    getJsonMock.mockImplementation((path: string) => path === "/api/strategy/weekly-price-review/watchlists"
      ? Promise.resolve({ options: [{ label: "watchlist", value: "watchlist", count: 2 }] })
      : Promise.resolve({
        watchlistKey: "watchlist",
        rows: [
          { symbol: "INFY", companyName: "Infosys", instrumentToken: 1, days: [], momentum_evidence: { above_sma200: true, weekly_returns: [], participation_events: [], participation_threshold: 2, participation_lookback_days: 90, data_status: "AVAILABLE", as_of_date: "2026-07-03", current_close: 112, sma200: 100, distance_from_sma200_pct: 12, fifty_two_week_high: 120, distance_from_fifty_two_week_high_pct: -6.67 } },
          { symbol: "TCS", companyName: "TCS", instrumentToken: 2, days: [], momentum_evidence: { above_sma200: false, weekly_returns: [], participation_events: [], participation_threshold: 2, participation_lookback_days: 90, data_status: "AVAILABLE", as_of_date: "2026-07-03", current_close: 90, sma200: 100, distance_from_sma200_pct: -10, fifty_two_week_high: 130, distance_from_fifty_two_week_high_pct: -30.77 } },
        ],
      }));

    render(<ThreeWeekWatchlistReviewPage onOpenStockReview={vi.fn()} />);
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("watchlist (2)"));
    expect(await screen.findByTestId("watchlist-stock-card-TCS")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("radio", { name: "Above 200 DMA" }));

    expect(screen.getByTestId("watchlist-stock-card-INFY")).toBeInTheDocument();
    expect(screen.queryByTestId("watchlist-stock-card-TCS")).not.toBeInTheDocument();
    expect(screen.getByText(/Showing 1 of 2 stocks/)).toBeInTheDocument();
  });
});

function buildDay(date: string, low: number, high: number, volume: number = 100_000, deliveryPercentage: number = 55) {
  return {
    date,
    open: low + 1,
    high,
    low,
    close: high - 1,
    volume,
    deliveryPercentage,
  };
}
