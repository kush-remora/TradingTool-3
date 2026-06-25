import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const getJsonMock = vi.fn();
const isIndianEquityMarketOpenMock = vi.fn();

vi.mock("../utils/api", () => ({
  getJson: (...args: unknown[]) => getJsonMock(...args),
}));

vi.mock("../utils/marketHours", () => ({
  isIndianEquityMarketOpen: () => isIndianEquityMarketOpenMock(),
}));

describe("useStockQuotes", () => {
  beforeEach(() => {
    getJsonMock.mockReset();
    isIndianEquityMarketOpenMock.mockReset();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("fetches one last snapshot when the market is closed", async () => {
    isIndianEquityMarketOpenMock.mockReturnValue(false);
    let resolveQuotes: ((value: unknown) => void) | null = null;
    getJsonMock.mockImplementation(() => new Promise((resolve) => {
      resolveQuotes = resolve;
    }));
    const { useStockQuotes } = await import("./useStockQuotes");

    const { result } = renderHook(() => useStockQuotes(["INFY", "TCS"]));

    expect(getJsonMock).toHaveBeenCalledWith("/api/stocks/quotes?symbols=INFY%2CTCS");
    expect(result.current.loading).toBe(true);

    await act(async () => {
      resolveQuotes?.([
        {
          symbol: "INFY",
          ltp: 1520,
          average_price: 1514,
          change_percent: 0.75,
          day_open: 1508,
          day_high: 1525,
          day_low: 1502,
          volume: 2500000,
          previous_day_volume: 2200000,
          updated_at: "2026-06-25T15:30:00+05:30",
        },
      ]);
      await Promise.resolve();
    });

    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeNull();
    expect(result.current.quotesBySymbol).toEqual({
      INFY: {
        symbol: "INFY",
        ltp: 1520,
        average_price: 1514,
        change_percent: 0.75,
        day_open: 1508,
        day_high: 1525,
        day_low: 1502,
        volume: 2500000,
        previous_day_volume: 2200000,
        updated_at: "2026-06-25T15:30:00+05:30",
      },
    });
  });
});
