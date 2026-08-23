import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { FourDayCloseStrengthBacktestPage } from "./FourDayCloseStrengthBacktestPage";
import type { FourDayCloseStrengthBacktestResponse } from "../types";

const runMock = vi.fn();
const getJsonMock = vi.hoisted(() => vi.fn());
const backtestDataMock = vi.hoisted<{ current: FourDayCloseStrengthBacktestResponse | null }>(() => ({ current: null }));

vi.mock("../hooks/useFourDayCloseStrengthBacktest", () => ({
  useFourDayCloseStrengthBacktest: () => ({ data: backtestDataMock.current, loading: false, error: null, run: runMock }),
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

describe("FourDayCloseStrengthBacktestPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    backtestDataMock.current = null;
  });

  it("runs a selected stock with the configured close-near-high threshold", () => {
    render(<FourDayCloseStrengthBacktestPage />);

    fireEvent.click(screen.getByRole("button", { name: "Select instrument" }));
    fireEvent.change(screen.getByRole("spinbutton", { name: "Close near high threshold" }), { target: { value: "85" } });
    fireEvent.click(screen.getByRole("button", { name: /Run INFY test/i }));

    expect(runMock).toHaveBeenCalledWith({
      symbol: "INFY",
      instrumentToken: 123,
      closePositionThresholdPct: 85,
    });
  });

  it("shows Kite link and opens the twenty-session signal audit", () => {
    const signal = {
      symbol: "INFY",
      companyName: "Infosys",
      instrumentToken: 123,
      signalDate: "2025-01-10",
      patternStartDate: "2025-01-07",
      patternEndDate: "2025-01-10",
      patternClosePositionPct: [82, 85, 90, 88],
      lastTwentyTradingDays: [],
    };
    backtestDataMock.current = {
      symbol: "INFY",
      watchlistKey: null,
      testedFromDate: "2020-01-10",
      testedToDate: "2025-01-10",
      closePositionThresholdPct: 80,
      patternSessions: 4,
      detailSessions: 20,
      signalCount: 1,
      signals: [signal],
      symbols: [{ ...signal, testedFromDate: "2020-01-10", testedToDate: "2025-01-10", signalCount: 1, signals: [signal] }],
    };

    render(<FourDayCloseStrengthBacktestPage />);

    expect(screen.getByRole("link", { name: "Open INFY in Kite" })).toHaveAttribute(
      "href",
      "https://kite.zerodha.com/chart/web/tvc/NSE/INFY/123",
    );
    fireEvent.click(screen.getByRole("button", { name: "Open 20-day audit for INFY on 2025-01-10" }));
    expect(screen.getByText("INFY · 20 trading days ending 10 Jan 2025")).toBeInTheDocument();
  });
});
