import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { DeliveryBreakoutScannerPage } from "./DeliveryBreakoutScannerPage";

const useDeliveryBreakoutScannerMock = vi.fn();
const useStockQuotesMock = vi.fn();

vi.mock("../hooks/useDeliveryBreakoutScanner", () => ({
  useDeliveryBreakoutScanner: () => useDeliveryBreakoutScannerMock(),
}));

vi.mock("../hooks/useStockQuotes", () => ({
  useStockQuotes: () => useStockQuotesMock(),
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
      event_high: 1570,
      event_low: 1480,
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
      quiet_days_20d: 12,
      range_20d_pct: 8,
      pre_event_change_10d_pct: 4,
      pre_event_upward_10d: true,
      accumulation_candidate_20d: true,
    },
    {
      symbol: "TCS",
      instrument_token: 2953217,
      event_date: "2026-06-22",
      event_type: "DELIVERY_ONLY",
      close: 3000,
      event_high: 3020,
      event_low: 2970,
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
      quiet_days_20d: 10,
      range_20d_pct: 7,
      pre_event_change_10d_pct: 6,
      pre_event_upward_10d: true,
      accumulation_candidate_20d: true,
    },
    {
      symbol: "WIPRO",
      instrument_token: 969473,
      event_date: "2026-06-20",
      event_type: "VOLUME_ONLY",
      close: 500,
      event_high: 500,
      event_low: 490,
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
      quiet_days_20d: 6,
      range_20d_pct: 12,
      pre_event_change_10d_pct: -3,
      pre_event_upward_10d: false,
      accumulation_candidate_20d: false,
    },
  ],
};

describe("DeliveryBreakoutScannerPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useStockQuotesMock.mockReturnValue({
      quotesBySymbol: {
        INFY: { symbol: "INFY", ltp: 1600 },
        TCS: { symbol: "TCS", ltp: 2980 },
        WIPRO: { symbol: "WIPRO", ltp: 505 },
      },
      loading: false,
      error: null,
    });
  });

  it("loads a selected watchlist and renders all event types", async () => {
    const loadDashboard = vi.fn().mockResolvedValue(response);
    const loadDashboards = vi.fn().mockResolvedValue({ dashboards: [response], errors: [] });
    const clear = vi.fn();
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
      loadDashboards,
      clear,
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
    expect(screen.getByText("All Stocks (3)")).toBeInTheDocument();
    expect(screen.getByText("Result vs Effort (<3% Low→High) (1)")).toBeInTheDocument();
    expect(screen.getByText("At Least 10% Below 52W High (1)")).toBeInTheDocument();
    expect(screen.getByText("20D Accumulation Candidate (1)")).toBeInTheDocument();
    expect(screen.getAllByText("Price Context").length).toBeGreaterThan(0);
    expect(screen.getByText("₹1,600.00")).toBeInTheDocument();
    expect(screen.getByText("+3.86% vs event")).toBeInTheDocument();
    expect(screen.getAllByText("Close→High")).toHaveLength(3);
    expect(screen.getByRole("link", { name: "Open INFY three-week review" })).toHaveAttribute(
      "href",
      "/TradingTool-3/console/three-week-stock-review?symbol=INFY",
    );
    expect(screen.getByRole("link", { name: "Open INFY in Kite" })).toHaveAttribute(
      "href",
      "https://kite.zerodha.com/chart/web/tvc/NSE/INFY/408065",
    );
  });

  it("shows only quiet low-to-high event days in the Result vs Effort tab", async () => {
    useDeliveryBreakoutScannerMock.mockReturnValue({
      watchlists: [{ label: "growth_watchlist", value: "growth_watchlist", count: 3 }],
      data: response,
      loadingWatchlists: false,
      loading: false,
      error: null,
      loadWatchlists: vi.fn().mockResolvedValue({ options: [] }),
      loadDashboard: vi.fn().mockResolvedValue(response),
      loadDashboards: vi.fn().mockResolvedValue({ dashboards: [], errors: [] }),
      clear: vi.fn(),
    });

    render(<DeliveryBreakoutScannerPage />);

    fireEvent.click(await screen.findByRole("tab", { name: "Result vs Effort (<3% Low→High) (1)" }));

    expect(screen.getByText("TCS")).toBeInTheDocument();
    expect(screen.queryByText("INFY")).not.toBeInTheDocument();
    expect(screen.queryByText("WIPRO")).not.toBeInTheDocument();

    fireEvent.click(await screen.findByRole("tab", { name: "At Least 10% Below 52W High (1)" }));

    expect(screen.getByText("TCS")).toBeInTheDocument();
    expect(screen.queryByText("WIPRO")).not.toBeInTheDocument();
    expect(screen.queryByText("INFY")).not.toBeInTheDocument();

    expect(screen.getByText("20D Accumulation Candidate (1)")).toBeInTheDocument();

    fireEvent.click(await screen.findByRole("tab", { name: "20D Accumulation Candidate (1)" }));

    expect(screen.getByText("TCS")).toBeInTheDocument();
    expect(screen.queryByText("WIPRO")).not.toBeInTheDocument();
    expect(screen.queryByText("INFY")).not.toBeInTheDocument();
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
      loadDashboards: vi.fn().mockResolvedValue({ dashboards: [], errors: [] }),
      clear: vi.fn(),
    });

    render(<DeliveryBreakoutScannerPage />);

    expect(screen.getByText("No unusual volume or delivery events matched this window.")).toBeInTheDocument();
  });

  it("runs multiple selected watchlists together", async () => {
    const loadDashboard = vi.fn().mockResolvedValue(response);
    const loadDashboards = vi.fn().mockResolvedValue({ dashboards: [response], errors: [] });
    useDeliveryBreakoutScannerMock.mockReturnValue({
      watchlists: [
        { label: "growth_watchlist", value: "growth_watchlist", count: 3 },
        { label: "core", value: "core", count: 5 },
      ],
      data: response,
      multiData: null,
      loadingWatchlists: false,
      loading: false,
      error: null,
      loadWatchlists: vi.fn().mockResolvedValue({
        options: [
          { label: "growth_watchlist", value: "growth_watchlist", count: 3 },
          { label: "core", value: "core", count: 5 },
        ],
      }),
      loadDashboard,
      loadDashboards,
      clear: vi.fn(),
    });

    render(<DeliveryBreakoutScannerPage />);

    const watchlist = await screen.findByRole("combobox", { name: "Watchlist" });
    fireEvent.mouseDown(watchlist);
    fireEvent.click(await screen.findByText("core (5)"));

    await waitFor(() => {
      expect(loadDashboards).toHaveBeenCalledWith(["growth_watchlist", "core"], undefined);
    });
  });
});
