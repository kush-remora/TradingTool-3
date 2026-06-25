import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { renderLiveMarketCell, resolveMarketChangePercent } from "./liveMarketCell";

const getCachedLiveMarketDataMock = vi.fn();

vi.mock("../hooks/useLiveMarketData", () => ({
  getCachedLiveMarketData: (...args: unknown[]) => getCachedLiveMarketDataMock(...args),
}));

vi.mock("./LiveMarketWidget", () => ({
  LiveMarketWidget: ({
    symbol,
    fallbackLtp,
    fallbackChangePercent,
    showDetails,
  }: {
    symbol: string;
    fallbackLtp?: number | null;
    fallbackChangePercent?: number | null;
    showDetails?: boolean;
  }) => (
    <div
      data-testid="live-market-widget"
      data-symbol={symbol}
      data-ltp={fallbackLtp == null ? "" : String(fallbackLtp)}
      data-change={fallbackChangePercent == null ? "" : String(fallbackChangePercent)}
      data-show-details={showDetails ? "true" : "false"}
    />
  ),
}));

describe("renderLiveMarketCell", () => {
  it("prefers live cached percent change for sorting", () => {
    getCachedLiveMarketDataMock.mockReturnValueOnce({ changePercent: 1.85 });

    expect(
      resolveMarketChangePercent(
        "INFY",
        { symbol: "INFY", ltp: 1500, change_percent: -0.25, day_open: null, day_high: null, day_low: null, volume: null, updated_at: "2026-06-25" },
        -0.5,
      ),
    ).toBe(1.85);
  });

  it("falls back to snapshot and row values when live data is absent", () => {
    getCachedLiveMarketDataMock.mockReturnValue(null);

    expect(
      resolveMarketChangePercent(
        "INFY",
        { symbol: "INFY", ltp: 1500, change_percent: -0.25, day_open: null, day_high: null, day_low: null, volume: null, updated_at: "2026-06-25" },
        -0.5,
      ),
    ).toBe(-0.25);

    expect(resolveMarketChangePercent("INFY", null, -0.5)).toBe(-0.5);
  });

  it("uses the shared live market widget contract", () => {
    render(
      renderLiveMarketCell({
        symbol: "INFY",
        fallbackLtp: 1520.5,
        fallbackChangePercent: 1.25,
        showDetails: false,
      }),
    );

    const widget = screen.getByTestId("live-market-widget");
    expect(widget).toHaveAttribute("data-symbol", "NSE:INFY");
    expect(widget).toHaveAttribute("data-ltp", "1520.5");
    expect(widget).toHaveAttribute("data-change", "1.25");
    expect(widget).toHaveAttribute("data-show-details", "false");
  });
});
