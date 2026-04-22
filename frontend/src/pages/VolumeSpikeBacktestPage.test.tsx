import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { VolumeSpikeBacktestPage } from "./VolumeSpikeBacktestPage";

const useVolumeSpikeBacktestMock = vi.fn();

vi.mock("../hooks/useVolumeSpikeBacktest", () => ({
  useVolumeSpikeBacktest: () => useVolumeSpikeBacktestMock(),
}));

describe("VolumeSpikeBacktestPage", () => {
  it("runs backtest from controls", () => {
    const run = vi.fn();
    useVolumeSpikeBacktestMock.mockReturnValue({
      data: null,
      loading: false,
      error: null,
      run,
    });

    render(<VolumeSpikeBacktestPage />);

    expect(screen.getByText("Volume Spike Backtest")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Run Backtest/i }));

    expect(run).toHaveBeenCalledTimes(1);
    const request = run.mock.calls[0][0];
    expect(request.delayMinutes).toBe(5);
    expect(request.earningsFilterMode).toBe("OFF");
  });

  it("renders result summary and table", () => {
    useVolumeSpikeBacktestMock.mockReturnValue({
      loading: false,
      error: null,
      run: vi.fn(),
      data: {
        config: {
          fromDate: "2026-03-01",
          toDate: "2026-03-31",
          delayMinutes: 5,
          earningsFilterMode: "OFF",
          earningsWindowStartOffsetDays: null,
          earningsWindowEndOffsetDays: null,
          rvolThreshold: 2,
          targetPct: 5,
          stopPct: 2,
          positionSizeInr: 200000,
          feePerTradeInr: 500,
        },
        summary: {
          symbolsConsidered: 1,
          totalTrades: 1,
          winningTrades: 1,
          losingTrades: 0,
          winRatePct: 100,
          grossPnlInr: 2500,
          totalFeesInr: 500,
          netPnlInr: 2000,
          avgNetReturnPct: 1,
          maxDrawdownInr: 0,
        },
        diagnostics: {
          symbolsFromEarningsUniverse: 0,
          symbolsFromManualInput: 1,
          symbolsWithResolvedToken: 1,
          symbolsWithNoToken: [],
          symbolsWithNoIntradayData: [],
          symbolsSkippedByEarningsFilter: [],
          symbolsWithNoTrades: [],
          cacheHits: 1,
          cacheMisses: 0,
          kiteFetchFailures: [],
        },
        trades: [
          {
            symbol: "INFY",
            instrumentToken: 408065,
            signalTime: "2026-03-12T10:00:00",
            entryTime: "2026-03-12T10:05:00",
            exitTime: "2026-03-12T13:10:00",
            entryPrice: 1200,
            exitPrice: 1225,
            quantity: 100,
            investedAmount: 120000,
            targetPrice: 1260,
            stopPrice: 1176,
            rvolAtSignal: 2.4,
            vwapAtSignal: 1198,
            prior30MinHigh: 1199,
            exitReason: "EOD",
            grossPnlInr: 2500,
            feeInr: 500,
            netPnlInr: 2000,
            netReturnPct: 1.67,
          },
        ],
      },
    });

    render(<VolumeSpikeBacktestPage />);

    expect(screen.getByText("Diagnostics")).toBeInTheDocument();
    expect(screen.getByText("INFY")).toBeInTheDocument();
    expect(screen.getAllByText("Trades").length).toBeGreaterThan(0);
  });
});
