import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { BollingerBacktestPage } from "./BollingerBacktestPage";

const useBollingerBacktestMock = vi.fn();

vi.mock("../hooks/useBollingerBacktest", () => ({
  useBollingerBacktest: () => useBollingerBacktestMock(),
}));

vi.mock("../utils/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../utils/api")>();
  return {
    ...actual,
    getJson: vi.fn().mockResolvedValue({
      options: [{ label: "Watchlist", value: "WATCHLIST", count: 20 }],
    }),
  };
});

describe("BollingerBacktestPage", () => {
  it("runs backtest using UI config", () => {
    const run = vi.fn();
    useBollingerBacktestMock.mockReturnValue({ data: null, loading: false, error: null, run });

    render(<BollingerBacktestPage />);
    fireEvent.click(screen.getByRole("button", { name: /Run Bollinger Squeeze Backtest/i }));

    expect(run).toHaveBeenCalledTimes(1);
    expect(run.mock.calls[0][0].universe).toBe("WATCHLIST");
  });

  it("renders summary and trade row", () => {
    useBollingerBacktestMock.mockReturnValue({
      loading: false,
      error: null,
      run: vi.fn(),
      data: {
        config: {
          universe: "WATCHLIST",
          capital: 200000,
          maxOpenPositions: 5,
          setupWindowDays: 5,
          tightSqueezeTolerancePct: 12,
          volumeMultiplier: 2,
          breakEvenProfitPct: 2,
          maxHoldDays: 5,
        },
        summary: {
          totalTrades: 1,
          winningTrades: 1,
          losingTrades: 0,
          winRatePct: 100,
          grossPnlInr: 1200,
          totalBrokerageInr: 40,
          netPnlInr: 1160,
          totalReturnPct: 0.58,
          avgReturnPerTradePct: 0.58,
          maxDrawdownInr: 0,
          finalCapital: 201160,
        },
        diagnostics: {
          symbolsConsidered: 20,
          symbolsWithInsufficientData: [],
          symbolsWithNoTrades: [],
        },
        trades: [
          {
            symbol: "INFY",
            companyName: "Infosys",
            entryDate: "2026-05-01",
            exitDate: "2026-05-04",
            holdingDays: 3,
            quantity: 100,
            investedAmount: 180000,
            entryPrice: 1800,
            exitPrice: 1812,
            exitReason: "MIDDLE_BAND",
            grossPnlInr: 1200,
            netPnlInr: 1160,
            netReturnPct: 0.64,
            entryCriteria: {
              percentB: -0.05,
              rsi14: 31,
              bandwidthPct: 5,
              volumeRatio20: 2.5,
              closeAboveSma200: true,
              signal: "OVERSOLD",
              reasoning: "Price near lower band",
            },
            exitCriteria: {
              percentB: 0.52,
              rsi14: 46,
              bandwidthPct: 5.3,
              volumeRatio20: 1.2,
              closeAboveSma200: true,
              signal: "NORMAL",
              reasoning: "",
            },
            debugRows: [
              {
                date: "2026-04-29",
                ltp: 1790,
                bbUpper: 1860,
                bbMiddle: 1820,
                bbLower: 1780,
                percentB: 0.12,
                bandwidthPct: 4.4,
                rsi14: 33,
                volumeRatio20: 1.8,
                closeAboveSma200: true,
                signal: "OVERSOLD",
                reasoning: "Price near lower band",
              },
            ],
          },
        ],
      },
    });

    render(<BollingerBacktestPage />);

    expect(screen.getByText("Diagnostics")).toBeInTheDocument();
    expect(screen.getByText("INFY")).toBeInTheDocument();
  });
});
