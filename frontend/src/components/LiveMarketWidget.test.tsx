import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { LiveMarketWidget } from "./LiveMarketWidget";

const useLiveMarketDataMock = vi.fn();
const useStockDetailMock = vi.fn();
const tradeMarketHistoryPanelMock = vi.fn();

vi.mock("../hooks/useLiveMarketData", () => ({
  useLiveMarketData: (...args: unknown[]) => useLiveMarketDataMock(...args),
}));

vi.mock("../hooks/useStockDetail", () => ({
  useStockDetail: (...args: unknown[]) => useStockDetailMock(...args),
}));

vi.mock("./TradeMarketHistoryPanel", () => ({
  TradeMarketHistoryPanel: (props: unknown) => {
    tradeMarketHistoryPanelMock(props);
    return <div data-testid="trade-market-history-panel" />;
  },
}));

describe("LiveMarketWidget", () => {
  it("does not mount the detail drawer until the user opens it", () => {
    useStockDetailMock.mockReturnValue({ data: null, loading: false, error: null });
    useLiveMarketDataMock.mockReturnValue({
      ltp: 105,
      averagePrice: 100,
      changePercent: 2.25,
      volume: 240000,
      buyQuantity: 700000,
      sellQuantity: 300000,
    });

    render(
      <LiveMarketWidget
        symbol="NSE:INFY"
        snapshot={{
          symbol: "INFY",
          ltp: 104,
          average_price: 99,
          change_percent: 1.2,
          day_open: 100,
          day_high: 106,
          day_low: 99,
          volume: 200000,
          previous_day_volume: 120000,
          updated_at: "2026-06-25",
        }}
      />,
    );

    expect(screen.queryByText("INFY Live Detail")).not.toBeInTheDocument();
    expect(useStockDetailMock).not.toHaveBeenCalled();
  });

  it("renders the compact standard live-market context with average-price and t-1 volume cues", () => {
    useStockDetailMock.mockReturnValue({ data: null, loading: false, error: null });
    useLiveMarketDataMock.mockReturnValue({
      ltp: 105,
      averagePrice: 100,
      changePercent: 2.25,
      volume: 240000,
      buyQuantity: 700000,
      sellQuantity: 300000,
    });

    render(
      <LiveMarketWidget
        symbol="NSE:INFY"
        snapshot={{
          symbol: "INFY",
          ltp: 104,
          average_price: 99,
          change_percent: 1.2,
          day_open: 100,
          day_high: 106,
          day_low: 99,
          volume: 200000,
          previous_day_volume: 120000,
          updated_at: "2026-06-25",
        }}
      />,
    );

    expect(screen.getByText("₹105.00")).toBeInTheDocument();
    expect(screen.getByText(/Above Avg \+5.0%/)).toBeInTheDocument();
    expect(screen.getByText("Vol 2.0x T-1")).toHaveStyle("color: #52c41a");
    expect(screen.getByLabelText("Pressure bar buy 70.0 percent sell 30.0 percent")).toBeInTheDocument();
  });

  it("renders the wide mode with extended detail lines", () => {
    useStockDetailMock.mockReturnValue({ data: null, loading: false, error: null });
    useLiveMarketDataMock.mockReturnValue({
      ltp: 105,
      averagePrice: 100.5,
      changePercent: 2.25,
      open: 101,
      high: 106,
      low: 99,
      volume: 180000,
      buyQuantity: 560000,
      sellQuantity: 440000,
    });

    render(
      <LiveMarketWidget
        symbol="NSE:INFY"
        mode="wide"
        snapshot={{
          symbol: "INFY",
          ltp: 104,
          average_price: 99.25,
          change_percent: 1.2,
          day_open: 100,
          day_high: 106,
          day_low: 99,
          volume: 150000,
          previous_day_volume: 120000,
          updated_at: "2026-06-25",
        }}
      />,
    );

    expect(screen.getByText("Avg:")).toBeInTheDocument();
    expect(screen.getByText("T-1:")).toBeInTheDocument();
    expect(screen.getByText("100.5")).toHaveStyle("color: #52c41a");
    expect(screen.getByText("1.5x")).toHaveStyle("color: #d46b08");
    expect(screen.getByText("Buy 56% (5.60 L)")).toBeInTheDocument();
    expect(screen.getByText("Sell 44% (4.40 L)")).toBeInTheDocument();
    expect(screen.getByLabelText("Pressure bar buy 56.0 percent sell 44.0 percent")).toBeInTheDocument();
    expect(screen.queryByText(/Above Avg|Below Avg/)).not.toBeInTheDocument();
  });

  it("shows a balanced pressure split when buy and sell quantities are near even", () => {
    useStockDetailMock.mockReturnValue({ data: null, loading: false, error: null });
    useLiveMarketDataMock.mockReturnValue({
      ltp: 105,
      averagePrice: 100,
      changePercent: 2.25,
      volume: 240000,
      buyQuantity: 520000,
      sellQuantity: 480000,
    });

    render(
      <LiveMarketWidget
        symbol="NSE:INFY"
        snapshot={{
          symbol: "INFY",
          ltp: 104,
          average_price: 99,
          change_percent: 1.2,
          day_open: 100,
          day_high: 106,
          day_low: 99,
          volume: 200000,
          previous_day_volume: 120000,
          updated_at: "2026-06-25",
        }}
      />,
    );

    expect(screen.getByLabelText("Pressure bar buy 52.0 percent sell 48.0 percent")).toBeInTheDocument();
  });

  it("opens the detail drawer on click and passes the stock symbol to the history panel", () => {
    useStockDetailMock.mockReturnValue({
      data: {
        symbol: "INFY",
        exchange: "NSE",
        avg_volume_20d: 150000,
        pivot_levels: null,
        days: [],
      },
      loading: false,
      error: null,
    });
    useLiveMarketDataMock.mockReturnValue({
      ltp: 105,
      averagePrice: 100,
      changePercent: 2.25,
      open: 101,
      high: 106,
      low: 99,
      volume: 240000,
      buyQuantity: 700000,
      sellQuantity: 300000,
      avgVol20d: 120000,
    });

    render(
      <LiveMarketWidget
        symbol="NSE:INFY"
        mode="wide"
        snapshot={{
          symbol: "INFY",
          ltp: 104,
          average_price: 99,
          change_percent: 1.2,
          day_open: 100,
          day_high: 106,
          day_low: 99,
          volume: 200000,
          previous_day_volume: 120000,
          updated_at: "2026-06-25",
        }}
      />,
    );

    fireEvent.click(screen.getByLabelText("Open live detail for NSE:INFY"));

    expect(screen.getByText("INFY Live Detail")).toBeInTheDocument();
    expect(screen.getByTestId("trade-market-history-panel")).toBeInTheDocument();
    expect(tradeMarketHistoryPanelMock).toHaveBeenCalled();
    expect(tradeMarketHistoryPanelMock.mock.calls.at(-1)?.[0]).toEqual(
      expect.objectContaining({
        symbol: "INFY",
        defaultExpanded: true,
        showToggle: false,
      }),
    );
  });
});
