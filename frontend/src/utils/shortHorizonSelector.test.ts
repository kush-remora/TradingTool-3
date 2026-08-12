import { describe, expect, it } from "vitest";
import type { WeeklyPriceWatchlistRow } from "../types";
import {
  buildShortHorizonCoreRows,
  buildShortHorizonStockRow,
  buildShortHorizonShortlistRows,
  calculateClosePositionPct,
  calculateShortHorizonShortlistSize,
  classifyClosePosition,
  filterShortHorizonRowsByEvidenceGate,
  filterShortHorizonRowsByShortlistGuards,
} from "./shortHorizonSelector";

describe("shortHorizonSelector", () => {
  it("counts a five-session target only from completed future sessions", () => {
    const row = buildRow(26, (index) => index === 10
      ? { close: 100 }
      : index === 12 ? { high: 106 } : undefined);
    const result = buildShortHorizonStockRow(row);

    expect(result.eligibleDayCount).toBe(20);
    expect(result.successfulDayCount).toBe(1);
    expect(result.successfulDays[0]).toMatchObject({ date: "2026-07-11", movePct: 6 });
  });

  it("explains where successful starting days closed in their daily range", () => {
    const row = buildRow(26, (index) => index === 10
      ? { high: 110, low: 90, close: 109 }
      : index === 12 ? { high: 115 } : undefined);
    const result = buildShortHorizonStockRow(row);

    expect(result.successCloseBuckets.HIGH).toBe(result.successfulDayCount);
    expect(result.successCloseBuckets.MIDDLE).toBe(0);
    expect(result.successCloseBuckets.LOW).toBe(0);
  });

  it("keeps flat candles in the middle instead of inventing strength", () => {
    const day = { date: "2026-08-01", open: 100, high: 100, low: 100, close: 100, volume: 1, deliveryPercentage: null };

    expect(calculateClosePositionPct(day)).toBe(50);
    expect(classifyClosePosition(50)).toBe("MIDDLE");
  });

  it("calculates recent high pullback and the largest recent volume bar", () => {
    const row = buildRow(30, (index) => ({
      high: index === 22 ? 150 : 102,
      close: index === 29 ? 140 : 101,
      volume: index === 27 ? 400 : 100,
      open: index === 27 ? 110 : 100,
    }));
    const result = buildShortHorizonStockRow(row);

    expect(result.recentHigh).toBe(150);
    expect(result.recentHighDate).toBe("2026-07-23");
    expect(result.pullbackFromRecentHighPct).toBeCloseTo(-6.666, 2);
    expect(result.recentVolumeMultiple).toBe(4);
    expect(result.recentVolumeDate).toBe("2026-07-28");
    expect(result.recentVolumeDirection).toBe("DOWN");
  });

  it("calculates current five-day and twenty-day moves from prior closes", () => {
    const row = buildRow(30, (index) => {
      if (index === 9) return { low: 80, close: 90 };
      if (index === 24) return { close: 100 };
      if (index === 29) return { close: 120 };
      return undefined;
    });

    const result = buildShortHorizonStockRow(row);

    expect(result.currentFiveDayMovePct).toBeCloseTo(20, 5);
    expect(result.currentTwentyDayMovePct).toBeCloseTo(33.333, 2);
  });

  it("counts target successes in the most recent six eligible days", () => {
    const row = buildRow(26, (index) => index === 15 || index === 17
      ? { close: 100 }
      : index === 16 || index === 18 ? { high: 106 } : undefined);

    const result = buildShortHorizonStockRow(row);

    expect(result.recentEligibleDayCount).toBe(6);
    expect(result.recentSuccessfulDayCount).toBe(2);
    expect(result.recentSuccessRatePct).toBeCloseTo(33.33, 2);
  });

  it("calculates an adaptive shortlist size with a cap of twenty per rule", () => {
    expect(calculateShortHorizonShortlistSize(10)).toBe(2);
    expect(calculateShortHorizonShortlistSize(50)).toBe(10);
    expect(calculateShortHorizonShortlistSize(100)).toBe(20);
    expect(calculateShortHorizonShortlistSize(200)).toBe(20);
  });

  it("rejects rows below either minimum evidence threshold", () => {
    const baseRow = buildShortHorizonStockRow(buildRow(26));

    expect(filterShortHorizonRowsByEvidenceGate([{
      ...baseRow,
      successfulDayCount: 2,
      recentSuccessfulDayCount: 6,
    }])).toHaveLength(0);
    expect(filterShortHorizonRowsByEvidenceGate([{
      ...baseRow,
      successfulDayCount: 5,
      recentSuccessfulDayCount: 1,
    }])).toHaveLength(0);
    expect(filterShortHorizonRowsByEvidenceGate([{
      ...baseRow,
      successfulDayCount: 5,
      recentSuccessfulDayCount: 2,
    }])).toHaveLength(1);
  });

  it("removes stocks more than ten percent below the recent high", () => {
    const baseRow = {
      ...buildShortHorizonStockRow(buildRow(26)),
      successfulDayCount: 5,
      recentSuccessfulDayCount: 2,
    };

    expect(filterShortHorizonRowsByShortlistGuards([{
      ...baseRow,
      pullbackFromRecentHighPct: -9.9,
    }])).toHaveLength(1);
    expect(filterShortHorizonRowsByShortlistGuards([{
      ...baseRow,
      pullbackFromRecentHighPct: -10,
    }])).toHaveLength(1);
    expect(filterShortHorizonRowsByShortlistGuards([{
      ...baseRow,
      pullbackFromRecentHighPct: -10.1,
    }])).toHaveLength(0);
  });

  it("rejects only a three-close decline that breaks the previous five-session floor", () => {
    const buildWeaknessRow = (latestClose: number) => buildShortHorizonStockRow(buildRow(26, (index) => {
      if (index >= 20 && index <= 24) return { low: 95, close: index === 23 ? 103 : index === 24 ? 101 : 100 };
      if (index === 25) return { low: 90, high: 102, close: latestClose };
      return undefined;
    }));
    const controlledPullback = buildWeaknessRow(99);
    const breakdown = buildWeaknessRow(94);

    expect(controlledPullback.lastThreeClosesDeclining).toBe(true);
    expect(controlledPullback.latestCloseBelowPreviousFiveSessionLow).toBe(false);
    expect(breakdown.latestCloseBelowPreviousFiveSessionLow).toBe(true);
    expect(filterShortHorizonRowsByShortlistGuards([{
      ...controlledPullback,
      successfulDayCount: 5,
      recentSuccessfulDayCount: 2,
      pullbackFromRecentHighPct: -1,
    }])).toHaveLength(1);
    expect(filterShortHorizonRowsByShortlistGuards([{
      ...breakdown,
      successfulDayCount: 5,
      recentSuccessfulDayCount: 2,
      pullbackFromRecentHighPct: -1,
    }])).toHaveLength(0);
  });

  it("unites the adaptive 20-day and recent-six rankings", () => {
    const rows = Array.from({ length: 100 }, (_, index) => ({
      ...buildShortHorizonStockRow(buildRow(26)),
      key: `STOCK-${index}`,
      symbol: `STOCK-${index}`,
      successfulDayCount: 120 - index,
      recentSuccessfulDayCount: index >= 80 ? 6 : 2,
    }));

    const shortlistRows = buildShortHorizonShortlistRows(rows);

    expect(shortlistRows).toHaveLength(40);
    expect(shortlistRows[0].symbol).toBe("STOCK-0");
    expect(shortlistRows[19].symbol).toBe("STOCK-19");
    expect(shortlistRows[20].symbol).toBe("STOCK-80");
    expect(shortlistRows.at(-1)?.symbol).toBe("STOCK-99");
  });

  it("keeps the shortlist limit based on the original watchlist size after filtering", () => {
    const rows = Array.from({ length: 100 }, (_, index) => ({
      ...buildShortHorizonStockRow(buildRow(26)),
      key: `STOCK-${index}`,
      symbol: `STOCK-${index}`,
      successfulDayCount: index < 20 ? 100 - index : 5,
      recentSuccessfulDayCount: index >= 20 && index < 36 ? 6 : 2,
      pullbackFromRecentHighPct: index < 36 ? -1 : -20,
    }));

    const shortlistRows = buildShortHorizonShortlistRows(rows);

    expect(shortlistRows).toHaveLength(36);
  });

  it("keeps only stocks selected by both rankings in the core list", () => {
    const rows = Array.from({ length: 100 }, (_, index) => ({
      ...buildShortHorizonStockRow(buildRow(26)),
      key: `STOCK-${index}`,
      symbol: `STOCK-${index}`,
      successfulDayCount: index < 20 ? 100 - index : 5,
      recentSuccessfulDayCount: index >= 10 && index < 30 ? 6 : 2,
      distanceFromFiftyTwoWeekHighPct: -index,
    }));

    const coreRows = buildShortHorizonCoreRows(rows);

    expect(coreRows).toHaveLength(10);
    expect(coreRows[0].symbol).toBe("STOCK-10");
    expect(coreRows.at(-1)?.symbol).toBe("STOCK-19");
  });
});

function buildRow(
  dayCount: number,
  override: (index: number) => Partial<WeeklyPriceWatchlistRow["days"][number]> | undefined = () => undefined,
): WeeklyPriceWatchlistRow {
  return {
    symbol: "ABC",
    companyName: "ABC Limited",
    instrumentToken: 123,
    days: Array.from({ length: dayCount }, (_, index) => ({
      date: `2026-07-${String(index + 1).padStart(2, "0")}`,
      open: 100,
      high: index === 0 ? 106 : 102,
      low: 98,
      close: index === 0 ? 100 : 101,
      volume: 1,
      deliveryPercentage: null,
      ...override(index),
    })),
  };
}
