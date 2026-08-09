import type { SummaryConsoleRow } from "../types";

export type SummaryConsoleFilterMatch = "ANY" | "ALL";
export type SummaryConsoleFilterScope = "SAME_SESSION" | "WINDOW";

export type SummaryConsoleSignalKey =
  | "sma200Crossed"
  | "largeMove"
  | "volumeAnomaly"
  | "breakout20LevelCrossed"
  | "breakout20CloseConfirmed"
  | "breakout40LevelCrossed"
  | "breakout40CloseConfirmed"
  | "breakout60LevelCrossed"
  | "breakout60CloseConfirmed";

export interface SummaryConsoleSignalOption {
  key: SummaryConsoleSignalKey;
  label: string;
}

export const SUMMARY_CONSOLE_SIGNAL_OPTIONS: SummaryConsoleSignalOption[] = [
  { key: "sma200Crossed", label: "200 SMA crossed" },
  { key: "largeMove", label: ">3% move" },
  { key: "volumeAnomaly", label: "Volume shock (2×)" },
  { key: "breakout20LevelCrossed", label: "20D high crossed" },
  { key: "breakout20CloseConfirmed", label: "20D close confirmed" },
  { key: "breakout40LevelCrossed", label: "40D high crossed" },
  { key: "breakout40CloseConfirmed", label: "40D close confirmed" },
  { key: "breakout60LevelCrossed", label: "60D high crossed" },
  { key: "breakout60CloseConfirmed", label: "60D close confirmed" },
];

function rowMatchesSignals(
  row: SummaryConsoleRow,
  selectedSignals: SummaryConsoleSignalKey[],
  match: SummaryConsoleFilterMatch,
): boolean {
  const signalResults = selectedSignals.map((signal) => row[signal]);
  return match === "ALL" ? signalResults.every(Boolean) : signalResults.some(Boolean);
}

function stockMatchesSignals(
  rows: SummaryConsoleRow[],
  selectedSignals: SummaryConsoleSignalKey[],
  match: SummaryConsoleFilterMatch,
): boolean {
  return match === "ALL"
    ? selectedSignals.every((signal) => rows.some((row) => row[signal]))
    : rows.some((row) => rowMatchesSignals(row, selectedSignals, "ANY"));
}

export function filterSummaryConsoleRows(
  rows: SummaryConsoleRow[],
  selectedSignals: SummaryConsoleSignalKey[],
  match: SummaryConsoleFilterMatch,
  scope: SummaryConsoleFilterScope,
): SummaryConsoleRow[] {
  if (selectedSignals.length === 0) return rows;

  if (scope === "SAME_SESSION") {
    return rows.filter((row) => rowMatchesSignals(row, selectedSignals, match));
  }

  const rowsBySymbol = new Map<string, SummaryConsoleRow[]>();
  rows.forEach((row) => {
    const symbolRows = rowsBySymbol.get(row.symbol) ?? [];
    rowsBySymbol.set(row.symbol, [...symbolRows, row]);
  });

  return rows.filter((row) => {
    const symbolRows = rowsBySymbol.get(row.symbol) ?? [];
    return stockMatchesSignals(symbolRows, selectedSignals, match);
  });
}

export function describeSummaryConsoleFilter(
  selectedSignals: SummaryConsoleSignalKey[],
  match: SummaryConsoleFilterMatch,
  scope: SummaryConsoleFilterScope,
  lookbackSessions: number,
): string {
  if (selectedSignals.length === 0) return "No signal filter was applied; all event rows are included.";

  const labels = selectedSignals
    .map((signal) => SUMMARY_CONSOLE_SIGNAL_OPTIONS.find((option) => option.key === signal)?.label ?? signal)
    .join(match === "ALL" ? " AND " : " OR ");
  const scopeDescription = scope === "WINDOW"
    ? `somewhere across the last ${lookbackSessions} sessions`
    : "on the same session row";
  return `${match === "ALL" ? "All" : "Any"} of these signals (${labels}) ${scopeDescription}.`;
}
