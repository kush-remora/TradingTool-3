import { describe, expect, it } from "vitest";
import { isIndianEquityMarketOpen } from "./marketHours";

describe("isIndianEquityMarketOpen", () => {
  it("returns true during weekday market hours", () => {
    expect(isIndianEquityMarketOpen(new Date("2026-06-24T05:00:00.000Z"))).toBe(true);
  });

  it("returns false before market open", () => {
    expect(isIndianEquityMarketOpen(new Date("2026-06-24T03:43:00.000Z"))).toBe(false);
  });

  it("returns false after market close", () => {
    expect(isIndianEquityMarketOpen(new Date("2026-06-24T10:01:00.000Z"))).toBe(false);
  });

  it("returns false on weekends", () => {
    expect(isIndianEquityMarketOpen(new Date("2026-06-27T05:00:00.000Z"))).toBe(false);
  });
});
