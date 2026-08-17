import { describe, expect, it } from "vitest";
import type { BreakoutDayQualityResponse } from "../types";
import { evaluateNextMorningOpen } from "./breakoutExecution";

describe("evaluateNextMorningOpen", () => {
  it("passes an open inside the quarter ATR consideration zone", () => {
    expect(evaluateNextMorningOpen(report(), 102)?.decision).toBe("PASS");
  });

  it("waits between quarter and half ATR above the line", () => {
    expect(evaluateNextMorningOpen(report(), 104)?.decision).toBe("WAIT");
  });

  it("rejects a chased open and an open below the line", () => {
    expect(evaluateNextMorningOpen(report(), 106)?.decision).toBe("REJECT");
    expect(evaluateNextMorningOpen(report(), 99)?.decision).toBe("REJECT");
  });

  it("rejects when the next obstacle leaves less than one ATR", () => {
    const value = report();
    value.chartContext.nextObstaclePrice = 108;
    expect(evaluateNextMorningOpen(value, 102)?.summary).toContain("less than 1 ATR");
  });
});

function report(): BreakoutDayQualityResponse {
  return {
    symbol: "TEST", date: "2026-08-11", structureStatus: "FRESH_BREAKOUT", structureDecision: "FRESH_BREAKOUT",
    structureExplanation: "Fresh breakout.", overallDecision: "PASS", decisionSummary: "Pass", open: 99, high: 104,
    low: 98, close: 103, volume: 1000, atr: 10, floor: 90, peak: 104, breakoutLine: 100, majorCeiling: 130,
    sma50: 95, sma200: 90, deliveryPercentage: null, deliveredQuantity: null, rules: [],
    chartContext: {
      overallDecision: "PASS", decisionSummary: "Context passes.", sma50: 95, sma200: 90, sma50ChangePctFiveSessions: 1,
      sma200ChangePctTwentySessions: 1, priorFiftyTwoWeekHigh: 130, nextObstaclePrice: 130,
      nextObstacleLabel: "Prior 52-week high", roomToObstaclePct: 26, roomToObstacleAtr: 2.7, rules: [],
    },
  };
}
