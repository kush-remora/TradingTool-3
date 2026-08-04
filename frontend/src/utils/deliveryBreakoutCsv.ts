import type { DeliveryBreakoutDashboardRow } from "../types";

const CSV_HEADERS = [
  "Symbol",
  "Instrument Token",
  "Event Date",
  "Event Type",
  "Price",
  "Event Day Change %",
  "Volume Avg 10D",
  "Volume Today",
  "Volume Shock",
  "Delivery Avg 10D",
  "Delivery Today",
  "Delivery Shock",
  "Delivery %",
  "52W High Distance %",
  "52W Low Distance %",
];

function escapeCsvValue(value: string | number | null | undefined): string {
  const text = value == null ? "" : String(value);
  return `"${text.replace(/"/g, '""')}"`;
}

function distancePercent(price: number | null, referencePrice: number | null): number | null {
  if (price == null || referencePrice == null || referencePrice === 0) return null;
  return ((price - referencePrice) / referencePrice) * 100;
}

export function buildDeliveryBreakoutCsv(rows: DeliveryBreakoutDashboardRow[]): string {
  const csvRows = rows.map((row) => {
    const price = row.close;
    return [
      row.symbol,
      row.instrument_token,
      row.event_date,
      row.event_type,
      price,
      row.close_pct_change,
      row.average_volume_10d,
      row.volume,
      row.volume_ratio,
      row.average_delivery_quantity_10d,
      row.delivery_quantity,
      row.delivery_ratio,
      row.delivery_percentage,
      distancePercent(price, row.fifty_two_week_high),
      distancePercent(price, row.fifty_two_week_low),
    ];
  });

  return [CSV_HEADERS, ...csvRows]
    .map((row) => row.map((value) => escapeCsvValue(value)).join(","))
    .join("\r\n") + "\r\n";
}
