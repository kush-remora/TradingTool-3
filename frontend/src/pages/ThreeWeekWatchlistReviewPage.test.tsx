import { fireEvent, render, screen, within } from "@testing-library/react";
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
            weekly_roc: { lookback_weeks: 3, current_roc_pct: -2.0, previous_roc_pct: -8.0, change_pct_points: 6.0, state: "RISING_FROM_NEGATIVE" },
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
            buildDay("2026-06-29", 104, 114, 150_000),
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
    expect(screen.getByRole("columnheader", { name: "52W high" })).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "Move from 30D low" })).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "10D volume anomaly" })).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "Weekly momentum" })).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "Weekly ROC" })).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "Structure" })).toBeInTheDocument();
    expect(screen.getAllByRole("img", { name: "Uptrend: higher high and higher low" })).toHaveLength(4);
    expect(screen.getByText("Low-day D/V higher")).toBeInTheDocument();
    expect(screen.getAllByText("Above 200 DMA").length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText("W1 +6.10%")).toBeInTheDocument();
    expect(screen.getByText("W4 +4.80%")).toBeInTheDocument();
    expect(screen.getByText(/High-volume days: 1 · lookback: 90 days/)).toBeInTheDocument();
    expect(screen.getByText("Dates: 29 Jun")).toBeInTheDocument();
    expect(screen.getByText("Near high")).toBeInTheDocument();
    expect(screen.getByText("≥10% move")).toBeInTheDocument();
    expect(screen.getByText("+15.00% from low")).toBeInTheDocument();
    expect(screen.getByText("1 day · max 2.4×")).toBeInTheDocument();
    expect(screen.getByText("≥5% weeks 2/3 · Up weeks 3/3")).toBeInTheDocument();
    expect(screen.getAllByText("Rising from negative").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Δ ROC +6.00 pp · 3W").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("LTP ₹115.00")).toBeInTheDocument();
    expect(screen.getByText("Delivery 55.00%")).toBeInTheDocument();
    expect(screen.getByText("Vol 1.50 L")).toBeInTheDocument();
    expect(screen.getByText("Vol vs prev +50.00%")).toBeInTheDocument();
    const kiteLink = screen.getAllByRole("link", { name: "Open INFY in Kite" })[0];
    expect(kiteLink).toHaveAttribute("href", "https://kite.zerodha.com/chart/web/tvc/NSE/INFY/408065");
    expect(kiteLink).toHaveAttribute("target", "_blank");
    expect(screen.getByTestId("momentum-participation-table")).toBeInTheDocument();
    expect(screen.getByText("Current LTP")).toBeInTheDocument();
    expect(screen.getByText("+3.60%")).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "Delivery" })).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole("button", { name: "Open review" })[0]);
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

  it("sorts the evidence summary with three rising weeks before two rising weeks", async () => {
    getJsonMock.mockImplementation((path: string) => path === "/api/strategy/weekly-price-review/watchlists"
      ? Promise.resolve({ options: [{ label: "watchlist", value: "watchlist", count: 2 }] })
      : Promise.resolve({
        watchlistKey: "watchlist",
        rows: [
          buildSummaryRow("INFY", [5, 6, 7], 112, 120, 4),
          buildSummaryRow("BHEL", [6, 7, 8], 118, 120, 5),
          buildSummaryRow("RELIANCE", [6, 7, 8], 118, 120, 4),
          buildSummaryRow("TCS", [5, -1, 4]),
        ],
      }));

    render(<ThreeWeekWatchlistReviewPage onOpenStockReview={vi.fn()} />);
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("watchlist (2)"));

    const summaryTable = await screen.findByTestId("watchlist-evidence-summary-table");
    const rows = within(summaryTable).getAllByRole("row");
    expect(rows[1]).toHaveTextContent("BHEL");
    expect(rows[1]).toHaveTextContent("≥5% weeks 3/3");
    expect(rows[1]).toHaveTextContent("max 5.0×");
    expect(rows[2]).toHaveTextContent("RELIANCE");
    expect(rows[2]).toHaveTextContent("≥5% weeks 3/3");
    expect(rows[2]).toHaveTextContent("max 4.0×");
    expect(rows[3]).toHaveTextContent("INFY");
    expect(rows[4]).toHaveTextContent("TCS");
    expect(rows[4]).toHaveTextContent("≥5% weeks 1/3");
  }, 10000);
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

function buildSummaryRow(symbol: string, weeklyReturns: number[], currentClose: number = 112, fiftyTwoWeekHigh: number = 120, volumeRatio: number | null = null) {
  return {
    symbol,
    companyName: symbol,
    instrumentToken: 408065,
    days: [
      buildDay("2026-06-01", 100, 110),
      buildDay("2026-06-08", 100, 110),
      buildDay("2026-06-15", 102, 112),
      buildDay("2026-06-22", 103, 113),
      buildDay("2026-06-29", 104, 114),
    ],
    momentum_evidence: {
      as_of_date: "2026-07-03",
      current_close: currentClose,
      sma200: 100,
      above_sma200: true,
      distance_from_sma200_pct: 12,
      fifty_two_week_high: fiftyTwoWeekHigh,
      distance_from_fifty_two_week_high_pct: -6.67,
      weekly_returns: weeklyReturns.map((returnPct, index) => ({
        week_start: `2026-06-${String(8 + index * 7).padStart(2, "0")}`,
        week_end: `2026-06-${String(12 + index * 7).padStart(2, "0")}`,
        return_pct: returnPct,
      })),
      weekly_roc: { lookback_weeks: 3, current_roc_pct: 4, previous_roc_pct: 1, change_pct_points: 3, state: "RISING_POSITIVE" },
      participation_events: volumeRatio == null ? [] : [{
        event_date: "2026-06-29",
        close: currentClose,
        volume: 500_000,
        volume_ratio: volumeRatio,
        daily_return_pct: 2,
        price_since_event_pct: 2,
        delivery_percentage: 55,
      }],
      participation_threshold: 2,
      participation_lookback_days: 90,
      data_status: "AVAILABLE",
    },
  };
}
