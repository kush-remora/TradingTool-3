import { describe, expect, it } from "vitest";
import type { DayDetail, Trade } from "../types";
import { computeTradeSessionSignals, deriveSignalConfigFromTrade } from "./tradeSessionSignals";

function makeDay(
  date: string,
  open: number,
  close: number,
  low: number,
  high: number,
  volume: number,
): DayDetail {
  return {
    date,
    open,
    close,
    low,
    high,
    volume,
    daily_change_pct: null,
    rsi14: null,
    vol_ratio: null,
  };
}

describe("tradeSessionSignals", () => {
  it("derives signal config from trade numeric fields", () => {
    const trade: Trade = {
      id: 1,
      instrument_token: 10,
      nse_symbol: "NETWEB",
      quantity: 5,
      avg_buy_price: "3070",
      today_low: "3044",
      stop_loss_percent: "2",
      stop_loss_price: "3000",
      notes: null,
      trade_date: "2026-04-01",
      close_price: null,
      close_date: null,
      created_at: "2026-04-01T00:00:00Z",
      updated_at: "2026-04-01T00:00:00Z",
    };

    expect(deriveSignalConfigFromTrade(trade)).toEqual({
      entryPrice: 3070,
      entryDayLow: 3044,
      stopPrice: 3000,
    });
  });

  it("classifies dip, bearish, high volume, and low severity signals", () => {
    const days: DayDetail[] = [
      makeDay("2026-03-25", 100, 102, 99, 101, 250),
      makeDay("2026-03-26", 100, 99, 99.5, 101.5, 140),
      makeDay("2026-03-27", 100, 98.5, 101, 104, 90),
      makeDay("2026-03-28", 100, 100.5, 102, 105, 110),
      makeDay("2026-03-29", 100, 100.3, 103, 107, 120),
      makeDay("2026-03-30", 100, 100.2, 104, 109, 130),
      makeDay("2026-03-31", 100, 100.1, 105, 111, 80),
      makeDay("2026-04-01", 100, 100.4, 106, 113, 85),
      makeDay("2026-04-02", 100, 100.6, 107, 115, 95),
      makeDay("2026-04-03", 100, 100.7, 108, 117, 100),
    ];

    const result = computeTradeSessionSignals(days, {
      entryPrice: 100,
      entryDayLow: 96,
      stopPrice: 90,
      bearishBodyThresholdPct: 1,
    });

    expect(result.supportZone).toEqual({ low: 90, high: 100 });
    expect(result.buyZone).toEqual({ low: 101, high: 104 });

    const dipRecovery = result.rowsByDate["2026-03-25"];
    expect(dipRecovery.isDipRecovery).toBe(true);
    expect(dipRecovery.isHighVolume).toBe(true);
    expect(dipRecovery.dominantSignal).toBe("dip_recovery");
    expect(dipRecovery.lowSeverity).toBe("entry_hit");

    const dipInZone = result.rowsByDate["2026-03-26"];
    expect(dipInZone.isDipInZone).toBe(true);
    expect(dipInZone.isBearishClose).toBe(true);
    expect(dipInZone.dominantSignal).toBe("dip_in_zone");
    expect(dipInZone.lowSeverity).toBe("entry_hit");

    const bearishOnly = result.rowsByDate["2026-03-27"];
    expect(bearishOnly.supportTouched).toBe(false);
    expect(bearishOnly.isBearishClose).toBe(true);
    expect(bearishOnly.dominantSignal).toBe("bearish_close");
    expect(bearishOnly.lowSeverity).toBe("buy_zone");
  });

  it("flags bottom-30% range compression days", () => {
    const days: DayDetail[] = [
      makeDay("d1", 100, 100, 99.5, 100.5, 10),
      makeDay("d2", 100, 100, 99, 101, 10),
      makeDay("d3", 100, 100, 98.5, 101.5, 10),
      makeDay("d4", 100, 100, 98, 102, 10),
      makeDay("d5", 100, 100, 97.5, 102.5, 10),
      makeDay("d6", 100, 100, 97, 103, 10),
      makeDay("d7", 100, 100, 96.5, 103.5, 10),
      makeDay("d8", 100, 100, 96, 104, 10),
      makeDay("d9", 100, 100, 95.5, 104.5, 10),
      makeDay("d10", 100, 100, 95, 105, 10),
    ];

    const result = computeTradeSessionSignals(days, { entryPrice: 100 });

    const compressedDates = days
      .filter((day) => result.rowsByDate[day.date].isRangeCompression)
      .map((day) => day.date);

    expect(compressedDates).toEqual(["d1", "d2", "d3"]);
  });
});
