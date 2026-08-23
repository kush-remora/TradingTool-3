import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AdaptiveBreakoutBacktestPage } from "./AdaptiveBreakoutBacktestPage";
import type { AdaptiveBreakoutBacktestResponse } from "../types";

const runMock = vi.fn();
const getJsonMock = vi.hoisted(() => vi.fn());
const backtestDataMock = vi.hoisted<{ current: AdaptiveBreakoutBacktestResponse | null }>(() => ({ current: null }));

vi.mock("../hooks/useAdaptiveBreakoutBacktest", () => ({
  useAdaptiveBreakoutBacktest: () => ({ data: backtestDataMock.current, loading: false, error: null, run: runMock }),
}));

vi.mock("../hooks/useInstrumentSearch", () => ({
  useInstrumentSearch: () => ({ allInstruments: [], loading: false, error: null }),
}));

vi.mock("../components/InstrumentSearch", () => ({
  InstrumentSearch: ({ onSelect }: { onSelect: (instrument: { trading_symbol: string; instrument_token: number }) => void }) => (
    <button onClick={() => onSelect({ trading_symbol: "INFY", instrument_token: 123 })}>Select instrument</button>
  ),
}));

vi.mock("../utils/api", () => ({ getJson: getJsonMock }));

describe("AdaptiveBreakoutBacktestPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    backtestDataMock.current = null;
  });

  it("runs the selected stock with the configured target and stop loss", () => {
    render(<AdaptiveBreakoutBacktestPage />);

    fireEvent.click(screen.getByRole("button", { name: "Select instrument" }));
    fireEvent.change(screen.getByRole("spinbutton", { name: "Target percentage" }), { target: { value: "8" } });
    fireEvent.change(screen.getByRole("spinbutton", { name: "Stop loss percentage" }), { target: { value: "3" } });
    fireEvent.click(screen.getByRole("button", { name: /Run INFY test/i }));

    expect(runMock).toHaveBeenCalledWith({
      symbol: "INFY",
      instrumentToken: 123,
      months: 6,
      targetPct: 8,
      stopLossPct: 3,
      entryTrigger: "FRESH_BREAKOUT",
      qualityFilter: "NONE",
    });
  });

  it("runs the selected watchlist with the configured target and stop loss", async () => {
    getJsonMock.mockResolvedValue({ options: [{ value: "leaders", label: "leaders", count: 2 }] });
    render(<AdaptiveBreakoutBacktestPage />);

    fireEvent.click(screen.getByRole("radio", { name: "Watchlist" }));
    fireEvent.mouseDown(await screen.findByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("leaders (2)"));
    fireEvent.change(screen.getByRole("spinbutton", { name: "Target percentage" }), { target: { value: "12" } });
    fireEvent.change(screen.getByRole("spinbutton", { name: "Stop loss percentage" }), { target: { value: "4" } });
    fireEvent.click(screen.getByRole("button", { name: /Run leaders test/i }));

    expect(runMock).toHaveBeenCalledWith({
      watchlistKey: "leaders",
      months: 6,
      targetPct: 12,
      stopLossPct: 4,
      entryTrigger: "FRESH_BREAKOUT",
      qualityFilter: "NONE",
    });
  });

  it("shows stock-wise results for a watchlist response", () => {
    const summary: AdaptiveBreakoutBacktestResponse["summary"] = {
      signalCount: 2,
      enteredTradeCount: 2,
      skippedEntryGapCount: 0,
      qualityFilteredCount: 0,
      targetHitCount: 1,
      stopLossCount: 1,
      endOfTestCount: 0,
      winRatePct: 50,
      averageHoldingSessions: 4,
      expectancyPct: 0,
      averageWinningReturnPct: 4.5,
      averageLosingReturnPct: -4.5,
      payoffRatio: 1,
      profitFactor: 1,
      forwardMetrics: [],
    };
    backtestDataMock.current = {
      symbol: null,
      watchlistKey: "leaders",
      entryTrigger: "FRESH_BREAKOUT",
      qualityFilter: "NONE",
      testedFromDate: "2024-01-01",
      testedToDate: "2025-12-31",
      targetPct: 5,
      stopLossPct: 5,
      transactionCostPct: 0.3,
      slippagePct: 0.2,
      entryRule: "Fresh breakout",
      ambiguousCandleRule: "Stop first on an ambiguous candle.",
      summary: { ...summary, signalCount: 3 },
      trades: [],
      symbols: [
        {
          symbol: "INFY",
          companyName: "Infosys Limited",
          testedFromDate: "2024-01-01",
          testedToDate: "2025-12-31",
          summary,
          trades: [],
        },
        {
          symbol: "TCS",
          companyName: "Tata Consultancy Services",
          testedFromDate: "2024-01-01",
          testedToDate: "2025-12-31",
          summary: { ...summary, enteredTradeCount: 0, skippedEntryGapCount: 1, targetHitCount: 0, stopLossCount: 0, winRatePct: null, expectancyPct: null },
          trades: [],
        },
      ],
    };

    render(<AdaptiveBreakoutBacktestPage />);

    expect(screen.getByText("Stock-wise results (2)")).toBeInTheDocument();
    expect(screen.getByText("INFY")).toBeInTheDocument();
    expect(screen.getByText("TCS")).toBeInTheDocument();
  }, 10000);
});
