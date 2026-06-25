import { renderHook } from "@testing-library/react";
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

  it("does not fetch quotes when the market is closed", async () => {
    isIndianEquityMarketOpenMock.mockReturnValue(false);
    const { useStockQuotes } = await import("./useStockQuotes");

    const { result } = renderHook(() => useStockQuotes(["INFY", "TCS"]));

    expect(getJsonMock).not.toHaveBeenCalled();
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeNull();
    expect(result.current.quotesBySymbol).toEqual({});
  });
});
