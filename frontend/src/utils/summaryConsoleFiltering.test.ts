import { describe, expect, it } from "vitest";
import type { SummaryConsoleRow } from "../types";
import { describeSummaryConsoleFilter, filterSummaryConsoleRows } from "./summaryConsoleFiltering";

function makeRow(overrides: Partial<SummaryConsoleRow>): SummaryConsoleRow {
  return {
    symbol: "INFY",
    companyName: "Infosys",
    instrumentToken: 408065,
    watchlists: ["leaders"],
    asOfDate: "2026-08-07",
    close: 1540,
    previousClose: 1500,
    dailyMovePct: 2.5,
    largeMove: false,
    sma200: 1500,
    sma200Crossed: false,
    volume: 100000,
    averageVolume5: 100000,
    volumeRatio: 1,
    volumeAnomaly: false,
    deliveryPercentage: 55,
    breakout20Level: 1500,
    breakout20LevelCrossed: false,
    breakout20CloseConfirmed: false,
    breakout40Level: 1500,
    breakout40LevelCrossed: false,
    breakout40CloseConfirmed: false,
    breakout60Level: 1500,
    breakout60LevelCrossed: false,
    breakout60CloseConfirmed: false,
    ...overrides,
  };
}

describe("filterSummaryConsoleRows", () => {
  it("requires every selected signal on the same session for ALL", () => {
    const rows = [
      makeRow({ asOfDate: "2026-08-07", sma200Crossed: true }),
      makeRow({ asOfDate: "2026-08-06", volumeAnomaly: true }),
      makeRow({ symbol: "TCS", volumeAnomaly: true }),
    ];

    const result = filterSummaryConsoleRows(rows, ["sma200Crossed", "volumeAnomaly"], "ALL", "SAME_SESSION");

    expect(result).toEqual([]);
  });

  it("allows selected signals on different sessions in WINDOW scope and keeps stock context rows", () => {
    const rows = [
      makeRow({ asOfDate: "2026-08-07", sma200Crossed: true }),
      makeRow({ asOfDate: "2026-08-06", volumeAnomaly: true }),
      makeRow({ symbol: "TCS", volumeAnomaly: true }),
    ];

    const result = filterSummaryConsoleRows(rows, ["sma200Crossed", "volumeAnomaly"], "ALL", "WINDOW");

    expect(result.map((row) => `${row.symbol}-${row.asOfDate}`)).toEqual([
      "INFY-2026-08-07",
      "INFY-2026-08-06",
    ]);
  });

  it("returns all event rows when no signals are selected", () => {
    const rows = [makeRow(), makeRow({ symbol: "TCS" })];

    expect(filterSummaryConsoleRows(rows, [], "ALL", "WINDOW")).toEqual(rows);
  });

  it("describes the active filter for the AI guide", () => {
    expect(describeSummaryConsoleFilter(["sma200Crossed", "volumeAnomaly"], "ALL", "WINDOW", 5))
      .toBe("All of these signals (200 SMA crossed AND Volume shock (2×)) somewhere across the last 5 sessions.");
  });
});
