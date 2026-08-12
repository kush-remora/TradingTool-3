import { describe, expect, it } from "vitest";
import type { DayDetail } from "../../types";
import { buildCompactDailyRows, buildCompactDeliveryContext, buildCompactThreeWeekFlow, buildCompactWeeklyRows } from "./compactStockReview";

const day = (date: string, low: number, high: number, close: number): DayDetail => ({
  date,
  open: close,
  high,
  low,
  close,
  volume: 1_000,
  daily_change_pct: null,
  rsi14: null,
  vol_ratio: null,
});

describe("buildCompactThreeWeekFlow", () => {
  it("shows weekly low alignment separately from higher-low and higher-high flow", () => {
    const rows = buildCompactDailyRows([
      day("2026-07-13", 99.5, 108, 104),
      day("2026-07-14", 99.8, 110, 106),
      day("2026-07-20", 100, 110, 105),
      day("2026-07-21", 100, 112, 110),
      day("2026-07-27", 100.5, 114, 112),
      day("2026-07-28", 100.8, 116, 115),
      day("2026-08-03", 101, 118, 117),
      day("2026-08-04", 101.5, 120, 119),
    ], []);

    const flow = buildCompactThreeWeekFlow(rows);

    expect(flow?.weeks.map((week) => week.label)).toEqual(["W−3", "W−2", "W−1", "WTD"]);
    expect(flow?.weeks.map((week) => week.lowDate)).toEqual([
      "2026-07-13",
      "2026-07-20",
      "2026-07-27",
      "2026-08-03",
    ]);
    expect(flow?.floorAligned).toBe(true);
    expect(flow?.lowStructure).toBe("HL");
    expect(flow?.highStructure).toBe("HH");
  });
});

describe("buildCompactWeeklyRows", () => {
  it("keeps low and high day evidence together for the weekly matrix", () => {
    const rows = buildCompactDailyRows([
      { ...day("2026-08-03", 98, 110, 102), open: 100, daily_change_pct: -2, volume: 2_000 },
      { ...day("2026-08-04", 101, 120, 108), open: 103, daily_change_pct: 5, volume: 1_000 },
    ], [
      { date: "2026-08-03", delivery_percentage: 40, delivered_quantity: null, traded_quantity: null },
      { date: "2026-08-04", delivery_percentage: 20, delivered_quantity: null, traded_quantity: null },
    ]);

    const [week] = buildCompactWeeklyRows(rows, 1);

    expect(week.lowDate).toBe("2026-08-03");
    expect(week.highDate).toBe("2026-08-04");
    expect(week.weeklyMovePct).toBeCloseTo(8);
    expect(week.lowHighPct).toBeCloseTo((120 - 98) / 98 * 100);
    expect(week.lowHighDirection).toBe("LOW_FIRST");
    expect(week.lowDeliveryPct).toBe(40);
    expect(week.highDeliveryPct).toBe(20);
    expect(week.lowDayPct).toBe(-2);
    expect(week.highDayPct).toBe(5);
  });

  it("calculates the maximum downside from the session open to the low", () => {
    const [latest] = buildCompactDailyRows([
      { ...day("2026-08-12", 4753, 4866, 4800), open: 4866 },
    ], []);

    expect(latest.openToLowPct).toBeCloseTo(((4753 - 4866) / 4866) * 100);
  });
});

describe("buildCompactDeliveryContext", () => {
  it("compares today's delivery with the prior ten sessions and flags high variability", () => {
    const dailyRows = buildCompactDailyRows(
      Array.from({ length: 11 }, (_, index) => day(`2026-08-${String(index + 1).padStart(2, "0")}`, 100, 110, 105)),
      Array.from({ length: 11 }, (_, index) => ({
        date: `2026-08-${String(index + 1).padStart(2, "0")}`,
        delivery_percentage: index === 10 ? 40 : index % 2 === 0 ? 10 : 30,
        delivered_quantity: null,
        traded_quantity: null,
      })),
    );

    const context = buildCompactDeliveryContext(dailyRows);

    expect(context.currentPct).toBe(40);
    expect(context.averagePct).toBe(20);
    expect(context.ratio).toBe(2);
    expect(context.state).toBe("ERRATIC");
  });
});
