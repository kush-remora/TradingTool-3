import type { SummaryConsoleResponse, SummaryConsoleRow } from "../types";

export interface SummaryConsoleColumnGuideEntry {
  column: string;
  meaning: string;
  designedToDo: string;
}

export const SUMMARY_CONSOLE_COLUMN_GUIDE: SummaryConsoleColumnGuideEntry[] = [
  { column: "requested_through_date", meaning: "The date cutoff requested by the console.", designedToDo: "Tell the AI which date range the scan was asked to cover. The actual market session may be earlier on a weekend, holiday, or before fresh data arrives." },
  { column: "lookback_sessions", meaning: "The number of latest completed trading sessions evaluated for every stock; currently 5.", designedToDo: "Tell the AI that separate rows can represent separate event days inside the five-session window." },
  { column: "session_date", meaning: "The actual daily candle date used for this stock.", designedToDo: "Use this as the stock's true event date." },
  { column: "symbol", meaning: "NSE trading symbol.", designedToDo: "Identify the stock and connect analysis back to the market instrument." },
  { column: "company_name", meaning: "Company name from the selected watchlist universe.", designedToDo: "Provide human-readable stock context." },
  { column: "instrument_token", meaning: "Kite instrument identifier.", designedToDo: "Use only as an identifier; it is not a signal." },
  { column: "watchlists", meaning: "Selected watchlists containing the stock, separated by ` | `.", designedToDo: "Show whether the stock has repeated importance across the selected universes." },
  { column: "close", meaning: "Today's/session closing price.", designedToDo: "Anchor the day's signals to the final price." },
  { column: "previous_close", meaning: "Previous completed session's close.", designedToDo: "Provide the baseline for the daily percentage move." },
  { column: "daily_move_pct", meaning: "Close-to-close percentage change from previous_close to close.", designedToDo: "Measure the size and direction of today's price move." },
  { column: "large_move_triggered", meaning: "True when the absolute daily move is greater than 3%.", designedToDo: "Flag an unusually strong upward or downward session." },
  { column: "sma_200", meaning: "Average of the previous 200 completed closing prices.", designedToDo: "Represent the long-term fair-value reference used by this console." },
  { column: "sma_200_crossed", meaning: "True when today's low-to-high range contains the previous-session 200 SMA.", designedToDo: "Flag a price interaction with the 200-day fair-value mark, in either direction." },
  { column: "volume", meaning: "Today's/session traded volume.", designedToDo: "Measure the amount of participation behind the move." },
  { column: "average_volume_5", meaning: "Average traded volume across the previous five completed sessions.", designedToDo: "Provide the volume baseline without including today's volume." },
  { column: "volume_ratio", meaning: "volume divided by average_volume_5.", designedToDo: "Quantify how many times normal today's participation was." },
  { column: "volume_anomaly_triggered", meaning: "True when volume_ratio is at least 2.0.", designedToDo: "Flag a simple volume shock for deeper effort-versus-result review." },
  { column: "delivery_percentage", meaning: "NSE delivery percentage for the session, when available.", designedToDo: "Add delivery context; do not treat it as a standalone buy signal." },
  { column: "breakout_20_level", meaning: "Highest closing price across the previous 20 completed sessions.", designedToDo: "Show the exact resistance level used for the 20-session breakout tests." },
  { column: "breakout_20_high_crossed", meaning: "True when today's high is greater than breakout_20_level.", designedToDo: "Detect an intraday cross of the prior 20-session closing resistance." },
  { column: "breakout_20_close_confirmed", meaning: "True when today's close is greater than breakout_20_level.", designedToDo: "Show whether the 20-session breakout held into the close." },
  { column: "breakout_40_level", meaning: "Highest closing price across the previous 40 completed sessions.", designedToDo: "Show the exact resistance level used for the 40-session breakout tests." },
  { column: "breakout_40_high_crossed", meaning: "True when today's high is greater than breakout_40_level.", designedToDo: "Detect an intraday cross of the prior 40-session closing resistance." },
  { column: "breakout_40_close_confirmed", meaning: "True when today's close is greater than breakout_40_level.", designedToDo: "Show whether the 40-session breakout held into the close." },
  { column: "breakout_60_level", meaning: "Highest closing price across the previous 60 completed sessions.", designedToDo: "Show the exact resistance level used for the 60-session breakout tests." },
  { column: "breakout_60_high_crossed", meaning: "True when today's high is greater than breakout_60_level.", designedToDo: "Detect an intraday cross of the prior 60-session closing resistance." },
  { column: "breakout_60_close_confirmed", meaning: "True when today's close is greater than breakout_60_level.", designedToDo: "Show whether the 60-session breakout held into the close." },
];

const CSV_HEADERS = SUMMARY_CONSOLE_COLUMN_GUIDE.map((entry) => entry.column);

function escapeCsvValue(value: string | number | boolean | null | undefined): string {
  const text = value == null ? "" : String(value);
  return `"${text.replace(/"/g, '""')}"`;
}

function toCsvRow(response: SummaryConsoleResponse, row: SummaryConsoleRow): Array<string | number | boolean | null> {
  return [
    response.requestedAsOfDate,
    response.lookbackSessions,
    row.asOfDate,
    row.symbol,
    row.companyName,
    row.instrumentToken,
    row.watchlists.join(" | "),
    row.close,
    row.previousClose,
    row.dailyMovePct,
    row.largeMove,
    row.sma200,
    row.sma200Crossed,
    row.volume,
    row.averageVolume5,
    row.volumeRatio,
    row.volumeAnomaly,
    row.deliveryPercentage,
    row.breakout20Level,
    row.breakout20LevelCrossed,
    row.breakout20CloseConfirmed,
    row.breakout40Level,
    row.breakout40LevelCrossed,
    row.breakout40CloseConfirmed,
    row.breakout60Level,
    row.breakout60LevelCrossed,
    row.breakout60CloseConfirmed,
  ];
}

export function buildSummaryConsoleCsv(response: SummaryConsoleResponse): string {
  const rows = response.rows.map((row) => toCsvRow(response, row));
  return [CSV_HEADERS, ...rows]
    .map((row) => row.map(escapeCsvValue).join(","))
    .join("\r\n") + "\r\n";
}

export function buildSummaryConsoleGuide(appliedFilter?: string): string {
  const lines = [
    "# Summary Console CSV Guide",
    "",
    "Purpose: identify watchlist stocks that deserve attention on the latest available market session. This is an observation and triage dataset, not a buy/sell recommendation.",
    `Applied console filter: ${appliedFilter ?? "No signal filter was applied; all event rows are included."}`,
    "",
    "Important rules:",
    "- Each row is one stock/session that triggered at least one Summary Console event. The same stock can appear on multiple session dates; do not combine those rows as if all signals happened on one day.",
    "- Baselines use only preceding completed sessions. Today's volume is excluded from average_volume_5, and today's close is excluded from all breakout levels and sma_200.",
    "- breakout_N_level is the highest closing price from the previous N completed sessions. high_crossed means today's high moved above that level. close_confirmed means today's close finished above that level.",
    "- delivery_percentage is context only. Do not use it alone to rank or recommend a stock.",
    "- For deeper review, prioritize close-confirmed breakouts, meaningful move/volume combinations, and cases where several independent signals agree. Then inspect the stock's price-volume structure and Wyckoff context.",
    "",
    "Suggested AI task:",
    "Read every row using this guide. Return exactly five stocks that deserve deeper research when at least five rows are available; otherwise return all available rows. Explain the evidence for each selection, identify conflicting signals, and do not present the result as a buy/sell recommendation. Prefer independent signal agreement and close-confirmed breakouts over a simple count of true flags.",
    "",
    "| Column | Meaning | Designed to do |",
    "| --- | --- | --- |",
    ...SUMMARY_CONSOLE_COLUMN_GUIDE.map((entry) => `| ${entry.column} | ${entry.meaning.replace(/\|/g, "\\|")} | ${entry.designedToDo.replace(/\|/g, "\\|")} |`),
    "",
  ];
  return lines.join("\n");
}

export function downloadSummaryConsoleFile(filename: string, content: string, contentType: string): void {
  const blob = new Blob([content], { type: contentType });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
