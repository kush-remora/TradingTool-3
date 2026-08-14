import { Tooltip } from "antd";
import type { AccumulationHeatmapDay } from "../utils/accumulationScanner";

type HeatmapMetric = "buyingInterest" | "greenClose" | "quietMove" | "volumeDryUp";

const HEATMAP_METRICS: Array<{ key: HeatmapMetric; label: string }> = [
  { key: "buyingInterest", label: "Buy" },
  { key: "greenClose", label: "Green" },
  { key: "quietMove", label: "Quiet" },
  { key: "volumeDryUp", label: "Vol" },
];

function formatPercent(value: number | null): string {
  return value == null ? "—" : `${value >= 0 ? "+" : ""}${value.toFixed(1)}%`;
}

function formatDayLabel(date: string): string {
  return new Intl.DateTimeFormat("en-IN", {
    weekday: "short",
    day: "2-digit",
    month: "short",
    timeZone: "UTC",
  }).format(new Date(`${date}T00:00:00Z`));
}

function formatVolumeRatio(day: AccumulationHeatmapDay): string {
  if (day.averageVolume10 == null || day.averageVolume10 <= 0) return "—";
  return `${(day.volume / day.averageVolume10).toFixed(1)}×`;
}

function formatDayTitle(day: AccumulationHeatmapDay, metric: HeatmapMetric): string {
  if (metric === "buyingInterest") {
    return `${formatDayLabel(day.date)} ${day.closeLocationPct == null ? "—" : `${day.closeLocationPct.toFixed(1)}%`}`;
  }
  if (metric === "greenClose") {
    return `${formatDayLabel(day.date)} ${formatPercent(day.closeChangePct)}`;
  }
  if (metric === "quietMove") {
    return `${formatDayLabel(day.date)} ${formatPercent(day.closeChangePct)}`;
  }
  return `${formatDayLabel(day.date)} ${formatVolumeRatio(day)}`;
}

function getDotState(value: boolean | null): string {
  if (value == null) return "unavailable";
  return value ? "active" : "inactive";
}

function HeatmapLine({
  days,
  metric,
  label,
}: {
  days: AccumulationHeatmapDay[];
  metric: HeatmapMetric;
  label: string;
}) {
  return (
    <div className="accumulation-heatmap-line">
      <span className="accumulation-heatmap-label">{label}</span>
      <span className="accumulation-heatmap-dots">
        {days.map((day) => (
          <Tooltip
            key={`${metric}-${day.date}`}
            title={formatDayTitle(day, metric)}
            mouseEnterDelay={0}
            arrow={false}
            styles={{
              container: {
                padding: "3px 6px",
                borderRadius: 4,
                background: "rgba(255, 255, 255, 0.92)",
                color: "#344054",
                fontSize: 10,
                lineHeight: "14px",
                boxShadow: "0 2px 8px rgba(16, 24, 40, 0.12)",
              },
            }}
          >
            <span
              className={`accumulation-heatmap-dot accumulation-heatmap-dot-${metric} accumulation-heatmap-dot-${getDotState(day[metric])}`}
              title={formatDayTitle(day, metric)}
              aria-label={formatDayTitle(day, metric)}
            />
          </Tooltip>
        ))}
      </span>
    </div>
  );
}

export function AccumulationHeatmap({ days }: { days: AccumulationHeatmapDay[] }) {
  return (
    <div className="accumulation-heatmap" aria-label="Latest 20-session accumulation heatmap">
      {HEATMAP_METRICS.map(({ key, label }) => (
        <HeatmapLine key={key} days={days} metric={key} label={label} />
      ))}
    </div>
  );
}
