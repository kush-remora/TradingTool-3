import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { TradeWithTargets } from "../types";
import { TradeJournalTable } from "./TradeJournalTable";

const useStockQuotesMock = vi.fn();
const useLiveMarketDataMock = vi.fn();
const tradeMarketHistoryPanelMock = vi.fn();

vi.mock("../hooks/useStockQuotes", () => ({
  useStockQuotes: (...args: unknown[]) => useStockQuotesMock(...args),
}));

vi.mock("../hooks/useLiveMarketData", () => ({
  useLiveMarketData: (...args: unknown[]) => useLiveMarketDataMock(...args),
}));

vi.mock("./TradeMarketHistoryPanel", () => ({
  TradeMarketHistoryPanel: (props: unknown) => {
    tradeMarketHistoryPanelMock(props);
    return <div data-testid="trade-history-panel" />;
  },
}));

describe("TradeJournalTable", () => {
  it("uses live price for current pnl when available", () => {
    useStockQuotesMock.mockReturnValue({
      quotesBySymbol: {
        NETWEB: {
          symbol: "NETWEB",
          ltp: 3100,
          change_percent: 1,
          day_open: null,
          day_high: null,
          day_low: null,
          volume: null,
          updated_at: "2026-06-25",
        },
      },
      loading: false,
      error: null,
    });
    useLiveMarketDataMock.mockReturnValue({ ltp: 3200 });

    const trades: TradeWithTargets[] = [
      {
        trade: {
          id: 101,
          instrument_token: 1,
          nse_symbol: "NETWEB",
          quantity: 10,
          avg_buy_price: "3070",
          today_low: "3044",
          stop_loss_percent: "2",
          stop_loss_price: "3000",
          notes: "test",
          trade_date: "2026-04-03",
          close_price: null,
          close_date: null,
          created_at: "2026-04-03T00:00:00Z",
          updated_at: "2026-04-03T00:00:00Z",
        },
        gtt_targets: [{ percent: 2, price: "3130", yield_percent: "1.95" }],
        total_invested: "30700",
      },
    ];

    render(
      <TradeJournalTable
        trades={trades}
        onClose={async () => {}}
        onDelete={async () => {}}
      />,
    );

    expect(screen.getByText("+₹1,300 (+4.2%)")).toBeInTheDocument();
  });

  it("passes derived signalConfig to expanded 10D panel", async () => {
    useStockQuotesMock.mockReturnValue({ quotesBySymbol: {}, loading: false, error: null });
    useLiveMarketDataMock.mockReturnValue(null);

    const trades: TradeWithTargets[] = [
      {
        trade: {
          id: 101,
          instrument_token: 1,
          nse_symbol: "NETWEB",
          quantity: 10,
          avg_buy_price: "3070",
          today_low: "3044",
          stop_loss_percent: "2",
          stop_loss_price: "3000",
          notes: "test",
          trade_date: "2026-04-03",
          close_price: null,
          close_date: null,
          created_at: "2026-04-03T00:00:00Z",
          updated_at: "2026-04-03T00:00:00Z",
        },
        gtt_targets: [{ percent: 2, price: "3130", yield_percent: "1.95" }],
        total_invested: "30700",
      },
    ];

    render(
      <TradeJournalTable
        trades={trades}
        onClose={async () => {}}
        onDelete={async () => {}}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: /10D Context/i }));

    await waitFor(() => {
      expect(screen.getByTestId("trade-history-panel")).toBeInTheDocument();
    });

    expect(tradeMarketHistoryPanelMock).toHaveBeenCalled();
    const lastProps = tradeMarketHistoryPanelMock.mock.calls.at(-1)?.[0] as {
      signalConfig?: { entryPrice: number; entryDayLow?: number; stopPrice?: number };
      showSignalLegend?: boolean;
    };

    expect(lastProps.signalConfig).toEqual({
      entryPrice: 3070,
      entryDayLow: 3044,
      stopPrice: 3000,
    });
    expect(lastProps.showSignalLegend).toBe(true);
  });
});
