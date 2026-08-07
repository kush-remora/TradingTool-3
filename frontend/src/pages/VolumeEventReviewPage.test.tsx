import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { VolumeEventReviewPage } from "./VolumeEventReviewPage";

const getJsonMock = vi.fn();

vi.mock("../utils/api", () => ({
  getJson: (...args: unknown[]) => getJsonMock(...args),
}));

vi.mock("../hooks/useStockQuotes", () => ({
  useStockQuotes: () => ({ quotesBySymbol: { INFY: { symbol: "INFY", ltp: 125 } }, loading: false, error: null }),
}));

describe("VolumeEventReviewPage", () => {
  it("shows the top three volume events by multiplier and current move", async () => {
    getJsonMock.mockImplementation((path: string) => path === "/api/strategy/weekly-price-review/watchlists"
      ? Promise.resolve({ options: [{ label: "watchlist", value: "watchlist", count: 1 }] })
      : Promise.resolve({
        watchlistKey: "watchlist",
        rows: [{
          symbol: "INFY",
          companyName: "Infosys",
          instrumentToken: 408065,
          days: [{ date: "2026-08-07", open: 120, high: 126, low: 118, close: 124, volume: 100_000, deliveryPercentage: 55 }],
          momentum_evidence: {
            as_of_date: "2026-08-07",
            current_close: 124,
            sma200: null,
            above_sma200: null,
            distance_from_sma200_pct: null,
            fifty_two_week_high: null,
            distance_from_fifty_two_week_high_pct: null,
            weekly_returns: [],
            participation_events: [
              buildEvent("2026-06-01", 10, 100),
              buildEvent("2026-07-01", 5, 105),
              buildEvent("2026-07-15", 3, 115),
              buildEvent("2026-08-06", 2, 120),
            ],
            participation_threshold: 2,
            participation_lookback_days: 90,
            data_status: "AVAILABLE",
          },
        }],
      }));

    render(<VolumeEventReviewPage onOpenStockReview={vi.fn()} />);
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("watchlist (1)"));

    expect(await screen.findByTestId("volume-event-review-table")).toBeInTheDocument();
    expect(screen.getByText("Lookback: 60 calendar days · minimum event: 2.0× prior 10-trading-day average · top 3 by multiplier.")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
    expect(screen.getByText("5.00× · 01 Jul · +19.05%")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open INFY in Kite" })).toHaveAttribute("target", "_blank");

    fireEvent.click(screen.getByRole("button", { name: "Expand row" }));
    expect(screen.getByRole("columnheader", { name: "Volume / prior 10D avg" })).toBeInTheDocument();
    expect(screen.getByText("5.00×")).toBeInTheDocument();
    expect(screen.getByText("3.00×")).toBeInTheDocument();
    expect(screen.getByText("2.00×")).toBeInTheDocument();
  }, 10000);

  it("sorts stocks by the strongest event date", async () => {
    getJsonMock.mockImplementation((path: string) => path === "/api/strategy/weekly-price-review/watchlists"
      ? Promise.resolve({ options: [{ label: "watchlist", value: "watchlist", count: 2 }] })
      : Promise.resolve({
        watchlistKey: "watchlist",
        rows: [
          buildStockRow("INFY", "2026-07-01"),
          buildStockRow("TCS", "2026-06-15"),
        ],
      }));

    render(<VolumeEventReviewPage onOpenStockReview={vi.fn()} />);
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("watchlist (2)"));

    const table = await screen.findByTestId("volume-event-review-table");
    const strongestEventHeader = screen.getByRole("columnheader", { name: "Strongest event" });
    expect(table).toHaveTextContent("INFY");
    expect(table).toHaveTextContent("TCS");
    expect(table.textContent?.indexOf("INFY")).toBeLessThan(table.textContent?.indexOf("TCS") ?? -1);

    fireEvent.click(strongestEventHeader);

    expect(table.textContent?.indexOf("TCS")).toBeLessThan(table.textContent?.indexOf("INFY") ?? -1);
  }, 10000);
});

function buildStockRow(symbol: string, eventDate: string) {
  return {
    symbol,
    companyName: symbol,
    instrumentToken: 1,
    days: [{ date: "2026-08-07", open: 120, high: 126, low: 118, close: 124, volume: 100_000, deliveryPercentage: 55 }],
    momentum_evidence: {
      as_of_date: "2026-08-07",
      current_close: 124,
      sma200: null,
      above_sma200: null,
      distance_from_sma200_pct: null,
      fifty_two_week_high: null,
      distance_from_fifty_two_week_high_pct: null,
      weekly_returns: [],
      participation_events: [buildEvent(eventDate, 2, 120)],
      participation_threshold: 2,
      participation_lookback_days: 90,
      data_status: "AVAILABLE",
    },
  };
}

function buildEvent(eventDate: string, volumeRatio: number, close: number) {
  return {
    event_date: eventDate,
    close,
    volume: 500_000,
    volume_ratio: volumeRatio,
    daily_return_pct: 2,
    price_since_event_pct: 2,
    delivery_percentage: 55,
  };
}
