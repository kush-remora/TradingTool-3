export type BuyZoneStatus = "below" | "inside" | "above" | "invalid";

export type BuyZoneFilter = "All" | "Inside zone" | "Near (<=5% above min)" | "Below zone";

export interface BuyZoneMetrics {
  ltp: number | null;
  buyDayLowMin: number;
  buyDayLowMax: number;
  ltpVsMinLowPct: number | null;
  distanceFromMin: number | null;
  zonePositionPct: number | null;
  zonePositionPctClamped: number | null;
  status: BuyZoneStatus;
}

const NEAR_ZONE_MAX_PCT = 5;

function round2(value: number): number {
  return Math.round(value * 100) / 100;
}

function isValidBuyZone(minLow: number, maxLow: number): boolean {
  return minLow > 0 && maxLow >= minLow;
}

export function computeBuyZoneMetrics(params: {
  ltp: number | null;
  buyDayLowMin: number;
  buyDayLowMax: number;
}): BuyZoneMetrics {
  const { ltp, buyDayLowMin, buyDayLowMax } = params;
  const validZone = isValidBuyZone(buyDayLowMin, buyDayLowMax);
  const hasLtp = ltp !== null;

  if (!validZone || !hasLtp) {
    return {
      ltp,
      buyDayLowMin,
      buyDayLowMax,
      ltpVsMinLowPct: null,
      distanceFromMin: null,
      zonePositionPct: null,
      zonePositionPctClamped: null,
      status: "invalid",
    };
  }

  const distanceFromMin = round2(ltp - buyDayLowMin);
  const ltpVsMinLowPct = round2((distanceFromMin / buyDayLowMin) * 100);
  const zoneRange = buyDayLowMax - buyDayLowMin;
  const zonePositionPct = zoneRange > 0
    ? round2(((ltp - buyDayLowMin) / zoneRange) * 100)
    : 0;
  const zonePositionPctClamped = Math.max(0, Math.min(100, zonePositionPct));

  let status: BuyZoneStatus = "inside";
  if (ltp < buyDayLowMin) status = "below";
  if (ltp > buyDayLowMax) status = "above";

  return {
    ltp,
    buyDayLowMin,
    buyDayLowMax,
    ltpVsMinLowPct,
    distanceFromMin,
    zonePositionPct,
    zonePositionPctClamped,
    status,
  };
}

export function matchesBuyZoneFilter(metrics: BuyZoneMetrics, filter: BuyZoneFilter): boolean {
  if (filter === "All") return true;
  if (metrics.status === "invalid") return false;
  if (filter === "Inside zone") return metrics.status === "inside";
  if (filter === "Below zone") return metrics.status === "below";

  if (filter === "Near (<=5% above min)") {
    const pct = metrics.ltpVsMinLowPct;
    if (pct === null) return false;
    return pct >= 0 && pct <= NEAR_ZONE_MAX_PCT;
  }

  return true;
}

export function compareByNearestBuyZone(a: BuyZoneMetrics, b: BuyZoneMetrics): number {
  if (a.ltpVsMinLowPct === null && b.ltpVsMinLowPct === null) return 0;
  if (a.ltpVsMinLowPct === null) return 1;
  if (b.ltpVsMinLowPct === null) return -1;
  return a.ltpVsMinLowPct - b.ltpVsMinLowPct;
}
