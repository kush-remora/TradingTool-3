import { describe, expect, it } from "vitest";
import {
  compareByNearestBuyZone,
  computeBuyZoneMetrics,
  matchesBuyZoneFilter,
} from "./screenerBuyZone";

describe("screenerBuyZone", () => {
  it("computes valid buy-zone metrics for a normal row", () => {
    const metrics = computeBuyZoneMetrics({
      ltp: 3600,
      buyDayLowMin: 3030,
      buyDayLowMax: 3644.5,
    });

    expect(metrics.status).toBe("inside");
    expect(metrics.distanceFromMin).toBe(570);
    expect(metrics.ltpVsMinLowPct).toBe(18.81);
    expect(metrics.zonePositionPct).toBe(92.76);
    expect(metrics.zonePositionPctClamped).toBe(92.76);
  });

  it("classifies below and above zone correctly", () => {
    const below = computeBuyZoneMetrics({
      ltp: 2900,
      buyDayLowMin: 3030,
      buyDayLowMax: 3644.5,
    });
    const above = computeBuyZoneMetrics({
      ltp: 3800,
      buyDayLowMin: 3030,
      buyDayLowMax: 3644.5,
    });

    expect(below.status).toBe("below");
    expect(below.ltpVsMinLowPct).toBeLessThan(0);
    expect(above.status).toBe("above");
    expect(above.ltpVsMinLowPct).toBeGreaterThan(0);
  });

  it("returns invalid metrics for missing ltp or invalid zone bounds", () => {
    const missingLtp = computeBuyZoneMetrics({
      ltp: null,
      buyDayLowMin: 3030,
      buyDayLowMax: 3644.5,
    });
    const invalidZone = computeBuyZoneMetrics({
      ltp: 3500,
      buyDayLowMin: 0,
      buyDayLowMax: 3644.5,
    });

    expect(missingLtp.status).toBe("invalid");
    expect(missingLtp.ltpVsMinLowPct).toBeNull();
    expect(invalidZone.status).toBe("invalid");
    expect(invalidZone.distanceFromMin).toBeNull();
  });

  it("applies buy-zone filters correctly", () => {
    const insideNear = computeBuyZoneMetrics({
      ltp: 3120,
      buyDayLowMin: 3030,
      buyDayLowMax: 3644.5,
    });
    const below = computeBuyZoneMetrics({
      ltp: 2900,
      buyDayLowMin: 3030,
      buyDayLowMax: 3644.5,
    });
    const insideFar = computeBuyZoneMetrics({
      ltp: 3500,
      buyDayLowMin: 3030,
      buyDayLowMax: 3644.5,
    });

    expect(matchesBuyZoneFilter(insideNear, "All")).toBe(true);
    expect(matchesBuyZoneFilter(insideNear, "Inside zone")).toBe(true);
    expect(matchesBuyZoneFilter(insideNear, "Near (<=5% above min)")).toBe(true);
    expect(matchesBuyZoneFilter(insideFar, "Near (<=5% above min)")).toBe(false);
    expect(matchesBuyZoneFilter(below, "Below zone")).toBe(true);
    expect(matchesBuyZoneFilter(below, "Inside zone")).toBe(false);
  });

  it("sorts nearest-to-min first and pushes invalid rows to the bottom", () => {
    const near = computeBuyZoneMetrics({
      ltp: 3080,
      buyDayLowMin: 3030,
      buyDayLowMax: 3644.5,
    });
    const far = computeBuyZoneMetrics({
      ltp: 3600,
      buyDayLowMin: 3030,
      buyDayLowMax: 3644.5,
    });
    const invalid = computeBuyZoneMetrics({
      ltp: null,
      buyDayLowMin: 3030,
      buyDayLowMax: 3644.5,
    });

    const sorted = [invalid, far, near].sort(compareByNearestBuyZone);
    expect(sorted[0]).toBe(near);
    expect(sorted[1]).toBe(far);
    expect(sorted[2]).toBe(invalid);
  });
});
