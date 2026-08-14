import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ShortHorizonSelectorPage } from "./ShortHorizonSelectorPage";

const getJsonMock = vi.fn();

vi.mock("../utils/api", () => ({
  getJson: (...args: unknown[]) => getJsonMock(...args),
}));

describe("ShortHorizonSelectorPage", () => {
  it("shows compact reach evidence and opens recent daily details", async () => {
    getJsonMock.mockImplementation((path: string) => {
      if (path === "/api/strategy/weekly-price-review/watchlists") {
        return Promise.resolve({ options: [{ label: "watchlist", value: "watchlist", count: 1 }] });
      }

      if (path === "/api/strategy/short-horizon-selector/tab-one-guide") {
        return Promise.resolve({
          title: "How to read Tab 1 · All Stocks",
          description: "Read current behaviour first.",
          readingOrder: ["Move now (Now 5D / Prior 5D / pace)", "Strong finishes"],
          columns: [{
            column: "Move now (Now 5D / Prior 5D / pace)",
            whatItShows: "Current movement.",
            whyImportant: "Shows activity.",
            howToRead: "Positive is rising.",
            caution: "Not enough alone.",
          }],
          bestCombination: "Positive movement and strong finishes.",
          importantNote: "Context only.",
        });
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
    expect(within(screen.getByTestId("short-horizon-selector-table")).getByText("First seen 21 Jul")).toBeInTheDocument();
    const firstSeenPerformance = screen.getByTestId("short-horizon-selector-table").querySelector(".short-horizon-first-seen-performance");
    expect(firstSeenPerformance).toHaveTextContent("Close +4.0% · High +10.0%");
    expect(firstSeenPerformance?.querySelector(".short-horizon-first-seen-return-positive")).not.toHaveClass("strong");
    expect(firstSeenPerformance?.querySelector(".short-horizon-first-seen-return-positive.strong")).toHaveTextContent("+10.0%");
    expect(within(screen.getByTestId("short-horizon-selector-table")).getByRole("columnheader", { name: "No." })).toBeInTheDocument();
    expect(screen.getByText("5D reach 20 / 20")).toBeInTheDocument();
    expect(screen.getByText("Recent tested 6D 6 / 6")).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-selector-table")).getByRole("columnheader", { name: "Strong finishes" })).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-selector-table")).getByText("1 / 5")).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-selector-table")).getByLabelText("Strong finish sequence, newest day first")).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-selector-table")).queryByText("T-1", { exact: true })).not.toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-selector-table")).getByRole("columnheader", { name: "Move now" })).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-selector-table")).getByRole("columnheader", { name: "Move quality" })).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-selector-table")).getByRole("columnheader", { name: "Volume activity" })).toBeInTheDocument();
    expect(screen.getByText("No abnormal volume in latest 5 sessions")).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-selector-table")).getAllByLabelText("filter").length).toBeGreaterThanOrEqual(4);
    expect(within(screen.getByTestId("short-horizon-selector-table")).getByText("Day +4.0%", { exact: true })).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-selector-table")).getByText("20D +4.0%", { exact: true })).toBeInTheDocument();
    const moveTable = screen.getByTestId("short-horizon-selector-table");
    const headerLabels = within(moveTable).getAllByRole("columnheader").map((header) => header.getAttribute("aria-label") ?? header.textContent);
    expect(headerLabels.indexOf("Latest finish")).toBe(headerLabels.indexOf("Strong finishes") + 1);
    const moveCell = within(screen.getByTestId("short-horizon-selector-table")).getByLabelText(/Now 5D Up \+4\.0%/);
    expect(moveCell).toHaveTextContent("3–5%");
    expect(moveCell).toHaveTextContent("Prior 5D");
    expect(moveCell).toHaveTextContent("+3.1%");
    expect(moveCell).toHaveTextContent("Steady");
    expect(moveCell).not.toHaveTextContent("Review");
    expect(moveCell).toHaveAttribute("aria-label", expect.stringContaining("5D path: 3/5 green"));
    expect(moveCell).toHaveAttribute("aria-label", expect.stringContaining("avg day +0.8%"));
    const latestCloseContext = within(screen.getByTestId("short-horizon-selector-table")).getByLabelText(/Stage Review/);
    expect(latestCloseContext).toHaveTextContent("20D +4.0%");
    expect(latestCloseContext).toHaveTextContent("Review");
    expect(screen.getByText(/5D reach: for each tested starting close, price touched \+5% within the next 5 trading sessions/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "How to read Tab 1" }));
    const guideDialog = await screen.findByRole("dialog");
    expect(within(guideDialog).getByText("Read current behaviour first.")).toBeInTheDocument();
    expect(within(guideDialog).getByRole("columnheader", { name: "What it shows" })).toBeInTheDocument();
    fireEvent.click(within(guideDialog).getByRole("button", { name: "Close" }));

    fireEvent.click(screen.getByRole("tab", { name: "Shortlist · 1" }));
    expect(await screen.findByTestId("short-horizon-shortlist-table")).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-shortlist-table")).getByRole("columnheader", { name: "Move now" })).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-shortlist-table")).getByRole("columnheader", { name: "Strong finishes" })).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-shortlist-table")).getByRole("columnheader", { name: "Move quality" })).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-shortlist-table")).getByRole("columnheader", { name: "Volume activity" })).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-shortlist-table")).getByRole("columnheader", { name: "52W high" })).toBeInTheDocument();
    expect(screen.getByTestId("short-horizon-tab-two-filters")).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-tab-two-filters")).getByText("Any pace")).toBeInTheDocument();
    expect(screen.getByText("Sorting rules")).toBeInTheDocument();
    expect(screen.getByText("Filtering rules")).toBeInTheDocument();
    expect(screen.getByText(/Rank by 5D reach across the last 20 usable starting days/)).toBeInTheDocument();
    expect(screen.getByText(/Reject structural weakness only when the last 3 closes fall in a row/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("tab", { name: "Best aligned · 0" }));
    expect(await screen.findByTestId("short-horizon-best-aligned-table")).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-best-aligned-table")).getByRole("columnheader", { name: "52W high" })).toBeInTheDocument();
    expect(screen.getByText(/Supply response volume is excluded; Quiet and Watch remain eligible/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("tab", { name: "Latest 2-day finish · 0" }));
    expect(await screen.findByTestId("short-horizon-latest-two-finish-table")).toBeInTheDocument();
    expect(screen.getByText(/Recent tested 6D must be at least 1 \/ 6/)).toBeInTheDocument();
    expect(screen.getByText(/at least one of the latest two completed candles must close at least 75%/)).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-latest-two-finish-table")).getByRole("columnheader", { name: "Move quality" })).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-latest-two-finish-table")).getByRole("columnheader", { name: "Volume activity" })).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-latest-two-finish-table")).getAllByLabelText("filter").length).toBeGreaterThanOrEqual(2);

    fireEvent.click(screen.getByRole("tab", { name: "Fresh today · 0" }));
    expect(await screen.findByTestId("short-horizon-fresh-today-table")).toBeInTheDocument();
    expect(screen.getByText(/Only stocks that entered Latest 2-day finish in the current completed session/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("tab", { name: "Accumulation · 1" }));
    const accumulationTable = await screen.findByTestId("short-horizon-accumulation-table");
    expect(within(accumulationTable).getByRole("columnheader", { name: "Buy-interest days" })).toBeInTheDocument();
    expect(within(accumulationTable).getByRole("columnheader", { name: "5D move" })).toBeInTheDocument();
    expect(within(accumulationTable).getByRole("columnheader", { name: "20D move" })).toBeInTheDocument();
    expect(within(accumulationTable).getByRole("columnheader", { name: "Volume below 10D" })).toBeInTheDocument();
    expect(within(accumulationTable).getByRole("columnheader", { name: "20D heatmap" })).toBeInTheDocument();
    expect(within(accumulationTable).getByLabelText("Latest 20-session accumulation heatmap")).toBeInTheDocument();
    expect(within(accumulationTable).getAllByLabelText(/Mon, 06 Jul/)).toHaveLength(4);
    const inactiveBuyDot = accumulationTable.querySelector(".accumulation-heatmap-dot-buyingInterest.accumulation-heatmap-dot-inactive");
    expect(inactiveBuyDot).not.toBeNull();
    expect(inactiveBuyDot).toHaveAttribute("title", "Mon, 06 Jul 50.0%");
    expect(screen.getByText(/30-session context: Buy = close at least 70% up the daily range/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("tab", { name: "Filter 1 · 0" }));
    expect(await screen.findByTestId("short-horizon-accumulation-filter-one-table")).toBeInTheDocument();
    expect(screen.getByText(/Filter 1 requires all three conditions: Buy-interest ≥10 \/ 30/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("tab", { name: "Filter 2 · 0" }));
    expect(await screen.findByTestId("short-horizon-accumulation-filter-two-table")).toBeInTheDocument();
    expect(screen.getByText(/Filter 2 starts with Filter 1 and requires at least 3 \/ 5 buying-interest days, 3 \/ 5 green closes, and a 5D move of at least \+5%/)).toBeInTheDocument();

    expect(screen.queryByRole("tab", { name: /Best aligned · Quiet/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: /Core/ })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("tab", { name: "All Stocks · 1" }));
    fireEvent.click(screen.getByRole("button", { name: "Details" }));
    const dialog = await screen.findByRole("dialog");
    expect(dialog).toBeInTheDocument();
    expect(document.querySelector(".ant-modal-mask")).not.toBeInTheDocument();
    expect(within(dialog).getByText("20")).toBeInTheDocument();
    expect(within(dialog).getByText("recent completed sessions")).toBeInTheDocument();
    expect(within(dialog).getByRole("columnheader", { name: "Open" })).toBeInTheDocument();
    expect(within(dialog).getByRole("columnheader", { name: "Volume vs 10D avg" })).toBeInTheDocument();
    expect(within(dialog).getByRole("columnheader", { name: "Delivery %" })).toBeInTheDocument();
    expect(within(dialog).getByRole("columnheader", { name: "Change %" })).toBeInTheDocument();
    expect(within(dialog).getByRole("columnheader", { name: "Close position" })).toBeInTheDocument();
    expect(within(dialog).getByRole("columnheader", { name: "From high" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Review" }));
    await waitFor(() => expect(onOpenCompactStockReview).toHaveBeenCalledWith("ABC"));
  }, 20000);
});

function buildDays() {
  return Array.from({ length: 25 }, (_, index) => ({
    date: `2026-07-${String(index + 1).padStart(2, "0")}`,
    open: 100,
    high: 110,
    low: 90,
    close: index === 14 ? 97 : index === 21 ? 101 : index === 22 ? 102 : index === 23 ? 100 : index === 24 ? 104 : 100,
    volume: 100,
    deliveryPercentage: null,
  }));
}
