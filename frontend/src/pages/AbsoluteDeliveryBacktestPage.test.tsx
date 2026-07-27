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
  closePrice: 240,
  sma50: 215.5,
  sma200: 140.5,
  sma50TwentySessionsAgo: 195.5,
  priceAboveSma50Passed: true,
  sma50AboveSma200Passed: true,
  sma50RisingPassed: true,
  uptrendMatched: true,
  trendDataStatus: "AVAILABLE" as const,
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
  closePrice: null,
  sma50: null,
  sma200: null,
  sma50TwentySessionsAgo: null,
  priceAboveSma50Passed: false,
  sma50AboveSma200Passed: false,
  sma50RisingPassed: false,
  uptrendMatched: false,
  trendDataStatus: "NO_CANDLE" as const,
  matched: false,
  dataStatus: "NO_RECORD" as const,
};

function backtestData(matchedRows = [matchedRow]) {
  return {
    criteria: {
      minimumTradedQuantityInclusive: 20_000_000,
      minimumDeliveryQuantityExclusive: 5_000_000,
      minimumDeliveryPercentageExclusive: 60.0,
      shortSmaPeriod: 50,
      longSmaPeriod: 200,
      shortSmaSlopeLookbackSessions: 20,
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
    const loadGroupings = vi.fn().mockResolvedValue(undefined);
    useAbsoluteDeliveryBacktestMock.mockReturnValue({
      data: backtestData(),
      groupings: [
        { value: "groww_HIGH_QUALITY", count: 2 },
        { value: "nifty_100", count: 100 },
      ],
      loading: false,
      loadingGroupings: false,
      error: null,
      groupingError: null,
      loadGroupings,
      loadBacktest,
    });

    render(<AbsoluteDeliveryBacktestPage />);

    await waitFor(() => expect(loadBacktest).toHaveBeenCalledWith("groww_HIGH_QUALITY"));
    expect(loadGroupings).toHaveBeenCalledTimes(1);
    expect(screen.getByText("Absolute Delivery Backtest")).toBeInTheDocument();
    expect(screen.getByText("Matches 1")).toBeInTheDocument();
    expect(screen.getByText("AAA")).toBeInTheDocument();
    expect(screen.queryByText("BBB")).not.toBeInTheDocument();

    fireEvent.click(screen.getByText("Entire Grouping (2)"));

    expect(await screen.findByText("BBB")).toBeInTheDocument();
    expect(screen.getByText("No record")).toBeInTheDocument();
    expect(screen.getByText("No candle")).toBeInTheDocument();
    expect(screen.getAllByText("Fail")).toHaveLength(6);
    expect(screen.getByText("Uptrend")).toBeInTheDocument();
  });

  it("reruns the audit when another grouping is selected", async () => {
    const loadBacktest = vi.fn().mockResolvedValue(undefined);
    useAbsoluteDeliveryBacktestMock.mockReturnValue({
      data: backtestData(),
      groupings: [
        { value: "groww_HIGH_QUALITY", count: 2 },
        { value: "nifty_100", count: 100 },
      ],
      loading: false,
      loadingGroupings: false,
      error: null,
      groupingError: null,
      loadGroupings: vi.fn().mockResolvedValue(undefined),
      loadBacktest,
    });

    render(<AbsoluteDeliveryBacktestPage />);
    await waitFor(() => expect(loadBacktest).toHaveBeenCalledWith("groww_HIGH_QUALITY"));
    loadBacktest.mockClear();

    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Institutional grouping" }));
    fireEvent.click(await screen.findByText("nifty_100 (100)"));

    await waitFor(() => expect(loadBacktest).toHaveBeenCalledWith("nifty_100"));
  });

  it("shows a clear empty state when no event matches", () => {
    useAbsoluteDeliveryBacktestMock.mockReturnValue({
      data: backtestData([]),
      groupings: [{ value: "groww_HIGH_QUALITY", count: 2 }],
      loading: false,
      loadingGroupings: false,
      error: null,
      groupingError: null,
      loadGroupings: vi.fn().mockResolvedValue(undefined),
      loadBacktest: vi.fn().mockResolvedValue(undefined),
    });

    render(<AbsoluteDeliveryBacktestPage />);

    expect(
      screen.getByText("No events matched all three absolute delivery conditions."),
    ).toBeInTheDocument();
  });
});
