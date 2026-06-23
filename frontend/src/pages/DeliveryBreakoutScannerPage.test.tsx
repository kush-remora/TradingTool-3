import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { DeliveryBreakoutScannerPage } from "./DeliveryBreakoutScannerPage";

const useDeliveryBreakoutScannerMock = vi.fn();

vi.mock("../hooks/useDeliveryBreakoutScanner", () => ({
  useDeliveryBreakoutScanner: () => useDeliveryBreakoutScannerMock(),
}));

describe("DeliveryBreakoutScannerPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders delivery-breakout summary and rows", async () => {
    const loadDashboard = vi.fn().mockResolvedValue(undefined);
    useDeliveryBreakoutScannerMock.mockReturnValue({
      loading: false,
      error: null,
      loadDashboard,
      data: {
        meta: {
          trade_date: "2026-06-23",
          scanned_count: 4000,
          liquidity_eligible_count: 3200,
          shortlisted_count: 2,
          confirmed_breakout_count: 1,
          quiet_clue_count: 1,
        },
        rows: [
          {
            symbol: "INFY",
            trade_date: "2026-06-23",
            close: 1540.5,
            close_pct_change: 3.2,
            volume: 250000,
            delivery_quantity: 140000,
            delivery_percentage: 56.0,
            prior_10d_max_volume: 100000,
            prior_10d_max_delivery_quantity: 80000,
            volume_ratio_vs_10d_max: 2.5,
            delivery_ratio_vs_10d_max: 1.75,
            has_quiet_clue: true,
            quiet_clue_day: "2026-06-22",
            is_confirmed_breakout_today: true,
            sma200: 1510.0,
            distance_from_sma200_pct: 2.02,
            is_near_200_sma: false,
            label: "CONFIRMED_BREAKOUT_WITH_CLUE",
          },
        ],
      },
    });

    render(<DeliveryBreakoutScannerPage />);

    await waitFor(() => {
      expect(loadDashboard).toHaveBeenCalledTimes(1);
    });

    expect(screen.getByText("Delivery Breakout Validation")).toBeInTheDocument();
    expect(screen.getByText("INFY")).toBeInTheDocument();
    expect(screen.getByText("CONFIRMED_BREAKOUT_WITH_CLUE")).toBeInTheDocument();
    expect(screen.getByText("Breakouts 1")).toBeInTheDocument();
  });

  it("shows empty state when no rows match", () => {
    useDeliveryBreakoutScannerMock.mockReturnValue({
      loading: false,
      error: null,
      loadDashboard: vi.fn().mockResolvedValue(undefined),
      data: {
        meta: {
          trade_date: "2026-06-23",
          scanned_count: 4000,
          liquidity_eligible_count: 3200,
          shortlisted_count: 0,
          confirmed_breakout_count: 0,
          quiet_clue_count: 0,
        },
        rows: [],
      },
    });

    render(<DeliveryBreakoutScannerPage />);

    expect(screen.getByText("No delivery-breakout candidates matched the current rules.")).toBeInTheDocument();
  });
});
