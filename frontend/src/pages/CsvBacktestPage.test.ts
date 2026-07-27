import { describe, expect, it } from "vitest";
import type { CsvBacktestTradeResult } from "../types";
import {
  buildCsvBacktestTableRows,
  buildSectorFilterOptions,
  calculateSlOutcomeSummary,
  formatBreakoutSpan,
  matchesMaximumV2RunPct,
  matchesSelectedSectors,
  matchesSectorFilter,
} from "./CsvBacktestPage";

const buildTrade = (
  sector: string,
  overrides: Partial<CsvBacktestTradeResult> = {},
): CsvBacktestTradeResult => ({
  symbol: "INFY",
  instrumentToken: 408065,
  marketCapName: "Large Cap",
  sector,
  signalDate: "2026-07-01",
  entryStrategy: "NEXT_DAY_OPEN",
  breakoutLevel: 1500,
  breakoutSpanSessions: 120,
  breakoutSpanIsLowerBound: false,
  breakoutDayMovePct: 2.15,
  breakoutDayDeliveryPct: 54.2,
  priorFiveDaysMaxDeliveryPct: 63.8,
  entryDate: "2026-07-02",
  entryPrice: 1510,
  firstFiveDaysLowestPrice: 1490,
  firstFiveDaysDropAmount: 20,
  firstFiveDaysDropPct: 1.32,
  firstThreeDaysRedCandleCount: 1,
  v2MaxPreBreakoutVolumeRatio: null,
  v2FailedResistanceAttempts: null,
  v2RecentRunBasePrice: null,
  v2MoveFromRecentBasePct: null,
  exitDate: "2026-07-10",
  exitPrice: 1600,
  profitLossPct: 5.96,
  daysHeld: 6,
  slHit: false,
  isOpen: false,
  ...overrides,
});

describe("CSV backtest sector filter", () => {
  it("formats exact and lower-bound breakout spans", () => {
    expect(formatBreakoutSpan(120, false)).toBe("120 days");
    expect(formatBreakoutSpan(500, true)).toBe("500+ days");
    expect(formatBreakoutSpan(null, false)).toBe("-");
  });

  it("builds distinct sorted options from the displayed sector values", () => {
    const trades = [
      buildTrade("Pharma"),
      buildTrade("IT"),
      buildTrade("Pharma"),
    ];

    expect(buildSectorFilterOptions(trades)).toEqual([
      { text: "IT", value: "IT" },
      { text: "Pharma", value: "Pharma" },
    ]);
  });

  it("matches only trades in the selected sector", () => {
    expect(matchesSectorFilter("IT", buildTrade("IT"))).toBe(true);
    expect(matchesSectorFilter("IT", buildTrade("Pharma"))).toBe(false);
  });

  it("applies the maximum V2 run and excludes trades without a V2 value", () => {
    expect(matchesMaximumV2RunPct(buildTrade("IT"), null)).toBe(true);
    expect(matchesMaximumV2RunPct(
      buildTrade("IT", { v2MoveFromRecentBasePct: null }),
      15,
    )).toBe(false);
    expect(matchesMaximumV2RunPct(
      buildTrade("IT", { v2MoveFromRecentBasePct: 14.9 }),
      15,
    )).toBe(true);
    expect(matchesMaximumV2RunPct(
      buildTrade("IT", { v2MoveFromRecentBasePct: 15.1 }),
      15,
    )).toBe(false);
  });

  it("builds stable table rows without repeated symbol and signal-date entries", () => {
    const tatapower = buildTrade("Power & Utilities", {
      symbol: "TATAPOWER",
      signalDate: "20-03-2026",
    });
    const sunpharma = buildTrade("Healthcare", {
      symbol: "SUNPHARMA",
      signalDate: "06-05-2026",
    });

    expect(buildCsvBacktestTableRows([
      tatapower,
      tatapower,
      sunpharma,
      sunpharma,
    ])).toEqual([
      { ...tatapower, tableRowId: "TATAPOWER-20-03-2026" },
      { ...sunpharma, tableRowId: "SUNPHARMA-06-05-2026" },
    ]);
  });

  it("composes selected sectors with the Maximum V2 Run filter", () => {
    const trades = [
      buildTrade("Power & Utilities", { v2MoveFromRecentBasePct: 14.3 }),
      buildTrade("Chemicals", { v2MoveFromRecentBasePct: 21.6 }),
      buildTrade("Healthcare", { v2MoveFromRecentBasePct: 11.2 }),
    ];

    const result = trades
      .filter((trade) => matchesMaximumV2RunPct(trade, 15))
      .filter((trade) => matchesSelectedSectors(trade, ["Healthcare"]));

    expect(result).toEqual([trades[2]]);
  });

  it("summarizes SL outcomes for the currently filtered rows", () => {
    const filteredTrades = [
      buildTrade("IT", { symbol: "INFY", slHit: false }),
      buildTrade("IT", { symbol: "TCS", slHit: false }),
      buildTrade("IT", { symbol: "WIPRO", slHit: true }),
    ];

    const summary = calculateSlOutcomeSummary(filteredTrades);

    expect(summary).toMatchObject({
      total: 3,
      slYes: 1,
      slNo: 2,
    });
    expect(summary.successRatePct).toBeCloseTo(66.7, 1);
    expect(calculateSlOutcomeSummary([])).toEqual({
      total: 0,
      slYes: 0,
      slNo: 0,
      successRatePct: 0,
    });
  });
});
