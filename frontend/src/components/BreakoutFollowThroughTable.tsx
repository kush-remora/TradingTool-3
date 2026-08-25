import { Card, Empty, Typography } from "antd";
import type { ReactNode } from "react";
import "../pages/adaptiveBreakoutScanner.css";

const { Text } = Typography;

export interface BreakoutFollowThroughDay {
  date: string;
  close: number;
  volume: number;
  volumeMultiple: number | null;
  closePositionPct: number | null;
}

export interface BreakoutFollowThroughEntry {
  symbol: string;
  companyName: string;
  breakoutDay: BreakoutFollowThroughDay;
  highestVolumeDay: BreakoutFollowThroughDay | null;
  currentPrice: number;
  currentDate: string;
  returnPct: number | null;
  daysPassed: number | null;
  stockDetailUrl: string;
}

function formatPrice(value: number): string {
  return `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatInteger(value: number): string {
  return value.toLocaleString("en-IN");
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat("en-IN", { day: "2-digit", month: "short", timeZone: "UTC" })
    .format(new Date(`${value}T00:00:00Z`));
}

function formatDateWithYear(value: string): string {
  return new Intl.DateTimeFormat("en-IN", { day: "2-digit", month: "short", year: "numeric", timeZone: "UTC" })
    .format(new Date(`${value}T00:00:00Z`));
}

function formatPercent(value: number | null): string {
  if (value == null) return "—";
  return `${value >= 0 ? "+" : ""}${value.toFixed(1)}%`;
}

function formatMultiple(value: number | null): string {
  return value == null ? "—" : `${value.toFixed(2)}×`;
}

function closePositionBucket(value: number | null): "HIGH" | "MIDDLE" | "LOW" | "UNKNOWN" {
  if (value == null) return "UNKNOWN";
  if (value >= 75) return "HIGH";
  if (value <= 25) return "LOW";
  return "MIDDLE";
}

function BreakoutPositionCell({ value }: { value: number | null }): ReactNode {
  const bucket = closePositionBucket(value);
  return (
    <span className={`adaptive-breakout-follow-position adaptive-breakout-follow-position-${bucket.toLowerCase()}`}>
      <strong>{value == null ? "—" : `${value.toFixed(0)}%`}</strong>
      <small>{bucket === "UNKNOWN" ? "unavailable" : bucket === "MIDDLE" ? "mid-range" : bucket.toLowerCase()}</small>
    </span>
  );
}

export function BreakoutFollowThroughTable({ entries }: { entries: BreakoutFollowThroughEntry[] }): ReactNode {
  return (
    <Card className="adaptive-breakout-follow-through-card" data-testid="adaptive-breakout-follow-through-card">
      <div className="adaptive-breakout-follow-through-header">
        <div>
          <strong>Already in breakout zone</strong>
          <span>Breakout day vs the highest-volume session after breakout · {entries.length} stock{entries.length === 1 ? "" : "s"}</span>
        </div>
        <span className="adaptive-breakout-follow-through-key">Close position = close within that day&apos;s low → high range</span>
      </div>
      {entries.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No completed breakout continuations in this scan." />
      ) : (
        <div className="adaptive-breakout-follow-through-table-wrap">
          <table className="adaptive-breakout-follow-through-table" data-testid="adaptive-breakout-follow-through-table">
            <thead>
              <tr>
                <th>Stock</th>
                <th>Evidence day</th>
                <th>Date</th>
                <th>Close price</th>
                <th>Volume</th>
                <th>Volume vs 10D avg</th>
                <th>Close position</th>
                <th>Today</th>
                <th>Change from breakout</th>
                <th>Days passed</th>
              </tr>
            </thead>
            <tbody>
              {entries.flatMap((entry) => {
                const evidenceRows: Array<{ label: string; day: BreakoutFollowThroughDay | null }> = [
                  { label: "Breakout day", day: entry.breakoutDay },
                  { label: "Highest volume after breakout", day: entry.highestVolumeDay },
                ];
                return evidenceRows.map(({ label, day }, index) => (
                  <tr className={index === 0 ? "adaptive-breakout-follow-breakout-row" : "adaptive-breakout-follow-volume-row"} key={`${entry.symbol}-${label}`}>
                    {index === 0 && (
                      <td className="adaptive-breakout-follow-stock" rowSpan={2}>
                        <a href={entry.stockDetailUrl} target="_blank" rel="noopener noreferrer">
                          <strong>{entry.symbol}</strong>
                        </a>
                        <span>{entry.companyName}</span>
                        <small>Already broken · {formatDateWithYear(entry.breakoutDay.date)}</small>
                      </td>
                    )}
                    <td className="adaptive-breakout-follow-evidence-label">{label}</td>
                    <td>{day == null ? "—" : formatDate(day.date)}</td>
                    <td>{day == null ? "—" : formatPrice(day.close)}</td>
                    <td>{day == null ? "—" : formatInteger(day.volume)}</td>
                    <td>{day == null ? "—" : formatMultiple(day.volumeMultiple)}</td>
                    <td>{day == null ? "—" : <BreakoutPositionCell value={day.closePositionPct} />}</td>
                    {index === 0 && (
                      <>
                        <td className="adaptive-breakout-follow-today" rowSpan={2}>
                          <strong>{formatPrice(entry.currentPrice)}</strong>
                          <small>{formatDate(entry.currentDate)}</small>
                        </td>
                        <td className={`adaptive-breakout-follow-return ${entry.returnPct == null || entry.returnPct === 0 ? "flat" : entry.returnPct > 0 ? "positive" : "negative"}`} rowSpan={2}>
                          <strong>{formatPercent(entry.returnPct)}</strong>
                          <small>close → current price</small>
                        </td>
                        <td className="adaptive-breakout-follow-days" rowSpan={2}>
                          <strong>{entry.daysPassed == null ? "—" : `${entry.daysPassed}d`}</strong>
                          <small>calendar days</small>
                        </td>
                      </>
                    )}
                  </tr>
                ));
              })}
            </tbody>
          </table>
        </div>
      )}
      <Text className="adaptive-breakout-follow-through-note" type="secondary">
        The second row searches all available sessions after the breakout, not only the visible 20D modal. Volume multiples use the preceding 10 completed sessions and exclude the day being measured. This helps judge whether the move is still orderly; it is not an entry signal by itself.
      </Text>
    </Card>
  );
}
