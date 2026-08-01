import { describe, expect, it } from "vitest";
import { buildBaseConsolidationObservations } from "./baseConsolidation";

describe("buildBaseConsolidationObservations", () => {
  it("counts inclusive one-percent low hits only in the previous twenty sessions", () => {
    const days = Array.from({ length: 30 }, (_, index) => ({
      date: `2026-06-${String(index + 1).padStart(2, "0")}`,
      open: 100,
      high: 110,
      low: index === 0 ? 99 : index === 1 ? 101 : 100,
      close: 105,
      volume: 100,
      deliveryPercentage: null,
    }));

    const observations = buildBaseConsolidationObservations(days);

    expect(observations).toHaveLength(10);
    expect(observations[0]).toMatchObject({ date: "2026-06-30", low: 100, hitCount: 20 });
  });
});
