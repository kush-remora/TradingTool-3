import { describe, expect, it } from "vitest";
import { computeCustomRankScores, DEFAULT_RANK_WEIGHTS, normalizeMetricSeries } from "./weeklyScreenerRanking";

describe("weeklyScreenerRanking", () => {
  it("normalizes inverse series correctly", () => {
    const normalized = normalizeMetricSeries([10, 20, 30], true);
    expect(normalized[0]).toBe(100);
    expect(normalized[1]).toBe(50);
    expect(normalized[2]).toBe(0);
  });

  it("computes weighted scores with null-safe fallback", () => {
    const scores = computeCustomRankScores(
      [
        {
          symbol: "AAA",
          vcpTightness: 8,
          volumeSignature: 1.2,
          strikeRate: 70,
          baselineDistance: 3,
          reboundConsistency: 10,
          expectedSwing: 7,
        },
        {
          symbol: "BBB",
          vcpTightness: null,
          volumeSignature: 2.4,
          strikeRate: 50,
          baselineDistance: 8,
          reboundConsistency: 6,
          expectedSwing: 4,
        },
      ],
      DEFAULT_RANK_WEIGHTS,
    );

    expect(scores).toHaveLength(2);
    const scoreA = scores.find((row) => row.symbol === "AAA")?.score ?? 0;
    const scoreB = scores.find((row) => row.symbol === "BBB")?.score ?? 0;
    expect(scoreA).toBeGreaterThan(scoreB);
    expect(scoreA).toBeGreaterThanOrEqual(0);
    expect(scoreA).toBeLessThanOrEqual(100);
  });
});
