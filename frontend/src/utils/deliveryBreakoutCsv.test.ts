import { describe, expect, it } from "vitest";
import { buildDeliveryBreakoutCsv } from "./deliveryBreakoutCsv";

describe("buildDeliveryBreakoutCsv", () => {
  it("exports the requested delivery and volume evidence columns", () => {
    const csv = buildDeliveryBreakoutCsv([
      {
        symbol: "INFY",
        instrument_token: 408065,
        event_date: "2026-06-23",
        event_type: "BOTH",
        close: 1540.5,
        prev_close: 1492.74,
        close_pct_change: 3.2,
        fifty_two_week_high: 1600,
        fifty_two_week_low: 1200,
        volume: 200000,
        delivery_quantity: 140000,
        delivery_percentage: 56,
        average_volume_10d: 100000,
        average_delivery_quantity_10d: 70000,
        volume_ratio: 2,
        delivery_ratio: 2,
      },
    ]);

    expect(csv).toContain("\"Symbol\",\"Instrument Token\",\"Event Date\",\"Event Type\",\"Price\"");
    expect(csv).toContain("\"Volume Avg 10D\"");
    expect(csv).toContain("\"Delivery Avg 10D\"");
    expect(csv).toContain("\"52W High Distance %\"");
    expect(csv).toContain("\"INFY\",\"408065\",\"2026-06-23\",\"BOTH\"");
  });
});
