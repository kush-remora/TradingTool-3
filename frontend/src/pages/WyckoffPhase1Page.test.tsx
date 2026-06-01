import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { WyckoffPhase1Page } from "./WyckoffPhase1Page";

const useWyckoffPhase1ScannerMock = vi.fn();

vi.mock("../hooks/useWyckoffPhase1Scanner", () => ({
  useWyckoffPhase1Scanner: () => useWyckoffPhase1ScannerMock(),
}));

vi.mock("../utils/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../utils/api")>();
  return {
    ...actual,
    getJson: vi.fn().mockImplementation((path: string) => {
      if (path === "/api/strategy/wyckoff/phase1/config") {
        return Promise.resolve({
          enabled: true,
          trackA: {
            deliveryThresholdByCap: { MID_CAP: 55, SMALL_CAP: 70, MICRO_CAP: 85, NANO_CAP: 92 },
            rollingDensity: { enabled: true, lookbackDays: 15, minThresholdBreaches: 4 },
            deliveryVolumeZScore: { enabled: true, baselineDays: 60, minZScore: 2 },
            lvqDq: { enabled: true, rollingMinDays: 63, nearMinPctOfRollingMin: 95, lookbackDays: 15, requireDeliveryPass: true },
            absorptionCheck: { enabled: true, spreadLookbackDays: 20 },
            lowVolumeHighDeliveryInfo: { enabled: true, mode: "INFO_ONLY", volumeBaselineDays: 50, maxVolumeVsBaselineRatio: 0.8 },
          },
          contextFilter: {
            roc20Range: { enabled: true, minDistancePct: -5, maxDistancePct: 5 },
            dma200Proximity: { enabled: true, minDistancePct: -5, maxDistancePct: 5 },
          },
        });
      }
      if (path === "/api/strategy/wyckoff/phase1/columns") {
        return Promise.resolve({
          enabled: true,
          defaultSort: [{ key: "signal_date", direction: "desc" }],
          columns: [
            { key: "symbol", enabled: true },
            { key: "signal_date", enabled: true },
            { key: "lvq_hit_count_15d", enabled: true },
            { key: "delivery_threshold_pct", enabled: false },
          ],
        });
      }
      if (path === "/api/watchlist/symbols") {
        return Promise.resolve([]);
      }
      return Promise.resolve({
        options: [
          { label: "Watchlist", value: "WATCHLIST", count: 20 },
          { label: "NIFTY_50", value: "NIFTY_50", count: 50 },
        ],
      });
    }),
  };
});

describe("WyckoffPhase1Page", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it("runs with selected universe keys only", async () => {
    const run = vi.fn().mockResolvedValue(undefined);
    useWyckoffPhase1ScannerMock.mockReturnValue({ data: null, loading: false, error: null, run });

    render(<WyckoffPhase1Page />);

    await waitFor(() => {
      expect(screen.getByText("Run Scan")).toBeInTheDocument();
    });

    fireEvent.mouseDown(screen.getAllByRole("combobox")[0]);
    fireEvent.click(await screen.findByText("WATCHLIST (20)"));

    fireEvent.click(screen.getByRole("button", { name: /Run Scan/i }));

    await waitFor(() => {
      expect(run).toHaveBeenCalledTimes(1);
    });

    const payload = run.mock.calls[0][0];
    expect(payload.universeKeys).toEqual(["WATCHLIST"]);
    expect(payload.symbols).toBeUndefined();
  });

  it("does not render symbol source controls", async () => {
    const run = vi.fn().mockResolvedValue(undefined);
    useWyckoffPhase1ScannerMock.mockReturnValue({ data: null, loading: false, error: null, run });

    render(<WyckoffPhase1Page />);

    await waitFor(() => {
      expect(screen.getByText("Run Scan")).toBeInTheDocument();
    });

    expect(screen.queryByText("Symbol Source (optional)")).not.toBeInTheDocument();
    expect(screen.queryByText(/^Selected symbols:/)).not.toBeInTheDocument();
  });

  it("restores persisted filters from local storage", async () => {
    localStorage.setItem(
      "wyckoff-phase1-filters-v1",
      JSON.stringify({
        universeKeys: ["WATCHLIST"],
      }),
    );

    const run = vi.fn().mockResolvedValue(undefined);
    useWyckoffPhase1ScannerMock.mockReturnValue({ data: null, loading: false, error: null, run });

    render(<WyckoffPhase1Page />);

    await waitFor(() => {
      expect(screen.getByText("Run Scan")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /Run Scan/i }));

    await waitFor(() => {
      expect(run).toHaveBeenCalledTimes(1);
    });

    const payload = run.mock.calls[0][0];
    expect(payload.universeKeys).toEqual(["WATCHLIST"]);
    expect(payload.symbols).toBeUndefined();
  });

  it("applies enabled columns from config", async () => {
    useWyckoffPhase1ScannerMock.mockReturnValue({
      loading: false,
      error: null,
      run: vi.fn(),
      data: {
        meta: {
          as_of_date: "2026-05-26",
          evaluated_trading_dates: ["2026-05-26"],
          universe_count: 10,
          matched_count: 1,
        },
        rows: [
          {
            symbol: "INFY",
            signal_date: "2026-05-26",
            days_ago: 0,
            index_key: "NIFTY_50",
            delivery_pct: 70,
            delivery_threshold_pct: 55,
            delivery_pass: 1,
            density_breach_count_15d: 4,
            density_pass: 1,
            delivery_volume_zscore_60d: 2.4,
            zscore_pass: 1,
            lvq_dq_pass: 1,
            lvq_hit_count_15d: 3,
            spread_pct: 1.4,
            avg_spread_pct_20d: 2.1,
            absorption_pass: 1,
            roc20_pct: -1.0,
            roc20_range_pass: 1,
            sma200_distance_pct: -0.5,
            sma_window_used: 200,
            dma200_range_pass: 1,
            low_volume_high_delivery_info: 0,
            volume_vs_50d_ratio: 1.4,
            passed_count: 6,
            accumulation_run_length_days: 2,
          },
        ],
      },
    });

    render(<WyckoffPhase1Page />);

    await waitFor(() => {
      expect(screen.getAllByText("Symbol").length).toBeGreaterThan(0);
      expect(screen.getAllByText("Signal Date").length).toBeGreaterThan(0);
    });

    expect(screen.queryByText("Delivery Threshold %")).not.toBeInTheDocument();
  });
});
