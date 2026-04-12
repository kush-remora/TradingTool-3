import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { S4VolumeSpikePage } from "./S4VolumeSpikePage";

const useS4VolumeSpikeMock = vi.fn();

vi.mock("../hooks/useS4VolumeSpike", () => ({
  useS4VolumeSpike: () => useS4VolumeSpikeMock(),
}));

vi.mock("../hooks/useStockDetail", () => ({
  useStockDetail: () => ({
    data: {
      symbol: "AAA",
      exchange: "NSE",
      avg_volume_20d: 120000,
      days: [
        {
          date: "2026-04-12",
          open: 120,
          high: 124,
          low: 119,
          close: 123.45,
          volume: 200000,
          daily_change_pct: 2.2,
          rsi14: 68.1,
          vol_ratio: 2.6,
        },
      ],
    },
    loading: false,
    error: null,
  }),
}));

describe("S4VolumeSpikePage", () => {
  it("renders profile tabs, dashboard stats, and refresh action", () => {
    const refreshMock = vi.fn();
    useS4VolumeSpikeMock.mockReturnValue({
      loading: false,
      refreshing: false,
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
              candidateCount: 25,
              minAverageTradedValue: 10,
              minHistoryBars: 45,
              todayVolumeRatioThreshold: 2,
              recent3dAvgVolumeRatioThreshold: 1.8,
              recent5dMaxVolumeRatioThreshold: 2,
              spikePersistenceThreshold: 2,
              breakoutPriceChangePctThreshold: 1.5,
              breakoutReturn3dPctThreshold: 3,
            },
            runAt: "2026-04-12T09:30:00.000Z",
            asOfDate: "2026-04-12",
            resolvedUniverseCount: 250,
            eligibleUniverseCount: 12,
            topCandidates: [
              {
                rank: 1,
                symbol: "AAA",
                companyName: "AAA LTD",
                instrumentToken: 1,
                profileId: "largemidcap250",
                baseUniversePreset: "NIFTY_LARGEMIDCAP_250",
                close: 123.45,
                avgVolume20d: 120000,
                avgTradedValueCr20d: 14.2,
                todayVolumeRatio: 2.6,
                recent3dAvgVolumeRatio: 2.1,
                recent5dMaxVolumeRatio: 2.8,
                spikePersistenceDays5d: 3,
                recent10dAvgVolumeRatio: 1.9,
                elevatedVolumeDays10d: 5,
                todayPriceChangePct: 2.2,
                priceReturn3dPct: 4.5,
                breakoutAbove20dHigh: true,
                indexRank: 84,
                indexSize: 250,
                indexLayer: "Top 100",
                todayVolumeScore: 26,
                recent3dVolumeScore: 17.5,
                persistenceScore: 9,
                priceScore: 15,
                classification: "BUILDUP_BREAKOUT",
                score: 82.4,
              },
            ],
            diagnostics: {
              baseUniverseCount: 250,
              unresolvedSymbols: [],
              insufficientHistorySymbols: [],
              illiquidSymbols: ["ILLQ"],
              disqualifiedSymbols: ["ZZZ"],
              backfilledSymbols: [],
              failedSymbols: [],
            },
          },
          {
            profileId: "smallcap250",
            profileLabel: "Smallcap250",
            available: false,
            stale: true,
            message: "No S4 candidates matched the current filters.",
            config: {
              enabled: true,
              profileId: "smallcap250",
              profileLabel: "Smallcap250",
              baseUniversePreset: "NIFTY_SMALLCAP_250",
              candidateCount: 25,
              minAverageTradedValue: 10,
              minHistoryBars: 45,
              todayVolumeRatioThreshold: 2,
              recent3dAvgVolumeRatioThreshold: 1.8,
              recent5dMaxVolumeRatioThreshold: 2,
              spikePersistenceThreshold: 2,
              breakoutPriceChangePctThreshold: 1.5,
              breakoutReturn3dPctThreshold: 3,
            },
            runAt: null,
            asOfDate: null,
            resolvedUniverseCount: 250,
            eligibleUniverseCount: 0,
            topCandidates: [],
            diagnostics: {
              baseUniverseCount: 250,
              unresolvedSymbols: [],
              insufficientHistorySymbols: [],
              illiquidSymbols: [],
              disqualifiedSymbols: [],
              backfilledSymbols: [],
              failedSymbols: [],
            },
          },
        ],
      },
    });

    render(<S4VolumeSpikePage />);

    expect(screen.getByRole("tab", { name: "LargeMidcap250" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Smallcap250" })).toBeInTheDocument();
    expect(screen.getByText("S4 Candidates")).toBeInTheDocument();
    expect(screen.getByText("Volume Regime View")).toBeInTheDocument();
    expect(screen.getByText("Candidate Drilldown")).toBeInTheDocument();
    expect(screen.getByText("Score Breakdown")).toBeInTheDocument();
    expect(screen.getAllByText("BUILDUP_BREAKOUT").length).toBeGreaterThan(0);
    expect(screen.getByText("84 / 250")).toBeInTheDocument();
    expect(screen.getByText("Top 100")).toBeInTheDocument();
    expect(screen.getByText("10D Volume Regime")).toBeInTheDocument();
    expect(screen.getByText("26.00 / 40")).toBeInTheDocument();
    expect(screen.getAllByText("₹14.20Cr").length).toBeGreaterThan(0);
    expect(screen.getAllByText("2.60x").length).toBeGreaterThan(0);

    fireEvent.click(screen.getByRole("button", { name: /Refresh S4/i }));
    expect(refreshMock).toHaveBeenCalledTimes(1);
  });
});
