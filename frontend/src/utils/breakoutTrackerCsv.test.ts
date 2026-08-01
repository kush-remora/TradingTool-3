import { describe, expect, it } from "vitest";
import type { BreakoutTrackerEntry, StockQuoteSnapshot } from "../types";
import { buildBreakoutTrackerCsv } from "./breakoutTrackerCsv";

describe("buildBreakoutTrackerCsv", () => {
  it("exports every entry and escapes notes containing CSV characters", () => {
    const entries: BreakoutTrackerEntry[] = [
      {
        id: 1,
        instrumentToken: 101,
        symbol: "INFY",
        companyName: "Infosys, Limited",
        breakoutDate: "2026-07-25",
        breakoutPrice: 100,
        notes: 'Delivery rose above 70%, then "held" support.\nReview again.',
      },
      {
        id: 2,
        instrumentToken: 202,
        symbol: "BEL",
        companyName: "Bharat Electronics",
        breakoutDate: "2026-07-20",
        breakoutPrice: 200,
        notes: "No quote available",
      },
    ];
    const quotesBySymbol: Record<string, StockQuoteSnapshot> = {
      INFY: {
        symbol: "INFY",
        ltp: 110,
        day_open: 109,
        day_high: 111,
        day_low: 108,
        volume: 1000,
        updated_at: "2026-07-25T12:00:00Z",
      },
    };

    expect(buildBreakoutTrackerCsv(entries, quotesBySymbol)).toBe(
      '"ID","Instrument Token","Symbol","Company Name","Breakout Date","Breakout Price","Last Price","Since Breakout %","Notes"\r\n' +
        '"1","101","INFY","Infosys, Limited","2026-07-25","100","110","10.00","Delivery rose above 70%, then ""held"" support.\nReview again."\r\n' +
        '"2","202","BEL","Bharat Electronics","2026-07-20","200","","","No quote available"\r\n',
    );
  });
});
