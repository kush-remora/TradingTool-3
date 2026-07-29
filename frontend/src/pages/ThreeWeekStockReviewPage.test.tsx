import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ThreeWeekStockReviewPage } from "./ThreeWeekStockReviewPage";
import { buildWeeklyPriceSummaries, buildWeeklyPriceTimelines } from "../utils/threeWeekStockReview";

const useStockDetailMock = vi.fn();

vi.mock("../hooks/useInstrumentSearch", () => ({
  useInstrumentSearch: () => ({
    allInstruments: [{
      instrument_token: 1,
      trading_symbol: "STLTECH-BE",
      company_name: "Sterlite Technologies",
      exchange: "NSE",
      instrument_type: "EQ",
    }],
    loading: false,
    error: null,
  }),
}));

vi.mock("../hooks/useStockDetail", () => ({
  useStockDetail: (...args: unknown[]) => useStockDetailMock(...args),
}));

const useInstrumentNotesMock = vi.fn();
vi.mock("../hooks/useInstrumentNotes", () => ({
  useInstrumentNotes: (...args: unknown[]) => useInstrumentNotesMock(...args),
}));

vi.mock("../components/InstrumentSearch", () => ({
  InstrumentSearch: ({ onSelect }: { onSelect: (instrument: { trading_symbol: string } | null) => void }) => (
    <button onClick={() => onSelect({ trading_symbol: "INFY" })}>Select INFY</button>
  ),
}));

vi.mock("../components/LiveMarketWidget", () => ({
  LiveMarketWidget: ({ symbol, mode }: { symbol: string; mode: string }) => <div>Live market: {symbol} ({mode})</div>,
}));

vi.mock("../components/FloatingInstrumentNotes", () => ({
  FloatingInstrumentNotes: () => <div>Notes editor</div>,
}));

describe("ThreeWeekStockReviewPage", () => {
  beforeEach(() => {
    useInstrumentNotesMock.mockReturnValue({ notes: [], loading: false, error: null, addNote: vi.fn(), removeNote: vi.fn() });
  });

  afterEach(() => {
    window.history.replaceState({}, "", "/");
  });

  it("summarises the latest three weeks with the dates of their high and low", () => {
    const summaries = buildWeeklyPriceSummaries([
      day("2026-07-06", 100, 110),
      day("2026-07-10", 95, 114),
      day("2026-07-13", 98, 111),
      day("2026-07-17", 96, 116),
      day("2026-07-20", 101, 117),
      day("2026-07-24", 99, 118),
      day("2026-07-27", 102, 120),
    ]);

    expect(summaries).toEqual([
      expect.objectContaining({ weekLabel: "Week of 2026-07-13", low: 96, lowDate: "2026-07-17", high: 116, highDate: "2026-07-17" }),
      expect.objectContaining({ weekLabel: "Week of 2026-07-20", low: 99, lowDate: "2026-07-24", high: 118, highDate: "2026-07-24" }),
      expect.objectContaining({ weekLabel: "Week of 2026-07-27", low: 102, lowDate: "2026-07-27", high: 120, highDate: "2026-07-27" }),
    ]);
    expect(summaries[1].rangePct).toBeCloseTo(19.19, 2);
  });

  it("loads enough history for three completed weeks plus the current week", () => {
    useStockDetailMock.mockReturnValue({
      data: { symbol: "INFY", exchange: "NSE", avg_volume_20d: null, pivot_levels: null, days: [] },
      loading: false,
      error: null,
    });
    render(<ThreeWeekStockReviewPage />);

    fireEvent.click(screen.getByRole("button", { name: "Select INFY" }));

    expect(useStockDetailMock).toHaveBeenLastCalledWith("INFY", 30);

    fireEvent.click(screen.getByRole("button", { name: "Show 3 months" }));

    expect(useStockDetailMock).toHaveBeenLastCalledWith("INFY", 70);
  });

  it("includes the current week after the preceding three weeks", () => {
    const summaries = buildWeeklyPriceSummaries([
      day("2026-07-06", 100, 110),
      day("2026-07-13", 101, 111),
      day("2026-07-20", 102, 112),
      day("2026-07-27", 103, 113),
    ], 4);

    expect(summaries.map((summary) => summary.weekLabel)).toEqual([
      "Week of 2026-07-06",
      "Week of 2026-07-13",
      "Week of 2026-07-20",
      "Week of 2026-07-27",
    ]);
  });

  it("shows the selected stock's live market widget above the review", () => {
    useStockDetailMock.mockReturnValue({
      data: null,
      loading: false,
      error: null,
    });
    render(<ThreeWeekStockReviewPage />);

    fireEvent.click(screen.getByRole("button", { name: "Select INFY" }));

    expect(screen.getByText("Live market: NSE:INFY (wide)")).toBeInTheDocument();
  });

  it("shows the latest delivery sessions beside the live market", () => {
    useStockDetailMock.mockReturnValue({
      data: {
        symbol: "INFY",
        exchange: "NSE",
        avg_volume_20d: null,
        pivot_levels: null,
        days: [],
        delivery_days: [{
          date: "2026-07-27",
          delivery_percentage: 56.25,
          delivered_quantity: 1_250_000,
          traded_quantity: 2_222_222,
        }],
      },
      loading: false,
      error: null,
    });
    render(<ThreeWeekStockReviewPage />);

    fireEvent.click(screen.getByRole("button", { name: "Select INFY" }));

    expect(screen.getByTestId("delivery-history-table")).toBeInTheDocument();
    expect(screen.getByText("56.25%")).toBeInTheDocument();
    expect(screen.getByText("12.50 L / 22.22 L")).toBeInTheDocument();
  });

  it("shows intraday low and high moves from the open", () => {
    useStockDetailMock.mockReturnValue({
      data: {
        symbol: "INFY",
        exchange: "NSE",
        avg_volume_20d: null,
        pivot_levels: null,
        delivery_days: [],
        days: [dayWithPrices("2026-07-27", 100, 105, 95, 110)],
      },
      loading: false,
      error: null,
    });
    render(<ThreeWeekStockReviewPage />);

    fireEvent.click(screen.getByRole("button", { name: "Select INFY" }));

    expect(screen.getByRole("columnheader", { name: "Low %" })).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "High %" })).toBeInTheDocument();
    expect(screen.getByText("-5.00%")).toHaveStyle({ color: "#cf1322" });
    expect(screen.getByText("+10.00%")).toHaveStyle({ color: "#389e0d" });
  });

  it("shows existing stock notes with a number, text, and created date beside fundamentals", () => {
    useInstrumentNotesMock.mockReturnValue({
      notes: [{
        id: 12,
        instrumentToken: 1,
        notes: "Watch for a spring below support",
        createdAt: "2026-07-28T18:30:00Z",
        updatedAt: "2026-07-28T18:30:00Z",
      }],
      loading: false,
      error: null,
      addNote: vi.fn(),
      removeNote: vi.fn(),
    });
    useStockDetailMock.mockReturnValue({ data: null, loading: false, error: null });
    render(<ThreeWeekStockReviewPage />);

    fireEvent.click(screen.getByRole("button", { name: "Select INFY" }));

    expect(screen.getByTestId("existing-stock-notes")).toHaveTextContent(/1\.\s*Watch for a spring below support\s*29 Jul 2026/);
  });

  it("selects the NSE equity variant when the URL uses its base symbol", async () => {
    window.history.replaceState({}, "", "/TradingTool-3/console/three-week-stock-review?symbol=STLTECH");
    useStockDetailMock.mockReturnValue({ data: null, loading: false, error: null });

    render(<ThreeWeekStockReviewPage />);

    await waitFor(() => expect(useStockDetailMock).toHaveBeenLastCalledWith("STLTECH-BE", 30));
    expect(screen.getByText("Live market: NSE:STLTECH-BE (wide)")).toBeInTheDocument();
  });

  it("keeps the calculator fixed to the lower-right viewport", () => {
    render(<ThreeWeekStockReviewPage />);

    expect(screen.getByTestId("floating-change-calculator")).toHaveStyle({
      position: "fixed",
      right: "20px",
      bottom: "20px",
    });
    expect(screen.queryByText("BUY / SELL CALCULATOR")).not.toBeInTheDocument();
  });

  it("calculates the third buy, sell, or percentage value from the other two", () => {
    render(<ThreeWeekStockReviewPage />);

    fireEvent.change(screen.getByRole("spinbutton", { name: "Buy price" }), { target: { value: "100" } });
    fireEvent.change(screen.getByRole("spinbutton", { name: "Sell price" }), { target: { value: "110" } });

    expect(screen.getByRole("spinbutton", { name: "Percentage change" })).toHaveValue("10.00");

    fireEvent.change(screen.getByRole("spinbutton", { name: "Percentage change" }), { target: { value: "20" } });

    expect(screen.getByRole("spinbutton", { name: "Sell price" })).toHaveValue("120.00");

    fireEvent.change(screen.getByRole("spinbutton", { name: "Buy price" }), { target: { value: "" } });
    fireEvent.change(screen.getByRole("spinbutton", { name: "Percentage change" }), { target: { value: "10" } });

    expect(screen.getByRole("spinbutton", { name: "Buy price" })).toHaveValue("109.09");
  });

  it("calculates daily and accumulated weekly return from each week's first trading open", () => {
    const timelines = buildWeeklyPriceTimelines([
      dayWithPrices("2026-07-20", 100, 102),
      dayWithPrices("2026-07-21", 101, 103),
      dayWithPrices("2026-07-22", 104, 101),
      dayWithPrices("2026-07-27", 200, 202),
    ], 2);

    expect(timelines[0].days).toEqual([
      expect.objectContaining({ date: "2026-07-20", dailyMovePct: 2, accumulatedWeeklyPct: 2, isWeekLow: true }),
      expect.objectContaining({ date: "2026-07-21", dailyMovePct: expect.closeTo(1.98, 2), accumulatedWeeklyPct: 3 }),
      expect.objectContaining({ date: "2026-07-22", dailyMovePct: expect.closeTo(-2.88, 2), accumulatedWeeklyPct: 1, isWeekHigh: true }),
    ]);
  });

  it("uses the first available trading session as the base when Monday is a holiday", () => {
    const [timeline] = buildWeeklyPriceTimelines([
      dayWithPrices("2026-07-21", 100, 105),
      dayWithPrices("2026-07-22", 104, 106),
    ]);

    expect(timeline.baseOpen).toBe(100);
    expect(timeline.days[0].accumulatedWeeklyPct).toBe(5);
    expect(timeline.days[1].accumulatedWeeklyPct).toBe(6);
  });

});

function day(date: string, low: number, high: number) {
  return { date, open: low + 2, close: high - 2, low, high, volume: 100, daily_change_pct: null, rsi14: null, vol_ratio: null };
}

function dayWithPrices(date: string, open: number, close: number, low: number = Math.min(open, close), high: number = Math.max(open, close)) {
  return { date, open, close, low, high, volume: 100, daily_change_pct: null, rsi14: null, vol_ratio: null };
}
