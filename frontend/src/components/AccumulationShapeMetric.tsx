import { Tooltip, Typography } from "antd";
import type { AccumulationLineFitMetrics, AccumulationShapeMetrics } from "../types";

interface AccumulationShapeMetricProps {
  shape: string;
  metrics: AccumulationShapeMetrics | null;
  lineFit: AccumulationLineFitMetrics | null;
}

function signed(value: number, digits: number): string {
  return `${value >= 0 ? "+" : ""}${value.toFixed(digits)}`;
}

export function AccumulationShapeMetric({ shape, metrics, lineFit }: AccumulationShapeMetricProps) {
  if (lineFit && (shape === "FLAT" || shape === "FLAT_GOLDEN")) {
    const title = <div>
      <div>Line direction: {signed(lineFit.slopePerTenSessions, 2)}% per 10 sessions</div>
      <div>Typical distance from the line: {lineFit.typicalDeviationPercent.toFixed(2)}%</div>
      <div>Largest remaining distance: {lineFit.maximumDeviationPercent.toFixed(2)}%</div>
      {lineFit.ignoredOutlierDate && <div>Ignored shock: {lineFit.ignoredOutlierDate}{lineFit.ignoredOutlierDeviationPercent === null ? "" : ` (${lineFit.ignoredOutlierDeviationPercent.toFixed(2)}% away)`}</div>}
    </div>;
    return <Tooltip title={title}><Typography.Text style={{ fontSize: 12 }}>line {signed(lineFit.slopePerTenSessions, 1)}%/10 · ±{lineFit.typicalDeviationPercent.toFixed(1)}%{lineFit.ignoredOutlierDate ? " ⚠" : ""}</Typography.Text></Tooltip>;
  }
  if (!metrics) return "-";

  const vertex = metrics.vertexPosition === null ? "outside / none" : signed(metrics.vertexPosition, 2);
  const title = <div>
    <div>Center slope: {signed(metrics.centerSlopePerTenSessions, 2)}% per 10 sessions</div>
    <div>Start → end slope: {signed(metrics.startSlopePerTenSessions, 2)}% → {signed(metrics.endSlopePerTenSessions, 2)}%</div>
    <div>Curvature: {signed(metrics.curvature, 3)} · turn: {vertex} (−1 start, +1 end)</div>
  </div>;

  return <Tooltip title={title}><Typography.Text style={{ fontSize: 12 }}>{signed(metrics.centerSlopePerTenSessions, 1)}%/10 · a {signed(metrics.curvature, 2)}</Typography.Text></Tooltip>;
}
