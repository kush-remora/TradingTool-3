import { describe, expect, it } from "vitest";
import type { WeeklyPriceWatchlistRow } from "../types";
import {
  ACCUMULATION_CLOSE_LOCATION_THRESHOLD_PCT,
  buildAccumulationFilterOneRows,
  buildAccumulationFilterTwoRows,
  buildAccumulationStockRow,
  countRecentBuyingInterestDays,
  countRecentGreenCloseDays,
} from "./accumulationScanner";

function buildRow(
  dayCount: number,
  customize: (index: number) => Partial<WeeklyPriceWatchlistRow["days"][number]> = () => ({}),
): WeeklyPriceWatchlistRow {
  const days = Array.from({ length: dayCount }, (_, index) => ({
    date: new Date(Date.UTC(2026, 6, 1 + index)).toISOString().slice(0, 10),
    open: 100,
    high: 105,
    low: 95,
    close: 100,
    volume: 100,
    deliveryPercentage: null,
    ...customize(index),
  }));

  return {
    symbol: "TEST",
    companyName: "Test Company",
    instrumentToken: 123,
    days,
  };
}

describe("accumulationScanner", () => {
  it("counts the latest 30 sessions and keeps a latest 20-session heatmap", () => {
    const result = buildAccumulationStockRow(buildRow(35, (index) => {
      if (index === 6) return { high: 110, low: 90, close: 104 };
      if (index === 20) return { volume: 50, high: 110, low: 100, close: 104 };
      if (index === 34) return { high: 110, low: 90, close: 106 };
      if (index > 6) return { high: 110, low: 100, close: 104 };
      return {};
    }));

    expect(result.countWindowSessions).toBe(30);
    expect(result.heatmapWindowSessions).toBe(20);
    expect(result.latestFiveDayMovePct).toBeCloseTo(1.923, 2);
    expect(result.latestTwentyDayMovePct).toBeCloseTo(1.923, 2);
    expect(result.buyingInterestCount).toBe(2);
    expect(result.greenCloseCount).toBe(2);
    expect(result.quietMoveCount).toBe(28);
    expect(result.volumeDryUpCount).toBe(1);
    expect(result.volumeEligibleSessionCount).toBe(25);
    expect(result.heatmap[0].date).toBe("2026-07-16");
    expect(result.heatmap.at(-1)?.date).toBe("2026-08-04");
    expect(result.heatmap.find((day) => day.date === "2026-07-21")?.volumeDryUp).toBe(true);
  });

  it("uses the 70% close-location boundary and excludes flat candles", () => {
    const row = buildRow(12, (index) => {
      if (index === 10) return { high: 110, low: 90, close: 104 };
      if (index === 11) return { high: 100, low: 100, close: 100 };
      return {};
    });

    const result = buildAccumulationStockRow(row);
    const boundaryDay = result.heatmap.find((day) => day.date === "2026-07-11");
    const flatDay = result.heatmap.find((day) => day.date === "2026-07-12");

    expect(ACCUMULATION_CLOSE_LOCATION_THRESHOLD_PCT).toBe(70);
    expect(boundaryDay?.closeLocationPct).toBe(70);
    expect(boundaryDay?.buyingInterest).toBe(true);
    expect(flatDay?.closeLocationPct).toBeNull();
    expect(flatDay?.buyingInterest).toBeNull();
  });

  it("compares volume with the prior ten sessions, excluding the current day", () => {
    const result = buildAccumulationStockRow(buildRow(12, (index) => (
      index === 10 ? { volume: 50 } : {}
    )));
    const dryUpDay = result.heatmap.find((day) => day.date === "2026-07-11");

    expect(dryUpDay?.averageVolume10).toBe(100);
    expect(dryUpDay?.volumeDryUp).toBe(true);
    expect(result.heatmap[0].volumeDryUp).toBeNull();
  });

  it("applies Filter 1 using only the three requested accumulation conditions", () => {
    const passingRow = {
      ...buildAccumulationStockRow(buildRow(30)),
      buyingInterestCount: 10,
      quietMoveCount: 8,
      volumeDryUpCount: 6,
    };
    const failingRow = { ...passingRow, quietMoveCount: 7 };

    expect(buildAccumulationFilterOneRows([passingRow])).toEqual([passingRow]);
    expect(buildAccumulationFilterOneRows([failingRow])).toEqual([]);
  });

  it("applies Filter 2 to the latest five sessions from Filter 1", () => {
    const baseRow = {
      ...buildAccumulationStockRow(buildRow(30)),
      buyingInterestCount: 10,
      quietMoveCount: 8,
      volumeDryUpCount: 6,
      latestFiveDayMovePct: 5,
    };
    const passingRow = {
      ...baseRow,
      heatmap: baseRow.heatmap.map((day, index) => ({
        ...day,
        buyingInterest: index >= baseRow.heatmap.length - 3 ? true : day.buyingInterest,
        greenClose: index >= baseRow.heatmap.length - 3 ? true : day.greenClose,
      })),
    };
    const failingRow = {
      ...passingRow,
      heatmap: passingRow.heatmap.map((day, index) => ({
        ...day,
        greenClose: index === passingRow.heatmap.length - 1 || index === passingRow.heatmap.length - 2,
      })),
    };
    const lowMoveRow = { ...passingRow, latestFiveDayMovePct: 4.9 };

    expect(countRecentBuyingInterestDays(passingRow)).toBe(3);
    expect(countRecentGreenCloseDays(passingRow)).toBe(3);
    expect(countRecentGreenCloseDays(failingRow)).toBe(2);
    expect(passingRow.latestFiveDayMovePct).toBe(5);
    expect(buildAccumulationFilterTwoRows([passingRow])).toEqual([passingRow]);
    expect(buildAccumulationFilterTwoRows([failingRow])).toEqual([]);
    expect(buildAccumulationFilterTwoRows([lowMoveRow])).toEqual([]);
  });
});
