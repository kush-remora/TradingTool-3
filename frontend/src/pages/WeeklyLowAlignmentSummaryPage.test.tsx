import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { WeeklyLowAlignmentSummaryPage } from "./WeeklyLowAlignmentSummaryPage";

const getJsonMock = vi.fn();

vi.mock("../utils/api", () => ({
  getJson: (...args: unknown[]) => getJsonMock(...args),
}));

describe("WeeklyLowAlignmentSummaryPage", () => {
  it("shows only aligned floor candidates and links them to the stock review", async () => {
    getJsonMock.mockImplementation((path: string) => path === "/api/strategy/weekly-price-review/watchlists"
      ? Promise.resolve({ options: [{ label: "watchlist", value: "watchlist", count: 2 }] })
      : Promise.resolve({
        watchlistKey: "watchlist",
        rows: [
          {
            symbol: "INFY",
            companyName: "Infosys",
            instrumentToken: 1,
            momentum_evidence: null,
            days: [
              buildDay("2026-05-25", 698, 730),
              buildDay("2026-06-01", 700, 735),
              buildDay("2026-06-08", 704, 738),
              buildDay("2026-06-09", 707, 740),
            ],
          },
          {
            symbol: "TCS",
            companyName: "TCS",
            instrumentToken: 2,
            momentum_evidence: null,
            days: [
              buildDay("2026-05-25", 700, 730),
              buildDay("2026-06-01", 710, 740),
              buildDay("2026-06-08", 720, 750),
            ],
          },
        ],
      }));

    const onOpenStockReview = vi.fn();
    render(<WeeklyLowAlignmentSummaryPage onOpenStockReview={onOpenStockReview} />);

    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("watchlist (2)"));

    expect(await screen.findByText("Weekly Low Alignment Summary")).toBeInTheDocument();
    const table = screen.getByTestId("weekly-low-alignment-summary-table");
    expect(within(table).getByText("INFY")).toBeInTheDocument();
    expect(within(table).queryByText("TCS")).not.toBeInTheDocument();
    expect(table).toHaveTextContent("₹704.00");
    expect(table).toHaveTextContent("₹700.00");
    expect(table).toHaveTextContent("₹698.00");
    expect(table).toHaveTextContent("+0.57%");
    expect(table).toHaveTextContent("+0.29%");
    expect(table).toHaveTextContent("Low on Mon, 08 Jun");
    expect(table).toHaveTextContent("Low on Mon, 01 Jun");
    expect(table).toHaveTextContent("Low on Mon, 25 May");
    expect(screen.getByRole("columnheader", { name: "Current vs last week" })).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "Last week vs last-to-last week" })).toBeInTheDocument();

    const reviewLink = within(table).getByRole("link", { name: "Open INFY in Three-Week Stock Review" });
    expect(reviewLink).toHaveAttribute("href", "/TradingTool-3/console/three-week-stock-review?symbol=INFY");
    fireEvent.click(reviewLink);
    expect(onOpenStockReview).toHaveBeenCalledWith("INFY");
  }, 10000);
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
