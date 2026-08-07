import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { StockHistoryDownloadPage } from "./StockHistoryDownloadPage";
import type { StockDetailResponse } from "../types";

const useStockDetailMock = vi.fn();

vi.mock("../hooks/useInstrumentSearch", () => ({
  useInstrumentSearch: () => ({
    allInstruments: [{ instrument_token: 1, trading_symbol: "INFY", company_name: "Infosys", exchange: "NSE", instrument_type: "EQ" }],
    loading: false,
    error: null,
  }),
}));

vi.mock("../hooks/useStockDetail", () => ({
  useStockDetail: (...args: unknown[]) => useStockDetailMock(...args),
}));

const detail: StockDetailResponse = {
  symbol: "INFY",
  exchange: "NSE",
  avg_volume_20d: null,
  pivot_levels: null,
  fundamentals: { currentPrice: 102, fiftyTwoWeekLow: null, fiftyTwoWeekHigh: null, sma200: null },
  days: [{ date: "2026-08-05", open: 100, high: 105, low: 98, close: 102, volume: 1000, daily_change_pct: null, rsi14: null, vol_ratio: null }],
  delivery_days: [],
};

describe("StockHistoryDownloadPage", () => {
  beforeEach(() => {
    useStockDetailMock.mockReturnValue({ data: detail, loading: false, error: null });
  });

  it("selects a stock, loads the default three-month period, and enables CSV download", async () => {
    render(<StockHistoryDownloadPage />);

    expect(screen.getByRole("button", { name: "Download CSV" })).toBeDisabled();
    fireEvent.mouseDown(screen.getByLabelText("Stock"));
    fireEvent.click(await screen.findByText("INFY — Infosys"));

    await waitFor(() => expect(useStockDetailMock).toHaveBeenLastCalledWith("INFY", 93));
    expect(screen.getByRole("button", { name: "Download CSV" })).toBeEnabled();
    expect(screen.getByText("INFY · 3 months")).toBeInTheDocument();
  });
});
