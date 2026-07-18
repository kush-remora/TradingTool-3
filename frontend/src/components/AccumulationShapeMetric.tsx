import { Tooltip, Typography } from "antd";
import type { AccumulationShapeMetrics } from "../types";

interface AccumulationShapeMetricProps {
  metrics: AccumulationShapeMetrics | null;
}

function signed(value: number, digits: number): string {
  return `${value >= 0 ? "+" : ""}${value.toFixed(digits)}`;
}

export function AccumulationShapeMetric({ metrics }: AccumulationShapeMetricProps) {
  if (!metrics) return "-";

  const vertex = metrics.vertexPosition === null ? "outside / none" : signed(metrics.vertexPosition, 2);
  const title = <div>
    <div>Center slope: {signed(metrics.centerSlopePerTenSessions, 2)}% per 10 sessions</div>
    <div>Start → end slope: {signed(metrics.startSlopePerTenSessions, 2)}% → {signed(metrics.endSlopePerTenSessions, 2)}%</div>
    <div>Curvature: {signed(metrics.curvature, 3)} · turn: {vertex} (−1 start, +1 end)</div>
  </div>;

  return <Tooltip title={title}><Typography.Text style={{ fontSize: 12 }}>{signed(metrics.centerSlopePerTenSessions, 1)}%/10 · a {signed(metrics.curvature, 2)}</Typography.Text></Tooltip>;
}
