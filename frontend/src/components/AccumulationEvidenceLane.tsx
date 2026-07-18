import { Space, Tag, Tooltip, Typography } from "antd";
import type { AccumulationEvidenceLane } from "../types";

const sources = [
  { key: "accumulation", label: "A", color: "#1677ff" },
  { key: "phaseD", label: "D", color: "#52c41a" },
  { key: "freshBreakout", label: "B", color: "#fa8c16" },
  { key: "fiftyTwoWeekHigh", label: "H", color: "#722ed1" },
] as const;

interface AccumulationEvidenceLaneProps {
  evidence: AccumulationEvidenceLane | null;
}

export function AccumulationEvidenceLaneView({ evidence }: AccumulationEvidenceLaneProps) {
  if (!evidence) return <Typography.Text type="secondary">Rerun required</Typography.Text>;
  const start = Date.parse(evidence.fromDate);
  const duration = Math.max(Date.parse(evidence.toDate) - start, 1);
  const dateLists = sources.map((source) => ({ ...source, dates: evidence[source.key] }));
  const tooltip = <Space orientation="vertical" size={2}>{dateLists.map((source) => <span key={source.key}>{source.label}: {source.dates.join(", ") || "-"}</span>)}</Space>;

  return <Tooltip title={tooltip}><div style={{ minWidth: 210 }}>
    <div style={{ position: "relative", height: 16, borderRadius: 8, background: "#f0f0f0", overflow: "hidden" }}>
      {dateLists.flatMap((source, sourceIndex) => source.dates.map((date) => <span key={`${source.key}-${date}`} style={{ position: "absolute", left: `${Math.min(Math.max(((Date.parse(date) - start) / duration) * 100, 0), 100)}%`, top: sourceIndex * 3, width: 5, height: 10, borderRadius: 3, background: source.color, transform: "translateX(-50%)" }} />))}
    </div>
    <Space size={5} style={{ fontSize: 11, marginTop: 3 }}>{dateLists.map((source) => <span key={source.key} style={{ color: source.color }}>{source.label} {source.dates.length}</span>)}</Space>
  </div></Tooltip>;
}

export function AccumulationEvidenceDates({ evidence }: AccumulationEvidenceLaneProps) {
  if (!evidence) return <Typography.Text type="secondary">Rerun required to show the saved six-month evidence.</Typography.Text>;
  return <Space wrap size={4}>{sources.map((source) => <Tag key={source.key} color={source.color}>{source.label}: {evidence[source.key].join(", ") || "-"}</Tag>)}</Space>;
}
