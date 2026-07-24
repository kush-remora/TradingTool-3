import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AbsoluteDeliveryBacktestPage } from "./AbsoluteDeliveryBacktestPage";

const useAbsoluteDeliveryBacktestMock = vi.fn();

vi.mock("../hooks/useAbsoluteDeliveryBacktest", () => ({
  useAbsoluteDeliveryBacktest: () => useAbsoluteDeliveryBacktestMock(),
}));

const matchedRow = {
  symbol: "AAA",
  companyName: "AAA LTD",
  tradingDate: "2026-07-24",
  tradedQuantity: 20_000_000,
  deliveryQuantity: 12_000_000,
  deliveryPercentage: 61.0,
  tradedQuantityPassed: true,
  deliveryQuantityPassed: true,
  deliveryPercentagePassed: true,
  matched: true,
  dataStatus: "AVAILABLE" as const,
};

const missingRow = {
  symbol: "BBB",
  companyName: "BBB LTD",
  tradingDate: "2026-07-24",
  tradedQuantity: null,
  deliveryQuantity: null,
  deliveryPercentage: null,
  tradedQuantityPassed: false,
  deliveryQuantityPassed: false,
  deliveryPercentagePassed: false,
  matched: false,
  dataStatus: "NO_RECORD" as const,
};

function backtestData(matchedRows = [matchedRow]) {
  return {
    criteria: {
      minimumTradedQuantityInclusive: 20_000_000,
      minimumDeliveryQuantityExclusive: 10_000_000,
      minimumDeliveryPercentageExclusive: 60.0,
    },
    summary: {
      universeKey: "groww_HIGH_QUALITY",
      fromDate: "2026-01-24",
      toDate: "2026-07-24",
      watchlistSymbolCount: 2,
      tradingDateCount: 1,
      expectedRowCount: 2,
      evaluatedRowCount: 1,
      missingRowCount: 1,
      matchedRowCount: matchedRows.length,
    },
    matchedRows,
    allRows: [matchedRow, missingRow],
  };
}

describe("AbsoluteDeliveryBacktestPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders summary and both six-month audit tables", async () => {
    const loadBacktest = vi.fn().mockResolvedValue(undefined);
    useAbsoluteDeliveryBacktestMock.mockReturnValue({
      data: backtestData(),
      loading: false,
      error: null,
      loadBacktest,
    });

    render(<AbsoluteDeliveryBacktestPage />);

    await waitFor(() => expect(loadBacktest).toHaveBeenCalledTimes(1));
    expect(screen.getByText("Absolute Delivery Backtest")).toBeInTheDocument();
    expect(screen.getByText("Matches 1")).toBeInTheDocument();
    expect(screen.getByText("AAA")).toBeInTheDocument();
    expect(screen.queryByText("BBB")).not.toBeInTheDocument();

    fireEvent.click(screen.getByText("Entire Watchlist (2)"));

    expect(await screen.findByText("BBB")).toBeInTheDocument();
    expect(screen.getByText("No record")).toBeInTheDocument();
    expect(screen.getAllByText("Fail")).toHaveLength(3);
  });

  it("shows a clear empty state when no event matches", () => {
    useAbsoluteDeliveryBacktestMock.mockReturnValue({
      data: backtestData([]),
      loading: false,
      error: null,
      loadBacktest: vi.fn().mockResolvedValue(undefined),
    });

    render(<AbsoluteDeliveryBacktestPage />);

    expect(
      screen.getByText("No events matched all three absolute delivery conditions."),
    ).toBeInTheDocument();
  });
});
