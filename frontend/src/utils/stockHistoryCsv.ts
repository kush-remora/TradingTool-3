import type { DeliveryDayDetail, StockDetailResponse } from "../types";

export interface StockHistoryCsvRow {
  key: string;
  date: string;
  day: string;
  open: number;
  close: number;
  low: number;
  high: number;
  openToHighPct: number | null;
  openToClosePct: number | null;
  volume: number;
  deliveryVolume: number | null;
  deliveryPct: number | null;
}

const CSV_HEADERS = [
  "Date",
  "Day",
  "Open",
  "Close",
  "Low",
  "High",
  "Intraday % (Open, High)",
  "% (Open, Close)",
  "Volume",
  "Delivery Volume",
  "Delivery %",
];

const formatDay = (date: string): string => new Intl.DateTimeFormat("en-IN", {
  weekday: "short",
  timeZone: "UTC",
}).format(new Date(`${date}T00:00:00Z`));

const calculateOpenPercent = (value: number, open: number): number | null => (
  open === 0 ? null : ((value - open) / open) * 100
);

const csvCell = (value: string | number | null): string => {
  if (value == null) return "";
  const text = typeof value === "number"
    ? (Number.isInteger(value) ? String(value) : value.toFixed(2))
    : value;
  return /[",\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
};

const deliveryByDate = (deliveryDays: DeliveryDayDetail[]): Map<string, DeliveryDayDetail> => (
  new Map(deliveryDays.map((delivery) => [delivery.date, delivery]))
);

export const buildStockHistoryRows = (data: StockDetailResponse): StockHistoryCsvRow[] => {
  const delivery = deliveryByDate(data.delivery_days);
  return data.days.map((day) => {
    const deliveryDay = delivery.get(day.date);
    return {
      key: day.date,
      date: day.date,
      day: formatDay(day.date),
      open: day.open,
      close: day.close,
      low: day.low,
      high: day.high,
      openToHighPct: calculateOpenPercent(day.high, day.open),
      openToClosePct: calculateOpenPercent(day.close, day.open),
      volume: day.volume,
      deliveryVolume: deliveryDay?.delivered_quantity ?? null,
      deliveryPct: deliveryDay?.delivery_percentage ?? null,
    };
  });
};

export const buildStockHistoryCsv = (rows: StockHistoryCsvRow[]): string => [
  CSV_HEADERS,
  ...rows.map((row) => [
    row.date,
    row.day,
    row.open,
    row.close,
    row.low,
    row.high,
    row.openToHighPct,
    row.openToClosePct,
    row.volume,
    row.deliveryVolume,
    row.deliveryPct,
  ]),
].map((row) => row.map(csvCell).join(",")).join("\n");
