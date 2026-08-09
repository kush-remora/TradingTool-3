import { describe, expect, it } from "vitest";
import { buildSummaryConsoleCsv, buildSummaryConsoleGuide } from "./summaryConsoleExport";

const response = {
  requestedAsOfDate: "2026-08-07",
  lookbackSessions: 5,
  watchlists: ["growth_watchlist"],
  scannedCount: 1,
  eventCount: 1,
  uniqueStockCount: 1,
  rows: [{
    symbol: "INFY",
    companyName: "Infosys, Limited",
    instrumentToken: 408065,
    watchlists: ["growth_watchlist", "leaders"],
    asOfDate: "2026-08-07",
    close: 1540.5,
    previousClose: 1492.74,
    dailyMovePct: 3.2,
    largeMove: true,
    sma200: 1500,
    sma200Crossed: true,
    volume: 200000,
    averageVolume5: 100000,
    volumeRatio: 2,
    volumeAnomaly: true,
    deliveryPercentage: 56,
    breakout20Level: 1500,
    breakout20LevelCrossed: true,
    breakout20CloseConfirmed: true,
    breakout40Level: 1480,
    breakout40LevelCrossed: false,
    breakout40CloseConfirmed: false,
    breakout60Level: 1460,
    breakout60LevelCrossed: true,
    breakout60CloseConfirmed: false,
  }],
};

describe("summaryConsoleExport", () => {
  it("exports explicit AI-friendly headers and escaped values", () => {
    const csv = buildSummaryConsoleCsv(response);

    expect(csv).toContain("requested_through_date");
    expect(csv).toContain("breakout_20_high_crossed");
    expect(csv).toContain("\"Infosys, Limited\"");
    expect(csv).toContain("\"true\"");
  });

  it("explains the detection rules and every exported column", () => {
    const guide = buildSummaryConsoleGuide();

    expect(guide).toContain("Baselines use only preceding completed sessions");
    expect(guide).toContain("breakout_60_close_confirmed");
    expect(guide).toContain("delivery_percentage is context only");
    expect(guide).toContain("Return exactly five stocks");
    expect(guide).toContain("Applied console filter: No signal filter was applied");
    expect(buildSummaryConsoleGuide("All of these signals (200 SMA crossed AND Volume shock (2×)) somewhere across the last 5 sessions.")).toContain(
      "Applied console filter: All of these signals",
    );
  });
});
