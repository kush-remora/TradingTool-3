import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { WeeklyLowLimitBacktestPage } from "./WeeklyLowLimitBacktestPage";

const runMock = vi.fn();
const useWeeklyLowLimitBacktestMock = vi.fn();

vi.mock("../hooks/useWeeklyLowLimitBacktest", () => ({
  useWeeklyLowLimitBacktest: () => useWeeklyLowLimitBacktestMock(),
}));

vi.mock("../hooks/useInstrumentSearch", () => ({
  useInstrumentSearch: () => ({
    allInstruments: [{ instrument_token: 123, trading_symbol: "TEST", company_name: "Test Ltd", exchange: "NSE", instrument_type: "EQ" }],
    loading: false,
    error: null,
  }),
}));

vi.mock("../components/InstrumentSearch", () => ({
  InstrumentSearch: ({ onSelect }: { onSelect: (instrument: { instrument_token: number; trading_symbol: string }) => void }) => (
    <button onClick={() => onSelect({ instrument_token: 123, trading_symbol: "TEST" })}>Select TEST</button>
  ),
}));

vi.mock("../utils/api", () => ({
  getJson: vi.fn().mockResolvedValue({ options: [] }),
}));

const trade = {
  symbol: "TEST",
  instrumentToken: 123,
  previousWeekStartDate: "2025-09-29",
  entryWeekStartDate: "2025-10-06",
  orderStartDate: "2025-10-06",
  orderEndDate: "2025-10-10",
  previousWeekLow: 100,
  previousWeekLowDate: "2025-09-29",
  previousWeekLastClose: 103,
  limitPrice: 100,
  outcome: "TARGET_HIT",
  entryDate: "2025-10-07",
  entryOpenDeviationPct: 0.5,
  entryPrice: 100,
  stopPrice: 95,
  targetPrice: 105,
  exitDate: "2025-10-09",
  exitPrice: 105,
  holdingTradingDays: 2,
  returnPct: 5,
  gapFill: false,
  exitWasAmbiguous: false,
};

describe("WeeklyLowLimitBacktestPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useWeeklyLowLimitBacktestMock.mockReturnValue({
      data: {
        mode: "STOCK",
        selection: "TEST",
        testedFromDate: "2025-04-01",
        testedToDate: "2025-09-30",
        entryRule: "ANY_DAY_MAX_5_TRADING_DAYS",
        summary: { setupCount: 2, noFillCount: 1, filledTradeCount: 1, targetHitCount: 1, stopLossCount: 0, timeExitCount: 0, positionOpenSkipCount: 0, premarketFilterSkipCount: 0, openDeviationSkipCount: 0, ambiguousExitCount: 0, averageReturnPct: 5 },
        symbols: [{ symbol: "TEST", companyName: "Test Ltd", entryRule: "ANY_DAY_MAX_5_TRADING_DAYS", testedFromDate: "2025-04-01", testedToDate: "2025-09-30", summary: { setupCount: 2, noFillCount: 1, filledTradeCount: 1, targetHitCount: 1, stopLossCount: 0, positionOpenSkipCount: 0, premarketFilterSkipCount: 0, openDeviationSkipCount: 0, ambiguousExitCount: 0, timeExitCount: 0, averageReturnPct: 5 }, trades: [trade, { ...trade, entryWeekStartDate: "2025-10-13", outcome: "NO_FILL", entryDate: null, entryOpenDeviationPct: null, entryPrice: null, stopPrice: null, targetPrice: null, exitDate: null, exitPrice: null, holdingTradingDays: null, returnPct: null }] }],
      },
      loading: false,
      error: null,
      run: runMock,
    });
  });

  it("renders the weekly audit and sends the selected stock request", async () => {
    render(<WeeklyLowLimitBacktestPage />);

    await waitFor(() => {
      expect(screen.getByText("Weekly Low Limit Backtest")).toBeInTheDocument();
      expect(screen.getByText("TARGET_HIT")).toBeInTheDocument();
      expect(screen.getByText("NO_FILL")).toBeInTheDocument();
      expect(screen.getByText(/Tue, 2025-10-07/)).toBeInTheDocument();
      expect(screen.getByText(/Thu, 2025-10-09/)).toBeInTheDocument();
    });

    const openSpy = vi.spyOn(window, "open").mockImplementation(() => null);
    fireEvent.click(screen.getAllByRole("button", { name: "Validate daily path" })[0]);
    expect(openSpy).toHaveBeenCalledWith(
      expect.stringContaining("console/weekly-low-limit-validation"),
      "_blank",
      "noopener,noreferrer",
    );
    openSpy.mockRestore();

    fireEvent.click(screen.getByText("Select TEST"));
    fireEvent.click(screen.getByRole("button", { name: "Run backtest" }));

    expect(runMock).toHaveBeenCalledWith({ mode: "STOCK", entryRule: "ANY_DAY_MAX_5_TRADING_DAYS", symbol: "TEST", instrumentToken: 123 });
  });

  it("sends the Monday to Wednesday rule when selected", async () => {
    render(<WeeklyLowLimitBacktestPage />);

    await waitFor(() => expect(screen.getByText("Weekly Low Limit Backtest")).toBeInTheDocument());
    fireEvent.click(screen.getByText("Select TEST"));
    fireEvent.click(screen.getByText("Mon–Wed only · Friday exit"));
    fireEvent.click(screen.getByRole("button", { name: "Run backtest" }));

    expect(runMock).toHaveBeenCalledWith({ mode: "STOCK", entryRule: "FIRST_3_DAYS_WEEK_CLOSE", symbol: "TEST", instrumentToken: 123 });
  });
});
