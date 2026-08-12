import type {
  FreshBreakoutDates,
  InstrumentSearchResult,
  LiveMarketUpdate,
  StockDetailResponse,
  StockNote,
} from "../../types";
import type { CompactDailyRow, CompactWeeklyRow } from "./compactStockReview";
import {
  buildCompactThreeWeekFlow,
  formatPrice,
  formatQuantity,
  formatSignedPrice,
  formatSignedPercent,
} from "./compactStockReview";

export interface CompactReviewExportInput {
  instrument: InstrumentSearchResult;
  data: StockDetailResponse;
  liveData: LiveMarketUpdate | null;
  dailyRows: CompactDailyRow[];
  weeklyRows: CompactWeeklyRow[];
  notes: StockNote[];
}

const BREAKOUT_HORIZONS: Array<{
  label: string;
  dateKey: keyof FreshBreakoutDates;
  levelKey: keyof FreshBreakoutDates;
}> = [
  { label: "20D", dateKey: "breakout_20d", levelKey: "breakout_20d_level" },
  { label: "50D", dateKey: "breakout_50d", levelKey: "breakout_50d_level" },
  { label: "52D", dateKey: "breakout_52d", levelKey: "breakout_52d_level" },
  { label: "100D", dateKey: "breakout_100d", levelKey: "breakout_100d_level" },
];

const markdownCell = (value: string): string => value.replaceAll("|", "\\|").replaceAll("\n", " ");

const markdownTable = (headers: string[], rows: string[][]): string => [
  `| ${headers.join(" | ")} |`,
  `| ${headers.map(() => "---").join(" | ")} |`,
  ...rows.map((row) => `| ${row.map(markdownCell).join(" | ")} |`),
].join("\n");

const formatDate = (date: string | null): string => {
  if (!date) return "—";
  return new Intl.DateTimeFormat("en-IN", {
    weekday: "short",
    day: "2-digit",
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(`${date}T00:00:00Z`));
};

const formatDateTime = (dateTime: string): string => new Intl.DateTimeFormat("en-IN", {
  weekday: "short",
  day: "2-digit",
  month: "short",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
  timeZone: "Asia/Kolkata",
}).format(new Date(dateTime));

const formatRatio = (value: number | null): string => value == null ? "—" : `${value.toFixed(2)}×`;

const formatDay = (day: CompactDailyRow): string => `${day.date} (${formatDate(day.date).split(",")[0]})`;

const moveFromSessionsAgo = (
  currentPrice: number | null,
  dailyRows: CompactDailyRow[],
  lookback: number,
): number | null => {
  const previous = dailyRows.at(-(lookback + 1));
  if (currentPrice == null || previous == null || previous.close === 0) return null;
  return ((currentPrice - previous.close) / previous.close) * 100;
};

const buildSnapshot = (input: CompactReviewExportInput, latestDay: CompactDailyRow | null): string => {
  const { data, dailyRows } = input;
  const currentPrice = input.liveData?.ltp ?? latestDay?.close ?? data.fundamentals.currentPrice;
  const deliveryDate = data.delivery_days.find((day) => day.delivery_percentage != null)?.date ?? null;
  const flow = buildCompactThreeWeekFlow(dailyRows);
  const floorGap = flow && flow.floorHigh !== 0 ? ((currentPrice - flow.floorHigh) / flow.floorHigh) * 100 : null;
  const rsi = data.rsi14_range;
  const roc = data.roc9;

  const rows = [
    ["Latest close", formatPrice(latestDay?.close ?? null)],
    ["Live price", input.liveData == null ? "—" : formatPrice(input.liveData.ltp)],
    ["Latest candle", formatDate(latestDay?.date ?? null)],
    ["Latest delivery", `${latestDay?.deliveryPct == null ? "—" : `${latestDay.deliveryPct.toFixed(1)}%`} · as of ${formatDate(deliveryDate)}`],
    ["Today OHLC", latestDay == null ? "—" : `${formatPrice(latestDay.open)} / ${formatPrice(latestDay.high)} / ${formatPrice(latestDay.low)} / ${formatPrice(latestDay.close)}`],
    ["Today move", formatSignedPercent(latestDay?.daily_change_pct ?? null, 2)],
    ["Open → low", latestDay == null ? "—" : `${formatSignedPrice(latestDay.low - latestDay.open)} · ${formatSignedPercent(latestDay.openToLowPct, 2)}`],
    ["Today range", formatSignedPercent(latestDay?.spreadPct ?? null, 2)],
    ["Close position", latestDay?.closePositionPct == null ? "—" : `${latestDay.closePositionPct.toFixed(1)}% of low-to-high range`],
    ["Volume", latestDay == null ? "—" : `${formatQuantity(latestDay.volume)} · ${latestDay.volumeVsPrior10dPct == null ? "—" : `${(latestDay.volumeVsPrior10dPct / 100).toFixed(2)}× vs prior 10D`}`],
    ["RSI 14", rsi?.current == null ? "—" : `${rsi.current.toFixed(1)} · range ${rsi.min_60d?.toFixed(1) ?? "—"}–${rsi.max_60d?.toFixed(1) ?? "—"} · ${rsi.direction_3d ?? "—"}`],
    ["ROC 9", roc?.current == null ? "—" : `${roc.current > 0 ? "+" : ""}${roc.current.toFixed(1)}% · ${roc.direction_3d ?? "—"}`],
    ["200D / 100D", `${formatPrice(data.fundamentals.sma200)} / ${formatPrice(data.fundamentals.sma100)}`],
    ["52-week low / high", `${formatPrice(data.fundamentals.fiftyTwoWeekLow)} / ${formatPrice(data.fundamentals.fiftyTwoWeekHigh)}`],
    ["20D / 40D / 60D move", [20, 40, 60].map((lookback) => `${lookback}D ${formatSignedPercent(moveFromSessionsAgo(currentPrice, dailyRows, lookback), 1)}`).join(" · ")],
    ["Weekly low band", flow == null ? "—" : `${flow.floorAligned ? "Floor" : "Low band"} ${formatPrice(flow.floorLow)}–${formatPrice(flow.floorHigh)} · Gap ${formatSignedPercent(floorGap, 1)} · ${flow.lowStructure} + ${flow.highStructure}`],
  ];

  return markdownTable(["Metric", "Value"], rows);
};

const buildBreakoutSection = (data: StockDetailResponse, currentPrice: number | null): string => {
  const dates = data.breakout_dates;
  const rows = BREAKOUT_HORIZONS.map(({ label, dateKey, levelKey }) => {
    const date = dates?.[dateKey] as string | null | undefined;
    const level = typeof dates?.[levelKey] === "number" ? dates[levelKey] as number : null;
    const distance = level != null && currentPrice != null && level !== 0 ? ((currentPrice - level) / level) * 100 : null;
    return [label, formatDate(date ?? null), formatPrice(level), formatSignedPercent(distance, 1)];
  });
  return markdownTable(["Horizon", "Fresh breakout", "Level", "Current vs level"], rows);
};

const buildWeeklySection = (weeks: CompactWeeklyRow[]): string => markdownTable(
  ["Week", "Week %", "Low", "High", "Volume low / high", "Delivery low / high", "Day % low / high"],
  weeks.map((week, index) => [
    week.isCurrent ? "WTD" : `W−${index}`,
    `${formatSignedPercent(week.weeklyMovePct, 1)} · ${week.lowHighDirection === "LOW_FIRST" ? "L→H" : week.lowHighDirection === "HIGH_FIRST" ? "H→L" : "L/H same day"} ${formatSignedPercent(week.lowHighPct, 1)}`,
    `${formatPrice(week.low)} · ${formatDate(week.lowDate)}`,
    `${formatPrice(week.high)} · ${formatDate(week.highDate)}`,
    `${formatQuantity(week.lowVolume)} / ${formatQuantity(week.highVolume)} · ${formatRatio(week.lowVolumeRatio)} / ${formatRatio(week.highVolumeRatio)}`,
    `${week.lowDeliveryPct == null ? "—" : `${week.lowDeliveryPct.toFixed(1)}%`} / ${week.highDeliveryPct == null ? "—" : `${week.highDeliveryPct.toFixed(1)}%`}`,
    `${formatSignedPercent(week.lowDayPct, 1)} / ${formatSignedPercent(week.highDayPct, 1)}`,
  ]),
);

const buildDailySection = (dailyRows: CompactDailyRow[]): string => markdownTable(
  ["Date", "Open", "High", "Low", "Close", "Open → Low", "Day %", "RSI14", "ROC9", "Volume", "Vol vs 10D", "Delivery"],
  dailyRows.slice(-60).map((day) => [
    formatDay(day),
    formatPrice(day.open),
    formatPrice(day.high),
    formatPrice(day.low),
    formatPrice(day.close),
    `${formatSignedPrice(day.low - day.open)} · ${formatSignedPercent(day.openToLowPct, 2)}`,
    formatSignedPercent(day.daily_change_pct, 2),
    day.rsi14 == null ? "—" : day.rsi14.toFixed(1),
    formatSignedPercent(day.roc9Pct, 2),
    formatQuantity(day.volume),
    day.volumeVsPrior10dPct == null ? "—" : `${(day.volumeVsPrior10dPct / 100).toFixed(2)}×`,
    day.deliveryPct == null ? "—" : `${day.deliveryPct.toFixed(1)}%`,
  ]),
);

const buildTopVolumeSection = (dailyRows: CompactDailyRow[]): string => markdownTable(
  ["Date", "Volume", "Effort → result", "O / H / L / C", "Day %", "Delivery"],
  [...dailyRows]
    .slice(-40)
    .sort((left, right) => right.volume - left.volume || right.date.localeCompare(left.date))
    .slice(0, 4)
    .map((day) => [
      formatDay(day),
      formatQuantity(day.volume),
      `${formatRatio(day.volumeVsPrior10dPct == null ? null : day.volumeVsPrior10dPct / 100)} → ${formatSignedPercent(day.daily_change_pct, 1)}`,
      `${formatPrice(day.open)} / ${formatPrice(day.high)} / ${formatPrice(day.low)} / ${formatPrice(day.close)}`,
      formatSignedPercent(day.daily_change_pct, 1),
      day.deliveryPct == null ? "—" : `${day.deliveryPct.toFixed(1)}%`,
    ]),
);

const noteSessionDate = (createdAt: string): string => new Intl.DateTimeFormat("en-CA", {
  timeZone: "Asia/Kolkata",
}).format(new Date(createdAt));

const buildNotesSection = (notes: StockNote[], dailyRows: CompactDailyRow[]): string => {
  if (notes.length === 0) return "No saved observations.";
  return notes.map((note) => {
    const session = dailyRows.find((day) => day.date === noteSessionDate(note.createdAt));
    const evidence = session == null
      ? "Evidence: no matching candle in the exported 60-session window."
      : `Evidence: O/H/L/C ${formatPrice(session.open)} / ${formatPrice(session.high)} / ${formatPrice(session.low)} / ${formatPrice(session.close)} · Day ${formatSignedPercent(session.daily_change_pct, 1)} · Volume ${formatQuantity(session.volume)} · Delivery ${session.deliveryPct == null ? "—" : `${session.deliveryPct.toFixed(1)}%`}`;
    return `### ${formatDateTime(note.createdAt)}\n\n${evidence}\n\n${note.notes.trim()}`;
  }).join("\n\n");
};

export function buildCompactReviewMarkdown(input: CompactReviewExportInput): string {
  const latestDay = input.dailyRows.at(-1) ?? null;
  const currentPrice = input.liveData?.ltp ?? latestDay?.close ?? input.data.fundamentals.currentPrice;
  const generatedAt = new Date().toISOString();
  const sections = [
    `# ${input.instrument.trading_symbol} — compact stock review`,
    `Generated: ${generatedAt}`,
    `Company: ${input.instrument.company_name}`,
    `Exchange: ${input.instrument.exchange}`,
    "",
    "## Snapshot",
    buildSnapshot(input, latestDay),
    "",
    "## Last fresh breakout",
    buildBreakoutSection(input.data, currentPrice),
    "",
    "## Four-week structure",
    buildWeeklySection(input.weeklyRows),
    "",
    "## Top volume days · 40 sessions",
    buildTopVolumeSection(input.dailyRows),
    "",
    "## Daily candles · latest 60 sessions",
    buildDailySection(input.dailyRows),
    "",
    "## Observation log",
    buildNotesSection(input.notes, input.dailyRows),
    "",
    "## Field notes",
    "- Prices are in INR; volume is traded shares.",
    "- Vol vs 10D is the session volume divided by the average volume of the previous 10 sessions; the session itself is excluded.",
    "- ROC9 is the close percentage change versus nine trading sessions earlier.",
    "- Delivery is shown with its source date because it can arrive after the latest candle.",
    "- This export contains raw evidence only; it does not make a buy or sell recommendation.",
  ];
  return `${sections.join("\n")}\n`;
}

export function downloadCompactReviewMarkdown(input: CompactReviewExportInput): void {
  const markdown = buildCompactReviewMarkdown(input);
  const date = input.dailyRows.at(-1)?.date ?? new Date().toISOString().slice(0, 10);
  const blob = new Blob([markdown], { type: "text/markdown;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = `${input.instrument.trading_symbol}-compact-review-${date}.md`;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}
