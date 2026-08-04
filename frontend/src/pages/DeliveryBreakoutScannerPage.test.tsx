import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { DeliveryBreakoutScannerPage } from "./DeliveryBreakoutScannerPage";

const useDeliveryBreakoutScannerMock = vi.fn();

vi.mock("../hooks/useDeliveryBreakoutScanner", () => ({
  useDeliveryBreakoutScanner: () => useDeliveryBreakoutScannerMock(),
}));

const response = {
  meta: {
    watchlist_key: "growth_watchlist",
    trade_date: "2026-06-23",
    window_start_date: "2026-06-10",
    window_end_date: "2026-06-23",
    scanned_count: 3,
    data_available_count: 3,
    event_count: 3,
    both_count: 1,
    delivery_only_count: 1,
    volume_only_count: 1,
    no_event_count: 0,
  },
  rows: [
    {
      symbol: "INFY",
      instrument_token: 408065,
      event_date: "2026-06-23",
      event_type: "BOTH",
      close: 1540.5,
      prev_close: 1492.74,
      close_pct_change: 3.2,
      fifty_two_week_high: 1600,
      fifty_two_week_low: 1200,
      volume: 200000,
      delivery_quantity: 140000,
      delivery_percentage: 56,
      average_volume_10d: 100000,
      average_delivery_quantity_10d: 70000,
      volume_ratio: 2,
      delivery_ratio: 2,
    },
    {
      symbol: "TCS",
      instrument_token: 2953217,
      event_date: "2026-06-22",
      event_type: "DELIVERY_ONLY",
      close: 3000,
      prev_close: 2990,
      close_pct_change: 0.33,
      fifty_two_week_high: 3500,
      fifty_two_week_low: 2500,
      volume: 100000,
      delivery_quantity: 120000,
      delivery_percentage: 60,
      average_volume_10d: 100000,
      average_delivery_quantity_10d: 60000,
      volume_ratio: 1,
      delivery_ratio: 2,
    },
    {
      symbol: "WIPRO",
      instrument_token: 969473,
      event_date: "2026-06-20",
      event_type: "VOLUME_ONLY",
      close: 500,
      prev_close: 495,
      close_pct_change: 1.01,
      fifty_two_week_high: 600,
      fifty_two_week_low: 400,
      volume: 220000,
      delivery_quantity: null,
      delivery_percentage: null,
      average_volume_10d: 100000,
      average_delivery_quantity_10d: null,
      volume_ratio: 2.2,
      delivery_ratio: null,
    },
  ],
};

describe("DeliveryBreakoutScannerPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads a selected watchlist and renders all event types", async () => {
    const loadDashboard = vi.fn().mockResolvedValue(response);
    const loadWatchlists = vi.fn().mockResolvedValue({
      options: [{ label: "growth_watchlist", value: "growth_watchlist", count: 3 }],
    });
    useDeliveryBreakoutScannerMock.mockReturnValue({
      watchlists: [{ label: "growth_watchlist", value: "growth_watchlist", count: 3 }],
      data: response,
      loadingWatchlists: false,
      loading: false,
      error: null,
      loadWatchlists,
      loadDashboard,
    });

    render(<DeliveryBreakoutScannerPage />);

    await waitFor(() => {
      expect(loadWatchlists).toHaveBeenCalledTimes(1);
      expect(loadDashboard).toHaveBeenCalledWith("growth_watchlist", undefined);
    });

    expect(screen.getByText("Delivery Breakout Validation")).toBeInTheDocument();
    expect(screen.getByText("INFY")).toBeInTheDocument();
    expect(screen.getByText("BOTH")).toBeInTheDocument();
    expect(screen.getByText("DELIVERY_ONLY")).toBeInTheDocument();
    expect(screen.getByText("VOLUME_ONLY")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open INFY three-week review" })).toHaveAttribute(
      "href",
      "/TradingTool-3/console/three-week-stock-review?symbol=INFY",
    );
    expect(screen.getByRole("link", { name: "Open INFY in Kite" })).toHaveAttribute(
      "href",
      "https://kite.zerodha.com/chart/web/tvc/NSE/INFY/408065",
    );
  });

  it("shows the no-event state when the selected window has no events", () => {
    const emptyResponse = {
      ...response,
      meta: { ...response.meta, event_count: 0, both_count: 0, delivery_only_count: 0, volume_only_count: 0, no_event_count: 3 },
      rows: [],
    };
    useDeliveryBreakoutScannerMock.mockReturnValue({
      watchlists: [{ label: "growth_watchlist", value: "growth_watchlist", count: 3 }],
      data: emptyResponse,
      loadingWatchlists: false,
      loading: false,
      error: null,
      loadWatchlists: vi.fn().mockResolvedValue({ options: [] }),
      loadDashboard: vi.fn().mockResolvedValue(emptyResponse),
    });

    render(<DeliveryBreakoutScannerPage />);

    expect(screen.getByText("No unusual volume or delivery events matched this window.")).toBeInTheDocument();
  });
});
