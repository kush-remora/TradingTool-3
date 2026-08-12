import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { DayDetail, LiveMarketUpdate, StockDetailResponse, TradeWithTargets } from "../../types";
import { CompactStockReviewPage } from "./CompactStockReviewPage";

const getJsonMock = vi.hoisted(() => vi.fn());
const useStockDetailMock = vi.fn();
const useTradeDataMock = vi.hoisted(() => vi.fn());
const liveMarketDataMock = vi.hoisted(() => vi.fn());
const addNoteMock = vi.fn(async () => true);

vi.mock("../../utils/api", () => ({ getJson: getJsonMock }));

vi.mock("../../hooks/useInstrumentSearch", () => ({
  useInstrumentSearch: () => ({
    allInstruments: [{
      instrument_token: 4462849,
      trading_symbol: "NETWEB",
      company_name: "Netweb Technologies India",
      exchange: "NSE",
      instrument_type: "EQ",
    }],
    loading: false,
    error: null,
  }),
}));

vi.mock("../../hooks/useStockDetail", () => ({
  useStockDetail: (symbol: string | null, days: number) => useStockDetailMock(symbol, days),
}));
vi.mock("../../hooks/useTradeData", () => ({
  useTradeData: () => useTradeDataMock(),
}));

vi.mock("../../hooks/useLiveMarketData", () => ({ useLiveMarketData: () => liveMarketDataMock() }));
vi.mock("../../hooks/useInstrumentNotes", () => ({
  useInstrumentNotes: () => ({
    notes: [{ id: 1, instrumentToken: 4462849, notes: "Demand improved above the prior low.", createdAt: "2026-08-10T18:30:00Z", updatedAt: "2026-08-10T18:30:00Z" }],
    loading: false,
    error: null,
    addNote: addNoteMock,
    removeNote: vi.fn(),
  }),
}));

vi.mock("./CompactStockChart", () => ({
  CompactStockChart: () => <div data-testid="compact-price-chart" />,
}));

const day = (date: string, open: number, high: number, low: number, close: number, volume: number): DayDetail => ({
  date,
  open,
  high,
  low,
  close,
  volume,
  daily_change_pct: ((close - open) / open) * 100,
  rsi14: null,
  vol_ratio: null,
});

const detail: StockDetailResponse = {
  symbol: "NETWEB",
  exchange: "NSE",
  avg_volume_20d: 1_200_000,
  pivot_levels: null,
  fundamentals: {
    currentPrice: 4855,
    fiftyTwoWeekLow: 2037.3,
    fiftyTwoWeekHigh: 5244,
    sma200: 3739.01,
    sma100: 4137.64,
  },
  days: [
    day("2026-07-20", 4300, 4383, 4210, 4250, 700_000),
    day("2026-07-24", 4260, 4290, 4115, 4200, 600_000),
    day("2026-07-27", 4220, 4400, 4129, 4380, 900_000),
    day("2026-07-29", 4390, 4510, 4320, 4450, 1_600_000),
    day("2026-07-31", 4460, 4598, 4410, 4524, 4_500_000),
    day("2026-08-03", 4560, 4740, 4540, 4586, 2_300_000),
    day("2026-08-04", 4609, 4780, 4593, 4762, 1_800_000),
    day("2026-08-05", 4821, 4933, 4725, 4894, 1_900_000),
    day("2026-08-06", 4900, 4980, 4783, 4816, 1_100_000),
    day("2026-08-07", 4790, 4968, 4780, 4939, 1_300_000),
    day("2026-08-10", 4958, 5085, 4889, 4903, 1_200_000),
    day("2026-08-11", 4903, 4940, 4792.5, 4855, 680_000),
  ],
  delivery_days: [
    { date: "2026-08-10", delivery_percentage: 23.38, delivered_quantity: 283_000, traded_quantity: 1_210_000 },
    { date: "2026-08-07", delivery_percentage: 20.9, delivered_quantity: 271_000, traded_quantity: 1_297_000 },
  ],
  momentum_evidence: {
    as_of_date: "2026-08-11",
    current_close: 4855,
    sma200: 3739.01,
    above_sma200: true,
    distance_from_sma200_pct: 29.85,
    fifty_two_week_high: 5244,
    distance_from_fifty_two_week_high_pct: -7.42,
    weekly_returns: [],
    weekly_roc: null,
    participation_events: [{ event_date: "2026-07-31", close: 4524, volume: 4_500_000, volume_ratio: 4.91, daily_return_pct: 7.84, price_since_event_pct: 7.32, delivery_percentage: 16.07 }],
    participation_threshold: 2,
    participation_lookback_days: 90,
    data_status: "AVAILABLE",
  },
  breakout_dates: {
    breakout_20d: "2026-07-31",
    breakout_50d: "2026-07-29",
    breakout_52d: "2026-07-29",
    breakout_100d: "2026-06-12",
    breakout_20d_level: 4500,
    breakout_50d_level: 4400,
    breakout_52d_level: 4400,
    breakout_100d_level: 4000,
  },
};

const paperTradeFixture: TradeWithTargets = {
  trade: {
    id: 12,
    instrument_token: 4462849,
    nse_symbol: "NETWEB",
    quantity: 1,
    avg_buy_price: "4800",
    today_low: null,
    stop_loss_percent: "5",
    stop_loss_price: "4560",
    notes: null,
    trade_date: "2026-08-01",
    close_price: null,
    close_date: null,
    created_at: "2026-08-01T10:00:00Z",
    updated_at: "2026-08-01T10:00:00Z",
  },
  gtt_targets: [],
  total_invested: "4800",
};

describe("CompactStockReviewPage", () => {
  beforeEach(() => {
    window.history.replaceState({}, "", "/TradingTool-3/console/compact-stock-review?symbol=NETWEB");
    addNoteMock.mockClear();
    liveMarketDataMock.mockReturnValue(null);
    useTradeDataMock.mockReturnValue({
      trades: [paperTradeFixture],
      loading: false,
      error: null,
      refetch: vi.fn(),
      createTrade: vi.fn(async () => paperTradeFixture),
      closeTrade: vi.fn(async () => undefined),
      deleteTrade: vi.fn(async () => undefined),
    });
    getJsonMock.mockImplementation((path: string) => {
      if (path === "/api/strategy/weekly-price-review/watchlists") {
        return Promise.resolve({ options: [{ label: "review", value: "review", count: 2 }] });
      }
      if (path.startsWith("/api/stocks/watchlists/")) {
        return Promise.resolve([
          { instrument_token: 4462849, trading_symbol: "NETWEB", company_name: "Netweb Technologies India", exchange: "NSE", instrument_type: "EQ" },
          { instrument_token: 12345, trading_symbol: "TCS", company_name: "Tata Consultancy Services", exchange: "NSE", instrument_type: "EQ" },
        ]);
      }
      return Promise.reject(new Error(`Unexpected GET ${path}`));
    });
    useStockDetailMock.mockImplementation((symbol: string | null) => ({
      data: symbol ? detail : null,
      loading: false,
      error: null,
    }));
  });

  it("shows the complete stock story on one page while using the 150-session detail source", async () => {
    render(<CompactStockReviewPage />);

    expect(await screen.findAllByText("Netweb Technologies India")).toHaveLength(2);
    const paperPosition = screen.getByRole("region", { name: "Open paper trade" });
    expect(paperPosition).toHaveTextContent("01 Aug 2026");
    expect(paperPosition).toHaveTextContent("₹4,800.00");
    expect(paperPosition).toHaveTextContent("+1.15%");
    expect(paperPosition).toHaveTextContent("11d old");
    expect(screen.getByRole("button", { name: "Export compact review as Markdown" })).toBeEnabled();
    expect(screen.getAllByText("Close Tue, 11 Aug 2026")).toHaveLength(2);
    expect(screen.getByText("as of 10 Aug 2026")).toBeInTheDocument();
    const kiteLink = screen.getAllByRole("link", { name: "Open NETWEB in Kite" })[0];
    expect(kiteLink).toHaveAttribute("href", "https://kite.zerodha.com/chart/web/tvc/NSE/NETWEB/4462849");
    expect(kiteLink).toHaveAttribute("target", "_blank");
    expect(screen.getByTestId("compact-price-chart")).toBeInTheDocument();
    expect(screen.getByText("Move")).toBeInTheDocument();
    expect(document.querySelector(".crh-ltp-col")).toHaveTextContent("LTP₹4,855Latest close");
    expect(document.querySelector(".crh-open-low-col")).toHaveTextContent("−₹110.5");
    expect(document.querySelector(".crh-open-low-col")).toHaveTextContent("-2.3%");
    expect(document.querySelector(".crh-move-col")).toHaveTextContent("5D");
    expect(screen.getByRole("combobox", { name: "Review watchlist" })).toBeInTheDocument();
    expect(screen.getByText("Last fresh breakout")).toBeInTheDocument();
    expect(screen.getByTestId("compact-breakout-dates")).toHaveTextContent("31 Jul 2026");
    expect(screen.getByTestId("compact-breakout-dates")).toHaveTextContent("12 Jun 2026");
    expect(screen.getByTestId("compact-breakout-dates")).toHaveTextContent("₹4,500");
    expect(screen.getByTestId("compact-breakout-dates")).toHaveTextContent("+7.9%");
    expect(document.querySelector(".crh-flow-summary")).toHaveTextContent("Gap");
    expect(document.querySelector(".crh-breakout-col")).toHaveClass("crh-breakout-col");
    expect(document.querySelector(".crh-secondary-wrap .crh-move-col")).toBeInTheDocument();
    expect(document.querySelector(".crh-primary-wrap .crh-move-col")).not.toBeInTheDocument();
    expect(screen.getByText("Observation log")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Take note" })).toBeInTheDocument();
    expect(screen.getByText("Four-week structure")).toBeInTheDocument();
    expect(screen.getByText("Recent tape")).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "Open → Low" })).toBeInTheDocument();
    expect(screen.getByText("Top volume days · 40 sessions")).toBeInTheDocument();
    expect(screen.getByText("Effort → result")).toBeInTheDocument();
    expect(screen.getAllByText("W low").length).toBeGreaterThan(0);
    expect(screen.getAllByText("W high").length).toBeGreaterThan(0);
    const expandTape = screen.getByRole("button", { name: "Show last 30 days" });
    expect(expandTape).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(expandTape);
    expect(screen.getByRole("button", { name: "Collapse to 10 days" })).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByText("4W flow")).toBeInTheDocument();
    expect(screen.getByText("HL + HH")).toBeInTheDocument();
    expect(screen.getByText("Demand improved above the prior low.")).toBeInTheDocument();
    expect(screen.getAllByText("O / H / L / C")).toHaveLength(2);
    expect(useStockDetailMock).toHaveBeenLastCalledWith("NETWEB", 150);
    const paperTradeButton = screen.getByRole("button", { name: "Add paper trade for current stock" });
    expect(paperTradeButton).toBeEnabled();
    fireEvent.click(paperTradeButton);
    expect(screen.getAllByDisplayValue("NETWEB")).toHaveLength(4);
    expect(screen.getByDisplayValue("4855.00")).toBeInTheDocument();
  });

  it("moves through the selected watchlist while keeping independent search available", async () => {
    window.history.replaceState({}, "", "/TradingTool-3/console/compact-stock-review?symbol=NETWEB&watchlist=review");
    render(<CompactStockReviewPage />);

    expect(await screen.findByText("1/2")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Next watchlist stock" }));

    expect(await screen.findByText("2/2")).toBeInTheDocument();
    expect(useStockDetailMock).toHaveBeenLastCalledWith("TCS", 150);
  });

  it("uses live session open and low for the main dip metric", async () => {
    const liveUpdate: LiveMarketUpdate = {
      symbol: "NSE:NETWEB",
      instrumentToken: 4462849,
      ltp: 4800,
      averagePrice: null,
      changePercent: -1.2,
      open: 4866,
      high: 4900,
      low: 4753,
      volume: 900_000,
      buyQuantity: 1,
      sellQuantity: 1,
      buyPressurePct: null,
      sellPressurePct: null,
      buyerDominancePass: null,
      pressureSide: "NEUTRAL",
      avgVol20d: null,
      volumeHeat: null,
      updatedAt: 0,
    };
    liveMarketDataMock.mockReturnValue(liveUpdate);

    render(<CompactStockReviewPage />);

    expect(await screen.findAllByText("Netweb Technologies India")).toHaveLength(2);
    expect(document.querySelector(".crh-ltp-col")).toHaveTextContent("LTP₹4,800Live feed");
    expect(document.querySelector(".crh-open-low-col")).toHaveTextContent("−₹113");
    expect(document.querySelector(".crh-open-low-col")).toHaveTextContent("-2.3%");
  });

  it("saves today's observation without leaving the compact console", async () => {
    render(<CompactStockReviewPage />);

    fireEvent.click(screen.getByRole("button", { name: "Take note" }));
    const note = await screen.findByLabelText("Today's observation");
    fireEvent.change(note, { target: { value: "Supply stayed light during the pullback." } });
    fireEvent.click(screen.getByRole("button", { name: "Send" }));

    await waitFor(() => expect(addNoteMock).toHaveBeenCalledWith("Supply stayed light during the pullback."));
    expect(screen.getByText("Observation log")).toBeInTheDocument();
  });
});
