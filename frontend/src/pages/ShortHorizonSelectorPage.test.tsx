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
          readingOrder: ["Move now (Now 5D / Prior 5D / Earlier 10D)", "Strong finishes"],
          columns: [{
            column: "Move now (Now 5D / Prior 5D / Earlier 10D)",
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
    expect(within(screen.getByTestId("short-horizon-selector-table")).getByRole("columnheader", { name: "No." })).toBeInTheDocument();
    expect(screen.getByText("5D reach 20 / 20")).toBeInTheDocument();
    expect(screen.getByText("Recent tested 6D 6 / 6")).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-selector-table")).getByRole("columnheader", { name: "Strong finishes" })).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-selector-table")).getByText("1 / 5")).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-selector-table")).getByLabelText("Strong finish sequence, newest day first")).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-selector-table")).queryByText("T-1", { exact: true })).not.toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-selector-table")).getByRole("columnheader", { name: "Move now" })).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-selector-table")).getByRole("columnheader", { name: "Move quality" })).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-selector-table")).getByRole("columnheader", { name: "Exit pressure" })).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-selector-table")).getAllByLabelText("filter").length).toBeGreaterThanOrEqual(6);
    expect(within(screen.getByTestId("short-horizon-selector-table")).getByText("20D +4.0%", { exact: true })).toBeInTheDocument();
    const moveTable = screen.getByTestId("short-horizon-selector-table");
    const headerLabels = within(moveTable).getAllByRole("columnheader").map((header) => header.getAttribute("aria-label") ?? header.textContent);
    expect(headerLabels.indexOf("Latest finish")).toBe(headerLabels.indexOf("Strong finishes") + 1);
    expect(within(moveTable).getAllByText("+0.0%", { exact: true })).toHaveLength(2);
    expect(Array.from(moveTable.querySelectorAll(".short-horizon-move-period")).map((label) => label.textContent)).toEqual(["Now 5D", "Prior 5D", "Earlier 10D"]);
    const moveCell = within(screen.getByTestId("short-horizon-selector-table")).getByLabelText(/Now 5D \+4\.0%/);
    const moveText = moveCell.textContent ?? "";
    expect(moveText.indexOf("Now 5D")).toBeLessThan(moveText.indexOf("Prior 5D"));
    expect(moveText.indexOf("Prior 5D")).toBeLessThan(moveText.indexOf("Earlier 10D"));
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
    expect(within(screen.getByTestId("short-horizon-shortlist-table")).getByRole("columnheader", { name: "Exit pressure" })).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-shortlist-table")).getByRole("columnheader", { name: "52W high" })).toBeInTheDocument();
    expect(screen.getByTestId("short-horizon-tab-two-filters")).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-tab-two-filters")).getByText("Accelerating")).toBeInTheDocument();
    expect(screen.getByText("Sorting rules")).toBeInTheDocument();
    expect(screen.getByText("Filtering rules")).toBeInTheDocument();
    expect(screen.getByText(/Rank by 5D reach across the last 20 usable starting days/)).toBeInTheDocument();
    expect(screen.getByText(/Reject structural weakness only when the last 3 closes fall in a row/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("tab", { name: "Best aligned · 0" }));
    expect(await screen.findByTestId("short-horizon-best-aligned-table")).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-best-aligned-table")).getByRole("columnheader", { name: "52W high" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("tab", { name: "Core · 1" }));
    expect(await screen.findByTestId("short-horizon-core-table")).toBeInTheDocument();
    expect(screen.getByText(/A stock must be in the top 1 by both 5D reach count and Recent tested 6D reach count/)).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-core-table")).getByRole("columnheader", { name: "5D move" })).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-core-table")).getByRole("columnheader", { name: "20D move" })).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-core-table")).getByRole("columnheader", { name: "Strong finishes" })).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-core-table")).getByRole("columnheader", { name: "Move quality" })).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-core-table")).getByRole("columnheader", { name: "Exit pressure" })).toBeInTheDocument();
    expect(within(screen.getByTestId("short-horizon-core-table")).getByRole("columnheader", { name: "52W high" })).toBeInTheDocument();

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
  }, 10000);
});

function buildDays() {
  return Array.from({ length: 25 }, (_, index) => ({
    date: `2026-07-${String(index + 1).padStart(2, "0")}`,
    open: 100,
    high: 110,
    low: 90,
    close: index === 24 ? 104 : 100,
    volume: 100,
    deliveryPercentage: null,
  }));
}
