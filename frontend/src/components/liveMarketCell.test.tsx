import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { renderLiveMarketCell } from "./liveMarketCell";

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
