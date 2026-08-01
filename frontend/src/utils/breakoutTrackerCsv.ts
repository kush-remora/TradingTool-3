import type { BreakoutTrackerEntry, StockQuoteSnapshot } from "../types";

const CSV_HEADERS = [
  "ID",
  "Instrument Token",
  "Symbol",
  "Company Name",
  "Breakout Date",
  "Breakout Price",
  "Last Price",
  "Since Breakout %",
  "Notes",
];

function escapeCsvValue(value: string | number | null): string {
  const text = value == null ? "" : String(value);
  return `"${text.replace(/"/g, '""')}"`;
}

function performance(currentPrice: number | null | undefined, breakoutPrice: number): number | null {
  if (currentPrice == null || breakoutPrice <= 0) return null;
  return ((currentPrice - breakoutPrice) / breakoutPrice) * 100;
}

export function buildBreakoutTrackerCsv(
  entries: BreakoutTrackerEntry[],
  quotesBySymbol: Record<string, StockQuoteSnapshot> = {},
): string {
  const rows = entries.map((entry) => {
    const currentPrice = quotesBySymbol[entry.symbol]?.ltp;
    const currentPerformance = performance(currentPrice, entry.breakoutPrice);

    return [
      entry.id,
      entry.instrumentToken,
      entry.symbol,
      entry.companyName,
      entry.breakoutDate,
      entry.breakoutPrice,
      currentPrice,
      currentPerformance == null ? null : currentPerformance.toFixed(2),
      entry.notes,
    ];
  });

  return [CSV_HEADERS, ...rows]
    .map((row) => row.map((value) => escapeCsvValue(value)).join(","))
    .join("\r\n") + "\r\n";
}
