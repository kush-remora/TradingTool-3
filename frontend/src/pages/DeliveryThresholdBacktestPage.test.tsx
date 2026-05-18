import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { DeliveryThresholdBacktestPage } from "./DeliveryThresholdBacktestPage";

const useDeliveryThresholdBacktestMock = vi.fn();

vi.mock("../hooks/useDeliveryThresholdBacktest", () => ({
  useDeliveryThresholdBacktest: () => useDeliveryThresholdBacktestMock(),
}));

vi.mock("../utils/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../utils/api")>();
  return {
    ...actual,
    getJson: vi.fn().mockImplementation((path: string) => {
      if (path === "/api/strategy/delivery-threshold/config") {
        return Promise.resolve({
          thresholds: {
            NIFTY_LARGEMIDCAP_250: 55,
            NIFTY_SMALLCAP_250: 70,
          },
          profitPct: 10,
          fromDate: null,
          toDate: null,
        });
      }
      return Promise.resolve({
        options: [
          { label: "Watchlist", value: "WATCHLIST", count: 20 },
          { label: "NIFTY_LARGEMIDCAP_250", value: "NIFTY_LARGEMIDCAP_250", count: 250 },
          { label: "NIFTY_SMALLCAP_250", value: "NIFTY_SMALLCAP_250", count: 250 },
        ],
      });
    }),
  };
});

describe("DeliveryThresholdBacktestPage", () => {
  it("builds payload and runs backtest", async () => {
    const run = vi.fn().mockResolvedValue(undefined);
    useDeliveryThresholdBacktestMock.mockReturnValue({ data: null, loading: false, error: null, run });

    render(<DeliveryThresholdBacktestPage />);

    await waitFor(() => {
      expect(screen.getByText("Run Scan")).toBeInTheDocument();
    });

    fireEvent.change(screen.getByPlaceholderText("INFY, TCS, RELIANCE"), {
      target: { value: "INFY, TCS" },
    });

    fireEvent.click(screen.getByRole("button", { name: /Run Scan/i }));

    await waitFor(() => {
      expect(run).toHaveBeenCalledTimes(1);
    });

    const payload = run.mock.calls[0][0];
    expect(payload.indexKeys).toEqual(["NIFTY_LARGEMIDCAP_250", "NIFTY_SMALLCAP_250"]);
    expect(payload.symbols).toEqual(["INFY", "TCS"]);
    expect(payload.config.profitPct).toBe(10);
  });

  it("renders result rows", async () => {
    useDeliveryThresholdBacktestMock.mockReturnValue({
      loading: false,
      error: null,
      run: vi.fn(),
      data: {
        config: {
          indexKeys: ["NIFTY_LARGEMIDCAP_250"],
          symbols: [],
          thresholds: { NIFTY_LARGEMIDCAP_250: 55 },
          profitPct: 10,
          fromDate: "2025-05-18",
          toDate: "2026-05-18",
        },
        summary: {
          totalBuys: 2,
          hitCount: 1,
          hitRatePct: 50,
          daysToHitAvg: 12,
          daysToHitMedian: 12,
          daysToHitMin: 12,
          daysToHitMax: 12,
          openCount: 1,
        },
        rows: [
          {
            symbol: "INFY",
            index: "NIFTY_LARGEMIDCAP_250",
            entryDate: "2026-01-10",
            entryPrice: 100,
            entryDeliveryPct: 60,
            totalVolumeCount: 100000,
            avg20dVolumeAtSignal: 120000,
            signalVolumeVs20dPct: 83.33,
            targetPrice: 110,
            fiftyTwoWeekHighAtBuy: 145,
            fiftyTwoWeekLowAtBuy: 92,
            pctFrom52WeekHighAtBuy: -24.14,
            pctFrom52WeekLowAtBuy: 8.70,
            buyDayOfWeek: "Mon",
            exitDate: "2026-01-22",
            exitPrice: 110,
            holdingDays: 12,
            rsiBuy: 45,
            rsiSell: 58,
            maxDrawdownAtBuyPct: -18.4,
            status: "HIT",
            currentPrice: 120,
            floatingPnlPct: null,
            thresholdUsed: 55,
          },
        ],
      },
    });

    render(<DeliveryThresholdBacktestPage />);

    expect(screen.getByText("INFY")).toBeInTheDocument();
    expect(screen.getByText("Hit Count")).toBeInTheDocument();
    expect(screen.getByText("HIT")).toBeInTheDocument();
  });
});
