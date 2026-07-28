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
            prev_close: 1492.74,
            fifty_two_week_high: 1600,
            fifty_two_week_low: 1200,
            close_pct_change: 3.2,
            volume: 250000,
            delivery_quantity: 140000,
            delivery_percentage: 56.0,
            prev_volume: 100000,
            prev_delivery_quantity: 80000,
            volume_ratio: 2.5,
            delivery_ratio: 1.75,
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
    expect(screen.getByText("High: -3.72%")).toBeInTheDocument();
    expect(screen.getByText("Low: +28.38%")).toBeInTheDocument();
    expect(screen.getByText("2.50x")).toBeInTheDocument();
    expect(screen.getByText("1.75x")).toBeInTheDocument();
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
