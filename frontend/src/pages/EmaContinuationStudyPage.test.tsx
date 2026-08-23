import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { EmaContinuationStudyPage } from "./EmaContinuationStudyPage";
import type { EmaContinuationStudyResponse, EmaContinuationWatchlistStudyResponse } from "../types";

const runMock = vi.fn();
const getJsonMock = vi.hoisted(() => vi.fn());
const studyDataMock = vi.hoisted<{ current: EmaContinuationStudyResponse | null }>(() => ({ current: null }));
const watchlistDataMock = vi.hoisted<{ current: EmaContinuationWatchlistStudyResponse | null }>(() => ({ current: null }));

vi.mock("../hooks/useEmaContinuationStudy", () => ({
  useEmaContinuationStudy: () => ({ data: studyDataMock.current, loading: false, error: null, run: runMock }),
}));

const runWatchlistMock = vi.fn();
const clearWatchlistMock = vi.fn();
vi.mock("../hooks/useEmaContinuationWatchlistStudy", () => ({
  useEmaContinuationWatchlistStudy: () => ({ data: watchlistDataMock.current, loading: false, error: null, run: runWatchlistMock, clear: clearWatchlistMock }),
}));

vi.mock("./EmaContinuationChart", () => ({
  EmaContinuationChart: () => <div data-testid="ema-chart" />,
}));

vi.mock("../utils/api", () => ({ getJson: getJsonMock }));

const studyResponse: EmaContinuationStudyResponse = {
  symbol: "NETWEB",
  watchlistKey: "leaders",
  testedFromDate: "2026-01-01",
  testedToDate: "2026-08-21",
  warmupSessions: 260,
  minimumBaseSessions: 5,
  maximumBaseSessions: 15,
  maximumBaseWidthPct: 12,
  atrPeriodSessions: 14,
  volumeLookbackSessions: 20,
  minimumVolumeRatio: 1.5,
  entryRule: "EMA cross creates the setup. Buy after base breakout.",
  baseRule: "Base uses 5–15 sessions and must be no wider than 12%.",
  summary: { emaCrossCount: 1, baseBreakoutCount: 1, latestSignalDate: "2026-08-19", latestSignalType: "BASE_BREAKOUT" },
  candles: [{ date: "2026-08-21", open: 5400, high: 5650, low: 5350, close: 5600, volume: 1000, closeNearHighPct: 83.33, closeAboveEma20Atr: 1.25, volumeVs20SessionAverageRatio: 1.2, volumeConfirmed: false, ema10: 5200, ema20: 5000, sma200: 3800, emaProximityPct: 7.69, emaCross: true, baseBreakout: false }],
  signals: [
    { type: "EMA_CROSS", date: "2026-08-05", setupDate: "2026-08-05", baseStartDate: null, baseEndDate: null, baseHigh: null, baseLow: null, baseWidthPct: null, close: 5000, ema10: 4800, ema20: 4700, sma200: 3700, closeNearHighPct: 80, closeAboveEma20Atr: 0.8, volumeVs20SessionAverageRatio: 1.8, volumeConfirmed: true, entryDate: "2026-08-06", entryPrice: 5020, daysTo5PctTarget: 2, daysTo10PctTarget: 6, daysTo20PctTarget: null },
    { type: "BASE_BREAKOUT", date: "2026-08-13", setupDate: "2026-08-05", baseStartDate: "2026-08-06", baseEndDate: "2026-08-12", baseHigh: 5175, baseLow: 4700, baseWidthPct: 10.1, close: 5181, ema10: 4800, ema20: 4600, sma200: 3700, closeNearHighPct: 76, closeAboveEma20Atr: 1.1, volumeVs20SessionAverageRatio: 1.8, volumeConfirmed: true, entryDate: "2026-08-14", entryPrice: 5200, daysTo5PctTarget: 3, daysTo10PctTarget: 8, daysTo20PctTarget: null },
  ],
  targetPerformance: [
    { targetPct: 5, observations: 1, hitCount: 1, hitRatePct: 100, averageDaysToHit: 3 },
    { targetPct: 10, observations: 1, hitCount: 1, hitRatePct: 100, averageDaysToHit: 8 },
    { targetPct: 20, observations: 1, hitCount: 0, hitRatePct: 0, averageDaysToHit: null },
  ],
};

describe("EmaContinuationStudyPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    studyDataMock.current = null;
    watchlistDataMock.current = null;
    getJsonMock.mockImplementation((path: string) => {
      if (path.includes("members")) return Promise.resolve([{ instrument_token: 123, trading_symbol: "NETWEB", company_name: "Netweb Technologies" }]);
      if (path.includes("watchlists")) return Promise.resolve({ options: [{ value: "leaders", label: "Leaders", count: 2 }] });
      return Promise.resolve({ symbol: "NETWEB", price: 5600, state: "ABOVE_BOTH_EMAS", latestCompletedCandleDate: "2026-08-21", isLiveQuote: false, ema10: 5200, ema20: 5000, sma200: 3800, closeNearHighPct: 83.33, closeAboveEma20Atr: 1.25, distanceToEma10Pct: 7.69, distanceToEma20Pct: 12, distanceToSma200Pct: 47, open: 5400, high: 5650, low: 5350, volume: 1000, changePct: 1, asOfDate: "2026-08-22", updatedAt: null });
    });
  });

  it("loads members after watchlist selection and runs one stock study", async () => {
    render(<EmaContinuationStudyPage />);

    fireEvent.mouseDown(await screen.findByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("Leaders (2)"));
    fireEvent.mouseDown(await screen.findByRole("combobox", { name: "Stock" }));
    fireEvent.click(await screen.findByText(/NETWEB ·/));
    fireEvent.click(screen.getByRole("button", { name: /Run Study · NETWEB/i }));

    await waitFor(() => expect(runMock).toHaveBeenCalledWith({ watchlistKey: "leaders", symbol: "NETWEB", instrumentToken: 123 }));
  });

  it("runs the complete selected watchlist", async () => {
    render(<EmaContinuationStudyPage />);

    fireEvent.mouseDown(await screen.findByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("Leaders (2)"));
    fireEvent.click(screen.getByRole("button", { name: "Run Complete Watchlist" }));

    await waitFor(() => expect(runWatchlistMock).toHaveBeenCalledWith({ watchlistKey: "leaders" }));
  });

  it("renders watchlist results separately from the selected stock chart", () => {
    watchlistDataMock.current = {
      watchlistKey: "leaders",
      testedFromDate: "2026-01-01",
      testedToDate: "2026-08-21",
      stockCount: 2,
      analyzedCount: 1,
      rows: [
        { symbol: "NETWEB", companyName: "Netweb Technologies", instrumentToken: 123, status: "ANALYZED", errorMessage: null, emaCrossCount: 1, baseBreakoutCount: 2, totalEntryCount: 3, latestSignalDate: "2026-08-19", latestSignalType: "BASE_BREAKOUT" },
        { symbol: "OTHER", companyName: "Other Limited", instrumentToken: 456, status: "ERROR", errorMessage: "Insufficient warm-up history.", emaCrossCount: 0, baseBreakoutCount: 0, totalEntryCount: 0, latestSignalDate: null, latestSignalType: null },
      ],
    };

    render(<EmaContinuationStudyPage />);

    expect(screen.getByText("Watchlist results · 1/2 analyzed")).toBeInTheDocument();
    expect(screen.getByText("Netweb Technologies")).toBeInTheDocument();
    expect(screen.queryByTestId("ema-chart")).not.toBeInTheDocument();
  });

  it("renders chart and signals from the study response", async () => {
    studyDataMock.current = studyResponse;
    render(<EmaContinuationStudyPage />);

    expect(screen.getByTestId("ema-chart")).toBeInTheDocument();
    expect(screen.getByText("Signals (2)")).toBeInTheDocument();
    expect(screen.getByText("Base breakout")).toBeInTheDocument();
  });

  it("exposes sorting and filtering controls for every table column", () => {
    studyDataMock.current = studyResponse;
    const { container } = render(<EmaContinuationStudyPage />);

    expect(container.querySelectorAll("th.ant-table-column-has-sorters")).toHaveLength(20);
    expect(container.querySelectorAll(".ant-table-filter-trigger").length).toBeGreaterThanOrEqual(20);
  });

  it("cleans up the current-session refresh timer on unmount", () => {
    studyDataMock.current = studyResponse;
    const intervalSpy = vi.spyOn(window, "setInterval");
    const clearSpy = vi.spyOn(window, "clearInterval");
    const { unmount } = render(<EmaContinuationStudyPage />);

    unmount();

    expect(intervalSpy).toHaveBeenCalledWith(expect.any(Function), 5 * 60 * 1000);
    const intervalResult = intervalSpy.mock.results[intervalSpy.mock.results.length - 1]?.value;
    expect(clearSpy).toHaveBeenCalledWith(intervalResult);
    intervalSpy.mockRestore();
    clearSpy.mockRestore();
  });
});
