import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { LiveMarketDetailDrawer } from "./LiveMarketDetailDrawer";

const useStockDetailMock = vi.fn();
const tradeMarketHistoryPanelMock = vi.fn();

vi.mock("../hooks/useStockDetail", () => ({
  useStockDetail: (...args: unknown[]) => useStockDetailMock(...args),
}));

vi.mock("./TradeMarketHistoryPanel", () => ({
  TradeMarketHistoryPanel: (props: unknown) => {
    tradeMarketHistoryPanelMock(props);
    return <div data-testid="trade-market-history-panel" />;
  },
}));

describe("LiveMarketDetailDrawer", () => {
  it("uses stock-detail avg volume fallback and renders pivot levels", () => {
    useStockDetailMock.mockReturnValue({
      data: {
        symbol: "AUTOAXLES",
        exchange: "NSE",
        avg_volume_20d: 456789,
        pivot_levels: {
          pivot: 3049.51,
          r1: 3077.88,
          r2: 3122.11,
          r3: 3150.48,
          s1: 3005.28,
          s2: 2976.91,
          s3: 2932.68,
        },
        days: [],
      },
      loading: false,
      error: null,
    });

    render(
      <LiveMarketDetailDrawer
        symbol="NSE:AUTOAXLES"
        open
        onClose={() => {}}
        snapshot={{
          symbol: "AUTOAXLES",
          ltp: 3000,
          average_price: 1810,
          change_percent: 0.5,
          day_open: 1750,
          day_high: 1860,
          day_low: 1740,
          volume: 12000,
          previous_day_volume: 2000,
          updated_at: "2026-06-25",
        }}
        liveData={{
          symbol: "NSE:AUTOAXLES",
          instrumentToken: 0,
          ltp: 3009.4,
          changePercent: 0.87,
          open: 1762,
          high: 1853.2,
          low: 1762,
          volume: 15900,
          averagePrice: 1820.2,
          buyQuantity: 12300,
          sellQuantity: 7800,
          buyPressurePct: null,
          sellPressurePct: null,
          buyerDominancePass: null,
          pressureSide: "NEUTRAL",
          avgVol20d: null,
          volumeHeat: null,
          updatedAt: 0,
        }}
      />,
    );

    expect(useStockDetailMock).toHaveBeenCalledWith("AUTOAXLES", 10);
    expect(screen.getByText("20D Avg Vol:")).toBeInTheDocument();
    expect(screen.getByText("4.57 L")).toBeInTheDocument();
    expect(screen.getByText("Support and Resistance")).toBeInTheDocument();
    expect(screen.getByText("PIVOT 3,049.51")).toBeInTheDocument();
    expect(screen.getByText("PRICE 3,009.40")).toBeInTheDocument();
    expect(screen.getByText("3,150.48")).toBeInTheDocument();
    expect(screen.getByText("3,005.28")).toBeInTheDocument();
    expect(screen.getByTestId("trade-market-history-panel")).toBeInTheDocument();
  });
});
