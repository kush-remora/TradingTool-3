import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { RsiMomentumPage } from "./RsiMomentumPage";

const useRsiMomentumMock = vi.fn();
const useLiveMarketDataMock = vi.fn();

vi.mock("../hooks/useRsiMomentum", () => ({
  useRsiMomentum: () => useRsiMomentumMock(),
}));

vi.mock("../hooks/useLiveMarketData", () => ({
  useLiveMarketData: (...args: unknown[]) => useLiveMarketDataMock(...args),
}));

vi.mock("../components/LiveMarketWidget", () => ({
  LiveMarketWidget: () => <div data-testid="live-market-widget">Live</div>,
}));

describe("RsiMomentumPage", () => {
  it("renders profile tabs and board columns with skip visibility", async () => {
    const refreshMock = vi.fn();

    useRsiMomentumMock.mockReturnValue({
      loading: false,
      refreshing: false,
      refreshingProfileId: null,
      error: null,
      refresh: refreshMock,
      data: {
        partialSuccess: false,
        errors: [],
        profiles: [
          {
            profileId: "largemidcap250",
            profileLabel: "LargeMidcap250",
            available: true,
            stale: false,
            message: null,
            config: {
              enabled: true,
              profileId: "largemidcap250",
              profileLabel: "LargeMidcap250",
              baseUniversePreset: "NIFTY_LARGEMIDCAP_250",
              candidateCount: 20,
              boardDisplayCount: 40,
              replacementPoolCount: 40,
              holdingCount: 10,
              rsiPeriods: [22, 44, 66],
              minAverageTradedValue: 10,
              maxExtensionAboveSma20ForNewEntry: 0.2,
              maxExtensionAboveSma20ForNewEntryPct: 20,
              maxExtensionAboveSma20ForSkipNewEntry: 0.3,
              maxExtensionAboveSma20ForSkipNewEntryPct: 30,
              rebalanceDay: "FRIDAY",
              rebalanceTime: "15:40",
              rsiCalibrationRunAt: null,
              rsiCalibrationMethod: null,
              rsiCalibrationSampleRange: null,
            },
            runAt: "2026-04-11T00:00:00.000Z",
            asOfDate: "2026-04-11",
            resolvedUniverseCount: 250,
            eligibleUniverseCount: 180,
            topCandidates: [
              {
                rank: 1,
                symbol: "AAA",
                companyName: "AAA LTD",
                instrumentToken: 1,
                avgRsi: 91,
                rsi22: 92,
                rsi44: 91,
                rsi66: 90,
                close: 126,
                sma20: 100,
                extensionAboveSma20Pct: 26,
                buyZoneLow10w: 101,
                buyZoneHigh10w: 121,
                lowestRsi50d: 42,
                highestRsi50d: 84,
                avgTradedValueCr: 120,
                inBaseUniverse: true,
                inWatchlist: false,
                entryBlocked: true,
                entryBlockReason: "PRICE_EXTENSION_ABOVE_SMA20",
                entryAction: "SKIP",
                targetWeightPct: null,
              },
            ],
            holdings: [],
            rebalance: {
              entries: ["AAA"],
              exits: ["ZZZ"],
              holds: ["CCC"],
            },
            diagnostics: {
              baseUniverseCount: 250,
              watchlistCount: 10,
              watchlistAdditionsCount: 5,
              unresolvedSymbols: [],
              insufficientHistorySymbols: [],
              illiquidSymbols: [],
              backfilledSymbols: [],
              failedSymbols: [],
            },
          },
          {
            profileId: "smallcap250",
            profileLabel: "Smallcap250",
            available: true,
            stale: false,
            message: null,
            config: {
              enabled: true,
              profileId: "smallcap250",
              profileLabel: "Smallcap250",
              baseUniversePreset: "NIFTY_SMALLCAP_250",
              candidateCount: 20,
              boardDisplayCount: 40,
              replacementPoolCount: 40,
              holdingCount: 10,
              rsiPeriods: [22, 44, 66],
              minAverageTradedValue: 10,
              maxExtensionAboveSma20ForNewEntry: 0.2,
              maxExtensionAboveSma20ForNewEntryPct: 20,
              maxExtensionAboveSma20ForSkipNewEntry: 0.3,
              maxExtensionAboveSma20ForSkipNewEntryPct: 30,
              rebalanceDay: "FRIDAY",
              rebalanceTime: "15:40",
              rsiCalibrationRunAt: null,
              rsiCalibrationMethod: null,
              rsiCalibrationSampleRange: null,
            },
            runAt: "2026-04-11T00:00:00.000Z",
            asOfDate: "2026-04-11",
            resolvedUniverseCount: 250,
            eligibleUniverseCount: 170,
            topCandidates: [],
            holdings: [],
            rebalance: { entries: [], exits: [], holds: [] },
            diagnostics: {
              baseUniverseCount: 250,
              watchlistCount: 10,
              watchlistAdditionsCount: 5,
              unresolvedSymbols: [],
              insufficientHistorySymbols: [],
              illiquidSymbols: [],
              backfilledSymbols: [],
              failedSymbols: [],
            },
          },
        ],
      },
    });
    useLiveMarketDataMock.mockReturnValue({
      buyQuantity: 120000,
      sellQuantity: 90000,
      pressureSide: "BUYERS_AGGRESSIVE",
    });

    render(<RsiMomentumPage />);

    expect(screen.getByRole("tab", { name: "LargeMidcap250" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Smallcap250" })).toBeInTheDocument();
    expect(screen.getAllByText("Current Price").length).toBeGreaterThan(0);
    expect(screen.getAllByText("SMA20 (20-day avg)").length).toBeGreaterThan(0);
    expect(screen.getAllByText("% Above SMA20").length).toBeGreaterThan(0);
    expect(screen.getAllByText("SKIP").length).toBeGreaterThan(0);
    expect(screen.getByText("₹126.00")).toBeInTheDocument();
    expect(screen.getByText("26.00%")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /Retry This Profile/i }));
    expect(refreshMock).toHaveBeenCalledWith("largemidcap250");
  });
});
